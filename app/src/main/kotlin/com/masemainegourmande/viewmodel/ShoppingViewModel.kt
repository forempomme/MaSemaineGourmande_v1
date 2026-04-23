package com.masemainegourmande.viewmodel

import androidx.lifecycle.*
import com.masemainegourmande.data.model.*
import com.masemainegourmande.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ShoppingGroup(
    val categoryId:   String,
    val categoryName: String,
    val items:        List<ShoppingItemEntity>
)

class ShoppingViewModel(private val repo: AppRepository) : ViewModel() {

    private val rawItems      = repo.observeActiveShoppingItems()
    private val rawCategories = repo.observeCategories()

    // In-memory item order: catId -> ordered list of itemIds
    // Survives config changes, resets on app restart (acceptable for shopping list)
    private val _itemOrders = mutableMapOf<String, List<String>>()

    val groups: StateFlow<List<ShoppingGroup>> =
        combine(rawItems, rawCategories) { items, cats ->
            cats.sortedBy { it.sortOrder }.map { cat ->
                val catItems    = items.filter { it.categoryId == cat.id }
                val orderedIds  = _itemOrders[cat.id]
                val orderedItems = if (orderedIds != null) {
                    orderedIds.mapNotNull { id -> catItems.find { it.id == id } } +
                    catItems.filter { item -> orderedIds.none { it == item.id } }
                } else catItems
                ShoppingGroup(
                    categoryId   = cat.id,
                    categoryName = cat.name,
                    items        = orderedItems
                )
            }.filter { it.items.isNotEmpty() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> =
        groups.map { g -> g.sumOf { it.items.size } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Add recipe to shopping list ───────────────────────────

    fun addRecipe(recipe: RecipeEntity, persons: Int) {
        viewModelScope.launch {
            val categories = repo.getCategories()
            repo.addRecipeToShopping(recipe, persons, categories)
        }
    }

    fun addManualItem(name: String, qty: Double, unit: String) {
        viewModelScope.launch {
            val categories = repo.getCategories()
            repo.addManualShoppingItem(name, qty, unit, categories)
        }
    }

    // ── Check / delete ────────────────────────────────────────

    fun setChecked(id: String, checked: Boolean) {
        viewModelScope.launch { repo.setItemChecked(id, checked) }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch { repo.deleteShoppingItem(id) }
    }

    fun clearChecked() {
        viewModelScope.launch { repo.clearCheckedItems() }
    }

    fun clearAll() {
        // Uses the correct method name from AppRepository
        viewModelScope.launch { repo.clearAllShoppingItems() }
    }


    // ── Drag-drop reorder ────────────────────────────────────

    /** Move category [fromId] to position of [toId]. All items in the category follow. */
    fun reorderCategory(fromId: String, toId: String) {
        viewModelScope.launch {
            val cats = repo.getCategories().sortedBy { it.sortOrder }.toMutableList()
            val fromIdx = cats.indexOfFirst { it.id == fromId }
            val toIdx   = cats.indexOfFirst { it.id == toId }
            if (fromIdx == -1 || toIdx == -1 || fromIdx == toIdx) return@launch
            val moved = cats.removeAt(fromIdx)
            cats.add(toIdx, moved)
            cats.forEachIndexed { i, cat -> repo.upsertCategory(cat.copy(sortOrder = i)) }
        }
    }

    /** Move item [fromItemId] to position of [toItemId] — in-memory only, no DAO changes. */
    fun reorderItem(catId: String, fromItemId: String, toItemId: String) {
        val current = (_itemOrders[catId]
            ?: groups.value.find { it.categoryId == catId }?.items?.map { it.id }
            ?: return).toMutableList()
        val fromIdx = current.indexOf(fromItemId)
        val toIdx   = current.indexOf(toItemId)
        if (fromIdx == -1 || toIdx == -1 || fromIdx == toIdx) return
        val moved = current.removeAt(fromIdx)
        current.add(toIdx, moved)
        _itemOrders[catId] = current
    }

    // ── Share text ────────────────────────────────────────────

    fun buildShareText(groups: List<ShoppingGroup>): String {
        if (groups.isEmpty()) return "Liste de courses vide."
        return buildString {
            appendLine("🛒 Liste de courses")
            appendLine()
            for (group in groups) {
                appendLine(group.categoryName)
                for (item in group.items) {
                    val qtyPart = if (item.qty > 0)
                        " — ${fmtQty(item.qty)} ${item.unit}".trimEnd() else ""
                    appendLine("• ${item.name}$qtyPart")
                }
                appendLine()
            }
        }.trim()
    }

    private fun fmtQty(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

    companion object {
        fun factory(repo: AppRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShoppingViewModel(repo) as T
        }
    }
}

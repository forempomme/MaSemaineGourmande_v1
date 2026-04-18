package com.masemainegourmande.viewmodel

import androidx.lifecycle.*
import com.masemainegourmande.data.model.*
import com.masemainegourmande.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════
// GROUPED SHOPPING UI MODEL
// ═══════════════════════════════════════════════════════════════

data class ShoppingGroup(
    val categoryId:   String,
    val categoryName: String,
    val items:        List<ShoppingItemEntity>
)

// ═══════════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════════

class ShoppingViewModel(private val repo: AppRepository) : ViewModel() {

    private val rawItems     = repo.observeActiveShoppingItems()
    private val rawCategories = repo.observeCategories()

    /** Shopping items grouped by category, sorted by category order. */
    val groups: StateFlow<List<ShoppingGroup>> =
        combine(rawItems, rawCategories) { items, cats ->
            val catMap = cats.associateBy { it.id }
            cats.map { cat ->
                ShoppingGroup(
                    categoryId   = cat.id,
                    categoryName = cat.name,
                    items        = items.filter { it.categoryId == cat.id }
                )
            }.filter { it.items.isNotEmpty() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> =
        groups.map { g -> g.sumOf { it.items.size } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Add recipe ingredients to list ────────────────────────

    fun addRecipe(recipe: RecipeEntity, persons: Int) {
        viewModelScope.launch {
            val categories = repo.getCategories()
            repo.addRecipeToShopping(recipe, persons, categories)
        }
    }

    // ── Manual item ───────────────────────────────────────────

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

    fun clearChecked() { viewModelScope.launch { repo.clearCheckedItems() } }
    fun clearAll()     { viewModelScope.launch { repo.clearAllItems() } }

    // ── Plain-text share export ───────────────────────────────

    fun buildShareText(groups: List<ShoppingGroup>): String {
        if (groups.isEmpty()) return "Liste de courses vide."
        return buildString {
            appendLine("🛒 Liste de courses")
            appendLine()
            for (group in groups) {
                appendLine(group.categoryName)
                for (item in group.items) {
                    val qtyPart = if (item.qty > 0)
                        " — ${formatQty(item.qty)} ${item.unit}".trimEnd() else ""
                    appendLine("• ${item.name}$qtyPart")
                }
                appendLine()
            }
        }.trim()
    }

    private fun formatQty(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else "%.1f".format(v)

    // ── Factory ───────────────────────────────────────────────

    companion object {
        fun factory(repo: AppRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ShoppingViewModel(repo) as T
            }
    }
}

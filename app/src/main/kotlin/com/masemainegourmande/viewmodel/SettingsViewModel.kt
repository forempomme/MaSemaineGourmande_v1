package com.masemainegourmande.viewmodel

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.*
import com.masemainegourmande.data.model.CategoryEntity
import com.masemainegourmande.data.model.PantryEntity
import com.masemainegourmande.data.model.encodeJson
import com.masemainegourmande.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(
    private val repo: AppRepository,
    private val prefs: android.content.SharedPreferences
) : ViewModel() {

    val defaultPersons = MutableStateFlow(prefs.getInt("default_persons", 4))

    val categories = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pantry = repo.observePantry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDefaultPersons(n: Int) {
        defaultPersons.value = n
        prefs.edit { putInt("default_persons", n) }
    }

    // ── Pantry ────────────────────────────────────────────────

    fun addPantryItem(name: String) {
        viewModelScope.launch { repo.addPantryItem(name) }
    }

    fun togglePantry(item: PantryEntity) {
        viewModelScope.launch { repo.setPantryChecked(item.id, !item.checked) }
    }

    fun deletePantryItem(id: String) {
        viewModelScope.launch { repo.deletePantryItem(id) }
    }

    fun uncheckAllPantry() {
        viewModelScope.launch { repo.uncheckAllPantry() }
    }

    // ── Categories ────────────────────────────────────────────

    fun upsertCategory(cat: CategoryEntity) {
        viewModelScope.launch { repo.upsertCategory(cat) }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch { repo.deleteCategory(id) }
    }

    fun newCategory(name: String, keywords: List<String>) {
        viewModelScope.launch {
            repo.upsertCategory(
                CategoryEntity(
                    id       = UUID.randomUUID().toString(),
                    name     = name,
                    keywords = keywords.encodeJson(),
                    sortOrder = categories.value.size
                )
            )
        }
    }

    companion object {
        fun factory(repo: AppRepository, context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(c: Class<T>): T =
                SettingsViewModel(repo, context.getSharedPreferences("msg_prefs", Context.MODE_PRIVATE)) as T
        }
    }
}

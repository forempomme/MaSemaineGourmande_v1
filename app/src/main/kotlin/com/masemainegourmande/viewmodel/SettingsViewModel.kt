package com.masemainegourmande.viewmodel

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.*
import com.masemainegourmande.data.model.*
import com.masemainegourmande.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

// ── Backup data classes ──────────────────────────────────────

@Serializable
data class AppBackup(
    val version: String = "1.6",
    val defaultPersons: Int = 4,
    val recipes: List<RecipeBackup> = emptyList(),
    val meals: List<MealBackup> = emptyList(),
    val categories: List<CatBackup> = emptyList()
)

@Serializable
data class RecipeBackup(
    val id: String, val name: String, val emoji: String,
    val portions: Int, val url: String = "", val tags: String = "[]",
    val ingredients: String = "[]", val steps: String = "[]",
    val favorite: Boolean = false, val rating: Int = 0, val note: String = ""
)

@Serializable
data class MealBackup(
    val id: String, val weekKey: String, val recipeId: String, val persons: Int
)

@Serializable
data class CatBackup(
    val id: String, val name: String, val keywords: String = "[]", val sortOrder: Int = 0
)

private val BKP_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class SettingsViewModel(
    private val repo: AppRepository,
    private val prefs: android.content.SharedPreferences
) : ViewModel() {

    val defaultPersons = MutableStateFlow(prefs.getInt("default_persons", 4))
    val categories     = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pantry         = repo.observePantry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Export JSON — returns via callback to avoid blocking UI
    val exportJson = MutableStateFlow<String?>(null)

    fun prepareExport() {
        viewModelScope.launch {
            val recipes   = repo.observeRecipes().first()
            val meals     = repo.observeAllMeals().first()
            val cats      = repo.getCategories()
            val backup    = AppBackup(
                defaultPersons = defaultPersons.value,
                recipes  = recipes.map { RecipeBackup(it.id, it.name, it.emoji, it.portions,
                    it.url, it.tags, it.ingredients, it.steps, it.favorite, it.rating, it.note) },
                meals    = meals.map { MealBackup(it.id, it.weekKey, it.recipeId, it.persons) },
                categories = cats.map { CatBackup(it.id, it.name, it.keywords, it.sortOrder) }
            )
            exportJson.value = BKP_JSON.encodeToString(backup)
        }
    }

    fun importFromJson(json: String) {
        viewModelScope.launch {
            runCatching {
                val backup = BKP_JSON.decodeFromString<AppBackup>(json)
                setDefaultPersons(backup.defaultPersons)
                backup.categories.forEach { c ->
                    repo.upsertCategory(CategoryEntity(c.id, c.name, c.keywords, c.sortOrder))
                }
                backup.recipes.forEach { r ->
                    repo.upsertRecipe(RecipeEntity(r.id, r.name, r.emoji, r.portions, r.url,
                        r.tags, r.ingredients, r.steps, r.favorite, r.rating, r.note))
                }
                backup.meals.forEach { m ->
                    repo.addMeals(m.weekKey, listOf(m.recipeId to m.persons))
                }
            }
        }
    }

    fun setDefaultPersons(n: Int) {
        defaultPersons.value = n
        prefs.edit { putInt("default_persons", n) }
    }

    fun upsertCategory(cat: CategoryEntity) { viewModelScope.launch { repo.upsertCategory(cat) } }
    fun deleteCategory(id: String)          { viewModelScope.launch { repo.deleteCategory(id) } }
    fun newCategory(name: String, keywords: List<String>) {
        viewModelScope.launch {
            repo.upsertCategory(CategoryEntity(UUID.randomUUID().toString(), name,
                keywords.encodeJson(), categories.value.size))
        }
    }

    fun addPantryItem(name: String)              { viewModelScope.launch { repo.addPantryItem(name) } }
    fun togglePantry(item: PantryEntity)         { viewModelScope.launch { repo.setPantryChecked(item.id, !item.checked) } }
    fun deletePantryItem(id: String)             { viewModelScope.launch { repo.deletePantryItem(id) } }
    fun uncheckAllPantry()                       { viewModelScope.launch { repo.uncheckAllPantry() } }

    companion object {
        fun factory(repo: AppRepository, context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(c: Class<T>): T =
                SettingsViewModel(repo, context.getSharedPreferences("msg_prefs", Context.MODE_PRIVATE)) as T
        }
    }
}

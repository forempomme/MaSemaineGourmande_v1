package com.masemainegourmande.viewmodel

import androidx.lifecycle.*
import com.masemainegourmande.data.model.*
import com.masemainegourmande.data.repository.AppRepository
import com.masemainegourmande.util.IsoWeekHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class RecipeSort { FAVORITES, RATING, NAME, COOKED, PORTIONS }

class RecipesViewModel(private val repo: AppRepository) : ViewModel() {

    val recipes: StateFlow<List<RecipeEntity>> =
        repo.observeRecipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allMeals: StateFlow<List<MealEntity>> =
        repo.observeAllMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Filters ───────────────────────────────────────────────
    val searchQuery = MutableStateFlow("")
    val activeTag   = MutableStateFlow<String?>(null)
    val sortMode    = MutableStateFlow(RecipeSort.FAVORITES)

    val cookCounts: StateFlow<Map<String, Int>> =
        allMeals.map { meals ->
            meals.groupBy { it.recipeId }.mapValues { it.value.size }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val lastCookedMap: StateFlow<Map<String, String?>> =
        allMeals.map { meals ->
            val cur = IsoWeekHelper.today()
            meals.groupBy { it.recipeId }.mapValues { (_, recipeMeals) ->
                val best = recipeMeals.mapNotNull { meal ->
                    IsoWeekHelper.parseKey(meal.weekKey)?.let { yw ->
                        IsoWeekHelper.weeksAgo(yw.year, yw.week)
                    }?.takeIf { it >= 0 }
                }.minOrNull()
                best?.let { IsoWeekHelper.lastCookedLabel(it) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val allTags: StateFlow<List<String>> =
        recipes.map { list ->
            list.flatMap { it.parseTags() }.distinct().sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filtered: StateFlow<List<RecipeEntity>> =
        combine(recipes, searchQuery, activeTag, sortMode, cookCounts) { list, q, tag, sort, counts ->
            var result = list.filter { r ->
                val matchQ = q.isBlank() || r.name.contains(q, true) ||
                             r.parseIngredients().any { it.name.contains(q, true) }
                val matchT = tag == null || r.parseTags().contains(tag)
                matchQ && matchT
            }
            result = when (sort) {
                RecipeSort.FAVORITES -> result.sortedWith(
                    compareByDescending<RecipeEntity> { if (it.favorite) 1 else 0 }
                        .thenByDescending { it.rating }
                        .thenBy { it.name }
                )
                RecipeSort.RATING   -> result.sortedWith(
                    compareByDescending<RecipeEntity> { it.rating }.thenBy { it.name }
                )
                RecipeSort.NAME     -> result.sortedBy { it.name }
                RecipeSort.COOKED   -> result.sortedByDescending { counts[it.id] ?: 0 }
                RecipeSort.PORTIONS -> result.sortedByDescending { it.portions }
            }
            result
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Mutations ─────────────────────────────────────────────

    fun saveNew(parsed: ParsedRecipe) {
        viewModelScope.launch { repo.saveRecipe(parsed) }
    }

    fun upsert(recipe: RecipeEntity) {
        viewModelScope.launch { repo.upsertRecipe(recipe) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteRecipe(id) }
    }

    fun toggleFavorite(recipe: RecipeEntity) {
        viewModelScope.launch { repo.upsertRecipe(recipe.copy(favorite = !recipe.favorite)) }
    }

    fun setRating(recipe: RecipeEntity, rating: Int) {
        viewModelScope.launch { repo.upsertRecipe(recipe.copy(rating = rating)) }
    }

    companion object {
        fun factory(repo: AppRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(c: Class<T>): T = RecipesViewModel(repo) as T
        }
    }
}

package com.masemainegourmande.viewmodel

import androidx.lifecycle.*
import com.masemainegourmande.data.model.MealEntity
import com.masemainegourmande.data.model.RecipeEntity
import com.masemainegourmande.data.repository.AppRepository
import com.masemainegourmande.util.IsoWeekHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WeekMeals(
    val weekKey: String,
    val year: Int,
    val week: Int,
    val meals: List<MealWithRecipe>
)

data class MealWithRecipe(
    val meal: MealEntity,
    val recipe: RecipeEntity
)

class PlanningViewModel(private val repo: AppRepository) : ViewModel() {

    val allMeals: StateFlow<List<MealEntity>> =
        repo.observeAllMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recipes: StateFlow<List<RecipeEntity>> =
        repo.observeRecipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Returns MealWithRecipe list for a given weekKey. */
    fun mealsForWeek(weekKey: String): List<MealWithRecipe> {
        val recipeMap = recipes.value.associateBy { it.id }
        return allMeals.value
            .filter { it.weekKey == weekKey }
            .mapNotNull { meal ->
                recipeMap[meal.recipeId]?.let { MealWithRecipe(meal, it) }
            }
    }

    /** All weekKeys that have at least one meal, grouped by year. */
    val mealsByYear: StateFlow<Map<Int, List<WeekMeals>>> =
        combine(allMeals, recipes) { meals, recipeList ->
            val recipeMap = recipeList.associateBy { it.id }
            meals.groupBy { it.weekKey }
                .mapNotNull { (key, keyMeals) ->
                    val yw = IsoWeekHelper.parseKey(key) ?: return@mapNotNull null
                    val enriched = keyMeals.mapNotNull { meal ->
                        recipeMap[meal.recipeId]?.let { MealWithRecipe(meal, it) }
                    }
                    WeekMeals(key, yw.year, yw.week, enriched)
                }
                .groupBy { it.year }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun addMeals(weekKey: String, selections: List<Pair<String, Int>>) {
        viewModelScope.launch { repo.addMeals(weekKey, selections) }
    }


    fun toggleMealDone(mealId: String, currentDone: Boolean) {
        viewModelScope.launch {
            repo.updateMealDone(mealId, !currentDone)
        }
    }

    fun deleteMeal(id: String) {
        viewModelScope.launch { repo.deleteMeal(id) }
    }

    fun updatePersons(id: String, persons: Int) {
        viewModelScope.launch { repo.updateMealPersons(id, persons) }
    }

    fun duplicateWeek(srcKey: String, dstKey: String) {
        viewModelScope.launch { repo.duplicateWeek(srcKey, dstKey) }
    }

    companion object {
        fun factory(repo: AppRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(c: Class<T>): T = PlanningViewModel(repo) as T
        }
    }
}

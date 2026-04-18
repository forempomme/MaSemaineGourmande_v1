package com.masemainegourmande.viewmodel

import androidx.lifecycle.*
import com.masemainegourmande.data.model.MealEntity
import com.masemainegourmande.data.model.RecipeEntity
import com.masemainegourmande.data.repository.AppRepository
import com.masemainegourmande.util.IsoWeekHelper
import kotlinx.coroutines.flow.*
import java.time.LocalDate

data class StatsData(
    val totalMeals: Int,
    val totalPersonsMeals: Int,
    val uniqueRecipes: Int,
    val activeWeeks: Int,
    val streak: Int,
    val recordWeekMeals: Int,
    val monthData: List<Pair<String, Int>>,  // (monthLabel, count)
    val top5Recipes: List<Pair<RecipeEntity, Int>>,
    val top5Ingredients: List<Pair<String, Int>>,
    val neverCooked: List<RecipeEntity>
)

class StatsViewModel(private val repo: AppRepository) : ViewModel() {

    private val meals   = repo.observeAllMeals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val recipes = repo.observeRecipes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<StatsData> =
        combine(meals, recipes) { mealList, recipeList -> compute(mealList, recipeList) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), compute(emptyList(), emptyList()))

    private fun compute(mealList: List<MealEntity>, recipeList: List<RecipeEntity>): StatsData {
        val recipeMap = recipeList.associateBy { it.id }
        val today = IsoWeekHelper.today()
        val curYear = LocalDate.now().year
        val months = listOf("Jan","Fév","Mar","Avr","Mai","Juin","Juil","Aoû","Sep","Oct","Nov","Déc")

        // Counts
        val totalMeals = mealList.size
        val totalPM = mealList.sumOf { it.persons }
        val uniqueRec = mealList.map { it.recipeId }.distinct().size
        val activeWeeks = mealList.map { it.weekKey }.distinct().size

        // Cook counts per recipe
        val cookCounts = mealList.groupBy { it.recipeId }.mapValues { it.value.size }

        // Streak — consecutive weeks with ≥1 meal going backward from current
        var streak = 0
        var sy = today.year; var sw = today.week
        repeat(52) {
            val key = IsoWeekHelper.YearWeek(sy, sw).key
            if (mealList.any { it.weekKey == key }) {
                streak++
                sw--
                if (sw < 1) { sy--; sw = IsoWeekHelper.weeksInYear(sy) }
            } else return@repeat
        }

        // Record week
        val recordWeekMeals = mealList.groupBy { it.weekKey }.values.maxOfOrNull { it.size } ?: 0

        // Monthly data for current year
        val monthData = months.mapIndexed { idx, label ->
            val count = mealList.count { meal ->
                val yw = IsoWeekHelper.parseKey(meal.weekKey) ?: return@count false
                if (yw.year != curYear) return@count false
                val (mon, _) = IsoWeekHelper.weekBounds(yw.year, yw.week)
                mon.monthValue == idx + 1
            }
            label to count
        }

        // Top 5 recipes
        val top5Recipes = cookCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .mapNotNull { (id, count) -> recipeMap[id]?.let { it to count } }

        // Top 5 ingredients
        val ingCounts = mutableMapOf<String, Int>()
        mealList.forEach { meal ->
            recipeMap[meal.recipeId]?.parseIngredients()?.forEach { ing ->
                ingCounts[ing.name] = (ingCounts[ing.name] ?: 0) + 1
            }
        }
        val top5Ings = ingCounts.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }

        // Never cooked
        val cookedIds = cookCounts.keys.toSet()
        val neverCooked = recipeList.filter { it.id !in cookedIds }

        return StatsData(totalMeals, totalPM, uniqueRec, activeWeeks, streak, recordWeekMeals,
            monthData, top5Recipes, top5Ings, neverCooked)
    }

    companion object {
        fun factory(repo: AppRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(c: Class<T>): T = StatsViewModel(repo) as T
        }
    }
}

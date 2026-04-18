package com.masemainegourmande.data.repository

import com.masemainegourmande.data.DEFAULT_CATEGORIES
import com.masemainegourmande.data.db.AppDatabase
import com.masemainegourmande.data.detectCategory
import com.masemainegourmande.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AppRepository(private val db: AppDatabase) {

    // ── Recipes ───────────────────────────────────────────────

    fun observeRecipes(): Flow<List<RecipeEntity>> = db.recipeDao().observeAll()

    suspend fun saveRecipe(parsed: ParsedRecipe): String {
        val id = UUID.randomUUID().toString()
        db.recipeDao().upsert(parsed.toEntity(id))
        return id
    }

    suspend fun upsertRecipe(recipe: RecipeEntity) = db.recipeDao().upsert(recipe)

    suspend fun deleteRecipe(id: String) {
        db.recipeDao().deleteById(id)
        db.shoppingDao().deleteByRecipe(id)
        // meals referencing this recipe will stay (soft delete acceptable)
    }

    suspend fun getRecipe(id: String): RecipeEntity? = db.recipeDao().getById(id)

    // ── Planning ──────────────────────────────────────────────

    fun observeAllMeals(): Flow<List<MealEntity>> = db.mealDao().observeAll()

    suspend fun addMeals(weekKey: String, recipeIds: List<Pair<String, Int>>) {
        val meals = recipeIds.map { (recipeId, persons) ->
            MealEntity(
                id       = UUID.randomUUID().toString(),
                weekKey  = weekKey,
                recipeId = recipeId,
                persons  = persons
            )
        }
        db.mealDao().upsertAll(meals)
    }

    suspend fun deleteMeal(id: String) = db.mealDao().deleteById(id)

    suspend fun updateMealPersons(id: String, persons: Int) =
        db.mealDao().updatePersons(id, persons)

    /** Copy all meals from srcKey to dstKey (new IDs). */
    suspend fun duplicateWeek(srcKey: String, dstKey: String) {
        val src = db.mealDao().getByWeek(srcKey)
        val copies = src.map { m ->
            m.copy(id = UUID.randomUUID().toString(), weekKey = dstKey)
        }
        db.mealDao().upsertAll(copies)
    }

    // ── Shopping list ─────────────────────────────────────────

    fun observeActiveShoppingItems(): Flow<List<ShoppingItemEntity>> =
        db.shoppingDao().observeActive()

    suspend fun addRecipeToShopping(recipe: RecipeEntity, persons: Int, categories: List<CategoryEntity>) {
        val ratio = if (recipe.portions > 0) persons.toDouble() / recipe.portions else 1.0
        val items = recipe.parseIngredients().map { ing ->
            ShoppingItemEntity(
                id           = UUID.randomUUID().toString(),
                name         = ing.name,
                qty          = roundQty(ing.qty * ratio),
                unit         = ing.unit,
                categoryId   = detectCategory(ing.name, categories),
                fromRecipeId = recipe.id
            )
        }
        db.shoppingDao().upsertAll(items)
    }

    suspend fun addManualShoppingItem(name: String, qty: Double, unit: String, categories: List<CategoryEntity>) {
        db.shoppingDao().upsert(
            ShoppingItemEntity(
                id         = UUID.randomUUID().toString(),
                name       = name,
                qty        = qty,
                unit       = unit,
                categoryId = detectCategory(name, categories)
            )
        )
    }

    suspend fun setItemChecked(id: String, checked: Boolean) = db.shoppingDao().setChecked(id, checked)
    suspend fun deleteShoppingItem(id: String) = db.shoppingDao().deleteById(id)
    suspend fun clearCheckedItems() = db.shoppingDao().clearChecked()
    suspend fun clearAllShoppingItems() = db.shoppingDao().clearAll()

    // ── Categories ────────────────────────────────────────────

    fun observeCategories(): Flow<List<CategoryEntity>> = db.categoryDao().observeAll()
    suspend fun getCategories(): List<CategoryEntity> = db.categoryDao().getAll()

    suspend fun seedCategoriesIfEmpty() {
        if (db.categoryDao().getAll().isEmpty()) db.categoryDao().upsertAll(DEFAULT_CATEGORIES)
    }

    suspend fun upsertCategory(cat: CategoryEntity) = db.categoryDao().upsert(cat)
    suspend fun deleteCategory(id: String) = db.categoryDao().deleteById(id)

    // ── Pantry ────────────────────────────────────────────────

    fun observePantry(): Flow<List<PantryEntity>> = db.pantryDao().observeAll()

    suspend fun addPantryItem(name: String) {
        db.pantryDao().upsert(
            PantryEntity(id = UUID.randomUUID().toString(), name = name)
        )
    }

    suspend fun setPantryChecked(id: String, checked: Boolean) = db.pantryDao().setChecked(id, checked)
    suspend fun deletePantryItem(id: String) = db.pantryDao().deleteById(id)
    suspend fun uncheckAllPantry() = db.pantryDao().uncheckAll()

    // ── Import history ────────────────────────────────────────

    fun observeImportHistory(): Flow<List<ImportHistoryEntity>> = db.importHistoryDao().observeAll()

    suspend fun saveImportHistory(url: String, name: String, emoji: String) {
        db.importHistoryDao().upsert(ImportHistoryEntity(url = url, name = name, emoji = emoji))
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun roundQty(v: Double) = Math.round(v * 10).toDouble() / 10
}

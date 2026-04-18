package com.masemainegourmande.data.repository

import com.masemainegourmande.data.DEFAULT_CATEGORIES
import com.masemainegourmande.data.db.AppDatabase
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

    suspend fun deleteRecipe(id: String) = db.recipeDao().deleteById(id)

    suspend fun getRecipe(id: String): RecipeEntity? = db.recipeDao().getById(id)

    // ── Shopping list ─────────────────────────────────────────

    fun observeActiveShoppingItems(): Flow<List<ShoppingItemEntity>> =
        db.shoppingDao().observeActive()

    /**
     * Add all ingredients from a recipe to the shopping list.
     * Ingredients are scaled by [persons] / [recipe.portions].
     */
    suspend fun addRecipeToShopping(
        recipe: RecipeEntity,
        persons: Int,
        categories: List<com.masemainegourmande.data.model.CategoryEntity>
    ) {
        val ratio = if (recipe.portions > 0) persons.toDouble() / recipe.portions else 1.0
        val items = recipe.getIngredients().map { ing ->
            ShoppingItemEntity(
                id           = UUID.randomUUID().toString(),
                name         = ing.name,
                qty          = roundQty(ing.qty * ratio),
                unit         = ing.unit,
                categoryId   = com.masemainegourmande.data.detectCategory(ing.name, categories),
                fromRecipeId = recipe.id,
                checked      = false,
                sortOrder    = 0
            )
        }
        db.shoppingDao().upsertAll(items)
    }

    suspend fun addManualShoppingItem(
        name: String,
        qty: Double,
        unit: String,
        categories: List<com.masemainegourmande.data.model.CategoryEntity>
    ) {
        val item = ShoppingItemEntity(
            id         = UUID.randomUUID().toString(),
            name       = name,
            qty        = qty,
            unit       = unit,
            categoryId = com.masemainegourmande.data.detectCategory(name, categories),
            checked    = false
        )
        db.shoppingDao().upsert(item)
    }

    suspend fun setItemChecked(id: String, checked: Boolean) =
        db.shoppingDao().setChecked(id, checked)

    suspend fun deleteShoppingItem(id: String) = db.shoppingDao().deleteById(id)

    suspend fun removeRecipeFromShopping(recipeId: String) =
        db.shoppingDao().deleteByRecipe(recipeId)

    suspend fun clearCheckedItems() = db.shoppingDao().clearChecked()
    suspend fun clearAllItems()     = db.shoppingDao().clearAll()

    // ── Categories ────────────────────────────────────────────

    fun observeCategories(): Flow<List<com.masemainegourmande.data.model.CategoryEntity>> =
        db.categoryDao().observeAll()

    suspend fun getCategories(): List<com.masemainegourmande.data.model.CategoryEntity> =
        db.categoryDao().getAll()

    /** Seed categories on first launch. */
    suspend fun seedCategoriesIfEmpty() {
        val existing = db.categoryDao().getAll()
        if (existing.isEmpty()) db.categoryDao().upsertAll(DEFAULT_CATEGORIES)
    }

    // ── Import history ────────────────────────────────────────

    fun observeImportHistory(): Flow<List<ImportHistoryEntity>> =
        db.importHistoryDao().observeAll()

    suspend fun saveImportHistory(url: String, name: String, emoji: String) {
        db.importHistoryDao().upsert(ImportHistoryEntity(url = url, name = name, emoji = emoji))
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun roundQty(v: Double): Double = Math.round(v * 10).toDouble() / 10
}

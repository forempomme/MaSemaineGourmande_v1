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

    suspend fun deleteRecipe(id: String) = db.recipeDao().deleteById(id)

    suspend fun getRecipe(id: String): RecipeEntity? = db.recipeDao().getById(id)

    // ── Shopping list ─────────────────────────────────────────

    fun observeActiveShoppingItems(): Flow<List<ShoppingItemEntity>> =
        db.shoppingDao().observeActive()

    suspend fun addRecipeToShopping(
        recipe: RecipeEntity,
        persons: Int,
        categories: List<CategoryEntity>
    ) {
        val ratio = if (recipe.portions > 0) persons.toDouble() / recipe.portions else 1.0
        // Use parseIngredients() — avoids clash with Room getter getIngredients()
        val items = recipe.parseIngredients().map { ing ->
            ShoppingItemEntity(
                id           = UUID.randomUUID().toString(),
                name         = ing.name,
                qty          = roundQty(ing.qty * ratio),
                unit         = ing.unit,
                categoryId   = detectCategory(ing.name, categories),
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
        categories: List<CategoryEntity>
    ) {
        val item = ShoppingItemEntity(
            id         = UUID.randomUUID().toString(),
            name       = name,
            qty        = qty,
            unit       = unit,
            categoryId = detectCategory(name, categories),
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

    fun observeCategories(): Flow<List<CategoryEntity>> = db.categoryDao().observeAll()

    suspend fun getCategories(): List<CategoryEntity> = db.categoryDao().getAll()

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

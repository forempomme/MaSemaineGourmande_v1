package com.masemainegourmande.data.db

import androidx.room.*
import com.masemainegourmande.data.model.*
import kotlinx.coroutines.flow.Flow

// ─── Recipe ──────────────────────────────────────────────────
@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: RecipeEntity)
    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ─── Meal (planning) ─────────────────────────────────────────
@Dao
interface MealDao {
    @Query("SELECT * FROM meals ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<MealEntity>>
    @Query("SELECT * FROM meals WHERE weekKey = :key ORDER BY addedAt ASC")
    suspend fun getByWeek(key: String): List<MealEntity>
    @Query("SELECT * FROM meals WHERE weekKey = :key ORDER BY addedAt ASC")
    fun observeByWeek(key: String): Flow<List<MealEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meal: MealEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(meals: List<MealEntity>)
    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun deleteById(id: String)
    @Query("DELETE FROM meals WHERE weekKey = :key")
    suspend fun deleteByWeek(key: String)
    @Query("UPDATE meals SET persons = :persons WHERE id = :id")
    suspend fun updatePersons(id: String, persons: Int)
}

// ─── Shopping ────────────────────────────────────────────────
@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items WHERE checked = 0 ORDER BY sortOrder ASC, addedAt ASC")
    fun observeActive(): Flow<List<ShoppingItemEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ShoppingItemEntity>)
    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: String)
    @Query("DELETE FROM shopping_items WHERE fromRecipeId = :recipeId")
    suspend fun deleteByRecipe(recipeId: String)
    @Query("DELETE FROM shopping_items WHERE checked = 1")
    suspend fun clearChecked()
    @Query("DELETE FROM shopping_items")
    suspend fun clearAll()
    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: String, checked: Boolean)
}

// ─── Category ────────────────────────────────────────────────
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getAll(): List<CategoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ─── Pantry ──────────────────────────────────────────────────
@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<PantryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PantryEntity)
    @Query("DELETE FROM pantry WHERE id = :id")
    suspend fun deleteById(id: String)
    @Query("UPDATE pantry SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: String, checked: Boolean)
    @Query("UPDATE pantry SET checked = 0")
    suspend fun uncheckAll()
}

// ─── Import history ──────────────────────────────────────────
@Dao
interface ImportHistoryDao {
    @Query("SELECT * FROM import_history ORDER BY importedAt DESC LIMIT 30")
    fun observeAll(): Flow<List<com.masemainegourmande.data.model.ImportHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: com.masemainegourmande.data.model.ImportHistoryEntity)
}

// ─── Database ────────────────────────────────────────────────
@Database(
    entities = [
        RecipeEntity::class,
        MealEntity::class,
        ShoppingItemEntity::class,
        CategoryEntity::class,
        PantryEntity::class,
        com.masemainegourmande.data.model.ImportHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun mealDao(): MealDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun categoryDao(): CategoryDao
    abstract fun pantryDao(): PantryDao
    abstract fun importHistoryDao(): ImportHistoryDao
    companion object { const val NAME = "msg_database" }
}

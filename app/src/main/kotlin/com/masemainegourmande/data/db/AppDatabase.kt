package com.masemainegourmande.data.db

import androidx.room.*
import com.masemainegourmande.data.model.*
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════════════════
// DAOs
// ═══════════════════════════════════════════════════════════════

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: RecipeEntity)

    @Delete
    suspend fun delete(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY sortOrder ASC, addedAt ASC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE checked = 0 ORDER BY sortOrder ASC, addedAt ASC")
    fun observeActive(): Flow<List<ShoppingItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ShoppingItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ShoppingItemEntity>)

    @Delete
    suspend fun delete(item: ShoppingItemEntity)

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
}

@Dao
interface ImportHistoryDao {
    @Query("SELECT * FROM import_history ORDER BY importedAt DESC LIMIT 30")
    fun observeAll(): Flow<List<ImportHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ImportHistoryEntity)

    @Query("DELETE FROM import_history WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

// ═══════════════════════════════════════════════════════════════
// DATABASE
// ═══════════════════════════════════════════════════════════════

@Database(
    entities = [
        RecipeEntity::class,
        ShoppingItemEntity::class,
        CategoryEntity::class,
        ImportHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun categoryDao(): CategoryDao
    abstract fun importHistoryDao(): ImportHistoryDao

    companion object {
        const val NAME = "msg_database"
    }
}

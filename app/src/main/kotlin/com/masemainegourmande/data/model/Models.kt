package com.masemainegourmande.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ═══════════════════════════════════════════════════════════════
// DOMAIN MODELS
// ═══════════════════════════════════════════════════════════════

@Serializable
data class Ingredient(
    val name: String,
    val qty: Double = 0.0,
    val unit: String = ""
)

data class ParsedRecipe(
    val name: String,
    val emoji: String,
    val portions: Int,
    val url: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val tags: List<String> = emptyList(),
    val note: String = ""
)

// ═══════════════════════════════════════════════════════════════
// ROOM ENTITIES
// ═══════════════════════════════════════════════════════════════

private val jsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val portions: Int,
    val url: String,
    val tags: String = "[]",
    val ingredients: String = "[]",
    val steps: String = "[]",
    val favorite: Boolean = false,
    val rating: Int = 0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getIngredients(): List<Ingredient> =
        runCatching { jsonCodec.decodeFromString<List<Ingredient>>(ingredients) }
            .getOrDefault(emptyList())

    fun getSteps(): List<String> =
        runCatching { jsonCodec.decodeFromString<List<String>>(steps) }
            .getOrDefault(emptyList())

    fun getTags(): List<String> =
        runCatching { jsonCodec.decodeFromString<List<String>>(tags) }
            .getOrDefault(emptyList())
}

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val qty: Double = 0.0,
    val unit: String = "",
    val categoryId: String,
    val fromRecipeId: String? = null,
    val checked: Boolean = false,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val keywords: String = "[]",
    val sortOrder: Int = 0
) {
    fun getKeywords(): List<String> =
        runCatching { jsonCodec.decodeFromString<List<String>>(keywords) }
            .getOrDefault(emptyList())
}

@Entity(tableName = "import_history")
data class ImportHistoryEntity(
    @PrimaryKey val url: String,
    val name: String,
    val emoji: String,
    val importedAt: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════════════════════════
// CONVERSION HELPERS
// ═══════════════════════════════════════════════════════════════

fun ParsedRecipe.toEntity(id: String) = RecipeEntity(
    id          = id,
    name        = name,
    emoji       = emoji,
    portions    = portions,
    url         = url,
    tags        = jsonCodec.encodeToString(tags),
    ingredients = jsonCodec.encodeToString(ingredients),
    steps       = jsonCodec.encodeToString(steps),
    note        = note
)

// @JvmName fixes the "Platform declaration clash" error:
// after generic erasure both functions had the same JVM signature.

@JvmName("encodeStringList")
fun List<String>.encodeJson(): String = jsonCodec.encodeToString(this)

@JvmName("encodeIngredientList")
fun List<Ingredient>.encodeJson(): String = jsonCodec.encodeToString(this)

package com.masemainegourmande.importer

import com.masemainegourmande.data.model.Ingredient
import com.masemainegourmande.data.model.ParsedRecipe
import kotlinx.serialization.json.*
import org.jsoup.nodes.Document

/**
 * Parses Schema.org/Recipe from:
 *   1. <script type="application/ld+json"> blocks
 *   2. <script id="__NEXT_DATA__"> (Next.js hydration blob)
 *   3. Microdata (itemtype="http://schema.org/Recipe")
 *
 * Port of parseJsonLdRecipe / extractRecipeNode / normaliseRecipeNode /
 * deepFindRecipeNode from the JSX source.
 */
object JsonLdParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Public entry ─────────────────────────────────────────

    fun parse(doc: Document, defaultPortions: Int): ParsedRecipe? =
        parseJsonLd(doc, defaultPortions)
            ?: parseNextData(doc, defaultPortions)
            ?: parseMicrodata(doc, defaultPortions)

    // ── 1. JSON-LD ────────────────────────────────────────────

    private fun parseJsonLd(doc: Document, defaultPortions: Int): ParsedRecipe? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val text = script.data().trim().takeIf { it.isNotEmpty() } ?: continue
            runCatching {
                val element = json.parseToJsonElement(text)
                val node    = findRecipeNode(element) ?: return@runCatching null
                buildParsedRecipe(node, defaultPortions)
            }.getOrNull()?.let { return it }
        }
        return null
    }

    // ── 2. Next.js __NEXT_DATA__ ──────────────────────────────

    private fun parseNextData(doc: Document, defaultPortions: Int): ParsedRecipe? {
        val script = doc.getElementById("__NEXT_DATA__") ?: return null
        return runCatching {
            val element = json.parseToJsonElement(script.data())
            val node    = deepFindRecipeNode(element) ?: return@runCatching null
            buildParsedRecipe(node, defaultPortions)
        }.getOrNull()
    }

    // ── 3. Microdata ──────────────────────────────────────────

    private fun parseMicrodata(doc: Document, defaultPortions: Int): ParsedRecipe? {
        val recipeEl = doc.selectFirst(
            "[itemtype*='schema.org/Recipe'], [itemtype*='Schema.org/Recipe']"
        ) ?: return null

        fun prop(name: String): String =
            recipeEl.selectFirst("[itemprop=$name]")
                ?.let { el -> el.attr("content").ifEmpty { el.text() } }
                .orEmpty()

        val name = prop("name").takeIf { it.isNotBlank() } ?: return null
        val portions = prop("recipeYield").let {
            Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: defaultPortions
        }
        val ingredients = recipeEl.select("[itemprop=recipeIngredient]")
            .map { IngredientParser.parse(it.text()) }
            .filter { it.name.isNotBlank() }
        val steps = recipeEl.select("[itemprop=recipeInstructions]")
            .mapNotNull { it.text().trim().takeIf { t -> t.length > 4 } }

        return ParsedRecipe(
            name        = name,
            emoji       = EmojiGuesser.guess(name),
            portions    = portions,
            url         = "",
            ingredients = ingredients,
            steps       = steps
        )
    }

    // ── JSON traversal helpers ────────────────────────────────

    /**
     * Finds the first JsonObject that has @type == "Recipe" (or contains it).
     * Handles @graph arrays, plain arrays, and nested structures.
     */
    fun findRecipeNode(element: JsonElement, depth: Int = 0): JsonObject? {
        if (depth > 10) return null
        return when (element) {
            is JsonObject -> {
                // Check @graph first
                element["@graph"]?.let { g ->
                    findRecipeNode(g, depth + 1)?.let { return it }
                }
                // Check @type
                if (isRecipeType(element["@type"])) return element
                // Recurse into all values
                for (v in element.values) {
                    findRecipeNode(v, depth + 1)?.let { return it }
                }
                null
            }
            is JsonArray  -> {
                for (item in element) {
                    findRecipeNode(item, depth + 1)?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    /**
     * Deep search for any object that looks like a recipe
     * (has @type Recipe, OR has recipeIngredient + name).
     */
    fun deepFindRecipeNode(element: JsonElement, depth: Int = 0): JsonObject? {
        if (depth > 12) return null
        return when (element) {
            is JsonObject -> {
                val type = element["@type"]
                if (type != null && isRecipeType(type)) return element
                // Heuristic: object with ingredients + name
                if ((element["recipeIngredient"] is JsonArray ||
                     element["ingredients"]      is JsonArray) &&
                    element["name"] != null) return element
                for (v in element.values) {
                    deepFindRecipeNode(v, depth + 1)?.let { return it }
                }
                null
            }
            is JsonArray  -> {
                for (item in element) {
                    deepFindRecipeNode(item, depth + 1)?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    private fun isRecipeType(typeEl: JsonElement?): Boolean {
        if (typeEl == null) return false
        val types = when (typeEl) {
            is JsonArray  -> typeEl.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOf(typeEl.contentOrNull.orEmpty())
            else -> emptyList()
        }
        return types.any { it.contains("recipe", ignoreCase = true) }
    }

    // ── Recipe node → ParsedRecipe ────────────────────────────

    private fun buildParsedRecipe(node: JsonObject, defaultPortions: Int): ParsedRecipe {
        val name = node.strOf("name", "title") ?: "Recette importée"

        // Yield / portions
        val portions = (node["recipeYield"] ?: node["servings"] ?: node["numberOfServings"])
            ?.let { yieldEl ->
                val raw = when (yieldEl) {
                    is JsonArray     -> (yieldEl.firstOrNull() as? JsonPrimitive)?.contentOrNull
                    is JsonPrimitive -> yieldEl.contentOrNull
                    else             -> null
                }.orEmpty()
                Regex("(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            } ?: defaultPortions

        // Ingredients
        val rawIngs = node["recipeIngredient"] ?: node["ingredients"]
        val ingredients: List<Ingredient> = when (rawIngs) {
            is JsonArray -> rawIngs.mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> IngredientParser.parse(el.contentOrNull ?: "")
                        .takeIf { it.name.isNotBlank() }
                    is JsonObject    -> {
                        val n = el.strOf("name","ingredient","label") ?: return@mapNotNull null
                        val q = el["quantity"]?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: 0.0
                        val u = el.strOf("unit","unitShortName") ?: ""
                        Ingredient(name = n, qty = q, unit = u)
                    }
                    else -> null
                }
            }
            else -> emptyList()
        }

        // Steps
        val steps = mutableListOf<String>()
        fun flattenInstructions(el: JsonElement?) {
            if (el == null) return
            when (el) {
                is JsonPrimitive -> el.contentOrNull
                    ?.split("\n")?.map { it.trim() }
                    ?.filter { it.length > 8 }
                    ?.forEach { steps.add(it) }
                is JsonArray     -> el.forEach { flattenInstructions(it) }
                is JsonObject    -> {
                    val sectionType = (el["@type"] as? JsonPrimitive)?.contentOrNull
                    if (sectionType == "HowToSection") {
                        flattenInstructions(el["itemListElement"])
                    } else {
                        val text = el.strOf("text","name","description") ?: ""
                        if (text.length > 4) steps.add(text)
                    }
                }
                else -> {}
            }
        }
        flattenInstructions(node["recipeInstructions"] ?: node["instructions"] ?: node["steps"])

        val url = node.strOf("url","@id") ?: ""

        val totalTime   = parseDurationMinutes(node.strOf("totalTime"))
        val cookTime    = parseDurationMinutes(node.strOf("cookTime"))
        val prepTime    = parseDurationMinutes(node.strOf("prepTime"))
        val timeMinutes = if (totalTime > 0) totalTime else cookTime + prepTime

        return ParsedRecipe(
            name            = name,
            emoji           = EmojiGuesser.guess(name),
            portions        = portions,
            url             = url,
            ingredients     = ingredients,
            steps           = steps,
            cookTimeMinutes = timeMinutes
        )
    }

    // ── Duration helper ──────────────────────────────────────────

    private fun parseDurationMinutes(iso: String?): Int {
        if (iso.isNullOrBlank()) return 0
        val h = Regex("""(\d+)H""").find(iso)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = Regex("""(\d+)M""").find(iso)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    // ── Small helper ──────────────────────────────────────────

    private fun JsonObject.strOf(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
}

package com.masemainegourmande.importer

import com.masemainegourmande.data.model.Ingredient
import com.masemainegourmande.data.model.ParsedRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Orchestrates the full import cascade:
 *
 *   1. Jow direct API (if jow.fr)
 *   2. Direct HTTP fetch → JSON-LD  (Android has no CORS — no proxy needed)
 *   3. Direct HTTP fetch → __NEXT_DATA__
 *   4. Direct HTTP fetch → Microdata
 *   5. Throw [ImportException] with descriptive message
 *
 * All network I/O runs on Dispatchers.IO.
 */
class RecipeImporter(private val defaultPortions: Int = 4) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
                .build()
            chain.proceed(req)
        }
        .build()

    private val jsonCodec = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Public API ────────────────────────────────────────────

    /**
     * @throws ImportException when all strategies fail.
     */
    suspend fun importFromUrl(url: String): ParsedRecipe = withContext(Dispatchers.IO) {
        val normalized = url.trim()

        // Step 1 — Jow special API
        if ("jow.fr" in normalized) {
            tryJowApi(normalized)?.let { return@withContext it }
        }

        // Step 2-4 — Fetch HTML and cascade parsers
        val html = fetchHtml(normalized)  // throws on network failure
        val doc  = Jsoup.parse(html, normalized)

        JsonLdParser.parse(doc, defaultPortions)?.let { return@withContext it.copy(url = normalized) }

        throw ImportException(
            "Aucune recette structurée (JSON-LD / Microdata) trouvée sur cette page.\n" +
            "Essayez l'onglet « Copier-coller » : ouvrez la page dans votre navigateur, " +
            "sélectionnez tout et collez le texte."
        )
    }

    fun importFromText(text: String): ParsedRecipe =
        TextParser.parse(text, defaultPortions)
            ?: throw ImportException("Impossible d'analyser ce texte. Vérifiez le format.")

    // ── Jow special API ───────────────────────────────────────

    private fun tryJowApi(pageUrl: String): ParsedRecipe? {
        // Extract recipe ID from URL pattern: /recipes/some-name-ABCDEF
        val id = Regex("""/recipes/[^/]+-([a-z0-9]{6,})(?:[/?]|$)""", RegexOption.IGNORE_CASE)
            .find(pageUrl)?.groupValues?.getOrNull(1) ?: return null

        return runCatching {
            val response = client.newCall(
                Request.Builder().url("https://api.jow.fr/public/recipe/$id").build()
            ).execute()
            if (!response.isSuccessful) return@runCatching null
            val body = response.body?.string() ?: return@runCatching null
            parseJowJson(body, pageUrl)
        }.getOrNull()
    }

    private fun parseJowJson(body: String, pageUrl: String): ParsedRecipe? {
        val root = runCatching { jsonCodec.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return null
        val name = (root["title"] ?: root["name"])
            ?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() } ?: return null
        val portions = (root["numberOfServings"] ?: root["servings"])
            ?.let { (it as? JsonPrimitive)?.intOrNull } ?: defaultPortions
        val ingredients = (root["ingredients"] as? JsonArray)?.mapNotNull { el ->
            val obj   = el as? JsonObject ?: return@mapNotNull null
            val iName = ((obj["ingredient"] as? JsonObject)?.get("name")
                        ?: obj["name"])?.let { (it as? JsonPrimitive)?.contentOrNull }
                        ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val qty   = obj["quantity"]?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: 0.0
            val unit  = obj["unit"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: ""
            Ingredient(name = iName, qty = qty, unit = unit)
        } ?: emptyList()
        val steps = (root["steps"] ?: root["instructions"])
            ?.let { it as? JsonArray }
            ?.mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> el.contentOrNull?.takeIf { it.length > 4 }
                    is JsonObject    -> (el["description"] ?: el["text"] ?: el["name"])
                        ?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.length > 4 }
                    else -> null
                }
            } ?: emptyList()
        return ParsedRecipe(
            name        = name,
            emoji       = EmojiGuesser.guess(name),
            portions    = portions,
            url         = pageUrl,
            ingredients = ingredients,
            steps       = steps
        )
    }

    // ── HTTP fetch ────────────────────────────────────────────

    private fun fetchHtml(url: String): String {
        val response = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute()
        }.getOrElse { e ->
            throw ImportException("Impossible de télécharger la page : ${e.message}")
        }
        if (!response.isSuccessful) {
            throw ImportException("Erreur HTTP ${response.code} pour $url")
        }
        return response.body?.string()
            ?: throw ImportException("La réponse du serveur est vide.")
    }
}

// ═══════════════════════════════════════════════════════════════

class ImportException(message: String) : Exception(message)

/** Known-site metadata for UI hints. */
data class SiteInfo(
    val name:       String,
    val compatible: Boolean,
    val tip:        String?
)

val KNOWN_SITES: Map<String, SiteInfo> = mapOf(
    "marmiton.org"    to SiteInfo("Marmiton",      true,  null),
    "750g.com"        to SiteInfo("750g",           true,  null),
    "jow.fr"          to SiteInfo("Jow",            true,  "Utilise l'API directe Jow — rapide."),
    "cuisineaz.com"   to SiteInfo("CuisineAZ",      false, "CuisineAZ peut bloquer les robots. Essayez Copier-coller."),
    "bbcgoodfood.com" to SiteInfo("BBC Good Food",  true,  null),
    "allrecipes.com"  to SiteInfo("Allrecipes",     true,  null),
    "epicurious.com"  to SiteInfo("Epicurious",     true,  null),
    "seriouseats.com" to SiteInfo("Serious Eats",   true,  null),
    "tasty.co"        to SiteInfo("Tasty",          true,  null),
    "hervecuisine.com" to SiteInfo("Hervé Cuisine", false, "Ce site bloque parfois les robots. Essayez Copier-coller."),
)

fun detectSite(url: String): SiteInfo? {
    val host = runCatching { java.net.URL(url).host.removePrefix("www.") }.getOrNull() ?: return null
    return KNOWN_SITES.entries.firstOrNull { (domain, _) -> host.contains(domain) }?.value
}

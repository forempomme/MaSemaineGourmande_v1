package com.masemainegourmande.importer

import com.masemainegourmande.data.model.Ingredient

/**
 * Parses a raw ingredient string like "200 g de spaghetti" into a structured [Ingredient].
 */
object IngredientParser {

    private val FRACTIONS = mapOf(
        "½" to 0.5, "¼" to 0.25, "¾" to 0.75,
        "⅓" to 0.333, "⅔" to 0.667,
        "⅛" to 0.125, "⅜" to 0.375, "⅝" to 0.625, "⅞" to 0.875
    )

    // Use a raw string (triple-quoted) so backslashes are literal — no const val issues
    private val INGREDIENT_RE by lazy {
        val units = listOf(
            "g", "kg", "mg", "ml", "l", "cl", "dl", "oz", "lb",
            // càs / c.s. / c.à.s. / cuillère à soupe / tbsp
            "càs", "c\\.à\\.s\\.?", "c\\.s\\.?",
            "cuill?\\.?\\s*(?:à|a)\\s*soupe", "tbsp",
            // càc / c.c. / c.à.c. / cuillère à café / tsp
            "càc", "c\\.à\\.c\\.?", "c\\.c\\.?",
            "cuill?\\.?\\s*(?:à|a)\\s*caf[eé]", "tsp",
            // other measures
            "cup", "pincée?", "sachet", "tranche", "branche", "feuille",
            "botte", "bouquet", "boîte", "boite", "pot", "verre", "bol",
            "filet", "noix", "morceau", "pointe", "paque?t",
            // counting nouns treated as units
            "quartiers?", "portions?", "parts?", "morceaux?", "demi",
            "rondelles?", "cubes?", "lamelles?", "lanières?", "bâtonnets?",
            "zestes?", "gousses?", "tiges?", "brins?", "copeaux?"
        ).joinToString("|")

        Regex(
            """^(\d+(?:[,.]?\d+)?)\s*($units)?\s*(?:de |d'|of )?(.+)$""",
            RegexOption.IGNORE_CASE
        )
    }

    private val ASCII_FRACTION = Regex("""(\d+)/(\d+)""")

    fun parse(raw: String): Ingredient {
        var text = raw.replace(Regex("\\s+"), " ").trim()

        for ((frac, dec) in FRACTIONS) text = text.replace(frac, dec.toString())

        text = ASCII_FRACTION.replace(text) { mr ->
            val a = mr.groupValues[1].toIntOrNull() ?: return@replace mr.value
            val b = mr.groupValues[2].toIntOrNull() ?: return@replace mr.value
            if (b == 0) mr.value else "%.3f".format(a.toDouble() / b)
        }

        val m = INGREDIENT_RE.find(text)
        return if (m != null) {
            val qty  = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            val unit = normaliseUnit(m.groupValues[2].trim())
            val name = m.groupValues[3].trim().trimEnd('.')
            Ingredient(name = name, qty = qty, unit = unit)
        } else {
            Ingredient(name = text.trimStart('-', '•', '*', ' '), qty = 0.0, unit = "")
        }
    }

    private fun normaliseUnit(raw: String): String = when {
        raw.isBlank() -> ""
        raw.equals("càs", ignoreCase = true) -> "c.s."
        raw.equals("càc", ignoreCase = true) -> "c.c."
        Regex("""cuill?.*soupe|c\.à\.s|c\.s\.|tbsp""", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "c.s."
        Regex("""cuill?.*caf[eé]|c\.à\.c|c\.c\.|tsp""", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "c.c."
        else -> raw.lowercase().trimEnd('.')
    }
}

package com.masemainegourmande.importer

import com.masemainegourmande.data.model.Ingredient

/**
 * Parses a raw ingredient string like "200 g de spaghetti" into a structured [Ingredient].
 * Port of `parseIngredientString` from the JSX source.
 */
object IngredientParser {

    // Unicode fractions → decimal
    private val FRACTIONS = mapOf(
        "½" to 0.5, "¼" to 0.25, "¾" to 0.75,
        "⅓" to 0.333, "⅔" to 0.667,
        "⅛" to 0.125, "⅜" to 0.375, "⅝" to 0.625, "⅞" to 0.875
    )

    // All recognised unit tokens
    private const val UNIT_PATTERN =
        "g|kg|mg|ml|l|cl|dl|oz|lb" +
        // càs / càc shortcuts (must come BEFORE longer cuill patterns)
        "|càs|c\.à\.s\.?|c\.s\.?|cuill?\.?\s*(?:à|a)\s*soupe|tbsp" +
        "|càc|c\.à\.c\.?|c\.c\.?|cuill?\.?\s*(?:à|a)\s*caf[eé]|tsp" +
        "|cup|pincée?|sachet|gousse|tranche|branche|feuille|botte|bouquet" +
        "|boîte|boite|pot|verre|bol|filet|noix|morceau|pointe|paque?t" +
        // Counting nouns treated as units so they stay with the ingredient name
        "|quartiers?|tranches?|portions?|parts?|morceaux|morceaux?|demi|moitié" +
        "|rondelles?|cubes?|lamelles?|lanières?|feuilles?|brins?|tiges?|gousses?" +
        "|bâtonnets?|dés|rubans?|copeaux?|zestes?|jus"

    private val INGREDIENT_RE = Regex(
        """^(\d+(?:[,.]?\d+)?)\s*($UNIT_PATTERN)?\s*(?:de |d'|of )?(.+)$""",
        RegexOption.IGNORE_CASE
    )

    // x/y fraction in ASCII
    private val ASCII_FRACTION = Regex("""(\d+)/(\d+)""")

    fun parse(raw: String): Ingredient {
        var text = raw.replace(Regex("\\s+"), " ").trim()

        // Replace unicode fractions
        for ((frac, dec) in FRACTIONS) text = text.replace(frac, dec.toString())

        // Replace ASCII fractions (1/2 → 0.5)
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
            // No leading quantity — whole-string is the ingredient name
            Ingredient(name = text.trimStart('-', '•', '*', ' '), qty = 0.0, unit = "")
        }
    }

    /** Normalise verbose unit strings to compact forms. */
    private fun normaliseUnit(raw: String): String = when {
        raw.isBlank() -> ""
        Regex("""càs|cuill?.*soupe|c\.à\.s|c\.s\.|tbsp""", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "c.s."
        Regex("""càc|cuill?.*caf[eé]|c\.à\.c|c\.c\.|tsp""", RegexOption.IGNORE_CASE).containsMatchIn(raw) -> "c.c."
        else -> raw.lowercase().trimEnd('.')
    }
}

package com.masemainegourmande.importer

import com.masemainegourmande.data.model.Ingredient

/**
 * Parses a raw ingredient string like "200 g de spaghetti" into a structured [Ingredient].
 * Units are matched longest-first to avoid partial matches (e.g. "gousses" must beat "g").
 */
object IngredientParser {

    private val FRACTIONS = mapOf(
        "½" to 0.5, "¼" to 0.25, "¾" to 0.75,
        "⅓" to 0.333, "⅔" to 0.667,
        "⅛" to 0.125, "⅜" to 0.375, "⅝" to 0.625, "⅞" to 0.875
    )

    // Units ordered LONGEST-FIRST to prevent "g" from eating "gousses", "tranche" eating "tranches", etc.
    private val UNIT_PATTERN: String = listOf(
        "kg", "mg", "ml", "cl", "dl", "oz", "lb", "g",
        "càs", "c\\.à\\.s\\.?", "c\\.s\\.?", "cuill?\\.?\\s*(?:à|a)\\s*soupe", "tbsp",
        "càc", "c\\.à\\.c\\.?", "c\\.c\\.?", "cuill?\\.?\\s*(?:à|a)\\s*caf[eé]", "tsp",
        "cup", "pincées", "pincée",
        "sachets", "sachet",
        "branches", "branche",
        "feuilles", "feuille",
        "bottes", "botte",
        "bouquets", "bouquet",
        "boîtes", "boîte", "boites", "boite",
        "verres", "verre",
        "bols", "bol",
        "filets", "filet",
        "morceaux", "morceau",
        "paquets", "paquet",
        "quartiers", "quartier",
        "tranches", "tranche",
        "portions", "portion",
        "rondelles", "rondelle",
        "lamelles", "lamelle",
        "lanières", "lanière",
        "bâtonnets", "bâtonnet",
        "zestes", "zeste",
        "gousses", "gousse",
        "tiges", "tige",
        "brins", "brin",
        "copeaux", "copeau",
        "cubes", "cube",
        "noix", "pot", "pointe", "demi", "parts?",
        "l(?!\\w)"   // litre — only when not followed by a word char
    ).joinToString("|")

    private val INGREDIENT_RE: Regex by lazy {
        Regex(
            """^(\d+(?:[,.]?\d+)?)\s*($UNIT_PATTERN)?\s*(?:de |d'|d'|of )?(.+)$""",
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

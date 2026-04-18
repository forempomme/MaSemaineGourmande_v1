package com.masemainegourmande.importer

import com.masemainegourmande.data.model.Ingredient
import com.masemainegourmande.data.model.ParsedRecipe

// ═══════════════════════════════════════════════════════════════
// EMOJI GUESSER  — port of guessEmoji() from JSX
// ═══════════════════════════════════════════════════════════════

object EmojiGuesser {
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""poulet|volaille|dinde|canard""")                       to "🍗",
        Regex("""porc|côte|lard|jambon|bacon""")                        to "🥩",
        Regex("""bœuf|boeuf|steak|burger""")                            to "🍔",
        Regex("""poisson|saumon|thon|cabillaud|lieu|sole""")            to "🐟",
        Regex("""crevette|fruits de mer|homard|moule""")                to "🦐",
        Regex("""pizza""")                                              to "🍕",
        Regex("""pasta|pâtes|spaghetti|lasagne|tagliatelle|ravioli""")  to "🍝",
        Regex("""riz|risotto|paella""")                                  to "🍚",
        Regex("""salade""")                                             to "🥗",
        Regex("""soupe|velouté|potage|bouillon""")                      to "🍲",
        Regex("""tarte|quiche|gratin""")                                to "🥧",
        Regex("""gâteau|cake|brownie|muffin|fondant""")                 to "🎂",
        Regex("""crêpe|pancake""")                                      to "🥞",
        Regex("""omelette|œuf|oeuf|egg""")                             to "🍳",
        Regex("""sandwich|wrap""")                                      to "🥪",
        Regex("""curry|wok|asiatique|thaï|japonais|chinois""")          to "🍜",
        Regex("""légume|végétar|vegan|courgette|aubergine|ratatouille""") to "🥘",
        Regex("""smoothie|jus|boisson""")                               to "🥤",
        Regex("""glace|sorbet""")                                       to "🍨",
    )

    fun guess(name: String): String {
        val lower = name.lowercase()
        return rules.firstOrNull { (re, _) -> re.containsMatchIn(lower) }?.second ?: "🍽️"
    }
}

// ═══════════════════════════════════════════════════════════════
// TEXT PARSER  — port of parseRecipeText() from JSX
// Handles plain-text pastes from the Copier-coller tab.
// ═══════════════════════════════════════════════════════════════

object TextParser {

    fun parse(raw: String, defaultPortions: Int): ParsedRecipe? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val name = lines.first().removePrefix("#").trim()

        var portions = defaultPortions
        Regex("""(\d+)\s*(?:personnes?|portions?|parts?)""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { portions = it }

        val ingredients = mutableListOf<Ingredient>()
        val steps       = mutableListOf<String>()
        var mode        = ParseMode.NONE

        for (line in lines.drop(1)) {
            when {
                Regex("""^#+\s*ingr[eé]dients?""", RegexOption.IGNORE_CASE).containsMatchIn(line)
                || line.lowercase().trim() == "ingrédients"
                || line.lowercase().trim() == "ingredients" -> {
                    mode = ParseMode.INGREDIENTS
                }
                Regex("""^#+\s*(pr[eé]paration|[eé]tapes?|instructions?)""", RegexOption.IGNORE_CASE).containsMatchIn(line)
                || Regex("""^(pr[eé]paration|[eé]tapes?)[\s:]*$""", RegexOption.IGNORE_CASE).containsMatchIn(line) -> {
                    mode = ParseMode.STEPS
                }
                mode == ParseMode.INGREDIENTS -> {
                    val ing = IngredientParser.parse(line.trimStart('-', '•', '*', ' '))
                    if (ing.name.isNotBlank()) ingredients.add(ing)
                }
                mode == ParseMode.STEPS
                || Regex("""^\d+[.)]\s""").containsMatchIn(line) -> {
                    val step = line.replace(Regex("""^\d+[.)]\s*"""), "").trim()
                    if (step.length > 5) steps.add(step)
                }
                // Auto-detect ingredient lines even without a header
                mode == ParseMode.NONE -> {
                    val maybeIng = IngredientParser.parse(line.trimStart('-', '•', '*', ' '))
                    if (maybeIng.qty > 0 && maybeIng.name.isNotBlank()) {
                        ingredients.add(maybeIng)
                    }
                }
            }
        }

        return ParsedRecipe(
            name        = name,
            emoji       = EmojiGuesser.guess(name),
            portions    = portions,
            url         = "",
            ingredients = ingredients,
            steps       = steps
        )
    }

    private enum class ParseMode { NONE, INGREDIENTS, STEPS }
}

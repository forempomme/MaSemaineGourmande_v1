package com.masemainegourmande.data

import com.masemainegourmande.data.model.CategoryEntity
import com.masemainegourmande.data.model.encodeJson

val DEFAULT_CATEGORIES: List<CategoryEntity> = listOf(
    CategoryEntity(id = "c1", name = "Viandes & Poissons", sortOrder = 0,
        keywords = listOf("poulet","boeuf","porc","agneau","veau","saumon","thon",
            "crevette","lard","jambon","dinde","canard","steak","roti","filet").encodeJson()),
    CategoryEntity(id = "c2", name = "Produits laitiers", sortOrder = 1,
        keywords = listOf("lait","creme","beurre","fromage","yaourt","mascarpone",
            "mozzarella","parmesan","gruyere","camembert","ricotta","emmental").encodeJson()),
    CategoryEntity(id = "c3", name = "Oeufs", sortOrder = 2,
        keywords = listOf("oeuf").encodeJson()),
    CategoryEntity(id = "c4", name = "Fruits & Legumes", sortOrder = 3,
        keywords = listOf("tomate","carotte","oignon","ail","poivron","courgette","aubergine",
            "epinard","salade","pomme","banane","citron","orange","champignon","poireau",
            "celeri","concombre","avocat","brocoli","chou","laitue","fenouil","radis",
            "asperge","betterave").encodeJson()),
    CategoryEntity(id = "c5", name = "Feculents", sortOrder = 4,
        keywords = listOf("pates","riz","pomme de terre","farine","semoule","lentille",
            "pois chiche","haricot","quinoa","boulgour","pain de mie","spaghetti","tagliatelle").encodeJson()),
    CategoryEntity(id = "c6", name = "Epicerie", sortOrder = 5,
        keywords = listOf("huile","vinaigre","moutarde","sel","poivre","sucre","miel",
            "sauce","concentre","bouillon","conserve","epice","cumin","paprika","curry",
            "cannelle","thym","romarin","basilic","origan").encodeJson()),
    CategoryEntity(id = "c7", name = "Boissons", sortOrder = 6,
        keywords = listOf("eau","jus","vin","biere","cafe","the","sirop").encodeJson()),
    CategoryEntity(id = "c8", name = "Boulangerie", sortOrder = 7,
        keywords = listOf("pain","baguette","brioche","croissant","focaccia").encodeJson()),
    CategoryEntity(id = "c9", name = "Autre", sortOrder = 8,
        keywords = listOf<String>().encodeJson())
)

fun detectCategory(ingredientName: String, categories: List<CategoryEntity>): String {
    val lower = ingredientName.lowercase()
    return categories
        .sortedBy { it.sortOrder }
        .firstOrNull { cat ->
            // Use parseKeywords() — avoids clash with Room getter getKeywords()
            cat.parseKeywords().any { kw -> lower.contains(kw.lowercase()) }
        }?.id ?: "c9"
}

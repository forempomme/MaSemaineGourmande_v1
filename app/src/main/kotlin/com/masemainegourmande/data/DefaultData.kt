package com.masemainegourmande.data

import com.masemainegourmande.data.model.CategoryEntity
import com.masemainegourmande.data.model.encodeJson

/** Default category list — mirrors INITIAL_DATA.categories from the JSX. */
val DEFAULT_CATEGORIES: List<CategoryEntity> = listOf(
    CategoryEntity(
        id = "c1", name = "Viandes & Poissons", sortOrder = 0,
        keywords = listOf("poulet","bœuf","boeuf","porc","agneau","veau","saumon","thon",
            "crevette","lard","jambon","dinde","canard","steak","côte","rôti","filet").encodeJson()
    ),
    CategoryEntity(
        id = "c2", name = "Produits laitiers", sortOrder = 1,
        keywords = listOf("lait","crème","beurre","fromage","yaourt","mascarpone",
            "mozzarella","parmesan","gruyère","camembert","ricotta","emmental").encodeJson()
    ),
    CategoryEntity(
        id = "c3", name = "Œufs", sortOrder = 2,
        keywords = listOf("œuf","oeuf").encodeJson()
    ),
    CategoryEntity(
        id = "c4", name = "Fruits & Légumes", sortOrder = 3,
        keywords = listOf("tomate","carotte","oignon","ail","poivron","courgette","aubergine",
            "épinard","salade","pomme","banane","citron","orange","champignon","poireau",
            "céleri","concombre","avocat","brocoli","chou","laitue","fenouil","radis",
            "asperge","betterave").encodeJson()
    ),
    CategoryEntity(
        id = "c5", name = "Féculents", sortOrder = 4,
        keywords = listOf("pâtes","riz","pomme de terre","farine","semoule","lentille",
            "pois chiche","haricot","quinoa","boulgour","pain de mie","spaghetti","tagliatelle").encodeJson()
    ),
    CategoryEntity(
        id = "c6", name = "Épicerie", sortOrder = 5,
        keywords = listOf("huile","vinaigre","moutarde","sel","poivre","sucre","miel",
            "sauce","concentré","bouillon","conserve","épice","cumin","paprika","curry",
            "cannelle","thym","romarin","basilic","origan").encodeJson()
    ),
    CategoryEntity(
        id = "c7", name = "Boissons", sortOrder = 6,
        keywords = listOf("eau","jus","vin","bière","café","thé","sirop").encodeJson()
    ),
    CategoryEntity(
        id = "c8", name = "Boulangerie", sortOrder = 7,
        keywords = listOf("pain","baguette","brioche","croissant","focaccia").encodeJson()
    ),
    CategoryEntity(
        id = "c9", name = "Autre", sortOrder = 8,
        keywords = listOf<String>().encodeJson()
    )
)

/** Detect category for an ingredient name. Returns category id. */
fun detectCategory(ingredientName: String, categories: List<CategoryEntity>): String {
    val lower = ingredientName.lowercase()
    return categories
        .sortedBy { it.sortOrder }
        .firstOrNull { cat ->
            cat.getKeywords().any { kw -> lower.contains(kw.lowercase()) }
        }?.id ?: "c9"
}

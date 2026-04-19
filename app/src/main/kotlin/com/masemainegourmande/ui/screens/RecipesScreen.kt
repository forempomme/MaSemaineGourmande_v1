package com.masemainegourmande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.Ingredient
import com.masemainegourmande.data.model.RecipeEntity
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.RecipeSort
import com.masemainegourmande.viewmodel.RecipesViewModel
import com.masemainegourmande.viewmodel.ShoppingViewModel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

val RECIPE_TAGS = listOf(
    "Végétarien","Vegan","Rapide","Dessert","Entrée","Soupe",
    "Petit-déj","Sans gluten","Économique","Festif","Fait maison","Batch cooking"
)

val FOOD_EMOJIS = listOf(
    "🍗","🥩","🐟","🍔","🥚","🧀","🥛","🍕","🍝","🍜","🍲","🥘","🥗",
    "🍱","🍣","🌮","🌯","🥙","🍞","🥖","🧁","🎂","🍰","🥧","🍨","🥞","🫕","🥦","🥕","🍅",
    "🥑","🌽","🍋","🍎","🍓","🧅","🧄","🫚","🍳","🍽️","🫙","🥫","🧆","🥜","🌿","🫛"
)

private val jsonSerializer = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun stringsToJson(list: List<String>): String =
    jsonSerializer.encodeToString(ListSerializer(String.serializer()), list)

private fun ingredientsToJson(list: List<Ingredient>): String =
    jsonSerializer.encodeToString(ListSerializer(Ingredient.serializer()), list)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    vm:             RecipesViewModel,
    shoppingVm:     ShoppingViewModel,
    defaultPersons: Int,
    importVm:       com.masemainegourmande.viewmodel.ImportViewModel
) {
    val filtered     by vm.filtered.collectAsState()
    val allTags      by vm.allTags.collectAsState()
    val cookCounts   by vm.cookCounts.collectAsState()
    val lastCooked   by vm.lastCookedMap.collectAsState()
    val searchQuery  by vm.searchQuery.collectAsState()
    val activeTag    by vm.activeTag.collectAsState()
    val sortMode     by vm.sortMode.collectAsState()

    var detailRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var editRecipe   by remember { mutableStateOf<RecipeEntity?>(null) }
    var showNew      by remember { mutableStateOf(false) }
    var showImport   by remember { mutableStateOf(false) }



    Column(Modifier.fillMaxSize().background(BgCream)) {
        // Action bar (import + add buttons) — no TopAppBar, header is shared
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { vm.searchQuery.value = it },
                placeholder = { Text("🔍 Nom ou ingrédient...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp), singleLine = true
            )
            Surface(color = PriOrangeLight, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(48.dp).clickable { showImport = true }) {
                Box(contentAlignment = Alignment.Center) { Text("📥", fontSize = 20.sp) }
            }
            Surface(color = PriOrange, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(48.dp).clickable { showNew = true }) {
                Box(contentAlignment = Alignment.Center) { Text("+", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }

            // ── Tag filters ──────────────────────────────
            if (allTags.isNotEmpty()) {
                LazyRow(
                    contentPadding      = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = activeTag == null,
                            onClick  = { vm.activeTag.value = null },
                            label    = { Text("Tous") },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PriOrange,
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                    items(allTags) { tag ->
                        FilterChip(
                            selected = activeTag == tag,
                            onClick  = { vm.activeTag.value = if (activeTag == tag) null else tag },
                            label    = { Text(tag) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PriOrange,
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // ── Sort chips ───────────────────────────────
            LazyRow(
                contentPadding      = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val sorts = listOf(
                    RecipeSort.FAVORITES to "⭐ Favoris",
                    RecipeSort.RATING    to "★ Note",
                    RecipeSort.NAME      to "A–Z",
                    RecipeSort.COOKED    to "Cuisinés",
                    RecipeSort.PORTIONS  to "Portions"
                )
                items(sorts) { (mode, label) ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick  = { vm.sortMode.value = mode },
                        label    = { Text(label, fontSize = 12.sp) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor     = Color.White
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // ── Recipe grid ──────────────────────────────
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🍳", fontSize = 48.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (searchQuery.isNotEmpty() || activeTag != null) "Aucun résultat"
                            else "Ajoutez votre première recette !",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns     = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { recipe ->
                        RecipeCard(
                            recipe          = recipe,
                            cookCount       = cookCounts[recipe.id] ?: 0,
                            lastCookedLabel = lastCooked[recipe.id],
                            onClick    = { detailRecipe = recipe },
                            onFavorite = { vm.toggleFavorite(recipe) },
                            onDelete   = { vm.delete(recipe.id) }
                        )
                    }
                }
            }
        }


    // ── Modals ───────────────────────────────────────────────
    detailRecipe?.let { recipe ->
        RecipeDetailSheet(
            recipe          = recipe,
            cookCount       = cookCounts[recipe.id] ?: 0,
            lastCookedLabel = lastCooked[recipe.id],
            defaultPersons  = defaultPersons,
            onClose         = { detailRecipe = null },
            onEdit          = { detailRecipe = null; editRecipe = recipe },
            onFavorite      = { vm.toggleFavorite(recipe); detailRecipe = recipe.copy(favorite = !recipe.favorite) },
            onRate          = { r -> vm.setRating(recipe, r); detailRecipe = recipe.copy(rating = r) },
            onAddToShopping = { persons -> shoppingVm.addRecipe(recipe, persons); detailRecipe = null }
        )
    }

    if (showNew || editRecipe != null) {
        RecipeEditSheet(
            recipe          = editRecipe,
            defaultPortions = defaultPersons,
            onClose         = { showNew = false; editRecipe = null },
            onSave          = { r -> vm.upsert(r); showNew = false; editRecipe = null }
        )
    }

    if (showImport) {
        ImportScreen(
            importVm       = importVm,
            shoppingVm     = shoppingVm,
            onRecipeSaved  = { showImport = false }
        )
    }
}

// ─── Recipe card ─────────────────────────────────────────────

@Composable
private fun RecipeCard(
    recipe:          RecipeEntity,
    cookCount:       Int,
    lastCookedLabel: String?,
    onClick:    () -> Unit,
    onFavorite: () -> Unit,
    onDelete:   () -> Unit
) {
    Card(
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border   = BorderStroke(1.dp, BorderBeige),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Box {
            if (cookCount > 0) {
                Surface(color = PriOrange, shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)) {
                    Text("${cookCount}×", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            IconButton(onClick = onFavorite,
                modifier = Modifier.align(Alignment.TopEnd).size(34.dp)) {
                Text(if (recipe.favorite) "★" else "☆", fontSize = 20.sp,
                    color = if (recipe.favorite) StarYellow else BorderBeige)
            }
            Column(
                Modifier.padding(top = 28.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(recipe.emoji, fontSize = 32.sp)
                Spacer(Modifier.height(4.dp))
                Text(recipe.name, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                    textAlign = TextAlign.Center, maxLines = 2)
                if (recipe.rating > 0) {
                    Row(horizontalArrangement = Arrangement.Center) {
                        repeat(recipe.rating)      { Text("★", fontSize = 11.sp, color = StarYellow) }
                        repeat(5 - recipe.rating) { Text("★", fontSize = 11.sp, color = BorderBeige) }
                    }
                }
                Text("👤 ${recipe.portions} portions", fontSize = 11.sp, color = TextMuted)
                if (lastCookedLabel != null) {
                    Text("🕐 $lastCookedLabel", fontSize = 10.sp, color = TextMuted, fontStyle = FontStyle.Italic)
                } else {
                    Text("🕐 Jamais cuisinée", fontSize = 10.sp, color = TextMuted, fontStyle = FontStyle.Italic)
                }
                val tags = recipe.parseTags()
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(top = 4.dp)) {
                        tags.take(2).forEach { tag ->
                            Surface(color = PriOrangeLight, shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(horizontal = 2.dp)) {
                                Text(tag, fontSize = 9.sp, color = PriOrange, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Recipe detail sheet ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeDetailSheet(
    recipe:          RecipeEntity,
    cookCount:       Int,
    lastCookedLabel: String?,
    defaultPersons:  Int,
    onClose:         () -> Unit,
    onEdit:          () -> Unit,
    onFavorite:      () -> Unit,
    onRate:          (Int) -> Unit,
    onAddToShopping: (Int) -> Unit
) {
    // Use mutableStateOf<Int> to avoid delegate resolution issues with mutableIntStateOf in nested scopes
    var persons     by remember { mutableStateOf(recipe.portions.takeIf { it > 0 } ?: defaultPersons) }
    var localRating by remember { mutableStateOf(recipe.rating) }
    val ratio       = persons.toDouble() / (recipe.portions.takeIf { it > 0 } ?: 1)
    val ratingLabels = listOf("","Mauvais","Passable","Bien","Très bien","Excellent")

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(recipe.emoji, fontSize = 32.sp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(recipe.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = onFavorite) {
                            Text(if (recipe.favorite) "★" else "☆", fontSize = 24.sp,
                                color = if (recipe.favorite) StarYellow else BorderBeige)
                        }
                    }
                    Text("${recipe.parseIngredients().size} ingrédients · ${recipe.parseSteps().size} étapes",
                        fontSize = 12.sp, color = TextMuted)
                    // Star rating
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { n ->
                            Text("★", fontSize = 22.sp,
                                color = if (n <= localRating) StarYellow else BorderBeige,
                                modifier = Modifier.clickable {
                                    val newRating = if (n == localRating) 0 else n
                                    localRating = newRating
                                    onRate(newRating)
                                })
                        }
                        if (localRating > 0) {
                            Text(ratingLabels[localRating], fontSize = 11.sp, color = TextMuted)
                        }
                    }
                    val tags = recipe.parseTags()
                    if (tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            tags.forEach { tag ->
                                Surface(color = PriOrangeLight, shape = CircleShape) {
                                    Text(tag, fontSize = 10.sp, color = PriOrange, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Cook history
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChip("🕐", lastCookedLabel ?: "Jamais", "Dernière fois", modifier = Modifier.weight(1f))
                StatChip("🍽️", cookCount.toString(), "fois cuisinée", highlight = true, modifier = Modifier.weight(1f))
            }

            // Portions stepper
            Card(colors = CardDefaults.cardColors(containerColor = PriOrangeLight), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("👤 Portions", fontWeight = FontWeight.Bold, color = PriOrange)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { if (persons > 1) persons-- },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrange)) {
                            Icon(Icons.Default.Remove, null, tint = Color.White)
                        }
                        Text(persons.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        IconButton(onClick = { persons++ },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrange)) {
                            Icon(Icons.Default.Add, null, tint = Color.White)
                        }
                    }
                }
            }

            // Ingredients
            Text("🧂 Ingrédients", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            recipe.parseIngredients().forEach { ing ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ing.name, fontSize = 14.sp)
                    val qty = if (ing.qty > 0) "${fmtQty(ing.qty * ratio)} ${ing.unit}".trim() else ing.unit.ifEmpty { "—" }
                    Text(qty, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PriOrange)
                }
                HorizontalDivider(color = BorderBeige)
            }

            // Steps
            val steps = recipe.parseSteps()
            if (steps.isNotEmpty()) {
                Text("📋 Préparation", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                steps.forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = PriOrange, shape = CircleShape, modifier = Modifier.size(24.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text((i + 1).toString(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                        Text(step, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Note
            if (recipe.note.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEA)),
                    border = BorderStroke(1.dp, Color(0x33F5A623))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("MA NOTE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9A7000))
                        Spacer(Modifier.height(4.dp))
                        Text(recipe.note, fontSize = 14.sp)
                    }
                }
            }

            // Actions
            Button(onClick = { onAddToShopping(persons) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AccGreen)) {
                Icon(Icons.Default.ShoppingCart, null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter à la liste de courses", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Modifier la recette")
            }
        }
    }
}

@Composable
private fun StatChip(icon: String, value: String, label: String, highlight: Boolean = false, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BgCream),
        border = BorderStroke(1.dp, BorderBeige), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 20.sp)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                color = if (highlight) PriOrange else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center)
            Text(label, fontSize = 10.sp, color = TextMuted)
        }
    }
}

// ─── Recipe edit sheet ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecipeEditSheet(
    recipe:          RecipeEntity?,
    defaultPortions: Int,
    onClose:         () -> Unit,
    onSave:          (RecipeEntity) -> Unit
) {
    val isNew = recipe == null
    var name      by remember { mutableStateOf(recipe?.name ?: "") }
    var emoji     by remember { mutableStateOf(recipe?.emoji ?: "🍽️") }
    var portions  by remember { mutableStateOf(recipe?.portions ?: defaultPortions) }
    var url       by remember { mutableStateOf(recipe?.url ?: "") }
    var note      by remember { mutableStateOf(recipe?.note ?: "") }
    var favorite  by remember { mutableStateOf(recipe?.favorite ?: false) }
    var rating    by remember { mutableStateOf(recipe?.rating ?: 0) }
    // Use ArrayList to avoid operator overload issues with + and -
    var tags        by remember { mutableStateOf(ArrayList(recipe?.parseTags() ?: emptyList())) }
    var ingredients by remember { mutableStateOf(ArrayList(recipe?.parseIngredients()
        ?: listOf(Ingredient("", 0.0, "")))) }
    var steps     by remember { mutableStateOf(ArrayList(recipe?.parseSteps() ?: listOf(""))) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (isNew) "Nouvelle recette" else "Modifier la recette",
                fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)

            // Emoji + Name
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { showEmojiPicker = !showEmojiPicker },
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PriOrangeLight),
                    contentPadding = PaddingValues(0.dp)
                ) { Text(emoji, fontSize = 26.sp) }
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Nom *") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp), singleLine = true)
            }

            if (showEmojiPicker) {
                Card(shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FOOD_EMOJIS.forEach { e ->
                            Text(e, fontSize = 24.sp,
                                modifier = Modifier.clickable { emoji = e; showEmojiPicker = false }.padding(4.dp))
                        }
                    }
                }
            }

            // Favorite + Rating
            Card(colors = CardDefaults.cardColors(containerColor = BgCream), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { favorite = !favorite }) {
                        Text(if (favorite) "★" else "☆", fontSize = 28.sp,
                            color = if (favorite) StarYellow else BorderBeige)
                    }
                    Text("Favori", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Row {
                        (1..5).forEach { n ->
                            Text("★", fontSize = 24.sp,
                                color = if (n <= rating) StarYellow else BorderBeige,
                                modifier = Modifier.clickable { rating = if (n == rating) 0 else n })
                        }
                    }
                }
            }

            // Portions + URL
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Portions", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { if (portions > 1) portions-- }, modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                            Icon(Icons.Default.Remove, null, tint = PriOrange, modifier = Modifier.size(16.dp))
                        }
                        Text(portions.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        IconButton(onClick = { portions++ }, modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                            Icon(Icons.Default.Add, null, tint = PriOrange, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                OutlinedTextField(value = url, onValueChange = { url = it },
                    label = { Text("URL (optionnel)") }, modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(10.dp), singleLine = true)
            }

            // Tags — use explicit add/remove to avoid operator overload ambiguity
            Text("Tags", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RECIPE_TAGS.forEach { tag ->
                    val active = tags.contains(tag)
                    FilterChip(
                        selected = active,
                        onClick  = {
                            val newList = ArrayList(tags)
                            if (active) newList.remove(tag) else newList.add(tag)
                            tags = newList
                        },
                        label  = { Text(tag, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PriOrange,
                            selectedLabelColor     = Color.White
                        )
                    )
                }
            }

            // Note
            OutlinedTextField(value = note, onValueChange = { note = it },
                label = { Text("Note personnelle") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                shape = RoundedCornerShape(10.dp), maxLines = 5)

            // Ingredients
            Text("🧂 Ingrédients", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            ingredients.forEachIndexed { i, ing ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = ing.name, onValueChange = {
                        val list = ArrayList(ingredients)
                        list[i] = list[i].copy(name = it)
                        ingredients = list
                    }, placeholder = { Text("Ingrédient") }, modifier = Modifier.weight(3f),
                        shape = RoundedCornerShape(8.dp), singleLine = true)
                    OutlinedTextField(
                        value = if (ing.qty > 0) fmtQty(ing.qty) else "",
                        onValueChange = {
                            val list = ArrayList(ingredients)
                            list[i] = list[i].copy(qty = it.toDoubleOrNull() ?: 0.0)
                            ingredients = list
                        }, placeholder = { Text("Qté") }, modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(8.dp), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    OutlinedTextField(value = ing.unit, onValueChange = {
                        val list = ArrayList(ingredients)
                        list[i] = list[i].copy(unit = it)
                        ingredients = list
                    }, placeholder = { Text("Unité") }, modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(8.dp), singleLine = true)
                    IconButton(onClick = {
                        val list = ArrayList(ingredients)
                        list.removeAt(i)
                        ingredients = list
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFFD04040))
                    }
                }
            }
            OutlinedButton(onClick = {
                ingredients = ArrayList(ingredients).also { it.add(Ingredient("", 0.0, "")) }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ingrédient")
            }

            // Steps
            Text("📋 Étapes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Surface(color = PriOrangeLight, shape = CircleShape,
                        modifier = Modifier.size(26.dp).padding(top = 12.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text((i + 1).toString(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = PriOrange)
                        }
                    }
                    OutlinedTextField(value = step, onValueChange = {
                        val list = ArrayList(steps)
                        list[i] = it
                        steps = list
                    }, placeholder = { Text("Étape ${i + 1}...") },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = RoundedCornerShape(8.dp), maxLines = 5)
                    IconButton(onClick = {
                        val list = ArrayList(steps)
                        list.removeAt(i)
                        steps = list
                    }, modifier = Modifier.size(32.dp).padding(top = 8.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFFD04040))
                    }
                }
            }
            OutlinedButton(onClick = {
                steps = ArrayList(steps).also { it.add("") }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Étape")
            }

            // Save / Cancel
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Annuler") }
                Button(
                    onClick = {
                        if (name.isBlank()) return@Button
                        val baseId  = recipe?.id ?: UUID.randomUUID().toString()
                        val entity  = RecipeEntity(
                            id          = baseId,
                            name        = name.trim(),
                            emoji       = emoji,
                            portions    = portions,
                            url         = url.trim(),
                            note        = note.trim(),
                            favorite    = favorite,
                            rating      = rating,
                            tags        = stringsToJson(tags as List<String>),
                            ingredients = ingredientsToJson(ingredients.filter { it.name.isNotBlank() }),
                            steps       = stringsToJson(steps.filter { it.isNotBlank() }),
                            createdAt   = recipe?.createdAt ?: System.currentTimeMillis()
                        )
                        onSave(entity)
                    },
                    enabled  = name.isNotBlank(),
                    modifier = Modifier.weight(2f),
                    colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)
                ) { Text("Enregistrer", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun fmtQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

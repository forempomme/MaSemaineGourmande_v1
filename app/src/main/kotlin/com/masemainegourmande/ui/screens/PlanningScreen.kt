package com.masemainegourmande.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.masemainegourmande.data.model.RecipeEntity
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.util.IsoWeekHelper
import com.masemainegourmande.viewmodel.MealWithRecipe
import com.masemainegourmande.viewmodel.PlanningViewModel
import kotlinx.coroutines.launch

/** Modifier.clickable without ripple effect — uses composed{} to safely call remember */
private fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    clickable(indication = null, interactionSource = interactionSource, onClick = onClick)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlanningScreen(vm: PlanningViewModel, defaultPersons: Int) {
    val allMeals by vm.allMeals.collectAsState()
    val recipes  by vm.recipes.collectAsState()
    val today     = IsoWeekHelper.today()
    val years     = (today.year..today.year + 3).toList()

    // Flat list of items: year-headers + week rows
    data class WeekItem(val year: Int, val week: Int, val isHeader: Boolean)
    val allItems = remember(years) {
        buildList {
            years.forEach { y ->
                add(WeekItem(y, 0, true))
                (1..IsoWeekHelper.weeksInYear(y)).forEach { w -> add(WeekItem(y, w, false)) }
            }
        }
    }

    // Index of the current week in the flat list
    val currentWeekIndex = remember(allItems, today) {
        allItems.indexOfFirst { !it.isHeader && it.year == today.year && it.week == today.week }
            .coerceAtLeast(0)
    }

    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    var addModal  by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dupModal  by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Scroll to current week immediately on first composition
    LaunchedEffect(Unit) {
        listState.scrollToItem(currentWeekIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Planning des repas", fontWeight = FontWeight.ExtraBold)
                        Text("Semaine ${today.week} · ${today.year}",
                            fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = PriOrange,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    // Go-to-current-week button
                    IconButton(onClick = {
                        scope.launch { listState.animateScrollToItem(currentWeekIndex) }
                    }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Semaine actuelle")
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start  = 14.dp, end = 14.dp,
                top    = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items       = allItems,
                key         = { if (it.isHeader) "hdr_${it.year}" else "${it.year}_W${it.week}" },
                contentType = { if (it.isHeader) "header" else "week" }
            ) { item ->
                if (item.isHeader) {
                    // Year divider
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(Modifier.weight(1f), color = BorderBeige)
                        Surface(color = PriOrange, shape = RoundedCornerShape(20.dp)) {
                            Text(
                                "📅 ${item.year}",
                                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                        HorizontalDivider(Modifier.weight(1f), color = BorderBeige)
                    }
                } else {
                    val weekKey   = IsoWeekHelper.YearWeek(item.year, item.week).key
                    val isCur     = item.year == today.year && item.week == today.week
                    val weekMeals = allMeals.filter { it.weekKey == weekKey }
                    val recipeMap = recipes.associateBy { it.id }
                    val meals     = weekMeals.mapNotNull { meal ->
                        recipeMap[meal.recipeId]?.let { MealWithRecipe(meal, it) }
                    }
                    val (monday, sunday) = IsoWeekHelper.weekBounds(item.year, item.week)

                    WeekCard(
                        year    = item.year, week = item.week,
                        monday  = IsoWeekHelper.fmt(monday),
                        sunday  = IsoWeekHelper.fmt(sunday),
                        isCurrent   = isCur,
                        meals       = meals,
                        onAdd       = { addModal = item.year to item.week },
                        onDuplicate = { dupModal = item.year to item.week },
                        onDelete    = { id -> vm.deleteMeal(id) },
                        onMinus     = { id, cur -> vm.updatePersons(id, maxOf(1, cur - 1)) },
                        onPlus      = { id, cur -> vm.updatePersons(id, cur + 1) }
                    )
                }
            }
        }
    }

    addModal?.let { (y, w) ->
        AddMealModal(
            recipes        = recipes,
            defaultPersons = defaultPersons,
            weekLabel      = "S$w · ${IsoWeekHelper.fmt(IsoWeekHelper.weekBounds(y, w).first)} – ${IsoWeekHelper.fmt(IsoWeekHelper.weekBounds(y, w).second)}",
            onDismiss      = { addModal = null },
            onAdd          = { selections ->
                vm.addMeals(IsoWeekHelper.YearWeek(y, w).key, selections)
                addModal = null
            }
        )
    }

    dupModal?.let { (sy, sw) ->
        DuplicateWeekModal(
            srcYear = sy, srcWeek = sw,
            onDismiss   = { dupModal = null },
            onDuplicate = { dy, dw ->
                vm.duplicateWeek(
                    IsoWeekHelper.YearWeek(sy, sw).key,
                    IsoWeekHelper.YearWeek(dy, dw).key
                )
                dupModal = null
            }
        )
    }
}

// ─── Week card ────────────────────────────────────────────────

@Composable
private fun WeekCard(
    year: Int, week: Int, monday: String, sunday: String, isCurrent: Boolean,
    meals: List<MealWithRecipe>,
    onAdd: () -> Unit, onDuplicate: () -> Unit,
    onDelete: (String) -> Unit,
    onMinus:  (String, Int) -> Unit,
    onPlus:   (String, Int) -> Unit
) {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) PriOrangeLight else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isCurrent) 2.dp else 1.dp,
            if (isCurrent) PriOrange else BorderBeige
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${if (isCurrent) "⭐ " else ""}S$week",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = if (isCurrent) PriOrange else MaterialTheme.colorScheme.onSurface
                    )
                    Text("$monday – $sunday", fontSize = 11.sp, color = TextMuted)
                }
                if (meals.isNotEmpty()) {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, null, tint = AccGreen, modifier = Modifier.size(18.dp))
                    }
                }
                FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.height(32.dp),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.filledTonalButtonColors(
                        containerColor = PriOrange, contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ajouter", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (meals.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                meals.forEach { mwr ->
                    MealRow(mwr = mwr,
                        onDelete = { onDelete(mwr.meal.id) },
                        onMinus  = { onMinus(mwr.meal.id, mwr.meal.persons) },
                        onPlus   = { onPlus(mwr.meal.id, mwr.meal.persons) })
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
private fun MealRow(mwr: MealWithRecipe, onDelete: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderBeige)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(mwr.recipe.emoji, fontSize = 18.sp)
            Text(mwr.recipe.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Stepper
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(color = PriOrangeLight, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(22.dp).noRippleClick { onMinus() }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PriOrange)
                    }
                }
                Text(mwr.meal.persons.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 16.dp), textAlign = TextAlign.Center)
                Surface(color = PriOrangeLight, shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(22.dp).noRippleClick { onPlus() }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PriOrange)
                    }
                }
                Text("👤", fontSize = 10.sp, color = TextMuted)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Add meal modal ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMealModal(
    recipes: List<RecipeEntity>, defaultPersons: Int, weekLabel: String,
    onDismiss: () -> Unit, onAdd: (List<Pair<String, Int>>) -> Unit
) {
    val selected = remember { mutableStateMapOf<String, Int>() }
    var search   by remember { mutableStateOf("") }
    val filtered = recipes.filter {
        search.isBlank() || it.name.contains(search, true) ||
        it.parseIngredients().any { i -> i.name.contains(search, true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ajouter des repas", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(weekLabel, fontSize = 12.sp, color = TextMuted)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }

            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Rechercher une recette...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp), singleLine = true
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.heightIn(max = 380.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(filtered, key = { it.id }) { recipe ->
                    val sel = selected.containsKey(recipe.id)
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Custom checkbox
                        Surface(
                            color  = if (sel) PriOrange else Color.Transparent,
                            border = BorderStroke(2.dp, if (sel) PriOrange else BorderBeige),
                            shape  = RoundedCornerShape(6.dp),
                            modifier = Modifier.size(22.dp).noRippleClick {
                                if (sel) selected.remove(recipe.id) else selected[recipe.id] = defaultPersons
                            }
                        ) {
                            if (sel) Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text(recipe.emoji, fontSize = 22.sp)
                        Column(Modifier.weight(1f)) {
                            Text(recipe.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val tags = recipe.parseTags()
                            if (tags.isNotEmpty()) Text(tags.take(2).joinToString(" · "), fontSize = 10.sp, color = TextMuted)
                        }
                        if (sel) {
                            val cnt = selected[recipe.id] ?: defaultPersons
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(color = PriOrangeLight, shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(26.dp).noRippleClick { selected[recipe.id] = maxOf(1, cnt - 1) }) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("−", fontSize = 14.sp, color = PriOrange, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(cnt.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    modifier = Modifier.widthIn(min = 18.dp), textAlign = TextAlign.Center)
                                Surface(color = PriOrangeLight, shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(26.dp).noRippleClick { selected[recipe.id] = cnt + 1 }) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("+", fontSize = 14.sp, color = PriOrange, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text("👤", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                    HorizontalDivider(color = BorderBeige, thickness = 0.5.dp)
                }
            }

            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)) { Text("Annuler") }
                Button(onClick = { onAdd(selected.map { (id, p) -> id to p }) },
                    enabled = selected.isNotEmpty(), modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                    Text(if (selected.isNotEmpty()) "Ajouter (${selected.size})" else "Ajouter", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Duplicate week modal ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicateWeekModal(
    srcYear: Int, srcWeek: Int, onDismiss: () -> Unit, onDuplicate: (Int, Int) -> Unit
) {
    val today = IsoWeekHelper.today()
    var selYear by remember { mutableStateOf(today.year) }
    var selWeek by remember { mutableStateOf(today.week) }
    val isSame  = selYear == srcYear && selWeek == srcWeek
    val (dstMon, dstSun) = IsoWeekHelper.weekBounds(selYear, selWeek)
    val (srcMon, srcSun) = IsoWeekHelper.weekBounds(srcYear, srcWeek)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Copier la semaine", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text("S$srcWeek · ${IsoWeekHelper.fmt(srcMon)} – ${IsoWeekHelper.fmt(srcSun)}",
                fontSize = 13.sp, color = TextMuted)

            Card(colors = CardDefaults.cardColors(containerColor = PriOrangeLight), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (selWeek > 1) selWeek-- else { selYear--; selWeek = IsoWeekHelper.weeksInYear(selYear) }
                    }, colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrange)) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("S$selWeek $selYear", fontWeight = FontWeight.Bold, color = PriOrange, fontSize = 16.sp)
                        Text("${IsoWeekHelper.fmt(dstMon)} – ${IsoWeekHelper.fmt(dstSun)}",
                            fontSize = 12.sp, color = PriOrange.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = {
                        if (selWeek < IsoWeekHelper.weeksInYear(selYear)) selWeek++ else { selYear++; selWeek = 1 }
                    }, colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrange)) {
                        Icon(Icons.Default.ArrowForward, null, tint = Color.White)
                    }
                }
            }

            if (isSame) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    border = BorderStroke(1.dp, Color(0xFFFFD166))) {
                    Text("Choisissez une semaine différente de la source.",
                        modifier = Modifier.padding(12.dp), fontSize = 13.sp, color = Color(0xFF856404))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Annuler") }
                Button(onClick = { onDuplicate(selYear, selWeek) }, enabled = !isSame,
                    modifier = Modifier.weight(2f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                    Text("Copier ici", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

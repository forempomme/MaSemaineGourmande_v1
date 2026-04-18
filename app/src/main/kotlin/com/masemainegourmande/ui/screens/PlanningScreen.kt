package com.masemainegourmande.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.RecipeEntity
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.util.IsoWeekHelper
import com.masemainegourmande.viewmodel.MealWithRecipe
import com.masemainegourmande.viewmodel.PlanningViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlanningScreen(vm: PlanningViewModel, defaultPersons: Int) {
    val mealsByYear by vm.mealsByYear.collectAsState()
    val recipes     by vm.recipes.collectAsState()
    val allMeals    by vm.allMeals.collectAsState()
    val today        = IsoWeekHelper.today()
    val years        = (today.year..today.year + 3).toList()
    val openYears    = remember { mutableStateMapOf(today.year to true) }
    var addModal     by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dupModal     by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Planning des repas", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PriOrange, titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = {
                        openYears[today.year] = true
                        scope.launch {
                            // scroll to approx current week
                            val totalItemsBefore = years.takeWhile { it < today.year }
                                .sumOf { IsoWeekHelper.weeksInYear(it) + 1 } + today.week
                            listState.animateScrollToItem(totalItemsBefore.coerceAtLeast(0))
                        }
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
                start = 14.dp, end = 14.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            years.forEach { year ->
                val isOpen = openYears[year] == true
                val weeksCount = IsoWeekHelper.weeksInYear(year)
                val yearMealCount = allMeals.count {
                    IsoWeekHelper.parseKey(it.weekKey)?.year == year
                }

                stickyHeader(key = "year_$year") {
                    YearHeader(
                        year = year, mealCount = yearMealCount, isOpen = isOpen,
                        onClick = { openYears[year] = !isOpen }
                    )
                }

                if (isOpen) {
                    items(
                        items = (1..weeksCount).toList(),
                        key   = { w -> "$year-W$w" }
                    ) { week ->
                        val weekKey = IsoWeekHelper.YearWeek(year, week).key
                        val isCurrent = year == today.year && week == today.week
                        val weekMeals = allMeals.filter { it.weekKey == weekKey }
                        val recipeMap = recipes.associateBy { it.id }
                        val mealsWithRecipes = weekMeals.mapNotNull { meal ->
                            recipeMap[meal.recipeId]?.let { MealWithRecipe(meal, it) }
                        }
                        val (monday, sunday) = IsoWeekHelper.weekBounds(year, week)

                        WeekCard(
                            year          = year,
                            week          = week,
                            monday        = IsoWeekHelper.fmt(monday),
                            sunday        = IsoWeekHelper.fmt(sunday),
                            isCurrent     = isCurrent,
                            meals         = mealsWithRecipes,
                            onAdd         = { addModal = year to week },
                            onDuplicate   = { dupModal = year to week },
                            onDelete      = { mealId -> vm.deleteMeal(mealId) },
                            onPersonsMinus = { mealId, cur -> vm.updatePersons(mealId, maxOf(1, cur - 1)) },
                            onPersonsPlus  = { mealId, cur -> vm.updatePersons(mealId, cur + 1) }
                        )
                    }
                }
            }
        }
    }

    addModal?.let { (y, w) ->
        AddMealModal(
            recipes       = recipes,
            defaultPersons = defaultPersons,
            weekLabel     = "S$w ${IsoWeekHelper.fmt(IsoWeekHelper.weekBounds(y, w).first)}",
            onDismiss     = { addModal = null },
            onAdd         = { selections ->
                val key = IsoWeekHelper.YearWeek(y, w).key
                vm.addMeals(key, selections)
                addModal = null
            }
        )
    }

    dupModal?.let { (sy, sw) ->
        DuplicateWeekModal(
            srcYear = sy, srcWeek = sw,
            onDismiss = { dupModal = null },
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

// ─── Year header ─────────────────────────────────────────────

@Composable
private fun YearHeader(year: Int, mealCount: Int, isOpen: Boolean, onClick: () -> Unit) {
    Surface(color = BgCream) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (isOpen) PriOrange else PriOrangeLight,
                contentColor   = if (isOpen) Color.White else PriOrange
            )
        ) {
            Text("📅 $year", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("$mealCount repas · ${if (isOpen) "▲" else "▼"}", fontSize = 12.sp)
        }
    }
}

// ─── Week card ───────────────────────────────────────────────

@Composable
private fun WeekCard(
    year: Int, week: Int, monday: String, sunday: String, isCurrent: Boolean,
    meals: List<MealWithRecipe>,
    onAdd: () -> Unit, onDuplicate: () -> Unit,
    onDelete: (String) -> Unit,
    onPersonsMinus: (String, Int) -> Unit,
    onPersonsPlus: (String, Int) -> Unit
) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) PriOrangeLight else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, if (isCurrent) PriOrange else BorderBeige),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
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
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copier", tint = AccGreen)
                    }
                }
                Button(
                    onClick = onAdd,
                    modifier = Modifier.height(32.dp),
                    shape    = RoundedCornerShape(8.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = PriOrange),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ajouter", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            meals.forEach { mwr ->
                Spacer(Modifier.height(6.dp))
                MealRow(
                    mwr            = mwr,
                    onDelete       = { onDelete(mwr.meal.id) },
                    onMinus        = { onPersonsMinus(mwr.meal.id, mwr.meal.persons) },
                    onPlus         = { onPersonsPlus(mwr.meal.id, mwr.meal.persons) }
                )
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
            Text(mwr.recipe.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            // Persons stepper
            IconButton(onClick = onMinus, modifier = Modifier.size(26.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                Icon(Icons.Default.Remove, contentDescription = null, tint = PriOrange, modifier = Modifier.size(14.dp))
            }
            Text(mwr.meal.persons.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onPlus, modifier = Modifier.size(26.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = PriOrange, modifier = Modifier.size(14.dp))
            }
            Text("👤", fontSize = 10.sp)
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Add meal modal ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMealModal(
    recipes: List<RecipeEntity>,
    defaultPersons: Int,
    weekLabel: String,
    onDismiss: () -> Unit,
    onAdd: (List<Pair<String, Int>>) -> Unit
) {
    val selected = remember { mutableStateMapOf<String, Int>() }
    var search by remember { mutableStateOf("") }
    val filtered = recipes.filter {
        search.isBlank() || it.name.contains(search, true) ||
        it.parseIngredients().any { i -> i.name.contains(search, true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            // Header
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ajouter des repas", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(weekLabel, fontSize = 12.sp, color = TextMuted)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
            // Search
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                placeholder = { Text("Rechercher une recette...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            // Recipe list
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 360.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(filtered, key = { it.id }) { recipe ->
                    val sel = selected.containsKey(recipe.id)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(checked = sel, onCheckedChange = {
                            if (sel) selected.remove(recipe.id) else selected[recipe.id] = defaultPersons
                        }, colors = CheckboxDefaults.colors(checkedColor = PriOrange))
                        Text(recipe.emoji, fontSize = 22.sp)
                        Column(Modifier.weight(1f)) {
                            Text(recipe.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (recipe.parseTags().isNotEmpty())
                                Text(recipe.parseTags().take(2).joinToString(" · "), fontSize = 10.sp, color = TextMuted)
                        }
                        if (sel) {
                            val cnt = selected[recipe.id] ?: defaultPersons
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { selected[recipe.id] = maxOf(1, cnt - 1) }, modifier = Modifier.size(28.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                                    Icon(Icons.Default.Remove, null, tint = PriOrange, modifier = Modifier.size(14.dp))
                                }
                                Text(cnt.toString(), fontWeight = FontWeight.Bold)
                                IconButton(onClick = { selected[recipe.id] = cnt + 1 }, modifier = Modifier.size(28.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                                    Icon(Icons.Default.Add, null, tint = PriOrange, modifier = Modifier.size(14.dp))
                                }
                                Text("👤", fontSize = 11.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = BorderBeige)
                }
            }
            // Confirm
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Annuler") }
                Button(
                    onClick = { onAdd(selected.map { (id, p) -> id to p }) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = PriOrange)
                ) {
                    Text(if (selected.isNotEmpty()) "Ajouter (${selected.size})" else "Ajouter")
                }
            }
        }
    }
}

// ─── Duplicate week modal ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicateWeekModal(
    srcYear: Int, srcWeek: Int,
    onDismiss: () -> Unit,
    onDuplicate: (Int, Int) -> Unit
) {
    val today  = IsoWeekHelper.today()
    val years  = (today.year..today.year + 3).toList()
    var selYear by remember { mutableIntStateOf(today.year) }
    var selWeek by remember { mutableIntStateOf(today.week) }
    val isSame = selYear == srcYear && selWeek == srcWeek
    val (dstMon, dstSun) = IsoWeekHelper.weekBounds(selYear, selWeek)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Copier la semaine S$srcWeek $srcYear", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text("Les repas seront ajoutés à ceux déjà présents.", fontSize = 13.sp, color = TextMuted)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Année", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    ExposedDropdownMenuBox(expanded = false, onExpandedChange = {}) {
                        OutlinedTextField(value = selYear.toString(), onValueChange = {},
                            readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp), singleLine = true)
                        ExposedDropdownMenu(expanded = false, onDismissRequest = {}) {
                            years.forEach { y ->
                                DropdownMenuItem(text = { Text(y.toString()) }, onClick = { selYear = y; selWeek = 1 })
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Semaine", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    OutlinedTextField(
                        value = "S$selWeek · ${IsoWeekHelper.fmt(IsoWeekHelper.weekBounds(selYear, selWeek).first)}",
                        onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true
                    )
                }
            }
            // Simple week picker
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { if (selWeek > 1) selWeek-- else { selYear--; selWeek = IsoWeekHelper.weeksInYear(selYear) } },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                    Icon(Icons.Default.ArrowBack, null, tint = PriOrange)
                }
                Text("S$selWeek $selYear\n${IsoWeekHelper.fmt(dstMon)} – ${IsoWeekHelper.fmt(dstSun)}",
                    modifier = Modifier.weight(1f), fontSize = 13.sp)
                IconButton(onClick = { if (selWeek < IsoWeekHelper.weeksInYear(selYear)) selWeek++ else { selYear++; selWeek = 1 } },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                    Icon(Icons.Default.ArrowForward, null, tint = PriOrange)
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
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Annuler") }
                Button(onClick = { onDuplicate(selYear, selWeek) }, enabled = !isSame,
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                    Text("Copier ici")
                }
            }
        }
    }
}

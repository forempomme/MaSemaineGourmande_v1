package com.masemainegourmande.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.RecipeEntity
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.util.IsoWeekHelper
import com.masemainegourmande.viewmodel.MealWithRecipe
import com.masemainegourmande.viewmodel.PlanningViewModel
import kotlinx.coroutines.launch

private fun Modifier.noRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        indication        = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick           = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(vm: PlanningViewModel, defaultPersons: Int) {
    val allMeals by vm.allMeals.collectAsState()
    val recipes  by vm.recipes.collectAsState()
    val today     = IsoWeekHelper.today()
    val years     = listOf(today.year, today.year + 1, today.year + 2, today.year + 3)

    // JSX: openYears = { [curY]: true } — only current year open by default
    val openYears = remember { mutableStateMapOf(today.year to true) }

    var addModal  by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dupModal  by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()

    // Count of items before current year in the lazy list (to scroll to current week)
    fun currentWeekScrollIndex(): Int {
        var idx = 0
        for (y in years) {
            idx++ // year header
            if (y == today.year) {
                idx += today.week - 1  // week rows before current week
                break
            } else if (openYears[y] == true) {
                idx += IsoWeekHelper.weeksInYear(y)
            }
        }
        return idx
    }

    fun scrollToCurrent() {
        openYears[today.year] = true
        scope.launch {
            // small delay to let recomposition happen after opening the year
            kotlinx.coroutines.delay(120)
            listState.animateScrollToItem(currentWeekScrollIndex())
        }
    }

    // Auto-scroll on first composition — mirrors JSX useEffect
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        listState.scrollToItem(currentWeekScrollIndex())
    }

    // Build flat item list for LazyColumn
    data class Item(
        val type: String,  // "year" or "week"
        val year: Int,
        val week: Int = 0
    )

    val items = remember(years, openYears.toMap()) {
        buildList {
            years.forEach { y ->
                add(Item("year", y))
                if (openYears[y] == true) {
                    (1..IsoWeekHelper.weeksInYear(y)).forEach { w ->
                        add(Item("week", y, w))
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(BgCream)) {
        // "Semaine actuelle" button — mirrors JSX top-right button
        Row(
            Modifier.fillMaxWidth().padding(end = 14.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color  = PriOrangeLight,
                shape  = RoundedCornerShape(10.dp),
                modifier = Modifier.clickable { scrollToCurrent() }
            ) {
                Text(
                    "📍 Semaine actuelle",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PriOrange,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp, top = 4.dp, bottom = 76.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items       = items,
                key         = { if (it.type == "year") "yr_${it.year}" else "${it.year}_W${it.week}" },
                contentType = { it.type }
            ) { item ->
                when (item.type) {
                    "year" -> {
                        val isOpen = openYears[item.year] == true
                        val yearMealCount = allMeals.count {
                            IsoWeekHelper.parseKey(it.weekKey)?.year == item.year
                        }
                        // JSX year accordion button
                        Button(
                            onClick = { openYears[item.year] = !isOpen },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape  = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOpen) PriOrange else PriOrangeLight,
                                contentColor   = if (isOpen) Color.White else PriOrange
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("📅 ${item.year}", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                modifier = Modifier.weight(1f),
                                color = if (isOpen) Color.White else PriOrange)
                            Text("$yearMealCount repas · ${if (isOpen) "▲" else "▼"}",
                                fontSize = 12.sp,
                                color = if (isOpen) Color.White.copy(alpha = 0.85f) else PriOrange)
                        }
                    }

                    "week" -> {
                        val isCur     = item.year == today.year && item.week == today.week
                        val weekKey   = IsoWeekHelper.YearWeek(item.year, item.week).key
                        val weekMeals = allMeals.filter { it.weekKey == weekKey }
                        val recipeMap = recipes.associateBy { it.id }
                        val meals = weekMeals.mapNotNull { meal ->
                            recipeMap[meal.recipeId]?.let { MealWithRecipe(meal, it) }
                        }
                        val (monday, sunday) = IsoWeekHelper.weekBounds(item.year, item.week)

                        WeekCard(
                            week    = item.week,
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
                        Spacer(Modifier.height(7.dp))
                    }
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
        DuplicateWeekModal(srcYear = sy, srcWeek = sw,
            onDismiss   = { dupModal = null },
            onDuplicate = { dy, dw ->
                vm.duplicateWeek(IsoWeekHelper.YearWeek(sy, sw).key, IsoWeekHelper.YearWeek(dy, dw).key)
                dupModal = null
            })
    }
}

// ─── Week card — mirrors JSX week card exactly ────────────────

@Composable
private fun WeekCard(
    week: Int, monday: String, sunday: String, isCurrent: Boolean,
    meals: List<MealWithRecipe>,
    onAdd: () -> Unit, onDuplicate: () -> Unit,
    onDelete: (String) -> Unit, onMinus: (String, Int) -> Unit, onPlus: (String, Int) -> Unit
) {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) PriOrangeLight else Color.White
        ),
        border = BorderStroke(
            width = if (isCurrent) 2.dp else 1.dp,
            color = if (isCurrent) PriOrange else BorderBeige
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${if (isCurrent) "⭐ " else ""}S$week",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (isCurrent) PriOrange else TextBrown
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("$monday – $sunday", fontSize = 11.sp, color = TextMuted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (meals.isNotEmpty()) {
                        // Copy button — mirrors JSX 📋 button
                        Surface(
                            color    = AccGreenLight,
                            shape    = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onDuplicate() }
                        ) {
                            Text("📋", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                    }
                    // Add button — orange, mirrors JSX "+ Ajouter"
                    Surface(
                        color    = PriOrange,
                        shape    = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onAdd() }
                    ) {
                        Text("+ Ajouter", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp))
                    }
                }
            }

            // Meal rows — mirrors JSX meal items
            if (meals.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                meals.forEach { mwr ->
                    MealRow(mwr = mwr,
                        onDelete = { onDelete(mwr.meal.id) },
                        onMinus  = { onMinus(mwr.meal.id, mwr.meal.persons) },
                        onPlus   = { onPlus(mwr.meal.id, mwr.meal.persons) })
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun MealRow(mwr: MealWithRecipe, onDelete: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, BorderBeige, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(mwr.recipe.emoji, fontSize = 18.sp)
        Text(mwr.recipe.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = TextBrown, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)

        // Persons stepper — matches JSX exactly (22×22 buttons)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(color = PriOrangeLight, shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(22.dp).noRipple { onMinus() }) {
                Box(contentAlignment = Alignment.Center) {
                    Text("−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PriOrange)
                }
            }
            Text(mwr.meal.persons.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = TextBrown, modifier = Modifier.widthIn(min = 16.dp),
                textAlign = TextAlign.Center)
            Surface(color = PriOrangeLight, shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(22.dp).noRipple { onPlus() }) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PriOrange)
                }
            }
            Text("👤", fontSize = 10.sp, color = TextMuted)
        }

        // ✕ delete button
        Box(Modifier.size(24.dp).noRipple { onDelete() },
            contentAlignment = Alignment.Center) {
            Text("✕", fontSize = 15.sp, color = TextMuted)
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
            OutlinedTextField(value = search, onValueChange = { search = it },
                placeholder = { Text("Rechercher une recette...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(10.dp), singleLine = true)
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.heightIn(max = 380.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(filtered, key = { it.id }) { recipe ->
                    val sel = selected.containsKey(recipe.id)
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Custom checkbox (matches JSX style)
                        Surface(
                            color  = if (sel) PriOrange else Color.Transparent,
                            border = BorderStroke(2.dp, if (sel) PriOrange else BorderBeige),
                            shape  = RoundedCornerShape(6.dp),
                            modifier = Modifier.size(22.dp).noRipple {
                                if (sel) selected.remove(recipe.id) else selected[recipe.id] = defaultPersons
                            }
                        ) {
                            if (sel) Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("✓", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text(recipe.emoji, fontSize = 22.sp)
                        Column(Modifier.weight(1f)) {
                            Text(recipe.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val tags = recipe.parseTags()
                            if (tags.isNotEmpty())
                                Text(tags.take(2).joinToString(" · "), fontSize = 10.sp, color = TextMuted)
                        }
                        if (sel) {
                            val cnt = selected[recipe.id] ?: defaultPersons
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(color = PriOrangeLight, shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(26.dp).noRipple { selected[recipe.id] = maxOf(1, cnt - 1) }) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("−", fontSize = 14.sp, color = PriOrange, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(cnt.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    modifier = Modifier.widthIn(min = 18.dp), textAlign = TextAlign.Center)
                                Surface(color = PriOrangeLight, shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(26.dp).noRipple { selected[recipe.id] = cnt + 1 }) {
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
                    Text(if (selected.isNotEmpty()) "Ajouter (${selected.size})" else "Ajouter",
                        fontWeight = FontWeight.Bold)
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
    val today  = IsoWeekHelper.today()
    val years  = listOf(today.year, today.year + 1, today.year + 2, today.year + 3)
    var selYear by remember { mutableStateOf(today.year) }
    var selWeek by remember { mutableStateOf(today.week) }
    val isSame  = selYear == srcYear && selWeek == srcWeek
    val (dstMon, dstSun) = IsoWeekHelper.weekBounds(selYear, selWeek)
    val (srcMon, srcSun) = IsoWeekHelper.weekBounds(srcYear, srcWeek)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📋 Copier la semaine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("S$srcWeek · ${IsoWeekHelper.fmt(srcMon)} – ${IsoWeekHelper.fmt(srcSun)}",
                        fontSize = 12.sp, color = TextMuted)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            Text("Choisissez la semaine de destination. Les repas seront ajoutés à ceux déjà présents.",
                fontSize = 13.sp, color = TextMuted)

            // Year + week selectors
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Année", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
                    ExposedDropdownMenuBox(expanded = false, onExpandedChange = {}) {
                        var yExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = yExpanded, onExpandedChange = { yExpanded = it }) {
                            OutlinedTextField(value = selYear.toString(), onValueChange = {},
                                readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp), singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yExpanded) })
                            ExposedDropdownMenu(expanded = yExpanded, onDismissRequest = { yExpanded = false }) {
                                years.forEach { y ->
                                    DropdownMenuItem(text = { Text(y.toString()) },
                                        onClick = { selYear = y; selWeek = 1; yExpanded = false })
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Semaine", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
                    var wExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = wExpanded, onExpandedChange = { wExpanded = it }) {
                        val (wMon, _) = IsoWeekHelper.weekBounds(selYear, selWeek)
                        OutlinedTextField(value = "S$selWeek · ${IsoWeekHelper.fmt(wMon)}", onValueChange = {},
                            readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp), singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wExpanded) })
                        ExposedDropdownMenu(expanded = wExpanded, onDismissRequest = { wExpanded = false }) {
                            (1..IsoWeekHelper.weeksInYear(selYear)).forEach { w ->
                                val (wm, _) = IsoWeekHelper.weekBounds(selYear, w)
                                DropdownMenuItem(text = { Text("S$w · ${IsoWeekHelper.fmt(wm)}") },
                                    onClick = { selWeek = w; wExpanded = false })
                            }
                        }
                    }
                }
            }

            if (!isSame) {
                Surface(color = PriOrangeLight, shape = RoundedCornerShape(10.dp)) {
                    Text("➡️ Copier vers S$selWeek $selYear (${IsoWeekHelper.fmt(dstMon)} – ${IsoWeekHelper.fmt(dstSun)})",
                        fontSize = 13.sp, color = PriOrange,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            } else {
                Surface(color = Color(0xFFFFF3CD), shape = RoundedCornerShape(10.dp)) {
                    Text("⚠️ Choisissez une semaine différente de la source.",
                        fontSize = 13.sp, color = Color(0xFF856404),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)) { Text("Annuler") }
                Button(onClick = { onDuplicate(selYear, selWeek) }, enabled = !isSame,
                    modifier = Modifier.weight(2f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                    Text("Copier ici", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

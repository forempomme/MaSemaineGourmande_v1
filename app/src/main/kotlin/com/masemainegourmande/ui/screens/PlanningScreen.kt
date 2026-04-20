package com.masemainegourmande.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
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
import com.masemainegourmande.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Modifier.noRipple(onClick: () -> Unit): Modifier = composed {
    clickable(indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(vm: PlanningViewModel, shoppingVm: ShoppingViewModel, defaultPersons: Int) {
    val allMeals by vm.allMeals.collectAsState()
    val recipes  by vm.recipes.collectAsState()
    val context   = LocalContext.current
    val today     = IsoWeekHelper.today()
    val years     = listOf(today.year, today.year+1, today.year+2, today.year+3)

    // ── Performance: precompute map once instead of filtering per-week ──
    val mealsByKey by remember(allMeals) {
        derivedStateOf { allMeals.groupBy { it.weekKey } }
    }
    val recipeMap by remember(recipes) {
        derivedStateOf { recipes.associateBy { it.id } }
    }

    val openYears  = remember { mutableStateMapOf(today.year to true) }
    var addModal   by remember { mutableStateOf<Pair<Int,Int>?>(null) }
    var dupModal   by remember { mutableStateOf<Pair<Int,Int>?>(null) }
    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()

    // Build flat list: year headers + week rows (only for open years)
    data class Item(val type: String, val year: Int, val week: Int = 0)
    val flatItems = remember(years, openYears.toMap()) {
        buildList {
            years.forEach { y ->
                add(Item("year", y))
                if (openYears[y] == true)
                    (1..IsoWeekHelper.weeksInYear(y)).forEach { w -> add(Item("week", y, w)) }
            }
        }
    }

    val currentIdx = remember(flatItems, today) {
        flatItems.indexOfFirst { !it.type.equals("year") && it.year == today.year && it.week == today.week }
            .coerceAtLeast(0)
    }

    LaunchedEffect(Unit) { listState.scrollToItem(currentIdx) }

    Column(Modifier.fillMaxSize().background(BgCream)) {
        // "Semaine actuelle" button
        Row(Modifier.fillMaxWidth().padding(end = 14.dp, top = 10.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.End) {
            Surface(color = PriOrangeLight, shape = RoundedCornerShape(10.dp),
                modifier = Modifier.clickable {
                    openYears[today.year] = true
                    scope.launch { delay(80); listState.animateScrollToItem(currentIdx) }
                }) {
                Text("📍 Semaine actuelle", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = PriOrange, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start=14.dp, end=14.dp, top=4.dp, bottom=80.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(flatItems,
                key         = { if (it.type=="year") "yr_${it.year}" else "${it.year}_W${it.week}" },
                contentType = { it.type }
            ) { item ->
                when (item.type) {
                    "year" -> {
                        val isOpen = openYears[item.year] == true
                        val cnt    = allMeals.count { IsoWeekHelper.parseKey(it.weekKey)?.year == item.year }
                        Button(
                            onClick = { openYears[item.year] = !isOpen },
                            modifier = Modifier.fillMaxWidth(),
                            shape  = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOpen) PriOrange else PriOrangeLight,
                                contentColor   = if (isOpen) Color.White else PriOrange),
                            contentPadding = PaddingValues(horizontal=16.dp, vertical=12.dp)
                        ) {
                            Text("📅 ${item.year}", fontWeight=FontWeight.Bold, fontSize=16.sp,
                                modifier=Modifier.weight(1f),
                                color=if(isOpen) Color.White else PriOrange)
                            Text("$cnt repas · ${if(isOpen) "▲" else "▼"}", fontSize=12.sp,
                                color=if(isOpen) Color.White.copy(alpha=.85f) else PriOrange)
                        }
                    }
                    "week" -> {
                        val weekKey  = IsoWeekHelper.YearWeek(item.year, item.week).key
                        val isCur    = item.year==today.year && item.week==today.week
                        val weekMeals = mealsByKey[weekKey] ?: emptyList()
                        val meals     = weekMeals.mapNotNull { meal ->
                            recipeMap[meal.recipeId]?.let { MealWithRecipe(meal, it) }
                        }
                        val (monday, sunday) = IsoWeekHelper.weekBounds(item.year, item.week)

                        WeekCard(
                            week=item.week, monday=IsoWeekHelper.fmt(monday), sunday=IsoWeekHelper.fmt(sunday),
                            isCurrent=isCur, meals=meals,
                            onAdd       = { addModal = item.year to item.week },
                            onDuplicate = { dupModal = item.year to item.week },
                            onShare     = {
                                val text = buildShareText(item.week, item.year,
                                    IsoWeekHelper.fmt(monday), IsoWeekHelper.fmt(sunday), meals)
                                context.startActivity(Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type="text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                                    }, "Partager les repas"))
                            },
                            onDelete    = { id -> vm.deleteMeal(id) },
                            onMinus     = { id, cur -> vm.updatePersons(id, maxOf(1, cur-1)) },
                            onPlus      = { id, cur -> vm.updatePersons(id, cur+1) }
                        )
                    }
                }
            }
        }
    }

    addModal?.let { (y, w) ->
        AddMealModal(
            recipes=recipes, defaultPersons=defaultPersons,
            weekLabel="S$w · ${IsoWeekHelper.fmt(IsoWeekHelper.weekBounds(y,w).first)} – ${IsoWeekHelper.fmt(IsoWeekHelper.weekBounds(y,w).second)}",
            onDismiss={ addModal=null },
            onAdd={ selections ->
                val key = IsoWeekHelper.YearWeek(y, w).key
                vm.addMeals(key, selections)
                // Auto-add ingredients to shopping list
                selections.forEach { (recipeId, persons) ->
                    recipes.find { it.id == recipeId }?.let { shoppingVm.addRecipe(it, persons) }
                }
                addModal=null
            }
        )
    }

    dupModal?.let { (sy, sw) ->
        DuplicateWeekModal(srcYear=sy, srcWeek=sw,
            onDismiss={ dupModal=null },
            onDuplicate={ dy, dw ->
                vm.duplicateWeek(IsoWeekHelper.YearWeek(sy,sw).key, IsoWeekHelper.YearWeek(dy,dw).key)
                dupModal=null
            })
    }
}

private fun buildShareText(week: Int, year: Int, monday: String, sunday: String,
                            meals: List<MealWithRecipe>): String {
    if (meals.isEmpty()) return "Aucun repas planifié pour la semaine $week."
    return buildString {
        appendLine("🍽️ Repas de la semaine $week ($year)")
        appendLine("$monday – $sunday")
        appendLine()
        meals.forEach { appendLine("• ${it.recipe.emoji} ${it.recipe.name}") }
    }.trim()
}

// ─── Week card ──────────────────────────────────────────────

@Composable
private fun WeekCard(
    week: Int, monday: String, sunday: String, isCurrent: Boolean,
    meals: List<MealWithRecipe>,
    onAdd: () -> Unit, onDuplicate: () -> Unit, onShare: () -> Unit,
    onDelete: (String) -> Unit, onMinus: (String,Int) -> Unit, onPlus: (String,Int) -> Unit
) {
    Card(
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if(isCurrent) PriOrangeLight else Color.White),
        border = BorderStroke(if(isCurrent) 2.dp else 1.dp, if(isCurrent) PriOrange else BorderBeige)
    ) {
        Column(Modifier.padding(horizontal=13.dp, vertical=11.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("${if(isCurrent) "⭐ " else ""}S$week",
                            fontWeight=FontWeight.Bold, fontSize=14.sp,
                            color=if(isCurrent) PriOrange else TextBrown)
                        Spacer(Modifier.width(6.dp))
                        Text("$monday – $sunday", fontSize=11.sp, color=TextMuted)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    if (meals.isNotEmpty()) {
                        Surface(color=AccGreenLight, shape=RoundedCornerShape(8.dp),
                            modifier=Modifier.clickable { onShare() }) {
                            Text("📤", fontSize=12.sp, fontWeight=FontWeight.Bold,
                                modifier=Modifier.padding(horizontal=9.dp, vertical=5.dp))
                        }
                        Surface(color=AccGreenLight, shape=RoundedCornerShape(8.dp),
                            modifier=Modifier.clickable { onDuplicate() }) {
                            Text("📋", fontSize=12.sp, fontWeight=FontWeight.Bold,
                                modifier=Modifier.padding(horizontal=9.dp, vertical=5.dp))
                        }
                    }
                    Surface(color=PriOrange, shape=RoundedCornerShape(8.dp),
                        modifier=Modifier.clickable { onAdd() }) {
                        Text("+ Ajouter", fontSize=13.sp, fontWeight=FontWeight.Bold,
                            color=Color.White,
                            modifier=Modifier.padding(horizontal=11.dp, vertical=5.dp))
                    }
                }
            }
            if (meals.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                meals.forEach { mwr ->
                    MealRow(mwr=mwr,
                        onDelete={ onDelete(mwr.meal.id) },
                        onMinus={ onMinus(mwr.meal.id, mwr.meal.persons) },
                        onPlus={ onPlus(mwr.meal.id, mwr.meal.persons) })
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun MealRow(mwr: MealWithRecipe, onDelete: ()->Unit, onMinus: ()->Unit, onPlus: ()->Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, BorderBeige, RoundedCornerShape(10.dp))
            .padding(horizontal=10.dp, vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(7.dp)
    ) {
        Text(mwr.recipe.emoji, fontSize=18.sp)
        Text(mwr.recipe.name, fontSize=13.sp, fontWeight=FontWeight.SemiBold, color=TextBrown,
            modifier=Modifier.weight(1f), maxLines=1, overflow=TextOverflow.Ellipsis)
        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Surface(color=PriOrangeLight, shape=RoundedCornerShape(6.dp),
                modifier=Modifier.size(22.dp).noRipple { onMinus() }) {
                Box(contentAlignment=Alignment.Center) {
                    Text("−", fontSize=14.sp, fontWeight=FontWeight.Bold, color=PriOrange)
                }
            }
            Text(mwr.meal.persons.toString(), fontSize=13.sp, fontWeight=FontWeight.Bold,
                color=TextBrown, modifier=Modifier.widthIn(min=16.dp), textAlign=TextAlign.Center)
            Surface(color=PriOrangeLight, shape=RoundedCornerShape(6.dp),
                modifier=Modifier.size(22.dp).noRipple { onPlus() }) {
                Box(contentAlignment=Alignment.Center) {
                    Text("+", fontSize=14.sp, fontWeight=FontWeight.Bold, color=PriOrange)
                }
            }
            Text("👤", fontSize=10.sp, color=TextMuted)
        }
        Box(Modifier.size(24.dp).noRipple { onDelete() }, contentAlignment=Alignment.Center) {
            Text("✕", fontSize=15.sp, color=TextMuted)
        }
    }
}

// ─── Add meal modal ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMealModal(
    recipes: List<RecipeEntity>, defaultPersons: Int, weekLabel: String,
    onDismiss: ()->Unit, onAdd: (List<Pair<String,Int>>)->Unit
) {
    val selected = remember { mutableStateMapOf<String,Int>() }
    var search by remember { mutableStateOf("") }
    val filtered = recipes.filter {
        search.isBlank() || it.name.contains(search, true) ||
        it.parseIngredients().any { i -> i.name.contains(search, true) }
    }

    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.padding(bottom=24.dp)) {
            Row(Modifier.padding(horizontal=20.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ajouter des repas", fontWeight=FontWeight.ExtraBold, fontSize=16.sp)
                    Text(weekLabel, fontSize=12.sp, color=TextMuted)
                }
                IconButton(onClick=onDismiss) { Icon(Icons.Default.Close, null) }
            }
            OutlinedTextField(value=search, onValueChange={ search=it },
                placeholder={ Text("Rechercher...") }, modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),
                shape=RoundedCornerShape(10.dp), singleLine=true)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max=380.dp), contentPadding=PaddingValues(horizontal=16.dp)) {
                items(filtered, key={ it.id }) { recipe ->
                    val sel = selected.containsKey(recipe.id)
                    Row(Modifier.fillMaxWidth().padding(vertical=7.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color=if(sel) PriOrange else Color.Transparent,
                            border=BorderStroke(2.dp, if(sel) PriOrange else BorderBeige),
                            shape=RoundedCornerShape(6.dp),
                            modifier=Modifier.size(22.dp).noRipple {
                                if(sel) selected.remove(recipe.id) else selected[recipe.id]=defaultPersons
                            }
                        ) {
                            if(sel) Box(contentAlignment=Alignment.Center, modifier=Modifier.fillMaxSize()) {
                                Text("✓", fontSize=13.sp, color=Color.White, fontWeight=FontWeight.ExtraBold)
                            }
                        }
                        Text(recipe.emoji, fontSize=22.sp)
                        Column(Modifier.weight(1f)) {
                            Text(recipe.name, fontWeight=FontWeight.SemiBold, fontSize=14.sp, maxLines=1,
                                overflow=TextOverflow.Ellipsis)
                            val tags=recipe.parseTags()
                            if(tags.isNotEmpty()) Text(tags.take(2).joinToString(" · "), fontSize=10.sp, color=TextMuted)
                        }
                        if(sel) {
                            val cnt=selected[recipe.id] ?: defaultPersons
                            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                Surface(color=PriOrangeLight, shape=RoundedCornerShape(6.dp),
                                    modifier=Modifier.size(26.dp).noRipple{ selected[recipe.id]=maxOf(1,cnt-1) }) {
                                    Box(contentAlignment=Alignment.Center) {
                                        Text("−", fontSize=14.sp, color=PriOrange, fontWeight=FontWeight.Bold)
                                    }
                                }
                                Text(cnt.toString(), fontWeight=FontWeight.Bold, fontSize=14.sp,
                                    modifier=Modifier.widthIn(min=18.dp), textAlign=TextAlign.Center)
                                Surface(color=PriOrangeLight, shape=RoundedCornerShape(6.dp),
                                    modifier=Modifier.size(26.dp).noRipple{ selected[recipe.id]=cnt+1 }) {
                                    Box(contentAlignment=Alignment.Center) {
                                        Text("+", fontSize=14.sp, color=PriOrange, fontWeight=FontWeight.Bold)
                                    }
                                }
                                Text("👤", fontSize=11.sp, color=TextMuted)
                            }
                        }
                    }
                    HorizontalDivider(color=BorderBeige, thickness=0.5.dp)
                }
            }
            Row(Modifier.padding(horizontal=16.dp, vertical=12.dp), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick=onDismiss, modifier=Modifier.weight(1f), shape=RoundedCornerShape(12.dp)) { Text("Annuler") }
                Button(onClick={ onAdd(selected.map{(id,p)->id to p}) },
                    enabled=selected.isNotEmpty(), modifier=Modifier.weight(2f), shape=RoundedCornerShape(12.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=PriOrange)) {
                    Text(if(selected.isNotEmpty()) "Ajouter (${selected.size})" else "Ajouter", fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Duplicate week modal ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicateWeekModal(srcYear: Int, srcWeek: Int, onDismiss: ()->Unit, onDuplicate: (Int,Int)->Unit) {
    val today = IsoWeekHelper.today()
    val years = listOf(today.year, today.year+1, today.year+2, today.year+3)
    var selYear by remember { mutableStateOf(today.year) }
    var selWeek by remember { mutableStateOf(today.week) }
    val isSame  = selYear==srcYear && selWeek==srcWeek
    val (dstMon, dstSun) = IsoWeekHelper.weekBounds(selYear, selWeek)
    val (srcMon, srcSun) = IsoWeekHelper.weekBounds(srcYear, srcWeek)

    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.padding(horizontal=20.dp).padding(bottom=32.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📋 Copier la semaine", fontWeight=FontWeight.Bold, fontSize=16.sp)
                    Text("S$srcWeek · ${IsoWeekHelper.fmt(srcMon)} – ${IsoWeekHelper.fmt(srcSun)}",
                        fontSize=12.sp, color=TextMuted)
                }
                IconButton(onClick=onDismiss) { Icon(Icons.Default.Close, null) }
            }
            Text("Les repas seront ajoutés à ceux déjà présents.", fontSize=13.sp, color=TextMuted)
            Card(colors=CardDefaults.cardColors(containerColor=PriOrangeLight), shape=RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={
                        if(selWeek>1) selWeek-- else { selYear--; selWeek=IsoWeekHelper.weeksInYear(selYear) }
                    }, colors=IconButtonDefaults.iconButtonColors(containerColor=PriOrange)) {
                        Icon(Icons.Default.ArrowBack, null, tint=Color.White)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("S$selWeek $selYear", fontWeight=FontWeight.Bold, color=PriOrange, fontSize=16.sp)
                        Text("${IsoWeekHelper.fmt(dstMon)} – ${IsoWeekHelper.fmt(dstSun)}",
                            fontSize=12.sp, color=PriOrange.copy(alpha=.7f))
                    }
                    IconButton(onClick={
                        if(selWeek<IsoWeekHelper.weeksInYear(selYear)) selWeek++ else { selYear++; selWeek=1 }
                    }, colors=IconButtonDefaults.iconButtonColors(containerColor=PriOrange)) {
                        Icon(Icons.Default.ArrowForward, null, tint=Color.White)
                    }
                }
            }
            if(isSame) Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF3CD)),
                border=BorderStroke(1.dp, Color(0xFFFFD166))) {
                Text("⚠️ Choisissez une semaine différente.",
                    modifier=Modifier.padding(12.dp), fontSize=13.sp, color=Color(0xFF856404))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick=onDismiss, modifier=Modifier.weight(1f), shape=RoundedCornerShape(12.dp)) { Text("Annuler") }
                Button(onClick={ onDuplicate(selYear,selWeek) }, enabled=!isSame,
                    modifier=Modifier.weight(2f), shape=RoundedCornerShape(12.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=PriOrange)) {
                    Text("Copier ici", fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}

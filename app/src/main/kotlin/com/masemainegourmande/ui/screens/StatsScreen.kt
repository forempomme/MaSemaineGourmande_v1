package com.masemainegourmande.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.StatsViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(vm: StatsViewModel) {
    val stats    by vm.stats.collectAsState()
    val curMonth  = LocalDate.now().monthValue - 1  // 0-indexed

    Box(Modifier.fillMaxSize()) {
        if (stats.totalMeals == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Aucune statistique", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Planifiez des repas pour voir vos données", color = TextMuted, fontSize = 14.sp)
                }
            }
            return@Box
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                top   = 8.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── KPI grid: 4 compact cards ─────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpiCard("🍽️", stats.totalMeals.toString(), "Repas", Modifier.weight(1f))
                    KpiCard("👥", stats.totalPersonsMeals.toString(), "Pers.", Modifier.weight(1f))
                    KpiCard("📖",
                        "${stats.uniqueRecipes}/${stats.neverCooked.size + stats.uniqueRecipes}",
                        "Recettes", Modifier.weight(1f))
                    KpiCard("📅", stats.activeWeeks.toString(), "Semaines", Modifier.weight(1f))
                }
            }

            // ── Streak + record ──────────────────────────
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = PriOrange),
                        modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔥", fontSize = 20.sp)
                            Text(stats.streak.toString(), fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp, color = Color.White)
                            Text("Sem. consécutives", fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                        }
                    }
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige),
                        modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🏆", fontSize = 20.sp)
                            Text(stats.recordWeekMeals.toString(), fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp, color = PriOrange)
                            Text("Repas record", fontSize = 9.sp,
                                color = TextMuted, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // ── Recette favorite ─────────────────────────
            if (stats.top5Recipes.isNotEmpty()) {
                item {
                    val (favRecipe, favCount) = stats.top5Recipes.first()
                    Card(shape=RoundedCornerShape(12.dp),
                        colors=CardDefaults.cardColors(containerColor=CardSurface),
                        border=BorderStroke(1.dp,BorderBeige),
                        modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("⭐", fontSize=22.sp)
                            Column(Modifier.weight(1f)) {
                                Text("Recette favorite", fontSize=11.sp, color=TextMuted,
                                    fontWeight=FontWeight.Bold)
                                Text("${favRecipe.emoji} ${favRecipe.name}", fontSize=14.sp,
                                    fontWeight=FontWeight.Bold, color=TextBrown)
                            }
                            Surface(color=PriOrangeLight, shape=RoundedCornerShape(8.dp)) {
                                Text("${favCount}×", fontSize=13.sp,
                                    fontWeight=FontWeight.ExtraBold, color=PriOrange,
                                    modifier=Modifier.padding(horizontal=8.dp, vertical=4.dp))
                            }
                        }
                    }
                }
            }

            // ── Monthly bar chart ────────────────────────
            item {
                Card(shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("📊 Repas par mois (${LocalDate.now().year})",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        MonthBarChart(data = stats.monthData, currentMonth = curMonth)
                    }
                }
            }

            // ── Top 5 recipes ────────────────────────────
            if (stats.top5Recipes.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🏅 Top 5 recettes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            val medals = listOf("🥇","🥈","🥉","🏅","🏅")
                            stats.top5Recipes.forEachIndexed { i, (recipe, count) ->
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(medals[i], fontSize = 18.sp)
                                    Text(recipe.emoji, fontSize = 18.sp)
                                    Text(recipe.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f))
                                    val maxCount = stats.top5Recipes.first().second
                                    Box(Modifier.width(50.dp).height(8.dp)) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            drawRoundRect(color = Color(0xFFF0E8E0),
                                                cornerRadius = CornerRadius(4.dp.toPx()))
                                            drawRoundRect(color = PriOrange,
                                                size = Size(size.width * count.toFloat() / maxCount, size.height),
                                                cornerRadius = CornerRadius(4.dp.toPx()))
                                        }
                                    }
                                    Surface(color = PriOrangeLight, shape = RoundedCornerShape(8.dp)) {
                                        Text("${count}×", fontSize = 11.sp, color = PriOrange,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Top 5 ingredients ────────────────────────
            if (stats.top5Ingredients.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🧂 Top 5 ingrédients", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            stats.top5Ingredients.forEachIndexed { i, (name, count) ->
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${i + 1}.", fontSize = 12.sp, color = TextMuted,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                                    Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Surface(color = PriOrangeLight, shape = RoundedCornerShape(8.dp)) {
                                        Text("${count}×", fontSize = 11.sp, color = PriOrange,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Never cooked ─────────────────────────────
            if (stats.neverCooked.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("💤 Jamais cuisinées (${stats.neverCooked.size})",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                stats.neverCooked.forEach { r ->
                                    Surface(color = PriOrangeLight, shape = RoundedCornerShape(20.dp)) {
                                        Text("${r.emoji} ${r.name}", fontSize = 12.sp, color = PriOrange,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── KPI card ─────────────────────────────────────────────────

@Composable
private fun KpiCard(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige),
        modifier = modifier) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 18.sp)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = PriOrange,
                textAlign = TextAlign.Center)
            Text(label, fontSize = 9.sp, color = TextMuted, textAlign = TextAlign.Center)
        }
    }
}

// ─── Bar chart ─────────────────────────────────────────────────

@Composable
private fun MonthBarChart(data: List<Pair<String, Int>>, currentMonth: Int) {
    val maxVal = data.maxOfOrNull { it.second }.takeIf { it != null && it > 0 } ?: 1
    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { i, (label, value) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (value > 0) {
                    Text(value.toString(), fontSize = 8.sp,
                        color = if (i == currentMonth) PriOrange else TextMuted)
                }
                val barHeight = (value.toFloat() / maxVal * 70).dp.coerceAtLeast(2.dp)
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(barHeight)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color        = if (i == currentMonth) PriOrange else PriOrangeLight,
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(label, fontSize = 7.sp,
                    color      = if (i == currentMonth) PriOrange else TextMuted,
                    fontWeight = if (i == currentMonth) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

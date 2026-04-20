package com.masemainegourmande.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.masemainegourmande.MsgApplication
import com.masemainegourmande.ui.screens.*
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.*
import androidx.compose.ui.platform.LocalContext

private data class Tab(val id: String, val label: String, val icon: String)
private val TABS = listOf(
    Tab("planning","Planning","📅"),
    Tab("recipes","Recettes","📖"),
    Tab("shopping","Courses","🛒"),
    Tab("stats","Stats","📊"),
    Tab("settings","Options","⚙️")
)
private val TAB_LABELS = mapOf(
    "planning" to "Planning des repas",
    "recipes"  to "Mes recettes",
    "shopping" to "Liste de courses",
    "stats"    to "Statistiques",
    "settings" to "Options"
)

@Composable
fun MsgNavHost() {
    val context   = LocalContext.current
    val app       = context.applicationContext as MsgApplication
    val repo      = app.repository
    val importer  = app.recipeImporter

    val planningVm = viewModel<PlanningViewModel>(factory = PlanningViewModel.factory(repo))
    val recipesVm  = viewModel<RecipesViewModel>(factory  = RecipesViewModel.factory(repo))
    val shoppingVm = viewModel<ShoppingViewModel>(factory = ShoppingViewModel.factory(repo))
    val statsVm    = viewModel<StatsViewModel>(factory    = StatsViewModel.factory(repo))
    val settingsVm = viewModel<SettingsViewModel>(factory = SettingsViewModel.factory(repo, context))
    val importVm   = viewModel<ImportViewModel>(factory   = ImportViewModel.factory(repo, importer))

    val defaultPersons by settingsVm.defaultPersons.collectAsState()
    val allRecipes     by recipesVm.recipes.collectAsState()
    val allMeals       by planningVm.allMeals.collectAsState()
    val importHistory  by importVm.importHistory.collectAsState()

    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val activeRoute   = backStack?.destination?.route ?: "planning"

    Column(Modifier.fillMaxSize().background(BgCream)) {
        AppHeader(activeRoute)

        Box(Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = "planning") {
                composable("planning") {
                    PlanningScreen(vm = planningVm, shoppingVm = shoppingVm,
                        defaultPersons = defaultPersons)
                }
                composable("recipes") {
                    RecipesScreen(vm = recipesVm, shoppingVm = shoppingVm,
                        defaultPersons = defaultPersons, importVm = importVm)
                }
                composable("shopping") { ShoppingScreen(vm = shoppingVm) }
                composable("stats")    { StatsScreen(vm = statsVm) }
                composable("settings") {
                    SettingsScreen(
                        vm          = settingsVm,
                        recipesVm   = recipesVm,
                        planningVm  = planningVm,
                        shoppingVm  = shoppingVm,
                        recipeCount = allRecipes.size,
                        mealCount   = allMeals.size,
                        importCount = importHistory.size
                    )
                }
            }
        }

        AppBottomNav(activeRoute) { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true; restoreState = true
            }
        }
    }
}

@Composable
private fun AppHeader(activeRoute: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(PriOrange, PriOrangeDark)))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🍽️", fontSize = 24.sp)
            Column(Modifier.weight(1f)) {
                Text("Ma Semaine Gourmande", color = Color.White,
                    fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(TAB_LABELS[activeRoute] ?: "", color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp)
            }
            Box(Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text("v1.6", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AppBottomNav(activeRoute: String, onSelect: (String) -> Unit) {
    // Same gradient as the top header
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(PriOrange, PriOrangeDark)))
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth()) {
            TABS.forEach { tab ->
                val active = activeRoute == tab.id
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(indication = null,
                            interactionSource = remember { MutableInteractionSource() }) { onSelect(tab.id) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(tab.icon, fontSize = 22.sp, lineHeight = 24.sp)
                    Text(
                        tab.label, fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal,
                        // Active = blanc pur, inactif = blanc semi-transparent
                        color = if (active) Color.White else Color.White.copy(alpha = 0.60f)
                    )
                    // Underline indicator for active tab
                    if (active) {
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .width(28.dp)
                                .height(2.dp)
                                .background(Color.White, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

package com.masemainegourmande.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.masemainegourmande.MsgApplication
import com.masemainegourmande.ui.screens.*
import com.masemainegourmande.ui.theme.PriOrange
import com.masemainegourmande.viewmodel.*
import androidx.compose.ui.platform.LocalContext

sealed class Screen(val route: String, val label: String, val icon: @Composable () -> Unit, val selectedIcon: @Composable () -> Unit) {
    object Planning  : Screen("planning",  "Planning",  { Icon(Icons.Outlined.CalendarMonth, null) }, { Icon(Icons.Filled.CalendarMonth, null) })
    object Recipes   : Screen("recipes",   "Recettes",  { Icon(Icons.Outlined.MenuBook, null) },      { Icon(Icons.Filled.MenuBook, null) })
    object Shopping  : Screen("shopping",  "Courses",   { Icon(Icons.Outlined.ShoppingCart, null) },  { Icon(Icons.Filled.ShoppingCart, null) })
    object Stats     : Screen("stats",     "Stats",     { Icon(Icons.Outlined.BarChart, null) },      { Icon(Icons.Filled.BarChart, null) })
    object Settings  : Screen("settings",  "Options",   { Icon(Icons.Outlined.Settings, null) },      { Icon(Icons.Filled.Settings, null) })
}

private val TABS = listOf(Screen.Planning, Screen.Recipes, Screen.Shopping, Screen.Stats, Screen.Settings)

@Composable
fun MsgNavHost() {
    val context    = LocalContext.current
    val app        = context.applicationContext as MsgApplication
    val repo       = app.repository
    val importer   = app.recipeImporter

    val planningVm  = viewModel<PlanningViewModel>(factory  = PlanningViewModel.factory(repo))
    val recipesVm   = viewModel<RecipesViewModel>(factory   = RecipesViewModel.factory(repo))
    val shoppingVm  = viewModel<ShoppingViewModel>(factory  = ShoppingViewModel.factory(repo))
    val statsVm     = viewModel<StatsViewModel>(factory     = StatsViewModel.factory(repo))
    val settingsVm  = viewModel<SettingsViewModel>(factory  = SettingsViewModel.factory(repo, context))
    val importVm    = viewModel<ImportViewModel>(factory    = ImportViewModel.factory(repo, importer))

    val defaultPersons by settingsVm.defaultPersons.collectAsState()
    val allRecipes     by recipesVm.recipes.collectAsState()
    val allMeals       by planningVm.allMeals.collectAsState()
    val importHistory  by importVm.importHistory.collectAsState()

    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentDest   = backStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { screen ->
                    val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected     = selected,
                        onClick      = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon         = { if (selected) screen.selectedIcon() else screen.icon() },
                        label        = { Text(screen.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
                        colors       = NavigationBarItemDefaults.colors(
                            selectedIconColor  = PriOrange,
                            selectedTextColor  = PriOrange,
                            indicatorColor     = PriOrange.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Planning.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Planning.route) {
                PlanningScreen(vm = planningVm, defaultPersons = defaultPersons)
            }
            composable(Screen.Recipes.route) {
                RecipesScreen(vm = recipesVm, shoppingVm = shoppingVm, defaultPersons = defaultPersons)
            }
            composable(Screen.Shopping.route) {
                ShoppingScreen(vm = shoppingVm)
            }
            composable(Screen.Stats.route) {
                StatsScreen(vm = statsVm)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    vm           = settingsVm,
                    recipeCount  = allRecipes.size,
                    mealCount    = allMeals.size,
                    importCount  = importHistory.size
                )
            }
        }
    }
}

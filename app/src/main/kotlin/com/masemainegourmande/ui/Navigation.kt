package com.masemainegourmande.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.masemainegourmande.ui.screens.ImportScreen
import com.masemainegourmande.ui.screens.ShoppingScreen
import com.masemainegourmande.ui.theme.PriOrange
import com.masemainegourmande.viewmodel.ImportViewModel
import com.masemainegourmande.viewmodel.ShoppingViewModel

// ═══════════════════════════════════════════════════════════════
// DESTINATIONS
// ═══════════════════════════════════════════════════════════════

sealed class Screen(
    val route:        String,
    val label:        String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Import   : Screen("import",   "Importer",  Icons.Filled.Download,      Icons.Outlined.Download)
    data object Shopping : Screen("shopping", "Courses",   Icons.Filled.ShoppingCart,  Icons.Outlined.ShoppingCart)
}

private val BOTTOM_SCREENS = listOf(Screen.Import, Screen.Shopping)

// ═══════════════════════════════════════════════════════════════
// ROOT NAV HOST
// ═══════════════════════════════════════════════════════════════

@Composable
fun MsgNavHost(
    importVm:   ImportViewModel,
    shoppingVm: ShoppingViewModel
) {
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentDest   = navBackStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                BOTTOM_SCREENS.forEach { screen ->
                    val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = {
                            Icon(
                                if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor     = PriOrange,
                            selectedTextColor     = PriOrange,
                            indicatorColor        = PriOrange.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Import.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Import.route) {
                ImportScreen(
                    importVm   = importVm,
                    shoppingVm = shoppingVm,
                    onRecipeSaved = {
                        // After saving, jump to shopping tab
                        navController.navigate(Screen.Shopping.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Shopping.route) {
                ShoppingScreen(vm = shoppingVm)
            }
        }
    }
}

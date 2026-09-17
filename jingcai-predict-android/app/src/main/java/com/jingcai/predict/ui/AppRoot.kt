package com.jingcai.predict.ui

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jingcai.predict.ui.screens.AnalysisScreen
import com.jingcai.predict.ui.screens.MatchesScreen
import com.jingcai.predict.ui.screens.MineScreen

@Composable
fun AppRoot(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val favIds = remember { mutableStateListOf("m1", "m3") }
    val context = LocalContext.current
    val toast: (String) -> Unit = { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = currentRoute == "matches",
                    onClick = {
                        navController.navigate("matches") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(Icons.Outlined.SportsSoccer, contentDescription = "赛事中心")
                    },
                    label = { Text("赛事中心", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    )
                )
                NavigationBarItem(
                    selected = currentRoute == "analysis",
                    onClick = {
                        navController.navigate("analysis") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Outlined.Analytics, contentDescription = "预测分析") },
                    label = { Text("预测分析", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    )
                )
                NavigationBarItem(
                    selected = currentRoute == "mine",
                    onClick = {
                        navController.navigate("mine") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Outlined.Person, contentDescription = "我的") },
                    label = { Text("我的", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    )
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "matches",
            modifier = Modifier.padding(padding)
        ) {
            composable("matches") {
                MatchesScreen(
                    favIds = favIds,
                    onToggleFav = { id ->
                        if (id in favIds) {
                            favIds.remove(id)
                            toast("已取消收藏")
                        } else {
                            favIds.add(id)
                            toast("已收藏")
                        }
                    },
                    onSearchClick = { toast("搜索功能开发中，敬请期待") }
                )
            }
            composable("analysis") { AnalysisScreen() }
            composable("mine") {
                MineScreen(
                    darkTheme = darkTheme,
                    onThemeChange = onThemeChange,
                    onShowToast = toast
                )
            }
        }
    }
}

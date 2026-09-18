package com.jingcai.predict.ui

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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.jingcai.predict.data.backtest.SettlementRunner
import com.jingcai.predict.data.llm.AiPredictionStore
import com.jingcai.predict.data.llm.PredictionBatchRunner
import com.jingcai.predict.data.remote.RemoteMatch
import com.jingcai.predict.data.slip.SlipHolder
import com.jingcai.predict.data.slip.SlipStore
import com.jingcai.predict.ui.components.AppSnackbarHost
import com.jingcai.predict.ui.components.MessageHost
import com.jingcai.predict.ui.components.UiMessage
import com.jingcai.predict.ui.screens.AnalysisScreen
import com.jingcai.predict.ui.screens.DetailHolder
import com.jingcai.predict.ui.screens.LeagueDetailScreen
import com.jingcai.predict.ui.screens.LlmConfigScreen
import com.jingcai.predict.ui.screens.MatchDetailScreen
import com.jingcai.predict.ui.screens.MatchesScreen
import com.jingcai.predict.ui.screens.MineScreen
import com.jingcai.predict.ui.screens.PlayerDetailScreen
import com.jingcai.predict.ui.screens.SearchScreen
import com.jingcai.predict.ui.screens.SlipCenterScreen
import com.jingcai.predict.ui.screens.TeamDetailScreen

@Composable
fun AppRoot(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val favIds = remember { mutableStateListOf("m1", "m3") }
    val context = LocalContext.current
    // 应用内提示宿主（替代系统 Toast）
    val snackbarHostState = remember { SnackbarHostState() }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // 详情页隐藏底部导航栏
    val isDetail = currentRoute == "teamDetail" ||
        currentRoute == "playerDetail" ||
        currentRoute == "matchDetail" ||
        currentRoute == "leagueDetail" ||
        currentRoute == "slipCenter" ||
        currentRoute == "llmConfig"

    // 启动时恢复上次未保存的方案单
    LaunchedEffect(Unit) {
        runCatching { SlipHolder.restore(SlipStore.loadCurrent(context)) }
    }

    // 启动时载入综合预测缓存，并开启后台批量预测（幂等：同一天不会重复预测）
    // 之后做一次结算回填：只用真实赛果判定已存快照的命中，不重新预测（内部已节流）
    LaunchedEffect(Unit) {
        AiPredictionStore.loadOnce(context)
        PredictionBatchRunner.start(context)
        SettlementRunner.settleAll(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!isDetail) {
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
        }
    ) { padding ->
        // 提示接收器：把 UiMessage 发出的提示交给上方 Snackbar 宿主展示
        MessageHost(snackbarHostState)

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
                            UiMessage.info("已取消收藏")
                        } else {
                            favIds.add(id)
                            UiMessage.success("已收藏")
                        }
                    },
                    onSearchClick = { navController.navigate("search") },
                    onOpenMatch = { match ->
                        DetailHolder.match = match
                        navController.navigate("matchDetail")
                    }
                )
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLeague = { league ->
                        DetailHolder.league = league
                        navController.navigate("leagueDetail")
                    },
                )
            }
            composable("leagueDetail") {
                LeagueDetailScreen(onBack = { navController.popBackStack() })
            }
            composable("teamDetail") {
                TeamDetailScreen(onBack = { navController.popBackStack() })
            }
            composable("playerDetail") {
                PlayerDetailScreen(onBack = { navController.popBackStack() })
            }
            composable("matchDetail") {
                MatchDetailScreen(onBack = { navController.popBackStack() })
            }
            composable("analysis") {
                AnalysisScreen(
                    onOpenMatch = { p ->
                        // 详情页的赔率与预测由它自己按 matchId 拉取/读缓存，这里只带齐展示所需的快照字段
                        DetailHolder.match = RemoteMatch(
                            matchId = p.matchId,
                            num = p.matchNum,
                            league = p.league,
                            time = p.kickoff,
                            home = p.home,
                            away = p.away,
                            had = null,
                            hhad = null,
                            goalLine = "",
                            status = when (p.statusText) {
                                "已完赛" -> "2"
                                "进行中" -> "1"
                                else -> "0"
                            }
                        )
                        navController.navigate("matchDetail")
                    }
                )
            }
            composable("slipCenter") {
                SlipCenterScreen(onBack = { navController.popBackStack() })
            }
            composable("llmConfig") {
                LlmConfigScreen(onBack = { navController.popBackStack() })
            }
            composable("mine") {
                MineScreen(
                    darkTheme = darkTheme,
                    onThemeChange = onThemeChange,
                    onShowToast = { UiMessage.info(it) },
                    onOpenSlips = { navController.navigate("slipCenter") },
                    onOpenLlmConfig = { navController.navigate("llmConfig") }
                )
            }
        }
    }
}

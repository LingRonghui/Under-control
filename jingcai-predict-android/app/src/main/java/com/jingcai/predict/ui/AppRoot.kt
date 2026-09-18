package com.jingcai.predict.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone

@Composable
fun AppRoot(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val favIds = remember { mutableStateListOf<String>() }
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
                // 底部导航：与页面背景同色，顶部 1dp 细线分层；选中态为主色图标 + 主色小字 + 极淡指示块
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Tone.hairline())
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                                )
                            )
                            .padding(horizontal = Space.sm, vertical = Space.sm)
                    ) {
                        BottomBarItem(
                            icon = Icons.Outlined.SportsSoccer,
                            label = "赛事中心",
                            selected = currentRoute == "matches",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate("matches") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        BottomBarItem(
                            icon = Icons.Outlined.Analytics,
                            label = "预测分析",
                            selected = currentRoute == "analysis",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate("analysis") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        BottomBarItem(
                            icon = Icons.Outlined.Person,
                            label = "我的",
                            selected = currentRoute == "mine",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate("mine") {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
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

/** 底部导航项：极淡指示块 + 主色图标与文字，未选中为中性色，切换仅做克制的颜色过渡 */
@Composable
private fun BottomBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "navTint"
    )
    val block by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else Color.Transparent,
        animationSpec = tween(180),
        label = "navBlock"
    )
    Column(
        modifier
            .clip(Corner.md)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = Space.xxs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .clip(Corner.sm)
                .background(block)
                .padding(horizontal = Space.md, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(Space.xxs))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1
        )
    }
}

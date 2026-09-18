package com.jingcai.predict

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jingcai.predict.ui.AppRoot
import com.jingcai.predict.ui.screens.SplashScreen
import com.jingcai.predict.ui.theme.JingCaiTheme
import kotlinx.coroutines.delay

/** 启动动画停留时长（毫秒），与 SplashScreen 的动画编排对应 */
private const val SPLASH_DURATION_MS = 1700L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var dark by rememberSaveable { mutableStateOf(true) }
            JingCaiTheme(darkTheme = dark) {
                // 启动动画：应用内容同时组合（后台预测任务照常启动），动画盖在其上淡出，
                // 这样"握手"期间既看不到首屏加载抖动，也不用推迟任何数据请求。
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_DURATION_MS)
                    showSplash = false
                }
                Box(Modifier.fillMaxSize()) {
                    AppRoot(
                        darkTheme = dark,
                        onThemeChange = { dark = it }
                    )
                    AnimatedVisibility(
                        visible = showSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(420)) +
                            scaleOut(targetScale = 1.04f, animationSpec = tween(420))
                    ) {
                        SplashScreen()
                    }
                }
            }
        }
    }
}

package com.jingcai.predict.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 应用内提示的语义类型：成功 / 失败 / 中性 */
enum class MsgKind { SUCCESS, ERROR, INFO }

/** 一条应用内提示：文案 + 语义类型（决定配色与图标） */
data class Msg(val text: String, val kind: MsgKind)

/**
 * 应用内提示的统一出口：任意位置（含非 Composable 的作用域）调用
 * [success] / [error] / [info]，由 AppRoot 中的 MessageHost 统一转成
 * 美化后的 Snackbar 展示，替代系统 Toast（样式与应用一致、可控）。
 */
object UiMessage {
    // 额外缓冲 8 条，避免发送时挂起
    private val _flow = MutableSharedFlow<Msg>(extraBufferCapacity = 8)
    val flow = _flow.asSharedFlow()

    /** 当前正在展示的提示类型（AppSnackbarHost 据此决定配色与图标） */
    var currentKind by mutableStateOf(MsgKind.INFO)

    fun success(t: String) {
        emit(Msg(t, MsgKind.SUCCESS))
    }

    fun error(t: String) {
        emit(Msg(t, MsgKind.ERROR))
    }

    fun info(t: String) {
        emit(Msg(t, MsgKind.INFO))
    }

    /** 非阻塞发送：无订阅者时直接丢弃，不影响调用方主流程 */
    private fun emit(m: Msg) {
        _flow.tryEmit(m)
    }
}

/**
 * 提示接收器：挂在 Scaffold 的宿主状态上，把 [UiMessage] 的提示依次交给 Snackbar 展示。
 * 每次展示前先同步类型，保证 [AppSnackbarHost] 取到的是本条提示的配色。
 */
@Composable
fun MessageHost(hostState: SnackbarHostState) {
    LaunchedEffect(Unit) {
        UiMessage.flow.collect { m ->
            UiMessage.currentKind = m.kind
            hostState.showSnackbar(
                message = m.text,
                withDismissAction = false,
                duration = SnackbarDuration.Short
            )
        }
    }
}

/** 应用内提示的视觉样式：圆角胶囊 + 语义配色 + 左侧图标 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState) {
    val kind = UiMessage.currentKind
    SnackbarHost(hostState) { data ->
        val container = when (kind) {
            MsgKind.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
            MsgKind.ERROR -> MaterialTheme.colorScheme.errorContainer
            MsgKind.INFO -> MaterialTheme.colorScheme.surfaceVariant
        }
        val content = when (kind) {
            MsgKind.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
            MsgKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
            MsgKind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val icon: ImageVector = when (kind) {
            MsgKind.SUCCESS -> Icons.Outlined.CheckCircle
            MsgKind.ERROR -> Icons.Outlined.ErrorOutline
            MsgKind.INFO -> Icons.Outlined.Info
        }
        Snackbar(
            // 水平 14dp / 底部 12dp 外边距，再叠一层轻微阴影（Snackbar 本身不暴露 elevation 参数）
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .shadow(3.dp, RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            containerColor = container,
            contentColor = content
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    data.visuals.message,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = content
                )
            }
        }
    }
}

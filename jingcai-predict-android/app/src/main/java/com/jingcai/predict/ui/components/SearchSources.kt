package com.jingcai.predict.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jingcai.predict.data.search.SearchHit
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular

/**
 * 「参考来源」区块：展示赛前情报实际引用到的**真实网络检索结果**。
 *
 * 【红线】这里只渲染传进来的真实数据（标题 / 站点名 / 链接），
 * 没有检索结果时如实提示「本次未获取到网络检索结果」，绝不填充任何占位来源。
 *
 * 用法：`SearchSourcesBlock(sources = cp.searchSources, onOpen = { openUrl(context, it) })`
 */
@Composable
fun SearchSourcesBlock(
    sources: List<SearchHit>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    emptyText: String = "本次未获取到网络检索结果",
) {
    SurfaceCard(modifier) {
        SectionTitle(
            title = "参考来源",
            subtitle = note ?: if (sources.isEmpty()) null else "网络检索真实抓取结果（${sources.size} 条）",
        )
        Spacer(Modifier.height(Space.md))
        if (sources.isEmpty()) {
            Text(
                emptyText,
                Modifier.alpha(0.9f),
                style = MaterialTheme.typography.bodySmall,
                color = Tone.textLabel(),
            )
        } else {
            sources.forEachIndexed { index, hit ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Corner.sm)
                        .clickable { onOpen(hit.url) }
                        .padding(vertical = Space.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        "${index + 1}",
                        Modifier
                            .width(18.dp)
                            .padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            hit.title.ifBlank { hit.url },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOf(hit.source, hit.publishDate)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(Space.sm))
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index != sources.lastIndex) {
                    Hairline(startPadding = 26.dp)
                }
            }
        }
    }
}

/**
 * 用系统浏览器打开来源链接（真实 URL）。
 * 失败（无应用可处理该链接 / 链接格式不受支持）时用 [UiMessage.error] 如实提示，不静默失败。
 */
fun openUrl(context: Context, url: String) {
    val target = url.trim()
    if (target.isEmpty()) {
        UiMessage.error("该来源没有可用链接")
        return
    }
    if (!target.startsWith("http://") && !target.startsWith("https://")) {
        UiMessage.error("暂不支持打开该链接（仅支持 http/https）")
        return
    }
    val error = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.exceptionOrNull()
    if (error != null) {
        UiMessage.error("无法打开链接：${error.message ?: "本机没有可处理该链接的应用"}")
    }
}

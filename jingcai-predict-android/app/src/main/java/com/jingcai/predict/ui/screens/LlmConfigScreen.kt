package com.jingcai.predict.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.llm.LlmClient
import com.jingcai.predict.data.llm.LlmConfig
import com.jingcai.predict.data.llm.LlmConfigStore
import com.jingcai.predict.data.llm.LlmPresets
import com.jingcai.predict.data.llm.LlmProvider
import com.jingcai.predict.data.search.WebSearch
import com.jingcai.predict.ui.components.SegmentedTabs
import com.jingcai.predict.ui.components.UiMessage
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 「测试检索」使用的固定检索词：只用于验证检索链路是否通（与任何具体比赛无关），
 * 不包含任何被虚构的数据。
 */
private const val SEARCH_TEST_QUERY = "足球 赛前 伤停 首发 情报"

/** 联网检索的三选一（取值 → 展示文案；“关闭”对应 LlmConfig.SEARCH_OFF） */
private val SEARCH_PROVIDER_OPTIONS = listOf(
    LlmConfig.SEARCH_OFF to "关闭",
    LlmConfig.SEARCH_ZHIPU to "智谱",
    LlmConfig.SEARCH_TAVILY to "Tavily",
)

/**
 * 模型配置页：选择服务预设、填写接口地址 / API Key / 模型名，
 * 支持从接口拉取真实模型列表、测试连通性，并把配置保存在本机（DataStore）。
 * 另含「联网检索」配置：关闭 / 智谱（复用上方 Key）/ Tavily。
 * 情报的联网检索由模型自主发起（智谱走官方内置检索工具、Tavily 走函数调用）；
 * 本页的「测试检索」只直接调一次检索接口，用于验证检索链路本身是否可用。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LlmConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 本地编辑状态（进入页面时从本机配置读入）
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var bankrollText by remember { mutableStateOf("100") }
    var showKey by remember { mutableStateOf(false) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }

    // 联网检索配置
    var searchProvider by remember { mutableStateOf(LlmConfig.SEARCH_OFF) }
    var tavilyKey by remember { mutableStateOf("") }
    var showTavilyKey by remember { mutableStateOf(false) }
    var testingSearch by remember { mutableStateOf(false) }

    // 拉取模型列表的状态
    var modelOptions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }

    // 测试连接的状态
    var testing by remember { mutableStateOf(false) }
    var testOk by remember { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }

    // 组装当前输入对应的配置（新增字段一并带上，避免漏字段）
    fun buildConfig() = LlmConfig(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        enabled = enabled,
        bankroll = bankrollText.trim().toDoubleOrNull() ?: 100.0,
        searchProvider = searchProvider,
        tavilyKey = tavilyKey.trim(),
    )

    LaunchedEffect(Unit) {
        val cfg = runCatching { LlmConfigStore.load(context) }.getOrDefault(LlmConfig())
        baseUrl = cfg.baseUrl
        apiKey = cfg.apiKey
        model = cfg.model
        enabled = cfg.enabled
        bankrollText = bankrollTextOf(cfg.bankroll)
        searchProvider = cfg.searchProvider
        tavilyKey = cfg.tavilyKey
        // 已保存的地址能对上某个预设时，高亮该预设
        selectedPresetId = LlmPresets
            .firstOrNull { it.baseUrl.isNotBlank() && it.baseUrl == cfg.baseUrl.trim().trimEnd('/') }
            ?.id
    }

    // 点击预设：填入该服务的接口地址与默认模型（默认模型为空时只填地址）
    fun applyPreset(p: LlmProvider) {
        selectedPresetId = p.id
        baseUrl = p.baseUrl
        if (p.defaultModel.isNotBlank()) model = p.defaultModel
        modelOptions = emptyList()
        modelsError = null
        testOk = null
        testError = null
    }

    // 拉取模型列表：GET {base}/models，如实展示返回结果或错误
    fun fetchModels() {
        val cfg = buildConfig()
        if (cfg.baseUrl.isBlank() || cfg.apiKey.isBlank()) {
            UiMessage.error("请先填写接口地址与 API Key")
            return
        }
        loadingModels = true
        modelsError = null
        testOk = null
        testError = null
        scope.launch {
            val result = LlmClient.listModels(cfg)
            loadingModels = false
            result.fold(
                onSuccess = { list ->
                    modelOptions = list
                    modelsError = if (list.isEmpty()) "接口返回的模型列表为空" else null
                },
                onFailure = { e ->
                    modelOptions = emptyList()
                    modelsError = e.message ?: "拉取失败（接口未返回错误信息）"
                }
            )
        }
    }

    // 测试连接：发一条最小对话请求，成功显示模型回复片段，失败如实显示错误
    fun testConnection() {
        val cfg = buildConfig()
        if (cfg.baseUrl.isBlank() || cfg.apiKey.isBlank()) {
            UiMessage.error("请先填写接口地址与 API Key")
            return
        }
        if (cfg.model.isBlank()) {
            UiMessage.error("请先填写模型名")
            return
        }
        testing = true
        testOk = null
        testError = null
        scope.launch {
            val result = LlmClient.testConnection(cfg)
            testing = false
            result.fold(
                onSuccess = { reply -> testOk = reply },
                onFailure = { e -> testError = e.message ?: "测试失败（接口未返回错误信息）" }
            )
        }
    }

    // 测试检索：用固定检索词真实调用一次检索接口；成功显示返回条数，失败如实回显原始原因
    fun testSearch() {
        val cfg = buildConfig()
        if (cfg.searchProvider.isBlank()) {
            UiMessage.error("请先选择检索服务商（智谱 或 Tavily）")
            return
        }
        if (cfg.searchProvider == LlmConfig.SEARCH_ZHIPU && cfg.apiKey.isBlank()) {
            UiMessage.error("智谱检索复用上方 API Key，请先填写 API Key")
            return
        }
        if (cfg.searchProvider == LlmConfig.SEARCH_TAVILY && cfg.tavilyKey.isBlank()) {
            UiMessage.error("请先填写 Tavily API Key")
            return
        }
        testingSearch = true
        scope.launch {
            val result = WebSearch.search(cfg, SEARCH_TEST_QUERY, WebSearch.DEFAULT_COUNT)
            testingSearch = false
            result.fold(
                onSuccess = { hits ->
                    if (hits.isEmpty()) {
                        UiMessage.error("本次未获取到网络检索结果（接口返回成功，但结果为空）")
                    } else {
                        UiMessage.success("检索成功：返回 ${hits.size} 条结果")
                    }
                },
                onFailure = { e ->
                    UiMessage.error("检索失败：${e.message ?: "接口未返回错误信息"}")
                }
            )
        }
    }

    // 保存当前配置
    fun saveConfig() {
        val cfg = buildConfig()
        scope.launch {
            runCatching { LlmConfigStore.save(context, cfg) }
                .onSuccess { UiMessage.success("已保存") }
                .onFailure { e ->
                    UiMessage.error("保存失败：${e.message ?: "未知错误"}")
                }
        }
    }

    // 清空输入并保存空配置
    fun clearConfig() {
        baseUrl = ""
        apiKey = ""
        model = ""
        enabled = true
        bankrollText = "100"
        selectedPresetId = null
        searchProvider = LlmConfig.SEARCH_OFF
        tavilyKey = ""
        showTavilyKey = false
        modelOptions = emptyList()
        modelsError = null
        testOk = null
        testError = null
        scope.launch {
            runCatching { LlmConfigStore.save(context, LlmConfig()) }
                .onSuccess { UiMessage.success("已清空") }
                .onFailure { e ->
                    UiMessage.error("清空失败：${e.message ?: "未知错误"}")
                }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：返回 + 标题
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                "模型配置",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 1) 服务预设
            SectionCard("服务预设") {
                LlmPresets.forEach { p ->
                    Chip(
                        text = p.name,
                        selected = selectedPresetId == p.id,
                        onClick = { applyPreset(p) }
                    )
                    Text(
                        p.note,
                        Modifier.padding(start = 2.dp, top = 4.dp, bottom = 8.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2) 接口地址
            SectionCard("接口地址") {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如 https://api.deepseek.com", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Text(
                    "兼容主流模型接口，实际请求 {地址}/chat/completions 与 {地址}/models",
                    Modifier.padding(top = 6.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3) API Key
            SectionCard("API Key") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴服务商控制台生成的 Key", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showKey) "隐藏" else "显示",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }

            // 4) 模型名 + 拉取模型列表
            SectionCard("模型名") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("如 deepseek-v4-pro", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { fetchModels() },
                        enabled = !loadingModels,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (loadingModels) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("拉取模型列表", fontSize = 12.sp)
                    }
                }

                if (modelOptions.isNotEmpty()) {
                    Text(
                        "接口返回的模型（点击填入）",
                        Modifier.padding(top = 10.dp, bottom = 6.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        modelOptions.forEach { id ->
                            Chip(text = id, selected = model == id) { model = id }
                        }
                    }
                }

                modelsError?.let { msg ->
                    Text(
                        "拉取失败：$msg",
                        Modifier.padding(top = 8.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 5) 测试连接
            SectionCard("测试连接") {
                Text(
                    "发送一条最小对话请求，验证地址 / Key / 模型是否可用。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { testConnection() },
                    enabled = !testing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (testing) "正在测试…" else "测试连接", fontSize = 13.sp)
                }
                testOk?.let { reply ->
                    Text(
                        "连接成功：$reply",
                        Modifier.padding(top = 10.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                testError?.let { msg ->
                    Text(
                        "测试失败：$msg",
                        Modifier.padding(top = 10.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 6) 启用模型分析
            SectionCard("模型分析") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用模型分析", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "关闭后不再向模型发起请求",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // 7) 联网检索
            SectionCard("联网检索") {
                Text(
                    "开启后，赛前情报的联网检索由模型自主完成：是否检索、检索什么、检索几次都由它自己决定，" +
                        "再据此整理情报。Tavily 由模型发起函数调用、应用真实执行检索；" +
                        "智谱由模型调用官方内置检索工具。命中的真实来源会随情报保存在本场结果里" +
                        "（详情页展示、可点击打开）。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "模型自主检索不可用时，会自动回退为应用侧按固定词检索一次；" +
                        "检索失败或无结果不影响其它结论，并会在详情页如实标注。",
                    Modifier.padding(top = 6.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SegmentedTabs(
                    items = SEARCH_PROVIDER_OPTIONS.map { it.second },
                    selectedIndex = SEARCH_PROVIDER_OPTIONS
                        .indexOfFirst { it.first == searchProvider }
                        .coerceAtLeast(0),
                    onSelect = { index ->
                        searchProvider = SEARCH_PROVIDER_OPTIONS.getOrNull(index)?.first
                            ?: LlmConfig.SEARCH_OFF
                    },
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    "智谱检索复用上方「API Key」（智谱开放平台同一个 Key，无需另填）；" +
                        "Tavily 需要使用单独的 Tavily API Key。",
                    Modifier.padding(top = 8.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (searchProvider == LlmConfig.SEARCH_TAVILY) {
                    OutlinedTextField(
                        value = tavilyKey,
                        onValueChange = { tavilyKey = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        placeholder = { Text("粘贴 Tavily API Key（tvly- 开头）", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(),
                        visualTransformation = if (showTavilyKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showTavilyKey = !showTavilyKey }) {
                                Icon(
                                    if (showTavilyKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showTavilyKey) "隐藏" else "显示",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                    Text(
                        "在 tavily.com 注册并登录后，可在个人控制台获取 API Key（有免费额度，形如 tvly-...）；" +
                            "Key 仅保存在本机（未加密）。",
                        Modifier.padding(top = 6.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { testSearch() },
                    enabled = !testingSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (testingSearch) {
                        CircularProgressIndicator(
                            Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (testingSearch) "正在检索…" else "测试检索", fontSize = 13.sp)
                }
                Text(
                    "测试使用固定检索词「$SEARCH_TEST_QUERY」，只验证直连检索链路（不经过模型）；" +
                        "结果会以底部提示告知成功条数，或原样显示失败原因（含 HTTP 状态码）。",
                    Modifier.padding(top = 8.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 8) 参考本金
            SectionCard("参考本金（元）") {
                OutlinedTextField(
                    value = bankrollText,
                    onValueChange = { bankrollText = filterDecimal(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("100", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    "用于「最具价值」的本金计算；留空按 100 元处理",
                    Modifier.padding(top = 6.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 诚实提示（如实说明数据与风险，不做美化）
            Text(
                "API Key 仅保存在本机（未加密），只会用于向你填写的接口地址发起请求；" +
                    "请勿在公共设备填写；模型返回的内容由第三方服务生成，" +
                    "本应用会用真实赔率校验其选项，不采信其给出的赔率。",
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 9) 底部按钮
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Button(
                    onClick = { saveConfig() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("保存", fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { clearConfig() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("清空配置", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 分组卡片：与「我的」页设置组保持一致的圆角 + 描边风格 */
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** 可点选小标签（选中态高亮） */
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (selected) primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** 输入框配色（与搜索页保持一致） */
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
)

/** 过滤非法字符：只保留数字与一个小数点 */
private fun filterDecimal(input: String): String {
    val sb = StringBuilder()
    var hasDot = false
    for (c in input) {
        when {
            c.isDigit() -> sb.append(c)
            c == '.' && !hasDot -> {
                hasDot = true
                sb.append(c)
            }
        }
    }
    return sb.toString()
}

/** 本金数值转输入框文本（去掉无意义的小数零） */
private fun bankrollTextOf(v: Double): String {
    if (v <= 0.0) return "100"
    val s = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    return s.ifBlank { "100" }
}

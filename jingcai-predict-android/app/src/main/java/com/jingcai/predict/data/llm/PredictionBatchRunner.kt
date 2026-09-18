package com.jingcai.predict.data.llm

import android.content.Context
import com.jingcai.predict.data.predict.ProfileRepository
import com.jingcai.predict.data.remote.JingCaiApi
import com.jingcai.predict.data.remote.RemoteMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.LocalDate

/** 后台批量预测进度（分析页顶部展示） */
data class BatchState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val finishedAt: Long = 0L,
    val error: String? = null,
    val failed: Int = 0,
    /** 失败/超时的比赛 matchId 列表（可只对这些场次重新预测） */
    val failedIds: List<String> = emptyList(),
)

/**
 * 后台批量预测 + 单场复核。
 *
 * 规则（与需求一致）：
 * 1. 只对**今明两日**的比赛做预测；已结束的比赛不再预测；
 * 2. 所有结果写入 [AiPredictionStore] 长期保留，**已有结果不再二次预测**（避免重复消耗）；
 * 3. App 打开即自动在后台开跑，用户进详情页可直接读结果，不用等待；
 * 4. 「模型复核」= 强制重新拉取最新前瞻与赔率后重算该场（reviewCount+1），只有这一条路径会覆盖旧结果。
 */
object PredictionBatchRunner {

    /**
     * ⚠️⚠️ 测试阀门（交付前保留，交付时改为 0 即解除限制）⚠️⚠️
     * 0 = 不限制（正式行为：今明两日全部比赛）；
     * N > 0 = 只预测「开赛时间最早的前 N 场」未开赛比赛，用于测试阶段节省调用成本。
     */
    private const val TEST_LIMIT = 3

    /**
     * 单场预测的硬超时（毫秒）：超过即判定该场失败并跳过，
     * 保证**任何一场卡住都不会拖住整批**，也不会让界面一直转圈。
     */
    private const val PER_MATCH_TIMEOUT_MS = 180_000L

    /** 最近一次失败/超时的比赛（matchId → 原因），供「只重跑出错场次」使用 */
    private val failedMatches = java.util.concurrent.ConcurrentHashMap<String, Pair<RemoteMatch, String>>()

    private val _state = MutableStateFlow(BatchState())
    val state: StateFlow<BatchState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedDay: String = ""
    private val runningLock = Any()
    @Volatile
    private var running = false

    /** App 打开时调用；同一天重复调用不会重复跑（幂等） */
    fun start(context: Context) {
        val today = LocalDate.now().toString()
        synchronized(runningLock) {
            if (running) return
            if (startedDay == today) {
                // 同一天已跑过：只补齐「还没有结果」的比赛（新增场次 / 上次失败的场次），已缓存的绝不重算
                scope.launch { fillMissing(context) }
                return
            }
            startedDay = today
        }
        scope.launch { runAll(context) }
    }

    private suspend fun runAll(context: Context) {
        if (!markRunning()) return
        try {
            AiPredictionStore.loadOnce(context)
            _state.value = BatchState(running = true, done = 0, total = 0, current = "正在获取今明两日赛程…")
            val matches = loadTargets(context) ?: run {
                _state.value = BatchState(running = false, error = "赛程获取失败，请稍后下拉刷新重试")
                return
            }
            if (matches.isEmpty()) {
                _state.value = BatchState(running = false, finishedAt = System.currentTimeMillis())
                return
            }
            predictAll(context, matches)
        } finally {
            running = false
        }
    }

    /**
     * 补齐：扫描当前赛程，只对**尚无结果**的比赛补算（已缓存的直接跳过，不重复调用）。
     * 用于「现场发现的漏预测」与「新上架场次」，不会造成二次预测。
     */
    private suspend fun fillMissing(context: Context) {
        if (!markRunning()) return
        try {
            AiPredictionStore.loadOnce(context)
            val matches = loadTargets(context) ?: return
            val missing = matches.filter { AiPredictionStore.get(it.matchId) == null }
            if (missing.isEmpty()) return
            _state.value = BatchState(running = true, done = 0, total = missing.size, current = "补齐未预测场次…")
            predictAll(context, missing)
        } finally {
            running = false
        }
    }

    private fun markRunning(): Boolean = synchronized(runningLock) {
        if (running) return false
        running = true
        true
    }

    /** 取预测目标：今明两日、未开赛；测试阀门口径为「开赛最早的前 N 场」 */
    private suspend fun loadTargets(context: Context): List<RemoteMatch>? {
        val days = runCatching { JingCaiApi.fetchMatchDays() }.getOrNull() ?: return null
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val list = days.flatMap { it.matches }
            .filter { !isFinished(it.status) && !isLive(it.status) }
            .filter { m ->
                val date = m.time.substringBefore(' ').takeIf { it.length >= 10 } ?: return@filter true
                val d = runCatching { LocalDate.parse(date) }.getOrNull()
                d == null || d == today || d == tomorrow
            }
            // 最近 = 开赛时间最早，保证阀门打开的永远是"最近的几场"
            .sortedBy { it.time }
        return if (TEST_LIMIT > 0) list.take(TEST_LIMIT) else list
    }

    private suspend fun predictAll(context: Context, matches: List<RemoteMatch>) {
        _state.value = BatchState(running = true, done = 0, total = matches.size, current = "开始预测…")
        val semaphore = Semaphore(2)   // 限流：最多 2 场并发，避免触发接口风控/模型限速
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val failed = java.util.concurrent.atomic.AtomicInteger(0)
        val lastError = java.util.concurrent.atomic.AtomicReference<String?>(null)
        coroutineScope {
            matches.forEach { m ->
                launch {
                    semaphore.withPermit {
                        _state.value = _state.value.copy(current = "${m.num} ${m.home} vs ${m.away}")
                        // 单场隔离：一场一个独立请求，异常与超时都不会影响其它场
                        var reason: String? = null
                        val ok = runCatching {
                            if (AiPredictionStore.get(m.matchId) == null) {
                                // 超时（null）判该场失败；否则按 Result 是否成功判定，失败原因原样保留
                                val r = withTimeoutOrNull(PER_MATCH_TIMEOUT_MS) { ensure(context, m, force = false) }
                                if (r == null) {
                                    reason = "单场处理超时（${PER_MATCH_TIMEOUT_MS / 1000}秒）"
                                    false
                                } else {
                                    r.exceptionOrNull()?.let { e ->
                                        reason = e.message ?: e.javaClass.simpleName
                                    }
                                    r.isSuccess
                                }
                            } else true
                        }.getOrElse { e ->
                            reason = e.message ?: e.javaClass.simpleName
                            false
                        }
                        if (!ok) {
                            val r = reason ?: "未知错误"
                            failed.incrementAndGet()
                            lastError.set("${m.num} ${m.home} vs ${m.away}：$r")
                            failedMatches[m.matchId] = m to r
                        } else {
                            failedMatches.remove(m.matchId)
                        }
                        _state.value = _state.value.copy(
                            done = done.incrementAndGet(),
                            failed = failed.get(),
                            error = lastError.get(),
                            failedIds = failedMatches.keys.toList(),
                        )
                    }
                }
            }
        }
        _state.value = _state.value.copy(
            running = false,
            finishedAt = System.currentTimeMillis(),
            current = "",
        )
    }

    /**
     * 只重跑**上一次失败/超时**的场次（单独的指令、单独重算），成功的场次一律不重算。
     * 供分析页「重新预测出错的 N 场」与用户手动重试使用。
     */
    fun retryFailed(context: Context) {
        val targets = failedMatches.values.map { it.first }.distinctBy { it.matchId }
        if (targets.isEmpty()) return
        scope.launch {
            if (!markRunning()) return@launch
            try {
                AiPredictionStore.loadOnce(context)
                failedMatches.clear()
                _state.value = BatchState(running = true, done = 0, total = targets.size, current = "重跑出错场次…")
                predictAll(context, targets)
            } finally {
                running = false
            }
        }
    }

    /**
     * 确保某场有综合预测结果。
     * @param force true = 模型复核（重新拉数据 + 重算 + 覆盖，reviewCount+1）
     */
    suspend fun ensure(
        context: Context,
        match: RemoteMatch,
        force: Boolean = false,
    ): Result<CombinedPrediction> {
        AiPredictionStore.loadOnce(context)
        val existing = AiPredictionStore.get(match.matchId)
        // 1) 已有结果且非复核 → 直接复用，避免二次预测
        if (!force && existing != null) return Result.success(existing)
        // 2) 已结束的比赛不再预测（即便复核请求也不重算，除非调用方明确 force 且有缓存）
        if (!force && (isFinished(match.status) || isLive(match.status))) {
            return Result.failure(IOException("本场已开赛/已结束，未在赛前保留预测"))
        }
        if (force && isFinished(match.status) && existing == null) {
            return Result.failure(IOException("本场已结束且赛前未保留预测，不再补算"))
        }
        val profile = ProfileRepository.getProfile(context, match.league)
        val cfg = LlmConfigStore.load(context).takeIf { it.ready }
        val built = PredictionPipeline.build(
            match = match,
            profile = profile,
            cfg = cfg,
            withPreview = true,
            reviewCount = (existing?.reviewCount ?: 0) + if (force && existing != null) 1 else 0,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        AiPredictionStore.upsert(context, built.prediction)
        return Result.success(built.prediction)
    }

    /** 复核失败时也要让界面能如实展示原因（不落盘、不编造） */
    suspend fun reviewWithDetail(context: Context, match: RemoteMatch): Result<PredictionPipeline.Built> {
        AiPredictionStore.loadOnce(context)
        val existing = AiPredictionStore.get(match.matchId)
        if (isFinished(match.status) && existing == null) {
            return Result.failure(IOException("本场已结束且赛前未保留预测，不再补算"))
        }
        val profile = ProfileRepository.getProfile(context, match.league)
        val cfg = LlmConfigStore.load(context).takeIf { it.ready }
        val built = PredictionPipeline.build(
            match = match,
            profile = profile,
            cfg = cfg,
            withPreview = true,
            reviewCount = (existing?.reviewCount ?: 0) + 1,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        AiPredictionStore.upsert(context, built.prediction)
        return Result.success(built)
    }

    private fun isFinished(status: String): Boolean = when (status.uppercase()) {
        "2", "CLOSED", "FINISHED" -> true
        else -> false
    }

    private fun isLive(status: String): Boolean = when (status.uppercase()) {
        "1", "LIVE", "OPEN" -> true
        else -> false
    }
}

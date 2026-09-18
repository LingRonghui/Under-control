# 项目记忆（工程口径 · 交接手册）

> 本文件是「竞彩足球预测 App」的工程记忆：红线、业务口径、架构、设计体系、构建与发布流程、已知遗留。
> 任何人（包括模型）接手本项目前，请先通读本文件。**涉及口径的改动必须先与产品负责人确认。**

- 应用名：**Under Control**
- 技术栈：Kotlin + Jetpack Compose（Material3），minSdk 26 / target 35
- 视觉主题：**深空玻璃 · 精密仪表**（Deep Space Glass）
- 发布仓库：`LingRonghui/Under-control`（远程名 `under`）
- 只读上游：`LingRonghui/football`（远程名 `origin`，**不推送**）

---

## 1. 红线（数据与表达，违反即失败）

1. 所有预测、赔率、赛果、统计**只能来自真实数据**：竞彩官方接口 `webapi.sporttery.cn`、官方前瞻接口。拿不到就如实说"拿不到"并降级展示，**严禁虚构、填充、示例化任何数字或结论**。
2. 参数与结论必须**可溯源、可复算**；冲突数据要标注采信口径。
3. 禁止编造来源媒体/链接；禁止把推测包装成"网络情报"。
4. **不做假"学习/训练"表述**。
5. 界面可见文案中所有 AI/架构类名词**统一称「模型」**；禁止出现 `AI`、`大模型`、`LLM`、`架构`、`引擎` 等字眼；**预测分析界面不得出现任何具体模型名称**（配置页除外）。
6. 用户可见提示统一走应用内 Snackbar（`ui/components/UiMessage.kt`），**禁止 Toast**。

---

## 2. 业务口径（改动前必须确认）

| 项 | 口径 |
|---|---|
| 综合置信度 | 模型概率（架构概率）× 分歧系数（模型与主选一致 1.00 / 分歧 0.85），上限 95 |
| 最具价值 | 概率 ≥ 45% 且 期望 > 0 中取「概率 × 期望」最高者；退档要标注；无正期望要明说不建议 |
| 最稳健 | 不看赔率，取概率最高者 |
| 预测范围 | 只对**今明两日、未开赛**比赛预测；已完赛不再预测；已有结果不二次预测；**「模型复核」是唯一强制重算单场的入口** |
| 单场隔离 | 单场 180s 硬超时；单次模型调用 90s；情报生成（含自主检索）整体 ≤ 60s |
| 命中配色 | 命中 = 红 `#D93A2B`；未中 = 绿 `#1B8A4B`；待结算 = 中性 `Tone.pending()` |
| 盈亏配色 | 盈利 = 红、亏损 = 绿（中文竞彩习惯） |
| 进行中 | 用**品牌主色**，不得用命中红（红只代表命中/盈利） |
| 方案结算 | `SlipAutoSettle` 为全应用唯一实现：`prize>0 → WON`；有未判定项 → `PENDING`（不下结论）；其余 `LOST` |
| 盈亏统计 | `SlipStats`：已结算独立统计（总投入/总回报/净盈亏/ROI/方案命中率/最大单笔盈亏/连红连黑）；**未结算只列占用本金与理论最高回报，不并入 ROI** |

---

## 3. 数据来源与外部接口

- **赛程 / 赔率 / 赛果**：竞彩官方接口（`data/remote/JingCaiApi.kt`）
- **赛前前瞻（10 路）**：`data/remote/MatchPreviewApi.kt`（特征、交锋、积分榜、近况、未来赛程、射手、伤停、实时比分、赔率、赛果）
- **联网检索**（`data/search/WebSearch.kt`）
  - 智谱：官方内置 `web_search` 工具，`enable=true`，**检索词由模型自主决定**（不写死 query）
  - Tavily：标准**函数调用循环**（最多 3 轮），检索结果回填后继续生成
  - 状态如实落盘：`searchMode` = `model`（模型自主）/ `app`（应用侧回退）；`searchState` = `""|ok|empty|no_call|failed:<原因>`；`searchRounds` = 实际检索轮次
  - **函数调用与智谱内置检索互斥**（官方优先级：函数调用 > 知识库 > 网络搜索），两条链路不得同时声明
- **大模型**：OpenAI 兼容协议；预设 DeepSeek / 智谱 GLM / Kimi / GPT / 自定义（`data/llm/LlmConfig.kt`）

---

## 4. 代码结构速览

```
data/predict/   Poisson(DC τ + 比分矩阵)、OddsMath(比例去水)、LeagueProfile(23 联赛参数模板)、
                PredictionEngine、ProfileRepository(联赛参数覆盖)、PredictionResult/OptionProbability
data/llm/       LlmClient(含工具循环 chatWithTools)、LlmAnalyzer、PreviewAnalyzer(赛前情报)、
                CombinedPrediction(综合结论契约 + CombineRule/ValueRule + LOGIC_VERSION)、
                PredictionPipeline(统一流水线)、AiPredictionStore(快照持久化)、PredictionBatchRunner(后台批量)
data/backtest/  BacktestStats(统计口径)、SettlementRunner(结算回填：只用真实赛果本地判定，不调模型)
data/slip/      SlipModels、SlipStore、SlipAutoSettle(唯一结算实现)、SlipStats(盈亏口径)、
                ParlayMath(官方过关计算)、SlipSettlement(命中判定)
data/search/    WebSearch(SearchHit / SearchProvider / 智谱 / Tavily)
data/remote/    JingCaiApi、MatchPreviewApi、SearchIndex、HttpClient
ui/theme/       Tokens(Space/Corner/Tone：玻璃质感 + 四级文本对比度)、Theme(色板/字体/形状)
ui/components/  CommonUi(SurfaceCard/SectionTitle/StatTile/HeroNumber/PillTag/Hairline/KeyValueRow/
                SegmentedTabs/EmptyState/ThinProgress/IconBadge/BrandMark/appBackground)、
                SearchSources(参考来源)、UiMessage(应用内提示)
ui/screens/     MatchesScreen、SearchScreen、LeagueDetailScreen、DetailScreens(三 Tab + 球队/球员)、
                AnalysisScreen、BacktestTab、SlipCenterScreen、LlmConfigScreen、MineScreen、SplashScreen
```

---

## 5. 设计体系（深空玻璃 · 精密仪表）

- **玻璃卡片**：半透明渐变底 + 渐变描边（上亮下隐）+ 顶部高光细线；`accent = true` 追加品牌色柔光。统一用 `SurfaceCard`，**不要在页面里自绘卡片底色/描边**。
- **四级文本对比度**（本轮重点）：`Tone.textStrong()`（数据/结论，近白）> `Tone.textBody()`（正文）> `Tone.textLabel()`（标签）> `Tone.textHint()`（提示）。**禁止用 `onSurfaceVariant` 承载数值或正文**。
- **数字**：一律 `.tabular()`；主数据用 `HeroNumber`，列表主数据 ≥ 17sp。
- **氛围底**：`Modifier.appBackground()`（深空底 + 品牌绿/青双色径向柔光），由 `AppRoot` 统一提供，页面不要再铺满屏底色。
- **动效**：180~600ms，克制；仪表盘/进度用渐变色 + 入场动画。
- **图标**：自适应图标「掌心拢球」（四指自掌根伸展、指尖内扣环抱足球）+ 单色层 + 深空渐变底；应用内 `BrandMark` 与图标同源（`ic_brand_mark.xml`）。
- **启动动画**：`SplashScreen` 掌心拢球 spring 回弹 + 能量环旋转 + 应用名/标语错落淡入，1.7s 后淡出放大交接（应用内容同时组合，后台任务照常启动）。

---

## 6. 构建 / 安装 / 验证（血泪经验）

- **JDK**：`C:\dev\jdk17`，必须显式设置 `$env:JAVA_HOME`（本机 PATH 里没有 java）
- **Android SDK**：`C:\dev\android-sdk`（`local.properties` 的 `sdk.dir`）
- **构建**：在 `jingcai-predict-android/` 下执行
  `gradlew.bat :app:assembleRelease --console=plain`
  产物：`app/build/outputs/apk/release/app-release.apk` → 拷贝到 `dist/jingcai-predict-v0.1.0-release.apk`
- **⚠️ 日志陷阱**：PowerShell 的 `*>` / `>` 重定向会按控制台宽度**截断** gradle 输出，导致编译错误看不到。
  必须用 `Start-Process -RedirectStandardOutput/-RedirectStandardError`（或 cmd 重定向）。
- **界面验证**：`adb shell uiautomator dump /sdcard/x.xml` + `adb pull` + 正则取 `text="…"`；**不要用截图**（产品负责人明确要求）。
  - dump 固有延迟约 1.1s；启动动画这类瞬时界面需临时延长时长后再抓。
- **模拟器**：`emulator-5554`
- **签名**：`jingcai-predict-android/keystore.properties`（已 gitignore）+ `C:\Users\Administrator\keystore\jingcai-predict\jingcai-release.jks`（alias `jingcai`）。**绝不提交密钥。**

---

## 7. 发布流程与网络限制（重要）

- 目标远程 **`under`**（`https://github.com/LingRonghui/Under-control.git`）；`origin` **不推送**。
- **本机 `github.com:443` 被阻断**：TCP 可连、但传输每秒约 1KB 后卡死；`git push` 会 21s 超时。
  而 `api.github.com` 正常（约 0.7s）。本机无代理端口、无 SSH 密钥。
- **可用方案：GitHub Git Data API**（`POST /git/blobs` → `/git/trees` → `/git/commits` → `PATCH /git/refs/heads/main`），凭据取环境变量 `GH_TOKEN`。要点：
  1. 上传**提交内的字节**：先 `git ls-tree -r HEAD` 取 blob sha，再用 `git cat-file blob <sha>`（经 cmd 重定向落临时文件）读字节；**不要直接读工作区文件**（换行符差异会让 tree 校验失败）。
  2. 新提交的 `parent` 用**远端当前 tip** → 快进更新，不需要强推。
  3. **提交前必须校验** API 返回的 tree sha 等于 `git rev-parse HEAD^{tree}`，不一致就中止。
  4. 远端 tip 可能是 API 建的（本地没有该对象）：用「tree sha 相同的本地提交」作为 diff 基线。
- 结果：本地与远端会是**两个 SHA、同一 tree**（内容逐字节相同）。网络可用时执行
  `git fetch under && git reset --hard under/main` 对齐本地。
- **仓库内已附可直接使用的推送脚本**：`tools/push_via_api.ps1`（路径自适应、不含任何密钥；用法：设好 `$env:GH_TOKEN` 后在仓库根运行）。
- 历史遗留：远端曾出现一条**无内容变化**的提交（`0b057a25` → `3472e9a7`），内容无影响，如需清理需强制指针回退。

---

## 8. 已知遗留 / 未决

1. `CombinedPrediction.LOGIC_VERSION` 仍为 `3`：**历史快照**没有 `searchMode/searchRounds/来源`，需「模型复核」或等新场次才会出现「参考来源」区块；若要全量失效重算则 +1。
2. `teamDetail` / `playerDetail` 两个路由**全工程无跳转**（页面不可达），`DetailHolder.team/player` 从未被赋值。
3. `data/remote/TeamDbApi.kt` 全部方法**无调用方**。
4. `SlipStore` / `AiPredictionStore` 解析失败会**静默回退为空**，且后续写入可能覆盖损坏数据（建议：区分"无数据"与"解析失败"并提示、写前备份）。
5. `LlmAnalyzer` 的 prompt 文本内仍含「引擎」等词（**不上屏**，未改以免影响模型行为）。
6. 智谱检索的响应来源字段取自顶层 `web_search[]`，属"官方文档未逐字段核实"；且**无法区分"未检索"与"检索到 0 条"**——此时记 `no_call`，不臆断为 `empty`。
7. 紧凑表格页（积分榜/射手/伤停，5~7 列）为防折行未强制 ≥17sp（用 14~16sp + 加粗 + 高对比）。
8. 赔率数字在不同页面格式尚未完全统一（`3.4` / `3.40`）。

---

## 9. 改动自检清单

- [ ] `:app:compileReleaseKotlin` 零错误
- [ ] 装机跑通：赛事中心（四分类）/ 预测分析（仪表盘 + 排行 + 回测）/ 我的（盈亏仪表盘），详情页三 Tab
- [ ] 文案合规：grep 界面字符串无 `AI` / `大模型` / `LLM` / `架构` / `引擎` / 具体模型名
- [ ] 口径复核：命中红 / 未中绿 / 待结算中性 / 进行中主色；未被点名的数值不再用 `onSurfaceVariant`
- [ ] 渠道复核：只新增真实数据来源，未编造任何数字或来源

---

## 10. 换机交接清单（新设备如何无缝接上）

### 10.1 从 GitHub 取（仓库已含源码 / APK / 本文档 / 推送脚本）

```
git clone https://github.com/LingRonghui/Under-control.git
cd Under-control
```

- 源码：`jingcai-predict-android/`
- 签名产物：`dist/jingcai-predict-v0.1.0-release.apk`
- 推送脚本：`tools/push_via_api.ps1`（`github.com` 不通时用）
- 若 `github.com` 拉不动，可改走 `api.github.com`（或用代理）

### 10.2 必须**手工拷贝**、且**绝不能上传 GitHub** 的东西

1. **签名密钥**：`C:\Users\Administrator\keystore\jingcai-predict\`（`jingcai-release.jks` + `keystore-info.txt`）
2. 在 `jingcai-predict-android/` 下**重建 `keystore.properties`**（键名固定，值取上面的 info 文件）：

```
storeFile=<新设备上 jks 的绝对路径>
storePassword=<见 keystore-info.txt>
keyAlias=jingcai
keyPassword=<见 keystore-info.txt>
```

   ⚠️ 没有它 `assembleRelease` 会产出**未签名**包，无法覆盖安装升级（`build.gradle.kts` 已做"文件缺失则不签名"的降级）。
3. **本地环境文件** `jingcai-predict-android/local.properties`（**未入库**，需自行创建）：

```
sdk.dir=<新设备的 Android SDK 路径>
```

4. **工具链**：JDK 17、Android SDK（含 platform-tools 的 adb）。本项目本机使用 `C:\dev\jdk17` 与 `C:\dev\android-sdk`，且 **PATH 里没有 java，必须显式设 `JAVA_HOME`**。

### 10.3 应用内数据不随仓库迁移（在新设备上重新配置）

- 模型配置（服务地址 / API Key / 模型名 / 联网检索服务商与 Key）：在「我的 → 模型配置」重新填写并「测试连接」
- 已保存方案与预测快照：存在设备本地 DataStore，不随仓库走

### 10.4 新设备恢复后的自检命令

```powershell
$env:JAVA_HOME="C:\dev\jdk17"
cd jingcai-predict-android
.\gradlew.bat :app:compileReleaseKotlin --console=plain     # 编译
.\gradlew.bat :app:assembleRelease --console=plain          # 打包（需 keystore.properties）

$adb="C:\dev\android-sdk\platform-tools\adb.exe"
& $adb -s emulator-5554 install -r app\build\outputs\apk\release\app-release.apk
& $adb -s emulator-5554 shell am start -n com.jingcai.predict/.MainActivity
& $adb -s emulator-5554 shell uiautomator dump /sdcard/v.xml
& $adb -s emulator-5554 pull /sdcard/v.xml .
```

校验签名包一致：比对 `dist/` 内 APK 的 SHA-256（当前 `D4B13CD35C663D813875D8547E3E785E838F3185EF518FB7578BC1E67BB95890`）。

### 10.5 关于本地与远端的 SHA 差异

新设备 `git clone` 拿到的**就是远端提交**（tree 与我这台机器逐字节一致，已校验）；我这台上那个 `362b3ec` 只是同内容的本地版本，**无需迁移**。

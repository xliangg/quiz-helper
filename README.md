# quiz_helper 答题助手（Android）

悬浮球答题工具：点击悬浮球 → 自动截取整屏 → 把截图发给**多模态视觉大模型** → 弹卡片显示**答案 + 简要解析**。
**只提示、不代填、不代提交**，答案由你自己抄写。

## 下载安装（普通用户）

无需编译，直接下载**正式签名版**：

- 最新版：<https://github.com/xliangg/quiz-helper/releases/latest>
- 直链：`quiz-helper-v0.1.0.apk`（约 12 MB）

安装步骤：允许「安装未知来源应用」→ 打开 App 授予「显示在其他应用上层」→ 设置页填 API Key → 点「启动悬浮球」。

## 功能

- 常驻可拖动悬浮球，点一下即截题
- 使用 **MediaProjection** 标准截屏 API，**不需要无障碍服务**（只需悬浮窗权限 + 首次「允许截屏」）
- 默认模型：火山方舟 **doubao-seed-2-1-turbo-260628**（OpenAI 兼容 `/chat/completions`）
- 设置页可切换：通义千问 VL、DeepSeek-VL、自定义 OpenAI 兼容端点
- 答案卡片：显示答案与解析，支持一键复制、关闭；**默认出现在屏幕下半部**（避免挡住上方长题干），按住标题或答案区域可**拖动**，位置会被记住
- 顶部**状态胶囊**实时反馈进度：点击后约半秒出现 `已截图 245KB（576×1280），正在发送…`，随后变为 `已发送请求，等待模型返回（3s）`；点击悬浮球还有轻微振动确认（截屏瞬间不能显示悬浮窗，否则会被截进画面）
- API Key 存于本机 `EncryptedSharedPreferences`（Keystore 异常时回退私有存储），不落日志、不进 git
- 内置「连通测试」，用一张示例题图验证 Key/端点/模型是否可用

## 环境要求

- Android 11+（minSdk 30），targetSdk 35
- **不含 native 库**，所有 CPU 架构通用
- JDK **17 或 21**（重要，见下）
- Android SDK Platform 35 + Build-Tools 35
- Gradle 由 wrapper 自动下载（已指向腾讯云镜像）

## 构建

### 方式 A：Android Studio

1. 用 Android Studio 打开本目录
2. **把 Gradle JDK 设为 17 或 21**：
   `Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK`
3. 同步后运行 `app` 或执行 `Build → Build Bundle(s)/APK(s) → Build APK(s)`

> ⚠️ **不要用 Android Studio 自带的 JDK 25 构建**。Kotlin 1.9.24 无法解析 JDK 25 的版本号，会报
> `java.lang.IllegalArgumentException: 25.0.3`。切到 JDK 17/21 即可。

### 方式 B：命令行

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"   # 换成你的 JDK 17/21 路径
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 打正式签名包（release）

签名信息从**项目根目录**的 `keystore.properties` 读取（该文件与 `*.jks`、`*.keystore` 均已被 `.gitignore` 忽略，不会进仓库）：

```properties
storeFile=C:/Users/you/keystores/quiz-helper/quiz-helper-release.jks
storePassword=你的密码
keyAlias=quiz-helper
keyPassword=你的密码
```

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

若 `keystore.properties` 不存在，release 会**自动降级为 debug 签名**，保证他人 clone 后仍能直接构建，不会报错。

生成自己的签名文件：

```bash
keytool -genkeypair -v -keystore quiz-helper-release.jks -alias quiz-helper \
  -keyalg RSA -keysize 4096 -validity 10000
```

> 签名文件与密码是应用的「身份」，丢失后**无法为已安装版本发布升级包**（只能卸载重装），请离线备份到两处以上。

## 安装与使用

1. 安装 `app-debug.apk`
2. 打开 App → 授予「显示在其他应用上层」权限
3. 点「启动悬浮球」
4. 进入「设置」：
   - 填入火山方舟 API Key（默认模型已填好）
   - 点「测试连接」确认可用 → 「保存配置」
5. 打开任意答题 App，点悬浮球：
   - 首次会弹「允许截屏」，授权后自动截题并显示答案卡片
   - 之后再点球即可静默截题（进程被杀后需重新授权一次）

## 设置页说明

| 区域 | 内容 |
|---|---|
| 服务开关 | 启停悬浮球、查看悬浮窗/截屏权限状态 |
| 模型配置 | 选择预设或自定义 Base URL / Model，填写 API Key |
| 答题行为 | 可改 System Prompt、截图最长边（默认 1280）、JPEG 质量（默认 80） |
| 连通测试 | 发送内置示例题图，返回答案或错误分类（401/403、404、超时、解析失败） |
| 合规声明 | 仅辅助学习、不代答、不作弊；数据仅本机与所选模型间传输 |
| 清除配置 | 清空 Key 与全部设置 |

## 常见问题

### 点「测试连接」成功，但点悬浮球却报「网络错误 / timeout」

这是**读取超时**，不是连不上。原因：连通测试发的是内置小图（几十 KB，秒回），而点球发的是整屏截图（缩放后仍有 100–300KB），视觉模型读整屏文字 + 生成 JSON 常需 10–60 秒。早期版本读取超时只有 30 秒，于是被误判成「网络错误」。

当前版本已调整为：连接 20s / 写入 90s / 读取 120s / 整次调用 150s，并在等待时显示「正在识别（Ns）」计时卡片。若仍然超时或太慢：

1. 设置页把**截图最长边**从 1280 降到 1024（或 900），**JPEG 质量**从 80 降到 70——请求体直接小一半以上；
2. 确认手机网络通畅（上传 base64 也吃带宽）；
3. 换更快的视觉模型（如通义 `qwen-vl-plus`）。

排查日志（`adb logcat`）：
- `QuizCapture`：打印截图尺寸、JPEG 体积、base64 体积；
- `QuizClient`：打印请求体大小、模型名、响应耗时。

### 其他错误对照

| 提示 | 含义 |
|---|---|
| `API Key 错误或无权限（HTTP 401/403）` | Key 填错、过期或未开通该模型 |
| `模型名或接口地址错误（HTTP 404）` | model 名或 Base URL 不对（Base URL 到 `/api/v3` 为止，不要带 `/chat/completions`） |
| `请求过于频繁或被限流（HTTP 429）` | 额度/频率限制，稍后重试 |
| `图片过大被服务器拒绝（HTTP 413）` | 调低截图边长或质量 |
| `模型 120 秒内未返回` | 见上一节 |
| `模型未返回有效答案` | 模型没按 JSON 格式回，检查提示词或换模型 |

## 速度优化（为什么之前要 100 秒）

豆包 `doubao-seed-2-1` 系列**默认开启深度思考**：模型会先输出一大段思维链再给答案，看图答题这种简单任务会被拖到几十秒甚至 100 秒以上。当前版本已做四项优化：

| 优化 | 说明 |
|---|---|
| `thinking: disabled` | 关闭深度思考，延迟通常下降一个量级（这是主因） |
| 流式输出 `stream: true` | 边生成边接收，状态胶囊实时显示「已接收 N 字」，不会再像卡死 |
| `max_tokens: 500` + 精简提示词 | 解析限制 80 字，输出越短越快 |
| 提示词自动升级 | 老版本存下的冗长默认提示词会被自动替换为精简版 |

设置页「答题行为」里新增 **深度思考** 开关：遇到需要推理的数学/逻辑题可以打开（更准但慢很多），日常选择保持关闭。

若仍偏慢，按顺序尝试：
1. 截图最长边降到 **1024**、质量降到 **70**（减少视觉 token 与上传量）；
2. 换更快的视觉模型（如通义 `qwen-vl-plus`）；
3. 确认网络上行速度（base64 图片要上传）。

「连通测试」现在会显示耗时，可直接对比开关深度思考的差别。

## 已知限制

- **截屏受保护页面**：银行类或设置了 `FLAG_SECURE` 的页面无法截屏，会提示「当前页面不支持截屏」，无法绕过。
- **截屏需重新授权**：Android 10+ 进程被杀后 MediaProjection token 失效，需再点一次「允许截屏」。
- **base64 内联**：默认把截图以 `data:image/jpeg;base64,...` 内联发送。火山方舟/通义兼容模式一般支持；若某模型不支持，请改用支持 `image_url` 的视觉模型。
- **DeepSeek-VL 预设**：官方 `deepseek-chat` 视觉能力有限，仅作候选，不保证识图效果。
- **截图仅整屏**：当前为全屏截图，由视觉模型自行定位题目；后续可扩展「框选答题区域」。

## 合规

本工具仅用于辅助学习与自测，**不自动代答、不参与任何违规考试**。请遵守所在平台与考试规则，风险自负。

# 轻跳 LiteSkip

受「李跳跳」启发的开源开屏广告跳过工具。基于 Android 无障碍服务，自动点击屏幕上的「跳过 / 同意 / 关闭」按钮。

> **免责声明**：本项目仅供学习交流，请遵守所在地区法律法规，勿用于商业用途。使用本软件产生的任何问题由使用者自行承担。

## 特性

- **跳过开屏广告**：全局关键词规则，自动点「跳过」按钮（只认窗口切换后 5 秒内，不会误点设置页里的"跳过此步"）
- **二次确认自动处理**：点掉开屏广告后 3.5 秒内自动补点「坚持跳过 / 放弃奖励」类确认框
- **自动同意隐私弹窗**：按 App 配置，首次启动不再手点「同意」
- **自动关闭应用内弹窗**：按 App 配置「关闭 / 稍后」类按钮
- **屏幕取词**：从任意目标界面直接抓取可点击控件的文字，勾选即生成规则，不用瞎猜按钮文案
- **自定义规则**：支持关键词、控件 ID（viewId）、Activity 限定、包含/完全相等四种粒度
- **点击日志与统计**：最近 50 次自动点击可查（时间/App/按钮文字），累计与今日次数、估算省时一目了然
- **规则导入导出**：JSON 文件一键备份、分享
- **深色主题**：Android 12+ 动态取色，低版本品牌绿配色；中英双语
- **不联网**：Manifest 里没有申请 INTERNET 权限，可在「设置 → 应用 → 轻跳 → 权限管理」验证；所有数据只存本机

## 安装使用

1. 从 [Releases](../../releases) 下载 APK 安装（或自行编译）
2. 打开轻跳，按引导页三步走：开启无障碍服务 → 设置后台保活 → 遇到没覆盖的弹窗用「屏幕取词」加规则
3. 打开总开关，完成——下次打开带开屏广告的 App 会自动跳过并弹 Toast 提示

小米 / Redmi（澎湃OS）保活设置见 [docs/keepalive-xiaomi.md](docs/keepalive-xiaomi.md)。

## 自行编译

要求：JDK 17、Android SDK（API 35）。

```bash
./gradlew :app:testDebugUnitTest          # 单元测试
./gradlew :app:assembleDebug              # Debug APK
./gradlew :app:assembleRelease            # Release APK (未配置签名时为未签名包)
```

Gradle 仓库已配置阿里云镜像、Gradle 发行版走腾讯镜像，国内网络可直接构建。

### Release 签名（可选）

签名信息从根目录 `keystore.properties` 读取（已 gitignore，不要提交）：

```properties
storeFile=keystore/litejump-release.jks
storePassword=你的密码
keyAlias=litejump
keyPassword=你的密码
```

生成 keystore：

```bash
keytool -genkeypair -v -keystore keystore/litejump-release.jks \
  -alias litejump -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=LiteSkip"
```

## 规则格式

内置规则在 `app/src/main/assets/rules.json`：

```json
{
  "version": 1,
  "global": [
    {
      "id": "builtin_splash_global",
      "type": "splash",
      "label": "开屏广告·跳过按钮（全局）",
      "keywords": ["跳过", "跳過", "Skip", "跳过广告"],
      "matchMode": "contains"
    },
    {
      "id": "builtin_confirm_global",
      "type": "confirm",
      "label": "跳过后·二次确认（全局）",
      "keywords": ["坚持跳过", "放弃奖励", "仍然跳过", "确认放弃"],
      "matchMode": "contains"
    }
  ],
  "apps": [
    {
      "id": "example_consent",
      "type": "consent",
      "packageName": "com.example.app",
      "label": "示例应用·隐私弹窗",
      "keywords": ["同意", "同意并继续"],
      "matchMode": "equals",
      "viewId": "com.example.app:id/btn_agree",
      "activityName": "com.example.app.PrivacyActivity"
    }
  ]
}
```

- `type`：`splash`（开屏跳过，可全局，仅窗口切换后 5 秒内生效）/ `consent`（同意隐私弹窗，须指定包名）/ `popup`（关闭应用内弹窗，须指定包名）/ `confirm`（跳过后二次确认，全局时跟随排除名单，仅追猎期内生效）
- `matchMode`：`contains`（包含）/ `equals`（完全相等）
- `viewId`（可选）：控件 ID 约束，支持全称或短名
- `activityName`（可选）：Activity 全名约束
- `keywords` 与 `viewId` 至少一个；同时给出时须全部满足才触发

导入功能兼容两种格式：本应用导出的规则数组，或上面的完整结构。

## 生效时机（防误点设计）

| 规则类型 | 何时响应 |
|---|---|
| splash | 仅窗口切换后 5 秒内 |
| confirm | 仅点中 splash 后 3.5 秒追猎期内 |
| consent / popup | 随时（按包名白名单启用） |

同一目标 4 秒冷却防连点；命中需同时满足：文字/控件ID 匹配、控件可见、有实际大小、自身或两层以内父节点可点击。

## 架构速览

```
app/src/main/java/com/litejump/app/
├── App.kt                  # 初始化规则仓库/日志/统计
├── MainActivity.kt         # 入口(引导页切换 + 主题)
├── engine/
│   ├── SkipService.kt      # 无障碍服务: 窗口期/追猎期控制, 多窗口扫描, 取词抓取
│   ├── Matcher.kt          # 节点树遍历 + 匹配 + 点击(事件/手势兜底) + 取词收集
│   ├── CaptureScheduler.kt # 取词倒计时
│   ├── CaptureHub.kt       # 取词结果交接(服务→UI)
│   ├── ClickLog.kt         # 自动点击日志(最近50条, 持久化)
│   └── Stats.kt            # 累计/今日/估算省时
├── rules/
│   ├── Models.kt           # 规则数据模型(kotlinx.serialization)
│   ├── TextMatcher.kt      # 纯文本匹配(有单测)
│   ├── RuleMerger.kt       # 导入合并纯逻辑(有单测)
│   └── RuleRepository.kt   # 内置+自定义规则合并, DataStore 持久化, 导入导出
├── data/DataStore.kt       # 共享 DataStore
└── ui/
    ├── AppUi.kt            # 设置界面(规则/日志/统计/备份/取词)
    ├── Onboarding.kt       # 首次启动三步引导
    └── Theme.kt            # 深色主题 + dynamicColor
```

原理一句话：`AccessibilityService` 收到 `TYPE_WINDOW_STATE_CHANGED`（新窗口，记录窗口期与 Activity）或 `TYPE_WINDOW_CONTENT_CHANGED`（内容变化，150ms 防抖合并）→ 遍历活动窗口与全部应用窗口的节点树（深度/数量有上限）→ 按「文字/控件ID 命中 + 可见 + 可点击 + 生效时机」筛选 → `ACTION_CLICK` 点击，失败则 `dispatchGesture` 坐标兜底 → 记日志/统计，splash 命中后进入二次确认追猎期 → 同一目标 4 秒冷却防止连点。

## CI

`.github/workflows/android.yml`：push/PR 自动跑单元测试并构建 Debug + Release APK（Release 在 CI 上为未签名包），产物见 Actions Artifacts。

## 致谢

- [李跳跳](https://zh.wikipedia.org/wiki/%E6%9D%8E%E8%B7%B3%E8%B7%B3)（理念来源，已停更）
- [zfdang/Android-Touch-Helper](https://github.com/zfdang/Android-Touch-Helper)（同类实现参考，MIT）
- [gkd-kit/gkd](https://github.com/gkd-kit/gkd)（订阅规则体系思路，本项目未使用其代码）

## License

[MIT](LICENSE)

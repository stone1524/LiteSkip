# 轻跳 LiteSkip

受「李跳跳」启发的开源开屏广告跳过工具。基于 Android 无障碍服务，自动点击屏幕上的「跳过 / 同意 / 关闭」按钮。

> **免责声明**：本项目仅供学习交流，请遵守所在地区法律法规，勿用于商业用途。使用本软件产生的任何问题由使用者自行承担。

## 特性

- **跳过开屏广告**：全局关键词规则，自动点「跳过」按钮
- **自动同意隐私弹窗**：按 App 配置，首次启动不再手点「同意」
- **自动关闭应用内弹窗**：按 App 配置「关闭 / 稍后」类按钮
- **自定义规则**：App 内可视化添加规则，无需懂代码
- **规则导入导出**：JSON 文件一键备份、分享
- **不联网**：Manifest 里没有申请 INTERNET 权限，可在「设置 → 应用 → 轻跳 → 权限管理」验证；所有数据只存本机

## 安装使用

1. 从 [Releases](../../releases) 下载 APK 安装（或自行编译）
2. 打开轻跳，点「去开启」跳转到系统无障碍设置，开启「轻跳 · 自动跳过服务」
3. 打开总开关，完成——下次打开带开屏广告的 App 会自动跳过并弹 Toast 提示

小米 / Redmi（澎湃OS）保活设置见 [docs/keepalive-xiaomi.md](docs/keepalive-xiaomi.md)。

## 自行编译

要求：JDK 17、Android SDK（API 35）。

```bash
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

Gradle 仓库已配置阿里云镜像、Gradle 发行版走腾讯镜像，国内网络可直接构建。

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
    }
  ],
  "apps": [
    {
      "id": "example_consent",
      "type": "consent",
      "packageName": "com.example.app",
      "label": "示例应用·隐私弹窗",
      "keywords": ["同意", "同意并继续"],
      "matchMode": "equals"
    }
  ]
}
```

- `type`：`splash`（开屏跳过，可全局）/ `consent`（同意隐私弹窗，须指定包名）/ `popup`（关闭应用内弹窗，须指定包名）
- `matchMode`：`contains`（包含）/ `equals`（完全相等）

导入功能兼容两种格式：本应用导出的规则数组，或上面的完整结构。

## 架构速览

```
app/src/main/java/com/litejump/app/
├── App.kt                  # 初始化规则仓库
├── MainActivity.kt         # 入口
├── engine/
│   ├── SkipService.kt      # 无障碍服务: 监听窗口变化 → 触发扫描 → 点击
│   └── Matcher.kt          # 节点树遍历 + 关键词匹配 + 点击(事件/手势兜底)
├── rules/
│   ├── Models.kt           # 规则数据模型(kotlinx.serialization)
│   └── RuleRepository.kt   # 内置+自定义规则合并, DataStore 持久化, 导入导出
└── ui/AppUi.kt             # Compose 设置界面
```

原理一句话：`AccessibilityService` 收到 `TYPE_WINDOW_STATE_CHANGED`（新窗口）或 `TYPE_WINDOW_CONTENT_CHANGED`（内容变化，150ms 防抖合并）→ 广度优先遍历节点树（深度/数量有上限）→ 按「文字命中 + 可见 + 可点击」筛选 → `ACTION_CLICK` 点击，失败则 `dispatchGesture` 坐标兜底 → 同一目标 4 秒冷却防止连点。

## 致谢

- [李跳跳](https://zh.wikipedia.org/wiki/%E6%9D%8E%E8%B7%B3%E8%B7%B3)（理念来源，已停更）
- [zfdang/Android-Touch-Helper](https://github.com/zfdang/Android-Touch-Helper)（同类实现参考，MIT）
- [gkd-kit/gkd](https://github.com/gkd-kit/gkd)（订阅规则体系思路，本项目未使用其代码）

## License

[MIT](LICENSE)

# DeepSeek 用量卡片

Android 桌面小部件，显示 DeepSeek API 余额和用量统计。

## 功能

- 桌面小部件实时显示 DeepSeek 余额
- 通过用法 Token 获取本月和今日用量（按模型拆分，缓存命中/未命中/输出）
- 每 15 分钟自动刷新
- 可单独使用 API Key 或用法 Token，也可同时填入

## 使用

1. 使用 Android Studio 或其他工具自行构建 APK
2. 安装 APK 后打开 App
3. 在 [DeepSeek Platform](https://platform.deepseek.com/api_keys) 创建 API Key 并填入
4. （可选）在 platform.deepseek.com/usage 页面，F12 → Network → 找到 `/api/v0/usage` 请求，复制 `Authorization: Bearer` 后面的 token 填入
5. 返回桌面，长按 → 添加小部件 → 找到"DeepSeek 用量卡片"拖放到桌面

## 说明

- 本人不懂安卓开发，本项目纯 vibe coding 完成
- 仅在个人设备上测试过，未做其他机型适配
- 如有 bug 欢迎提 issue，建议自行用 AI 排查修复

## 技术栈

- Kotlin + Android Jetpack Glance
- OkHttp + kotlinx.serialization
- DataStore Preferences + WorkManager
- DeepSeek Platform API

## License

MIT

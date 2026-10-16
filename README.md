# 儿童护眼与游戏时间管理 Android App

Kotlin 原型，面向普通 Android APK 模式。

## 当前实现
- 基础主页与使用情况访问权限入口
- RFC 6238 TOTP 核心算法及单元测试
- 开机广播占位（不承诺普通应用开机后自动锁屏）
- GitHub Actions 自动执行单元测试并构建 Debug APK

## 尚未实现
- TOTP 密钥安全配对、加密保存和家长端绑定流程
- 持续使用/每日游戏额度的可靠统计与自动执行
- 游戏选择器、休息倒计时服务、重启后状态恢复
- 设备所有者/Lock Task 模式

普通 APK 无法可靠阻止 Home、最近任务、强行停止、卸载或系统设置绕过。不要将此原型当作安全锁屏使用。

## 构建
需要 JDK 17 与 Gradle 8.9 左右：
`gradle testDebugUnitTest assembleDebug`

APK: `app/build/outputs/apk/debug/app-debug.apk`

# 儿童护眼与游戏时间管理 Android App

Kotlin 原型，面向荣耀等 Android 手机的普通 APK 安装方式，当前不修改系统配置。

## 当前实现
- 基础主页与“使用情况访问”系统设置入口
- 授权后显示今日各应用前台时长合计的估算值（不等同于纯游戏时长）
- 应用内 5 分钟休息倒计时提醒
- RFC 6238 TOTP 核心算法及单元测试（尚未接入完整的家长密钥配对 UI）
- 开机广播占位（不承诺普通应用开机后自动锁屏）
- GitHub Actions 自动运行单元测试、构建 Debug APK，并上传为 workflow artifact

## 尚未实现
- 家长 TOTP 密钥安全配对、加密保存、验证码解锁流程
- 选择具体游戏并准确统计各游戏每日额度
- 持续使用提醒服务、每日额度自动执行、重启后状态恢复
- 设备所有者/Lock Task 模式

## 重要限制
普通 APK 无法可靠阻止 Home、最近任务、强行停止、卸载或通过系统设置绕过；应用内倒计时也不能强制孩子一直停留在应用中。不要将当前原型当作安全锁屏或完整家长控制产品使用。更强限制需要评估 Android Device Owner / Lock Task 配置；这可能要求在设备初始设置阶段配置，具体操作前应备份数据。

## 构建
GitHub Actions 使用 JDK 17 和固定的 Gradle 版本构建：
`gradle testDebugUnitTest assembleDebug`

构建成功后，在仓库的 **Actions → Android APK → 对应运行记录 → Artifacts** 下载 `childcare-debug-apk`。APK 路径为 `app/build/outputs/apk/debug/app-debug.apk`。

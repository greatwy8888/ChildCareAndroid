# 极简屏幕保护 Android App

极简功能：
- 开机后尝试自动显示全屏保护界面。
- 首次启动由家长设置密码（至少 6 位）；本地保存 PBKDF2-HMAC-SHA256 派生摘要，不保存明文密码。
- 输入正确密码后正常使用手机 40 分钟。
- 40 分钟到期后尝试重新显示全屏保护界面。
- 保留 Device Owner / Lock Task 支持；普通安装无法可靠阻止 Home、最近任务或系统设置绕过。

## 构建 APK
GitHub Actions 自动运行 gradle testDebugUnitTest assembleDebug，然后在 Actions → Android APK → 对应运行记录 → Artifacts 下载 ChildCareAndroid-debug，解压得到 app-debug.apk。

Actions 页面：https://github.com/greatwy8888/ChildCareAndroid/actions

## 安装与设备所有者模式
先安装 APK，再在满足系统条件时执行：
    adb install app-debug.apk
    adb shell dpm set-device-owner com.example.childcare/.AdminReceiver

Device Owner 通常要求设备处于适合配置的初始状态，可能需要恢复出厂设置。恢复出厂设置会清除数据；操作前请备份。不要在没有可用 APK 和恢复方案时重置日常手机。

## 测试重点
1. 首次启动设置家长密码。
2. 错误密码不能解锁，正确密码可解锁。
3. 解锁后可使用其他应用。
4. 约 40 分钟后保护界面重新出现。
5. 重启后保护界面恢复。
6. 在真实荣耀手机上测试后台运行、开机启动和到期重新锁定。

## 限制与安全
- 这是初始极简实现，尚未在真实设备上验证。Android / 荣耀 MagicOS 可能限制开机后启动 Activity、后台启动 Activity 或计时服务；不能保证普通 APK 强制弹出并锁住其他应用。
- Device Owner + Lock Task 可提高锁定可靠性，但仍须实机测试。
- 家长密码应妥善保管。若忘记密码，目前没有应用内找回流程；需通过清除应用数据（若系统允许）或设备恢复流程处理。
- 当前为 Debug APK，适合测试，不是面向公开发布的正式签名版本。

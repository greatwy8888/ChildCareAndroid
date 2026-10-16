# 动态验证码屏幕保护（Device Owner 版本）

极简应用：家长 Google Authenticator/TOTP 验证码解锁整部手机 40 分钟，到期后重新进入保护界面。旧版使用统计、游戏选择、休息倒计时功能均已删除。

## 当前实现
- RFC 6238 TOTP：6 位验证码、30 秒周期、HMAC-SHA1，兼容 Google Authenticator 的手动密钥录入
- 首次启动生成随机密钥并使用 AndroidX Security Crypto 加密存储
- Device Owner 检测；在已正确配置 Device Owner 的设备上，进入 Lock Task 单应用锁定模式，并禁用状态栏
- 验证成功后退出 Lock Task、恢复状态栏，手机可正常使用其他应用；启动前台服务，保存 40 分钟绝对截止时间
- 到期后尝试将保护界面带回前台，并重新进入 Lock Task；开机/应用更新后清除解锁状态并尝试恢复锁定
- GitHub Actions 自动运行 TOTP 单元测试并构建 Debug APK

## 重要：必须先完成 Device Owner 配置
普通安装并不自动取得 Device Owner 权限。通常需在全新恢复出厂设置、尚未完成个人账号配置的设备上安装 APK，然后通过 Android Debug Bridge（ADB）配置：

```bash
adb install app-debug.apk
adb shell dpm set-device-owner com.example.childcare/.AdminReceiver
```

具体系统版本/荣耀 MagicOS 可能对步骤有差异；命令成功后再重启和测试。若提示已有账号、已有设备所有者或不符合条件，先不要继续操作，请停止并确认设备状态。恢复出厂设置会清除本机数据，操作前必须备份。

## 建议配置流程
1. 先在备用 Android 设备上测试，不要直接在唯一日用手机上试。
2. 备份手机所有照片、聊天记录、文件和验证器恢复资料。
3. 按荣耀手机对应版本的官方流程恢复出厂设置；首次设置时暂不添加个人账号（如系统允许）。
4. 通过 USB 调试/ADB 安装 APK 并运行上面的 Device Owner 命令。
5. 重启，确认进入保护界面；首次打开时把 TOTP 密钥录入家长 Google Authenticator，并保管好密钥。
6. 验证解锁 40 分钟、期间使用其他 App、到期自动重新锁定、重启后重新锁定。
7. 只有所有测试都通过后，才考虑在孩子日常手机上部署。

## 恢复与安全警告
Device Owner 具有较强设备管理能力，配置错误可能影响正常使用。密钥丢失、到期无法回到前台或设备管理配置异常时，可能需要使用 Android Recovery 恢复出厂设置；这会清除数据。当前版本没有家长远程恢复/重置流程。请始终在备用设备测试并备份。

## 重要限制
- 这是初始 Device Owner 实现，尚未在真实荣耀设备上验证。不同 Android/MagicOS 版本可能限制后台 Activity 启动、前台服务或锁定任务行为。不能在未实机测试前保证 40 分钟到期一定能打断任意前台应用。
- 前台服务会显示持续通知；若系统杀死进程，会根据保存的截止时间尝试恢复计时。
- 普通 APK 未配置 Device Owner 时，只能显示全屏 UI，无法可靠限制 Home、最近任务或系统设置。
- 为避免意外永久锁死，第一次必须在备用设备验证锁定、解锁、到期、重启和恢复流程。

## 构建
GitHub Actions 使用 JDK 17，执行 `gradle testDebugUnitTest assembleDebug`。在 **Actions → Android APK → 对应运行记录 → Artifacts** 下载 `ChildCareAndroid-debug`，解压后获取 `app-debug.apk`。

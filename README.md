# lock screen Android App

极简功能：
- 开机后通过 BOOT_COMPLETED 接收器尝试显示全屏保护界面；荣耀手机需要手动允许自启动和后台运行。
- 使用 Google Authenticator 兼容的 TOTP 六位动态密码解锁，时间步长 30 秒，允许前后一个时间步长以容忍少量时钟偏差。
- 输入正确密码后正常使用手机 40 分钟。
- 40 分钟到期后尝试重新显示全屏保护界面。
- Device Owner 模式下启用 Lock Task kiosk，并在保护界面恢复到前台时再次检查锁定状态；Android 9 及以上尝试禁用 Home/最近任务等 Lock Task 系统功能。普通安装无法可靠阻止 Home、最近任务或系统设置绕过。

## 构建 APK
GitHub Actions 自动运行 gradle testDebugUnitTest assembleDebug，然后在 Actions → Android APK → 对应运行记录 → Artifacts 下载 ChildCareAndroid-debug，解压得到 app-debug.apk。

Actions 页面：https://github.com/greatwy8888/ChildCareAndroid/actions

## 安装与设备所有者模式
先安装 APK，再在满足系统条件时执行：
    adb install app-debug.apk
    adb shell dpm set-device-owner com.example.childcare/.AdminReceiver

Device Owner 通常要求设备处于适合配置的初始状态，可能需要恢复出厂设置。恢复出厂设置会清除数据；操作前请备份。不要在没有可用 APK 和恢复方案时重置日常手机。配置完成后，保护界面才可使用系统级 Lock Task kiosk 限制 Home/最近任务；仅安装 APK 或只授予普通设备管理员权限不够。解锁时应用会退出 Lock Task，40 分钟后重新进入保护界面时再尝试启用。

## 荣耀手机开机启动排查

1. 安装后至少手动打开一次 App，并完成首次设置密码；Android 对从未启动过或被“强行停止”的应用可能不投递开机广播。
2. 在系统设置中为“lock screen”允许“自启动/自动启动”“后台活动/后台运行”，并将电池用量设为“不受限制”（各 MagicOS 版本名称可能不同）。
3. 不要在应用信息页点“强行停止”；强行停止后通常要手动再次打开应用，系统才会恢复广播。
4. 重启后如果仍不自动出现，普通 APK 无法绕过系统对后台启动 Activity 的限制。要实现更可靠的开机即锁定，需要在支持的设备上配置 Device Owner / Lock Task，并在真实手机上验证；仅安装 APK 不能保证。

## 界面与卸载限制
- 应用名称显示为 lock screen；保护界面采用深色卡片布局，显示“友情提示：”和“王梓淇请休息一会”。解锁提示不再显示谷歌验证器相关说明。
- 普通 APK 无法要求 Android 系统卸载界面输入本应用的动态密码。只有配置为 Device Owner 后，本应用才会通过系统策略阻止卸载；启用后通常不能直接从系统设置卸载，需由设备管理员按预先准备的恢复方案解除管理。请只在你拥有并负责管理的设备上启用。

## 测试重点
1. 使用 Google Authenticator 添加应用中配置的密钥，确认生成的六位动态密码可以解锁。
2. 空输入、非六位数字以及错误或过期动态密码均不能解锁。
3. 解锁后可使用其他应用。
4. 约 40 分钟后保护界面重新出现。
5. 重启后保护界面恢复。
6. 在真实荣耀手机上测试后台运行、开机启动和到期重新锁定。

## 限制与安全
- 这是初始极简实现，尚未在真实设备上验证。Android / 荣耀 MagicOS 可能限制开机后启动 Activity、后台启动 Activity 或计时服务；不能保证普通 APK 强制弹出并锁住其他应用。
- Device Owner + Lock Task 可提高锁定可靠性，但仍须实机测试。
- TOTP 密钥按需求固定写在 APK 中；懂得逆向 APK 的人可能提取该密钥。
- 当前为 Debug APK，适合测试，不是面向公开发布的正式签名版本。

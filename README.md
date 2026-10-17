# Lock Screen — 隔离测试分支

> **重要：这是独立测试包，不是已安装的保护版升级包。不要将它配置为日常手机的 Device Owner，也不要为测试而卸载旧版、清除数据或解除现有管理。**

## 本分支目的

本分支从 `feature/totp-protected-settings` 创建，用于在不覆盖现有安装的前提下，验证新版代码是否能够构建及进行功能测试。

- 应用显示名称：`lock screen TEST`
- 测试包名：`com.example.childcare.next`
- 测试版本：`0.1.0-test`（versionCode 1）
- 原保护版包名仍为：`com.example.childcare`
- 原有安装和 `main` 分支不由本分支修改。

## 构建与下载

打开 [GitHub Actions](https://github.com/greatwy8888/ChildCareAndroid/actions)，选择 `safe-alternative-test` 分支的最新 `Android APK` 工作流运行，然后在 Artifacts 中下载：

- `ChildCareAndroid-debug`：调试测试包。
- `ChildCareAndroid-release-signed`：只有当四项 GitHub Actions 签名密钥配置齐全并且 Release 构建成功时才会生成的签名版测试包。

工作流会报告 APK 签名证书指纹，但不会输出密钥或口令。请在安装前核对包名和证书信息。Release 包使用本测试分支配置的新签名密钥，**不等于**旧版 `com.example.childcare` 的兼容升级包。

构建成功仅代表编译和自动化单元测试通过，不代表已经在荣耀手机上验证了锁屏、开机启动、计时或防绕过行为。

计时结束时，测试版现在会在尝试返回锁屏前发布一条可点击的恢复通知；如果 Android 阻止后台界面启动，可通过通知手动返回 Lock Screen。**这只是恢复后备方案，不保证到期时强制切出其他应用**，并且可能受通知权限及厂商后台策略影响，必须在备用设备实测。

## 安全边界

1. 不要在孩子的日常保护手机上安装或启用此测试包，直到完成代码审查和备用设备验证。
2. 不要对测试包执行 `dpm set-device-owner`，也不要尝试让它取代原保护版。
3. 不要卸载原保护版、清除其数据、解除 Device Owner 或恢复出厂设置来“试试看”。
4. 独立包名不会继承原应用的 Device Owner 身份、应用数据或系统策略。
5. 如果找回原始签名私钥，仍应优先对原包名进行签名兼容升级；独立测试包不是升级签名的替代品。
6. 此分支不包含迁移授权流程，也不保证所有功能已经实机验证。

## 验证清单

- [x] GitHub Actions 构建和单元测试成功
- [x] 如签名密钥已配置，Release APK 构建和签名验证成功
- [ ] APK 包名确认为 `com.example.childcare.next`
- [ ] APK 显示名称确认为 `lock screen TEST`
- [ ] 在备用测试设备验证 TOTP 正确/错误输入
- [ ] 验证设定计时与到期重新锁定
- [ ] 验证重启行为和系统限制
- [ ] 记录测试结果并审查所有失败项
- [ ] 在讨论任何正式迁移前准备备份和恢复方案

## 最新安全审查说明

TOTP 算法现已独立到 `Totp.kt`，并新增 RFC 6238 单元测试。该改动仅改善算法可测试性，**没有解决 APK 内硬编码 TOTP 密钥可被提取的问题**。正式使用前仍需设计安全的密钥轮换和恢复流程。

迁移限制、风险和迁移前检查表见 [SECURITY_AND_MIGRATION.md](SECURITY_AND_MIGRATION.md)。备用设备实测步骤、只读日志采集命令和停止条件见 [docs/SAFE_PHYSICAL_DEVICE_TEST.md](docs/SAFE_PHYSICAL_DEVICE_TEST.md)。TOTP 每安装独立密钥、注册确认、恢复和卸载授权的设计要求见 [docs/TOTP_ENROLLMENT_MIGRATION_PLAN.md](docs/TOTP_ENROLLMENT_MIGRATION_PLAN.md)。在这些检查完成前，请勿在日常保护手机上安装测试包或更改现有 Device Owner 状态。

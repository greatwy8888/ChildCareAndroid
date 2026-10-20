# 私密相册（Private Gallery）

Android 本地照片与视频管理 App。此版本在独立分支 `private-gallery-app` 开发，不会修改 `main` 或原 Lock Screen 版本。

## 功能

- 首次打开创建用户名和登录密码；密码以 SHA-256 摘要保存，可在设置中修改。
- 从系统文件选择器导入多张照片或多个视频，并复制到 App 私有目录。
- 系统相册删除原图不会影响 App 内的独立副本。
- 自定义文件夹、新建、重命名和删除；删除文件夹时可选择同时永久删除其中的媒体。
- 删除文件前二次确认。
- 自定义登录背景图片。
- 图片查看、视频播放、文件列表和媒体计数。
- 默认不需要相册/媒体读取权限，使用 Android 系统文件选择器。

## 本地存储与注意事项

媒体文件位于 App 私有目录 `files/private_media`，索引与文件夹信息保存在本机应用设置中。文件不会自动上传云端，也不会显示为系统相册中的新增媒体。**卸载 App 或清除 App 数据会删除这些本地副本**；重要文件请另外备份。

此版本是首个功能实现，仍需通过 GitHub Actions 构建，并在 Android 实机验证导入、播放、删除和背景设置后再作为正式版本使用。登录是本机应用级密码门槛，不等同于经过安全审计的加密保险库；私有目录保护依赖 Android 应用沙箱。

## 构建 APK

1. 打开 [此分支的 GitHub Actions](https://github.com/greatwy8888/ChildCareAndroid/actions/workflows/android.yml?query=branch%3Aprivate-gallery-app)。
2. 等待最新的 **Private Gallery Android APK** 工作流完成。
3. 在运行详情页下载 `PrivateGallery-debug`，或在签名密钥已配置且 Release 构建成功时下载 `PrivateGallery-release-signed`。
4. APK 包名为 `com.example.privategallery`。这是独立应用，不会覆盖原来的 Lock Screen。

## 开发状态

- [x] 独立开发分支和包名
- [x] 登录、改密、自定义登录背景
- [x] 本地照片/视频导入与独立保存
- [x] 自定义文件夹与删除确认
- [ ] GitHub Actions 构建通过
- [ ] Android 实机测试

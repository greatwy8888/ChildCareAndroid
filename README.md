# 雄哥相册

独立的 Android 本地照片与视频管理 App，在 `private-gallery-app` 分支开发。与儿童保护/Lock Screen 应用分开，不修改原来的 `main` 分支或 Lock Screen 版本。

## 功能

- 首次打开创建用户名和登录密码；可在设置中修改密码。
- 从系统文件选择器导入多张照片和视频，并复制到 App 私有目录。
- 系统相册删除原图不会影响 App 内的独立副本。
- 创建、重命名和删除自定义文件夹；删除文件夹时可选择是否同时永久删除里面的媒体。
- 删除文件前显示确认提示。
- 自定义登录背景图片。
- 图片查看、视频播放、文件列表和媒体数量统计。
- 使用 Android 系统文件选择器，不需要广泛的相册读取权限。

## 本地存储说明

媒体文件位于 App 私有目录 `files/private_media`，索引和文件夹信息保存在本机。文件不会自动上传云端，也不会自动出现在系统相册中。**卸载 App 或清除 App 数据会删除这些本地副本**，重要文件请另行备份。

当前登录属于应用级密码门槛，并非经过安全审计的加密保险库；私有目录保护依赖 Android 应用沙箱。

## 构建 APK

1. 打开 [雄哥相册 GitHub Actions](https://github.com/greatwy8888/ChildCareAndroid/actions/workflows/android.yml?query=branch%3Aprivate-gallery-app)。
2. 等待最新的 **雄哥相册 Android APK** 工作流完成。
3. 在运行详情中下载 `XiongGeAlbum-debug`。若已配置签名密钥并且 Release 构建成功，可下载 `XiongGeAlbum-release-signed`。
4. 应用显示名称为 **雄哥相册**，包名为 `com.example.privategallery`，与原 Lock Screen 应用使用不同包名。

## 状态

- [x] 独立分支和独立应用包名
- [x] 登录、改密、自定义登录背景
- [x] 本地照片/视频导入与独立保存
- [x] 自定义文件夹和删除确认
- [ ] GitHub Actions 构建通过
- [ ] Android 实机测试

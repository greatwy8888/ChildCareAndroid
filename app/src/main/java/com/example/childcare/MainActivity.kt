package com.example.childcare

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable\nimport android.graphics.drawable.ColorDrawable\nimport android.widget.GridLayout
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("private_gallery", Context.MODE_PRIVATE) }
    private var folder = "root"\n    private var viewingFullscreenImage = false
    private val blue = Color.rgb(55, 126, 245)
    private val ink = Color.rgb(32, 44, 68)
    private val muted = Color.rgb(112, 126, 149)
    private val pale = Color.rgb(244, 247, 252)

    private val importer = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        var count = 0
        uris?.forEach { if (importFile(it)) count++ }
        toast("已独立保存 " + count + " 个文件")
        home()
    }
    private val bgPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            contentResolver.openInputStream(uri)?.use { input ->
                File(filesDir, "login_bg").outputStream().use { output -> input.copyTo(output) }
            }
            toast("登录背景已更新")
        } catch (_: Exception) { toast("图片保存失败") }
        settings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        File(filesDir, "private_media").mkdirs()
        if (prefs.getString("password_hash", null).isNullOrBlank()) setup() else login()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun bg(color: Int, radius: Int = 16): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(s: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    private fun btn(s: String, primary: Boolean = false, action: () -> Unit) =
        TextView(this).apply {
            text = s; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(if (primary) Color.WHITE else blue)
            background = bg(if (primary) blue else Color.WHITE, 13)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            setOnClickListener { action() }
        }
    private fun field(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText; setSingleLine(true); textSize = 15f
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        background = bg(Color.WHITE, 12); setPadding(dp(14), dp(8), dp(14), dp(8))
    }
    private fun page(c: LinearLayout) {
        val scroll = ScrollView(this)
        scroll.addView(c)
        setContentView(scroll)
    }
    private fun col() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(24)); setBackgroundColor(pale)
    }
    private fun gap(c: LinearLayout, h: Int = 12) { c.addView(View(this), LinearLayout.LayoutParams(1, dp(h))) }
    private fun addField(c: LinearLayout, f: EditText) {
        c.addView(f, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
    }

    private fun setup() {
        val c = col()
        c.addView(text("✦  私密相册", 30f, blue, true)); gap(c, 8)
        c.addView(text("创建密码，保护照片和视频", 14f, muted)); gap(c, 28)
        val user = field("用户名（默认 admin）").apply { setText("admin") }
        val p = field("设置密码（至少 6 位）", true)
        val again = field("再次输入密码", true)
        addField(c, user); addField(c, p); addField(c, again)
        c.addView(btn("创建并进入相册", true) {
            if (p.text.length < 6) { p.error = "密码至少 6 位"; return@btn }
            if (p.text.toString() != again.text.toString()) { again.error = "两次密码不一致"; return@btn }
            prefs.edit().putString("username", user.text.toString().trim().ifBlank { "admin" })
                .putString("password_hash", hash(p.text.toString())).apply()
            home()
        })
        gap(c, 18)
        c.addView(text("文件保存在本机应用私有空间，不会自动上传云端。", 12f, muted))
        page(c)
    }

    private fun login() {
        val root = FrameLayout(this).apply { setBackgroundColor(pale) }
        val bgFile = File(filesDir, "login_bg")
        if (bgFile.exists()) {
            val backgroundImage = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(BitmapFactory.decodeFile(bgFile.absolutePath))
            }
            root.addView(backgroundImage, FrameLayout.LayoutParams(-1, -1))
        } else {
            root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(33,111,232), Color.rgb(116,92,246), Color.rgb(229,241,255)))
        }
        root.addView(View(this).apply { setBackgroundColor(0x440C2148) }, FrameLayout.LayoutParams(-1, -1))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(28), dp(24), dp(28))
            background = bg(0xF7FFFFFF.toInt(), 26); elevation = dp(10).toFloat()
        }
        card.addView(text("◈", 42f, blue, true).apply { gravity = Gravity.CENTER })
        card.addView(text("雄哥相册", 29f, ink, true).apply { gravity = Gravity.CENTER }); gap(card, 5)
        card.addView(text("珍藏每一份美好回忆", 14f, muted).apply { gravity = Gravity.CENTER }); gap(card, 26)
        val user = field("用户名").apply { setText(prefs.getString("username", "admin")) }
        val p = field("登录密码", true)
        addField(card, user); addField(card, p)
        card.addView(btn("登 录", true) {
            if (user.text.toString().trim() == prefs.getString("username", "admin") && hash(p.text.toString()) == prefs.getString("password_hash", "")) home()
            else p.error = "用户名或密码不正确"
        })
        gap(card, 18)
        card.addView(text("本地存储 · 私密管理", 12f, muted).apply { gravity = Gravity.CENTER })
        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { setMargins(dp(28), dp(28), dp(28), dp(28)) })
        setContentView(root)
    }

    private data class Item(val id: String, val name: String, val path: String, val mime: String, val folder: String, val time: Long) {
        fun json() = JSONObject().put("id", id).put("name", name).put("path", path).put("mime", mime).put("folder", folder).put("time", time)
    }
    private fun items(): List<Item> = try {
        val a = JSONArray(prefs.getString("items", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Item(o.getString("id"), o.getString("name"), o.getString("path"), o.getString("mime"), o.optString("folder", "root"), o.optLong("time"))
        }.filter { File(filesDir, it.path).exists() }
    } catch (_: Exception) { emptyList() }
    private fun saveItems(list: List<Item>) {
        val a = JSONArray(); list.forEach { a.put(it.json()) }
        prefs.edit().putString("items", a.toString()).apply()
    }
    private fun folders(): LinkedHashMap<String, String> {
        val map = linkedMapOf<String, String>()
        try {
            val o = JSONObject(prefs.getString("folders", "{}")); val keys = o.keys()
            while (keys.hasNext()) { val k = keys.next(); map[k] = o.getString(k) }
        } catch (_: Exception) { }
        return map
    }
    private fun saveFolders(map: Map<String, String>) {
        val o = JSONObject(); map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("folders", o.toString()).apply()
    }

    private fun home() {
        val list = items()
        val fm = folders()
        val c = col()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text(if (folder == "root") "我的相册" else fm[folder] ?: "文件夹", 25f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(btn("⚙ 设置") { settings() })
        c.addView(header); gap(c, 8)
        c.addView(text("本地私密存储 · " + list.count { it.mime.startsWith("image/") } + " 张照片 · " +
            list.count { it.mime.startsWith("video/") } + " 个视频", 13f, muted)); gap(c, 16)
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(btn("＋ 新建文件夹") { createFolder() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        actions.addView(btn("⇧ 导入照片/视频", true) { importer.launch(arrayOf("image/*", "video/*")) }, LinearLayout.LayoutParams(0, -2, 1.3f))
        c.addView(actions); gap(c, 18)
        c.addView(text("文件夹（长按可管理）", 18f, ink, true)); gap(c, 8)
        c.addView(folderRow("全部照片与视频", "root", list.size))
        fm.forEach { (id, name) -> c.addView(folderRow(name, id, list.count { it.folder == id })) }
        gap(c, 18)
        c.addView(text(if (folder == "root") "最近添加" else "文件列表", 18f, ink, true)); gap(c, 8)
        val visible = if (folder == "root") list.sortedByDescending { it.time } else list.filter { it.folder == folder }.sortedByDescending { it.time }
        if (visible.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(16), dp(44), dp(16), dp(44))
                background = bg(Color.WHITE, 22)
            }
            empty.addView(text("▧", 48f, blue, true).apply { gravity = Gravity.CENTER })
            empty.addView(text("还没有照片或视频", 17f, ink, true).apply { gravity = Gravity.CENTER })
            empty.addView(text("导入媒体，开始整理你的美好回忆", 13f, muted).apply { gravity = Gravity.CENTER })
            c.addView(empty)
        } else {
            val grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
            visible.forEach { item ->
                val tile = thumbnailTile(item)
                val lp = GridLayout.LayoutParams().apply {
                    width = 0; height = dp(132); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                grid.addView(tile, lp)
            }
            c.addView(grid)
        }
        page(c)
    }

    private fun folderRow(name: String, id: String, count: Int): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(10), dp(10)); background = bg(Color.WHITE)
        }
        row.addView(text("▣", 25f, blue, true), LinearLayout.LayoutParams(dp(42), dp(44)))
        val detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        detail.addView(text(name, 15f, ink, true)); detail.addView(text(count.toString() + " 个文件", 12f, muted))
        row.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(btn("打开") { folder = id; home() })
        row.setOnClickListener { folder = id; home() }
        row.setOnLongClickListener { if (id != "root") folderMenu(id, name) else Toast.makeText(this, "这是全部文件视图", Toast.LENGTH_SHORT).show(); true }
        return LinearLayout(this).apply { setPadding(0, 0, 0, dp(8)); addView(row) }
    }

    private fun thumbnailTile(item: Item): View {
        val file = File(filesDir, item.path)
        val frame = FrameLayout(this).apply {
            background = bg(Color.WHITE, 14); clipToOutline = true; elevation = dp(1).toFloat()
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (item.mime.startsWith("image/")) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    val opts = BitmapFactory.Options()
                    opts.inSampleSize = maxOf(1, minOf(bounds.outWidth / 300, bounds.outHeight / 300))
                    setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, opts))
                } catch (_: Exception) { setImageDrawable(ColorDrawable(0xFFE5ECF7.toInt())) }
            } else setImageDrawable(ColorDrawable(0xFF263B63.toInt()))
        }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        if (item.mime.startsWith("video/")) {
            val badge = TextView(this).apply {
                text = "▶ 视频"; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                background = bg(0x99000000.toInt(), 9); setPadding(dp(7), dp(4), dp(7), dp(4))
            }
            frame.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(6), 0, 0, dp(6)) })
        }
        frame.setOnClickListener { openItem(item) }
        frame.setOnLongClickListener {
            AlertDialog.Builder(this).setTitle(item.name).setItems(arrayOf("查看", "删除")) { _, which ->
                if (which == 0) openItem(item) else AlertDialog.Builder(this).setTitle("删除文件？")
                    .setMessage("确定从雄哥相册中永久删除此文件吗？")
                    .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                        file.delete(); saveItems(items().filterNot { it.id == item.id }); home()
                    }.show()
            }.show()
            true
        }
        return frame
    }

    private fun createFolder() {
        val input = field("文件夹名称")
        AlertDialog.Builder(this).setTitle("新建文件夹").setView(input).setNegativeButton("取消", null)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { toast("请输入名称"); return@setPositiveButton }
                val map = folders()
                if (map.values.any { it.equals(name, true) }) { toast("名称已存在"); return@setPositiveButton }
                map[UUID.randomUUID().toString()] = name; saveFolders(map); home()
            }.show()
    }

    private fun folderMenu(id: String, name: String) {
        AlertDialog.Builder(this).setTitle(name).setItems(arrayOf("打开", "重命名", "删除文件夹")) { _, which ->
            when (which) {
                0 -> { folder = id; home() }
                1 -> {
                    val input = field("文件夹名称").apply { setText(name); selectAll() }
                    AlertDialog.Builder(this).setTitle("重命名").setView(input).setNegativeButton("取消", null)
                        .setPositiveButton("保存") { _, _ ->
                            val n = input.text.toString().trim()
                            if (n.isNotBlank()) { val map = folders(); map[id] = n; saveFolders(map); home() }
                        }.show()
                }
                2 -> {
                    val check = CheckBox(this).apply { text = "同时永久删除其中的所有文件" }
                    AlertDialog.Builder(this).setTitle("删除文件夹？").setMessage("确定删除“" + name + "”？不勾选则保留文件并移到全部文件。")
                        .setView(check).setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                            val all = items()
                            if (check.isChecked) {
                                all.filter { it.folder == id }.forEach { File(filesDir, it.path).delete() }
                                saveItems(all.filterNot { it.folder == id })
                            } else saveItems(all.map { if (it.folder == id) it.copy(folder = "root") else it })
                            val map = folders(); map.remove(id); saveFolders(map)
                            if (folder == id) folder = "root"
                            home()
                        }.show()
                }
            }
        }.show()
    }

    private fun settings() {
        val c = col()
        c.addView(text("设置", 26f, ink, true)); gap(c, 18)
        c.addView(btn("修改登录密码") { changePassword() }); gap(c)
        c.addView(btn("更换登录背景图片") { bgPicker.launch(arrayOf("image/*")) }); gap(c)
        c.addView(btn("返回相册", true) { home() }); gap(c, 24)
        c.addView(text("隐私说明", 17f, ink, true)); gap(c, 8)
        c.addView(text("导入的照片和视频会复制到 App 私有目录，与系统相册独立保存。从系统相册删除原图不会删除这里的副本。卸载 App 或清除 App 数据会删除本地文件，请提前备份。", 14f, muted))
        gap(c, 18); c.addView(btn("退出登录") { login() })
        page(c)
    }

    private fun changePassword() {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val old = field("当前密码", true); val next = field("新密码（至少 6 位）", true); val again = field("确认新密码", true)
        addField(c, old); addField(c, next); addField(c, again)
        val dialog = AlertDialog.Builder(this).setTitle("修改登录密码").setView(c).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (hash(old.text.toString()) != prefs.getString("password_hash", "")) { old.error = "当前密码不正确"; return@setOnClickListener }
                if (next.text.length < 6) { next.error = "密码至少 6 位"; return@setOnClickListener }
                if (next.text.toString() != again.text.toString()) { again.error = "两次密码不一致"; return@setOnClickListener }
                prefs.edit().putString("password_hash", hash(next.text.toString())).apply()
                dialog.dismiss(); toast("密码已修改"); settings()
            }
        }
        dialog.show()
    }

    private fun importFile(uri: Uri): Boolean {
        return try {
        val mime = contentResolver.getType(uri) ?: return false
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) return false
        var name = "媒体文件"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        val ext = name.substringAfterLast('.', "").takeIf { it.length in 1..8 }?.let { "." + it } ?: ""
        val id = UUID.randomUUID().toString()
        val rel = "private_media/" + id + ext
        contentResolver.openInputStream(uri)?.use { input -> File(filesDir, rel).outputStream().use { output -> input.copyTo(output) } } ?: return false
        saveItems(items() + Item(id, name, rel, mime, folder, System.currentTimeMillis()))
        true
        } catch (_: Exception) { false }
    }

    private fun openItem(item: Item) {
        val file = File(filesDir, item.path)
        if (!file.exists()) { toast("文件不存在"); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(btn("‹ 返回", false) { home() })
        if (item.mime.startsWith("video/")) {
            val v = VideoView(this)
            v.setVideoURI(Uri.fromFile(file)); v.setMediaController(MediaController(this))
            root.addView(v, LinearLayout.LayoutParams(-1, 0, 1f)); v.setOnPreparedListener { v.start() }
        } else {
            val image = ImageView(this).apply {
                setImageBitmap(BitmapFactory.decodeFile(file.absolutePath)); scaleType = ImageView.ScaleType.FIT_CENTER
            }
            root.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
    }

    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

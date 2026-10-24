package com.example.childcare

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.GridLayout
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
    private var folder = "root"
    private var viewingFullscreenImage = false
    private var listMode = false
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

    override fun onBackPressed() {
        if (viewingFullscreenImage) {
            viewingFullscreenImage = false
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            home()
        } else if (folder != "root") {
            folder = "root"; home()
        } else {
            AlertDialog.Builder(this).setTitle("退出私牍空间？")
                .setMessage("确定要退出应用吗？")
                .setNegativeButton("继续使用", null)
                .setPositiveButton("退出") { _, _ -> finish() }.show()
        }
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
    private fun field(hintText: String, password: Boolean = false, transparent: Boolean = false) = EditText(this).apply {
        hint = hintText; setSingleLine(true); textSize = 15f
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        background = bg(if (transparent) 0x33FFFFFF else Color.WHITE, 12)
        if (transparent) { setTextColor(Color.WHITE); setHintTextColor(0xDDFFFFFF.toInt()) }
        setPadding(dp(14), dp(8), dp(14), dp(8))
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
        c.addView(text("✦  私牍空间", 30f, blue, true)); gap(c, 8)
        c.addView(text("创建密码，保护照片和视频", 14f, muted)); gap(c, 28)
        val user = field("用户名（默认 admin）").apply { setText("admin") }
        val p = field("设置密码（至少 6 位）", true)
        val again = field("再次输入密码", true)
        val recoveryInput = field("自定义找回码（至少 6 位）")
        addField(c, user); addField(c, p); addField(c, again); addField(c, recoveryInput)
        c.addView(btn("创建并进入相册", true) {
            if (p.text.length < 6) { p.error = "密码至少 6 位"; return@btn }
            if (p.text.toString() != again.text.toString()) { again.error = "两次密码不一致"; return@btn }
            val recovery = recoveryInput.text.toString().trim().uppercase()
            if (recovery.length < 6) { recoveryInput.error = "找回码至少 6 位"; return@btn }
            if (recovery.equals(p.text.toString(), true)) { recoveryInput.error = "找回码不能与登录密码相同"; return@btn }
            prefs.edit().putString("username", user.text.toString().trim().ifBlank { "admin" })
                .putString("password_hash", hash(p.text.toString()))
                .putString("recovery_code_hash", hash(recovery)).apply()
            AlertDialog.Builder(this).setTitle("请保存密码找回码")
                .setMessage("找回码：\n\n" + recovery + "\n\n请抄写并保存在安全的地方。忘记密码时需要使用此找回码；为安全起见，本应用不会再次显示完整找回码。")
                .setCancelable(false).setPositiveButton("已保存，进入相册") { _, _ -> home() }.show()
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
        root.addView(View(this).apply { setBackgroundColor(0x220C2148) }, FrameLayout.LayoutParams(-1, -1))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(28), dp(24), dp(28))
            background = bg(0x990C2148.toInt(), 26); elevation = dp(10).toFloat()
        }
        card.addView(text("◈", 42f, blue, true).apply { gravity = Gravity.CENTER })
        card.addView(text("私牍空间", 29f, Color.WHITE, true).apply { gravity = Gravity.CENTER }); gap(card, 5)
        card.addView(text("珍藏每一份美好回忆", 14f, Color.WHITE).apply { gravity = Gravity.CENTER }); gap(card, 26)
        val user = field("用户名", transparent = true).apply { setText(prefs.getString("username", "admin")) }
        val p = field("登录密码", true, true)
        addField(card, user); addField(card, p)
        card.addView(btn("登 录", true) {
            if (user.text.toString().trim() == prefs.getString("username", "admin") && hash(p.text.toString()) == prefs.getString("password_hash", "")) home()
            else p.error = "用户名或密码不正确"
        })
        gap(card, 8)
        val links = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        links.addView(text("忘记密码？", 13f, Color.WHITE).apply {
            setPadding(dp(2), dp(8), dp(12), dp(8))
            setOnClickListener { forgotPassword() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        links.addView(text("本地私密存储", 12f, Color.WHITE).apply { gravity = Gravity.CENTER })
        card.addView(links)
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
        val all = items().sortedByDescending { it.time }
        val fm = folders()
        if (folder != "root") { folderScreen(fm[folder] ?: "文件夹", all.filter { it.folder == folder }); return }
        val c = col()
        c.setPadding(dp(18), dp(18), dp(18), dp(18))
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("私牍空间", 26f, ink, true))
        titles.addView(text("珍藏资料 · 私密文件", 12f, muted))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(btn("⌕ 搜索") { searchItems() })
        header.addView(btn("＋") { showAddMenu() }, LinearLayout.LayoutParams(dp(54), dp(48)).apply { leftMargin = dp(8) })
        c.addView(header); gap(c, 18)
        val cover = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(38,133,239), Color.rgb(93,91,238))).apply { cornerRadius = dp(22).toFloat() }
            elevation = dp(3).toFloat()
        }
        cover.addView(text("私牍空间", 19f, Color.WHITE, true)); gap(cover, 5)
        gap(cover, 16)
        val summary = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val photos = all.count { it.mime.startsWith("image/") }
        val videos = all.count { it.mime.startsWith("video/") }
        summary.addView(summaryCell("▧", photos.toString(), "张照片"), LinearLayout.LayoutParams(0, -2, 1f))
        summary.addView(summaryCell("▶", videos.toString(), "个视频"), LinearLayout.LayoutParams(0, -2, 1f))
        summary.addView(summaryCell("▣", fm.size.toString(), "个文件夹"), LinearLayout.LayoutParams(0, -2, 1f))
        cover.addView(summary); cover.setOnClickListener { folder = "root"; home() }
        c.addView(cover); gap(c, 22)
        val folderHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        folderHeader.addView(text("我的文件夹", 18f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        folderHeader.addView(btn("多选删除") { multiDeleteFolders() })
        folderHeader.addView(btn("＋ 新建") { createFolder() })
        c.addView(folderHeader); gap(c, 10)
        if (fm.isEmpty()) {
            val emptyFolder = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(16), dp(14), dp(16)); background = bg(Color.WHITE, 18) }
            emptyFolder.addView(text("📁", 27f), LinearLayout.LayoutParams(dp(48), dp(46)))
            val et = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            et.addView(text("创建你的第一个文件夹", 14f, ink, true))
            emptyFolder.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
            emptyFolder.setOnClickListener { createFolder() }; c.addView(emptyFolder)
        } else {
            val fg = GridLayout(this).apply { columnCount = 2 }
            fm.entries.forEachIndexed { index, entry ->
                val count = all.count { it.folder == entry.key }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(13), dp(13), dp(13))
                    background = bg(if (index % 3 == 0) 0xFFE8F3FF.toInt() else if (index % 3 == 1) 0xFFF0ECFF.toInt() else 0xFFFFF1E5.toInt(), 18)
                    setOnClickListener { folder = entry.key; home() }
                    setOnLongClickListener { folderMenu(entry.key, entry.value); true }
                }
                card.addView(text(if (index % 3 == 0) "🏞" else if (index % 3 == 1) "📷" else "📁", 26f)); gap(card, 8)
                card.addView(text(entry.value, 14f, ink, true)); card.addView(text(count.toString() + " 个文件", 12f, muted))
                val lp = GridLayout.LayoutParams().apply { width = 0; height = dp(116); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(3), dp(3), dp(3), dp(3)) }
                fg.addView(card, lp)
            }
            c.addView(fg)
        }
        gap(c, 20)
        val fileHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fileHeader.addView(text(if (folder == "root") "最近添加" else (fm[folder] ?: "文件列表"), 18f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (folder != "root") {
            fileHeader.addView(btn("‹ 返回") { folder = "root"; home() })
            fileHeader.addView(btn("⌂ 首页") { folder = "root"; home() })
        }
        fileHeader.addView(btn(if (listMode) "▦ 缩略图" else "☷ 文件列表") { listMode = !listMode; home() })
        fileHeader.addView(btn("多选删除") { multiDelete(visible) })
        c.addView(fileHeader); gap(c, 10)
        val visible = if (folder == "root") all else all.filter { it.folder == folder }
        if (visible.isEmpty()) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(16), dp(30), dp(16), dp(30)); background = bg(Color.WHITE, 20) }
            empty.addView(text("▧", 42f, blue, true).apply { gravity = Gravity.CENTER }); gap(empty, 8)
            empty.addView(text("还没有媒体文件", 16f, ink, true).apply { gravity = Gravity.CENTER })
            empty.addView(text("点击右上角 + 导入照片或视频", 12f, muted).apply { gravity = Gravity.CENTER })
            c.addView(empty)
        } else {
            val grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
            visible.take(60).forEach { item ->
                val lp = GridLayout.LayoutParams().apply { width = 0; height = dp(126); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(3), dp(3), dp(3), dp(3)) }
                grid.addView(thumbnailTile(item), lp)
            }
            c.addView(grid)
            if (visible.size > 60) { gap(c, 8); c.addView(text("显示最近 60 个文件，共 " + visible.size + " 个", 12f, muted)) }
        }
        gap(c, 18)
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = bg(Color.WHITE, 18); setPadding(dp(6), dp(7), dp(6), dp(7)) }
        nav.addView(navItem("▦", "相册", true) { folder = "root"; home() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(navItem("▣", "文件夹", false) { createFolder() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(navItem("◉", "发现", false) { showDiscovery() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(navItem("●", "我的", false) { settings() }, LinearLayout.LayoutParams(0, dp(54), 1f))
        c.addView(nav); page(c)
    }

    private fun summaryCell(symbol: String, number: String, label: String): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(symbol + "  " + number, 19f, Color.WHITE, true)); box.addView(text(label, 11f, 0xFFEAF2FF.toInt()))
        return box
    }

    private fun navItem(symbol: String, label: String, selected: Boolean, action: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = if (selected) bg(0xFFEAF2FF.toInt(), 12) else ColorDrawable(Color.TRANSPARENT)
            addView(text(symbol, 19f, if (selected) blue else muted, true).apply { gravity = Gravity.CENTER })
            addView(text(label, 11f, if (selected) blue else muted, selected).apply { gravity = Gravity.CENTER })
            setOnClickListener { action() }
        }

    private fun showAddMenu() {
        AlertDialog.Builder(this).setTitle("添加内容").setItems(arrayOf("导入照片 / 视频", "导入 PDF / TXT / HTML 等文档", "新建文件夹")) { _, which ->
            when (which) { 0 -> importer.launch(arrayOf("image/*", "video/*")); 1 -> importer.launch(arrayOf("application/pdf", "text/plain", "text/html", "application/*", "text/*")); else -> createFolder() }
        }.show()
    }

    private fun searchItems() {
        val input = field("输入文件名关键词")
        AlertDialog.Builder(this).setTitle("搜索相册").setView(input).setNegativeButton("取消", null)
            .setPositiveButton("搜索") { _, _ ->
                val q = input.text.toString().trim()
                showSearchResults(q, items().filter { it.name.contains(q, true) })
            }.show()
    }

    private fun showSearchResults(query: String, found: List<Item>) {
        val c = col()
        c.addView(text("搜索结果", 24f, ink, true)); gap(c, 5)
        c.addView(text("“" + query + "” · " + found.size + " 个结果", 13f, muted)); gap(c, 12)
        if (found.isEmpty()) c.addView(text("没有找到匹配的文件", 14f, muted))
        else {
            val grid = GridLayout(this).apply { columnCount = 3 }
            found.forEach { item ->
                val lp = GridLayout.LayoutParams().apply { width = 0; height = dp(126); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(3), dp(3), dp(3), dp(3)) }
                grid.addView(thumbnailTile(item), lp)
            }
            c.addView(grid)
        }
        c.addView(btn("返回相册", true) { home() }); page(c)
    }

    private fun showDiscovery() {
        val c = col()
        c.addView(text("发现", 25f, ink, true)); gap(c, 8)
        c.addView(text("让回忆更有条理", 15f, muted)); gap(c, 18)
        listOf(Triple("📷", "照片与视频", "所有导入内容均保存在应用私有空间"),
            Triple("📁", "自定义文件夹", "自定义文件夹管理"),
            Triple("🔒", "隐私保护", "登录密码保护，不会自动上传云端")).forEach { (icon, title, desc) ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; background = bg(Color.WHITE, 16); setPadding(dp(14), dp(14), dp(14), dp(14)) }
            row.addView(text(icon, 26f), LinearLayout.LayoutParams(dp(48), dp(48)))
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(text(title, 15f, ink, true)); details.addView(text(desc, 12f, muted))
            row.addView(details); c.addView(row); gap(c, 10)
        }
        c.addView(btn("返回相册", true) { home() }); page(c)
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
            } else {
                setBackgroundColor(0xFFE8EFFA.toInt())
                setImageDrawable(null)
            }
        }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        if (!item.mime.startsWith("image/") && !item.mime.startsWith("video/")) {
            val doc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xFFE8EFFA.toInt()) }
            val ext = item.name.substringAfterLast('.', "FILE").uppercase().take(5)
            doc.addView(text(if (item.mime == "application/pdf") "▤" else "▧", 34f, blue, true).apply { gravity = Gravity.CENTER })
            doc.addView(text(ext, 12f, ink, true).apply { gravity = Gravity.CENTER })
            frame.addView(doc, FrameLayout.LayoutParams(-1, -1))
        }
        if (item.mime.startsWith("video/")) {
            val badge = TextView(this).apply {
                text = "▶ 视频"; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                background = bg(0x99000000.toInt(), 9); setPadding(dp(7), dp(4), dp(7), dp(4))
            }
            frame.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(6), 0, 0, dp(6)) })
        }
        frame.setOnClickListener { openItem(item) }
        frame.setOnLongClickListener {
            AlertDialog.Builder(this).setTitle(item.name).setItems(arrayOf("查看", "重命名", "移动到文件夹", "删除")) { _, which ->
                when (which) {
                    0 -> openItem(item)
                    1 -> renameItem(item)
                    2 -> moveItem(item)
                    3 -> AlertDialog.Builder(this).setTitle("删除文件？")
                        .setMessage("确定从私牍空间中永久删除此文件吗？此操作不会删除系统相册中的原图。")
                        .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                            file.delete(); saveItems(items().filterNot { it.id == item.id }); home()
                        }.show()
                }
            }.show()
            true
        }
        return frame
    }

    private fun renameItem(item: Item) {
        val input = field("文件名").apply { setText(item.name); selectAll() }
        AlertDialog.Builder(this).setTitle("重命名文件").setView(input)
            .setNegativeButton("取消", null).setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { toast("文件名不能为空"); return@setPositiveButton }
                saveItems(items().map { if (it.id == item.id) it.copy(name = name) else it })
                home()
            }.show()
    }

    private fun moveItem(item: Item) {
        val fm = folders()
        val names = listOf("全部文件（根目录）") + fm.values.toList()
        AlertDialog.Builder(this).setTitle("移动到文件夹").setItems(names.toTypedArray()) { _, which ->
            val target = if (which == 0) "root" else fm.keys.elementAt(which - 1)
            saveItems(items().map { if (it.id == item.id) it.copy(folder = target) else it })
            folder = target
            toast("文件已移动")
            home()
        }.setNegativeButton("取消", null).show()
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

    private fun multiDeleteFolders() {
        val fm = folders()
        if (fm.isEmpty()) { toast("没有可删除的文件夹"); return }
        val ids = fm.keys.toList()
        val names = ids.map { fm[it] ?: it }.toTypedArray()
        val checked = BooleanArray(ids.size)
        AlertDialog.Builder(this).setTitle("多选删除文件夹")
            .setMultiChoiceItems(names, checked) { _, which, value -> checked[which] = value }
            .setNegativeButton("取消", null).setPositiveButton("下一步") { _, _ ->
                val chosen = ids.filterIndexed { i, _ -> checked[i] }.toSet()
                if (chosen.isEmpty()) { toast("没有选择文件夹"); return@setPositiveButton }
                val options = arrayOf("删除文件夹并保留文件（移到首页）", "删除文件夹及其中所有文件")
                AlertDialog.Builder(this).setTitle("确认删除 " + chosen.size + " 个文件夹")
                    .setSingleChoiceItems(options, 0, null)
                    .setNegativeButton("取消", null).setPositiveButton("确认删除") { d, _ ->
                        val list = items()
                        val removeFiles = (d as? AlertDialog)?.listView?.checkedItemPosition == 1
                        if (removeFiles) {
                            list.filter { it.folder in chosen }.forEach { File(filesDir, it.path).delete() }
                            saveItems(list.filterNot { it.folder in chosen })
                        } else saveItems(list.map { if (it.folder in chosen) it.copy(folder = "root") else it })
                        val remaining = folders().filterKeys { it !in chosen }
                        saveFolders(remaining)
                        if (folder in chosen) folder = "root"
                        home()
                    }.show()
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
        c.setPadding(dp(18), dp(22), dp(18), dp(26))
        c.addView(text("设置", 27f, ink, true))
        c.addView(text("个性化你的私密相册", 13f, muted)); gap(c, 20)
        c.addView(text("账号与安全", 14f, muted, true)); gap(c, 8)
        c.addView(settingRow("🔐", "修改密码", "定期更新登录密码") { changePassword() }); gap(c, 8)
        c.addView(settingRow("🗝", "设置找回码", "忘记密码时用于验证身份") { configureRecovery() }); gap(c, 8)
        c.addView(settingRow("🖼", "更换登录背景", "使用你喜欢的图片") { bgPicker.launch(arrayOf("image/*")) }); gap(c, 18)
        c.addView(text("存储与隐私", 14f, muted, true)); gap(c, 8)
        c.addView(settingRow("🛡", "本地隐私保护", "媒体仅保存在应用私有目录") {
            AlertDialog.Builder(this).setTitle("本地隐私保护")
                .setMessage("导入的照片和视频会复制到私牍空间的私有目录，与系统相册独立保存。从系统相册删除原图不会删除这里的副本。卸载 App 或清除 App 数据会删除本地文件，请提前备份。")
                .setPositiveButton("知道了", null).show()
        }); gap(c, 8)
        c.addView(settingRow("ℹ", "关于私牍空间", "版本 1.3.0 · 私密本地相册") {
            AlertDialog.Builder(this).setTitle("私牍空间")
                .setMessage("珍藏美好 · 私密相册\n\n照片与视频在本机独立保存，不会自动上传云端。请妥善保管登录密码和找回码，并注意备份重要文件。")
                .setPositiveButton("确定", null).show()
        }); gap(c, 18)
        c.addView(btn("返回相册", true) { folder = "root"; home() }); gap(c, 10)
        c.addView(btn("退出登录") { login() })
        page(c)
    }

    private fun settingRow(symbol: String, title: String, subtitle: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(13), dp(12), dp(13))
            background = bg(Color.WHITE, 16)
        }
        row.addView(text(symbol, 24f, blue, true), LinearLayout.LayoutParams(dp(48), dp(46)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(title, 15f, ink, true))
        info.addView(text(subtitle, 12f, muted))
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text("›", 25f, muted))
        row.setOnClickListener { action() }
        return row
    }

    private fun recoveryCode(): String = prefs.getString("recovery_code_plain", null) ?: ""

    private fun configureRecovery() {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val current = field("当前登录密码", true)
        val custom = field("自定义新找回码（至少 6 位）")
        addField(c, current); addField(c, custom)
        val dialog = AlertDialog.Builder(this).setTitle("设置找回密码凭据")
            .setMessage("为保护相册隐私，需要先验证当前密码。请输入你希望使用的找回码，请妥善保存。")
            .setView(c).setNegativeButton("取消", null).setPositiveButton("验证并生成", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (hash(current.text.toString()) != prefs.getString("password_hash", "")) {
                    current.error = "当前密码不正确"; return@setOnClickListener
                }
                val code = custom.text.toString().trim().uppercase()
                if (code.length < 6) { custom.error = "找回码至少 6 位"; return@setOnClickListener }
                if (code.equals(current.text.toString(), true)) { custom.error = "找回码不能与登录密码相同"; return@setOnClickListener }
                prefs.edit().putString("recovery_code_hash", hash(code)).apply()
                dialog.dismiss()
                AlertDialog.Builder(this).setTitle("请保存密码找回码")
                    .setMessage("找回码：\n\n" + code + "\n\n请抄写并保存在安全的地方。它只用于忘记密码时重设登录密码。为安全起见，本应用不会再次显示完整找回码。")
                    .setPositiveButton("已保存") { _, _ -> settings() }.show()
            }
        }
        dialog.show()
    }

    private fun forgotPassword() {
        if (prefs.getString("recovery_code_hash", null).isNullOrBlank()) {
            AlertDialog.Builder(this).setTitle("尚未设置找回码")
                .setMessage("此账号还没有密码找回码。为保护相册隐私，不能仅凭用户名直接重设密码。如果你仍能登录，请进入“我的 → 设置找回码”先行设置；如果已忘记密码且未设置找回码，本应用无法验证账号所有权。")
                .setPositiveButton("知道了", null).show()
            return
        }
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val user = field("用户名").apply { setText(prefs.getString("username", "admin")) }
        val code = field("密码找回码")
        val next = field("新密码（至少 6 位）", true)
        val again = field("确认新密码", true)
        addField(c, user); addField(c, code); addField(c, next); addField(c, again)
        val dialog = AlertDialog.Builder(this).setTitle("找回登录密码").setView(c)
            .setNegativeButton("取消", null).setPositiveButton("重设密码", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (user.text.toString().trim() != prefs.getString("username", "admin")) {
                    user.error = "用户名不正确"; return@setOnClickListener
                }
                if (hash(code.text.toString().trim().uppercase()) != prefs.getString("recovery_code_hash", "")) {
                    code.error = "找回码不正确"; return@setOnClickListener
                }
                if (next.text.length < 6) { next.error = "密码至少 6 位"; return@setOnClickListener }
                if (next.text.toString() != again.text.toString()) { again.error = "两次密码不一致"; return@setOnClickListener }
                prefs.edit().putString("password_hash", hash(next.text.toString())).apply()
                dialog.dismiss()
                toast("密码已重设，请重新登录")
                login()
            }
        }
        dialog.show()
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
        var mime = contentResolver.getType(uri) ?: "application/octet-stream"
        var name = "文件"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        val ext = name.substringAfterLast('.', "").takeIf { it.length in 1..8 }?.let { "." + it } ?: ""
        if (mime == "application/octet-stream") mime = when (ext.lowercase()) { ".pdf" -> "application/pdf"; ".txt" -> "text/plain"; ".html", ".htm" -> "text/html"; else -> "application/octet-stream" }
        val id = UUID.randomUUID().toString()
        val rel = "private_media/" + id + ext
        contentResolver.openInputStream(uri)?.use { input -> File(filesDir, rel).outputStream().use { output -> input.copyTo(output) } } ?: return false
        saveItems(items() + Item(id, name, rel, mime, folder, System.currentTimeMillis()))
        true
        } catch (_: Exception) { false }
    }

    @Suppress("DEPRECATION")
    private fun openItem(item: Item) {
        val file = File(filesDir, item.path)
        if (!file.exists()) { toast("文件不存在"); return }
        if (item.mime.startsWith("video/")) {
            viewingFullscreenImage = false
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
            val v = VideoView(this)
            v.setVideoURI(Uri.fromFile(file)); v.setMediaController(MediaController(this))
            root.addView(v, LinearLayout.LayoutParams(-1, -1))
            v.setOnPreparedListener { v.start() }
            setContentView(root)
        } else if (item.mime.startsWith("image/")) {
            viewingFullscreenImage = true
            window.setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            val image = ImageView(this).apply {
                setBackgroundColor(Color.BLACK); scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(BitmapFactory.decodeFile(file.absolutePath)); contentDescription = item.name
            }
            setContentView(image)
        } else {
            viewingFullscreenImage = false
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            when {
                item.mime == "text/plain" || item.name.endsWith(".txt", true) -> showTextDocument(item, file, false)
                item.mime == "text/html" || item.name.endsWith(".html", true) || item.name.endsWith(".htm", true) -> showTextDocument(item, file, true)
                item.mime == "application/pdf" || item.name.endsWith(".pdf", true) -> showPdfDocument(file, item.name)
                else -> {
                    val c = col()
                    c.addView(text(item.name, 20f, ink, true)); gap(c, 10)
                    c.addView(text("暂不支持直接预览此文件格式。", 14f, muted)); gap(c, 12)
                    c.addView(btn("‹ 返回") { home() })
                    c.addView(btn("⌂ 返回首页", true) { folder = "root"; home() })
                    page(c)
                }
            }
        }
    }

    private fun folderScreen(title: String, visible: List<Item>) {
        val c = col()
        c.setPadding(dp(16), dp(12), dp(16), dp(18))
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(btn("‹ 返回") { folder = "root"; home() })
        bar.addView(text(title, 19f, ink, true), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        bar.addView(btn("⌂ 首页") { folder = "root"; home() })
        c.addView(bar); gap(c, 14)
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(text(visible.size.toString() + " 个文件", 13f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(btn(if (listMode) "▦ 缩略图" else "☷ 文件列表") { listMode = !listMode; folderScreen(title, visible) })
        actions.addView(btn("多选删除") { multiDelete(visible) })
        c.addView(actions); gap(c, 10)
        if (visible.isEmpty()) {
            c.addView(text("此文件夹暂无文件", 15f, muted).apply { gravity = Gravity.CENTER })
        } else if (listMode) {
            visible.forEach { c.addView(documentListRow(it)); gap(c, 6) }
        } else {
            val grid = GridLayout(this).apply { columnCount = 3; alignmentMode = GridLayout.ALIGN_BOUNDS; useDefaultMargins = false }
            visible.forEach { item ->
                val lp = GridLayout.LayoutParams().apply { width = 0; height = dp(126); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); rowSpec = GridLayout.spec(GridLayout.UNDEFINED); setMargins(dp(3), dp(3), dp(3), dp(3)) }
                grid.addView(thumbnailTile(item), lp)
            }
            c.addView(grid)
        }
        c.addView(btn("返回首页", true) { folder = "root"; home() })
        page(c)
    }

    private fun documentListRow(item: Item): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); background = bg(Color.WHITE, 14) }
        row.addView(text(if (item.mime == "application/pdf") "▤" else if (item.mime.startsWith("image/")) "▧" else if (item.mime.startsWith("video/")) "▶" else "▣", 25f, blue, true), LinearLayout.LayoutParams(dp(44), dp(44)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(item.name, 14f, ink, true)); info.addView(text(item.mime, 11f, muted))
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(btn("打开") { openItem(item) })
        row.setOnClickListener { openItem(item) }
        row.setOnLongClickListener { itemMenu(item); true }
        return row
    }

    private fun itemMenu(item: Item) {
        AlertDialog.Builder(this).setTitle(item.name).setItems(arrayOf("打开", "删除")) { _, which ->
            if (which == 0) openItem(item) else AlertDialog.Builder(this).setTitle("删除文件？")
                .setMessage("确定永久删除此文件吗？").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                    File(filesDir, item.path).delete(); saveItems(items().filterNot { it.id == item.id }); home()
                }.show()
        }.show()
    }

    private fun multiDelete(visible: List<Item>) {
        if (visible.isEmpty()) { toast("当前没有可删除的文件"); return }
        val names = visible.map { it.name }.toTypedArray()
        val checked = BooleanArray(visible.size)
        AlertDialog.Builder(this).setTitle("多选删除文件")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton("取消", null).setPositiveButton("删除所选") { _, _ ->
                val ids = visible.filterIndexed { i, _ -> checked[i] }.map { it.id }.toSet()
                if (ids.isEmpty()) { toast("没有选择文件"); return@setPositiveButton }
                AlertDialog.Builder(this).setTitle("确认删除")
                    .setMessage("将永久删除选中的 " + ids.size + " 个文件。")
                    .setNegativeButton("取消", null).setPositiveButton("确认删除") { _, _ ->
                        items().filter { it.id in ids }.forEach { File(filesDir, it.path).delete() }
                        saveItems(items().filterNot { it.id in ids }); home()
                    }.show()
            }.show()
    }

    private fun showPdfDocument(file: File, name: String) {
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            val c = col()
            c.addView(text(name, 20f, ink, true)); gap(c, 8)
            c.addView(btn("‹ 返回") { renderer.close(); descriptor.close(); home() })
            c.addView(btn("⌂ 返回首页") { renderer.close(); descriptor.close(); folder = "root"; home() }); gap(c, 10)
            for (i in 0 until renderer.pageCount) {
                val pg = renderer.openPage(i)
                val bitmap = android.graphics.Bitmap.createBitmap(pg.width * 2, pg.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                pg.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pg.close()
                c.addView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            }
            page(c)
        } catch (_: Exception) { toast("PDF 打开失败"); home() }
    }

    private fun showTextDocument(item: Item, file: File, html: Boolean) {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(12)); setBackgroundColor(Color.WHITE) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(btn("‹ 返回") { home() })
        bar.addView(text(item.name, 15f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(btn("⌂ 首页") { folder = "root"; home() })
        c.addView(bar)
        if (html) {
            val web = WebView(this).apply {
                setBackgroundColor(Color.WHITE); settings.javaScriptEnabled = false; webViewClient = WebViewClient()
                loadDataWithBaseURL("file://" + file.parentFile?.absolutePath + "/", file.readText(Charsets.UTF_8), "text/html", "UTF-8", null)
            }
            c.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val body = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "无法读取此文本文件" }
            val tv = TextView(this).apply { text = body; textSize = 16f; setTextColor(Color.BLACK); setTextIsSelectable(true); gravity = Gravity.TOP; setPadding(dp(4), dp(12), dp(4), dp(12)) }
            val scroll = ScrollView(this); scroll.addView(tv); c.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(c)
    }

    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

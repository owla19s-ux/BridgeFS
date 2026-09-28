package com.owla19s.bridgefs

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.util.TypedValue
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("bridgefs", 0) }
    private val rootsKey = "root_paths"
    private val onboardingKey = "first_run_completed"

    private enum class Screen { MAIN, SETTINGS, HELP, ONBOARDING }

    private var screen = Screen.MAIN
    private var currentPath = File("/storage/emulated/0")
    private var firstResume = true

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = resources.getColor(R.color.bridgefs_surface)
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        screen = if (prefs.getBoolean(onboardingKey, false)) Screen.MAIN else Screen.ONBOARDING
        render()
        autoStartIfNeeded(screen == Screen.MAIN)
    }

    override fun onResume() {
        super.onResume()
        if (!firstResume) {
            if (screen == Screen.MAIN || screen == Screen.SETTINGS) render()
            autoStartIfNeeded(false)
        }
        firstResume = false
    }

    override fun onBackPressed() {
        when (screen) {
            Screen.MAIN -> {
                val storage = File("/storage/emulated/0")
                if (normalizedPath(currentPath.absolutePath) != normalizedPath(storage.absolutePath)) {
                    currentPath.parentFile?.let {
                        currentPath = it
                        render()
                    }
                } else {
                    super.onBackPressed()
                }
            }
            Screen.SETTINGS, Screen.HELP -> {
                screen = Screen.MAIN
                render()
            }
            Screen.ONBOARDING -> Unit
        }
    }

    private fun roots(): MutableList<String> {
        val saved = prefs.getStringSet(rootsKey, null)?.toMutableList() ?: mutableListOf()
        val current = prefs.getString("root_path", null)
        if (!current.isNullOrBlank() && !saved.contains(current)) saved.add(current)
        return saved.sorted().toMutableList()
    }

    private fun saveRoots(list: List<String>) {
        prefs.edit().putStringSet(rootsKey, list.toSet()).apply()
    }

    private fun normalizedPath(path: String): String =
        runCatching { File(path).canonicalPath.trimEnd('/') }
            .getOrElse { File(path).absolutePath.trimEnd('/') }

    private fun isRootAdded(path: String): Boolean =
        roots().any { normalizedPath(it).equals(normalizedPath(path), ignoreCase = true) }

    private fun isProtectedWorkspace(path: String): Boolean {
        val candidate = normalizedPath(path).replace('\\', '/').lowercase(Locale.ROOT)
        val storageRoot = normalizedPath(Environment.getExternalStorageDirectory().absolutePath)
            .replace('\\', '/').lowercase(Locale.ROOT)
        if (candidate == storageRoot) return true
        val segments = candidate.split('/').filter { it.isNotEmpty() }
        return segments.any { it == "android" } ||
            listOf("/android/data", "/android/obb", "/android/media").any { candidate.contains(it) }
    }

    private fun addRoot(path: String, onFinished: () -> Unit = {}) {
        val normalized = normalizedPath(path)
        if (isProtectedWorkspace(normalized)) {
            Toast.makeText(this, "此目录属于系统受保护区域，无法作为工作区", Toast.LENGTH_LONG).show()
            return
        }

        val confirmAndAdd = {
            val list = roots()
            if (list.none { normalizedPath(it).equals(normalized, ignoreCase = true) }) {
                list.add(normalized)
                saveRoots(list)
            }
            prefs.edit().putString("root_path", normalized).apply()
            onFinished()
        }

        if (File(normalized).name.equals("Download", ignoreCase = true)) {
            AlertDialog.Builder(this)
                .setTitle("请确认选择下载目录")
                .setMessage("该目录常用于存储系统下载文件。AI 在此处创建或修改文件可能会与常规下载内容混淆。确认将此处设为工作区吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认") { _, _ -> confirmAndAdd() }
                .show()
        } else {
            confirmAndAdd()
        }
    }

    private fun toggleRoot(path: String) {
        val normalized = normalizedPath(path)
        val list = roots()
        val existing = list.firstOrNull { normalizedPath(it).equals(normalized, ignoreCase = true) }

        if (existing != null) {
            list.remove(existing)
            saveRoots(list)
            if (normalizedPath(prefs.getString("root_path", "").orEmpty())
                    .equals(normalized, ignoreCase = true)
            ) {
                val editor = prefs.edit()
                val next = list.firstOrNull()
                if (next == null) editor.remove("root_path") else editor.putString("root_path", next)
                editor.apply()
            }
        } else {
            addRoot(normalized)
        }
    }

    private fun autoStartIfNeeded(showPermissionHint: Boolean) {
        if (!prefs.getBoolean("auto_show_overlay", true) || FileBridgeService.running) return
        if (!Settings.canDrawOverlays(this)) {
            if (showPermissionHint) {
                Toast.makeText(this, "请先在系统设置开启悬浮窗权限；ColorOS 后台自启动也需允许。", Toast.LENGTH_LONG).show()
            }
            return
        }
        val workspace = prefs.getString("root_path", null)
        if (workspace.isNullOrBlank() || !File(workspace).isDirectory || isProtectedWorkspace(workspace)) {
            if (showPermissionHint) {
                Toast.makeText(this, "请先选择并激活项目目录，再自动显示悬浮窗。", Toast.LENGTH_LONG).show()
            }
            return
        }
        runCatching {
            ContextCompatCompat.startService(this, Intent(this, FileBridgeService::class.java))
        }.onFailure {
            Toast.makeText(this, "自动显示悬浮窗失败，请检查系统的悬浮窗/自启动限制。", Toast.LENGTH_LONG).show()
        }
    }

    private fun render() {
        when (screen) {
            Screen.MAIN -> renderMainPage()
            Screen.SETTINGS -> renderSettingsPage()
            Screen.HELP -> renderHelpPage()
            Screen.ONBOARDING -> renderOnboardingPage()
        }
    }

    private fun basePage(content: LinearLayout, withBottom: Boolean = true): ScrollView {
        val page = ScrollView(this).apply { isFillViewport = true }
        ViewCompat.setOnApplyWindowInsetsListener(page) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
        }
        outer.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        if (withBottom) {
            val nav = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            val home = pageButton("主页") {
                screen = Screen.MAIN
                currentPath = File("/storage/emulated/0")
                render()
            }
            val back = pageButton("返回") { onBackPressedDispatcherAction() }
            val storage = File("/storage/emulated/0")
            val canBack = screen != Screen.MAIN ||
                normalizedPath(currentPath.absolutePath) != normalizedPath(storage.absolutePath)
            back.isEnabled = canBack
            back.alpha = if (canBack) 1f else 0.45f

            nav.addView(home, LinearLayout.LayoutParams(0, dp(42), 1f))
            nav.addView(back, LinearLayout.LayoutParams(0, dp(42), 1f).also { it.marginStart = dp(8) })
            outer.addView(nav, LinearLayout.LayoutParams(-1, dp(42)).also { it.topMargin = dp(8) })
        }

        page.addView(outer)
        return page
    }

    private fun onBackPressedDispatcherAction() {
        when (screen) {
            Screen.MAIN -> {
                val storage = File("/storage/emulated/0")
                if (normalizedPath(currentPath.absolutePath) != normalizedPath(storage.absolutePath)) {
                    currentPath.parentFile?.let { currentPath = it; render() }
                }
            }
            Screen.SETTINGS, Screen.HELP -> {
                screen = Screen.MAIN
                render()
            }
            Screen.ONBOARDING -> Unit
        }
    }

    private fun renderMainPage() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = "BridgeFS"
            textSize = 24f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        titleRow.addView(pageButton("⚙ 设置") {
            screen = Screen.SETTINGS
            render()
        }, LinearLayout.LayoutParams(dp(86), dp(40)))
        content.addView(titleRow)

        val pathView = TextView(this).apply {
            text = currentPath.absolutePath
            textSize = 13f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(resources.getColor(R.color.bridgefs_input_surface), dp(8))
        }
        content.addView(pathView, LinearLayout.LayoutParams(-1, dp(42)).also { it.topMargin = dp(4) })

        val hint = TextView(this).apply {
            text = "勾选文件夹即可设为 BridgeFS 可管理的工作区；点击文件夹进入。"
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
        }
        content.addView(hint, LinearLayout.LayoutParams(-1, dp(30)).also { it.topMargin = dp(4) })

        val files = currentPath.listFiles()
            ?.filter { !it.isHidden && (!it.isDirectory || !isProtectedWorkspace(it.absolutePath)) }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.getDefault()) })
            .orEmpty()

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false
            clipToPadding = false
            adapter = FileBrowserAdapter(files)
        }
        val rowHeight = dp(48)
        val listHeight = (rowHeight * files.size.coerceAtLeast(1)).coerceAtMost(dp(560))
        content.addView(recycler, LinearLayout.LayoutParams(-1, listHeight).also { it.topMargin = dp(2) })

        if (files.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "此目录为空"
                textSize = 13f
                setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }

        setContentView(basePage(content))
    }

    private fun renderSettingsPage() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = "设置"
            textSize = 24f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        titleRow.addView(TextView(this).apply {
            text = "BridgeFS"
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
        }, LinearLayout.LayoutParams(-2, dp(48)))
        content.addView(titleRow)

        addSectionTitle(content, "文件访问")
        val selected = roots()
        if (selected.isEmpty()) {
            addInfo(content, "尚未选择工作区目录")
        } else {
            selected.forEach { path ->
                val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                row.addView(TextView(this).apply {
                    text = path
                    textSize = 13f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    setTextColor(resources.getColor(R.color.bridgefs_text_primary))
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
                row.addView(pageButton("取消") {
                    toggleRoot(path)
                    render()
                }, LinearLayout.LayoutParams(dp(64), dp(36)))
                content.addView(row)
            }
        }
        content.addView(pageButton("打开主页选择文件夹") {
            screen = Screen.MAIN
            render()
        }, LinearLayout.LayoutParams(-1, dp(40)).also { it.topMargin = dp(6) })

        addSectionTitle(content, "悬浮窗")
        addSettingSwitch(content, "悬浮窗权限",
            if (Settings.canDrawOverlays(this)) "已开启" else "未开启"
        ) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        addSettingSwitch(content, "自动显示悬浮窗",
            if (prefs.getBoolean("auto_show_overlay", true)) "开启" else "关闭"
        ) {
            prefs.edit().putBoolean("auto_show_overlay", !prefs.getBoolean("auto_show_overlay", true)).apply()
            render()
        }
        addInfo(content, "ColorOS 还可能需要允许后台运行、自启动和锁定常驻。")

        addSectionTitle(content, "帮助")
        content.addView(pageButton("操作提示", {
            screen = Screen.HELP
            render()
        }), LinearLayout.LayoutParams(-1, dp(42)))
        content.addView(pageButton("指令帮助", {
            screen = Screen.HELP
            render()
        }), LinearLayout.LayoutParams(-1, dp(42)).also { it.topMargin = dp(6) })

        addSectionTitle(content, "关于")
        addInfo(content, "BridgeFS v0.1.2\nAndroid 本地文件桥 / AI 协作工作台")
        content.addView(TextView(this).apply {
            text = "github.com/owla19s-ux/BridgeFS"
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_accent))
            setPadding(0, dp(4), 0, dp(4))
            setOnClickListener {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/owla19s-ux/BridgeFS")))
                }
            }
        })

        setContentView(basePage(content))
    }

    private fun renderHelpPage() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = "操作提示 / 指令帮助"
            textSize = 22f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        titleRow.addView(TextView(this).apply {
            text = "设置"
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
        }, LinearLayout.LayoutParams(-2, dp(48)))
        content.addView(titleRow)

        addSectionTitle(content, "基本操作")
        addInfo(content,
            "1. 在主页浏览手机存储目录。\n" +
            "2. 点击文件夹进入下一层。\n" +
            "3. 勾选文件夹，将它加入 BridgeFS 可管理工作区。\n" +
            "4. 在设置中开启悬浮窗权限，并按系统要求允许后台运行。\n" +
            "5. 从 AI 对话复制 BridgeFS 指令，再通过悬浮窗执行。"
        )

        addSectionTitle(content, "指令")
        addInfo(content,
            "[list]\n列出当前工作区文件和目录。\n\n" +
            "[read: 文件名]\n读取指定文件内容。\n\n" +
            "[write: 文件名] 内容 [/write]\n创建或覆盖指定文件。\n\n" +
            "[mkdir: 文件夹名]\n创建文件夹。"
        )

        addSectionTitle(content, "注意")
        addInfo(content, "BridgeFS 只会把你勾选的工作区作为可管理目录。涉及系统受保护区域的目录不会作为工作区。")
        setContentView(basePage(content))
    }

    private fun renderOnboardingPage() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        content.addView(TextView(this).apply {
            text = "欢迎使用 BridgeFS"
            textSize = 26f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(-1, dp(52)))

        addInfo(content, "第一次使用只需要完成下面几步。之后可在「设置 → 操作提示」再次查看。")

        addSectionTitle(content, "1. 选择工作区")
        addInfo(content, "进入主页，浏览到你希望 AI 管理的文件夹，勾选它。BridgeFS 会记住已选择的工作区。")

        addSectionTitle(content, "2. 开启悬浮窗")
        addInfo(content, "进入设置，点击「悬浮窗权限」并在系统设置中允许。")

        addSectionTitle(content, "3. 允许后台运行")
        addInfo(content, "OPPO / ColorOS 可能需要额外允许后台运行、自启动，并把 BridgeFS 锁定在最近任务中，避免系统回收。")

        addSectionTitle(content, "4. 从 AI 对话执行指令")
        addInfo(content,
            "从 AI 对话复制 BridgeFS 指令，例如：\n\n" +
            "[list]\n\n" +
            "[write: 测试.txt] BridgeFS目录读写测试 [/write]\n\n" +
            "再通过 BridgeFS 悬浮面板执行。"
        )

        content.addView(pageButton("开始使用") {
            prefs.edit().putBoolean(onboardingKey, true).apply()
            screen = Screen.MAIN
            render()
        }, LinearLayout.LayoutParams(-1, dp(46)).also { it.topMargin = dp(12) })

        setContentView(basePage(content, withBottom = false))
    }

    private fun addSectionTitle(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(-1, dp(32)).also { it.topMargin = dp(12) })
    }

    private fun addInfo(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(0, dp(2), 0, dp(2))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addSettingSwitch(parent: LinearLayout, title: String, status: String, action: () -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = "$title\n$status"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        row.addView(pageButton("管理") { action() }, LinearLayout.LayoutParams(dp(72), dp(36)))
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(52)))
    }

    private inner class FileBrowserAdapter(
        private val files: List<File>
    ) : RecyclerView.Adapter<FileBrowserAdapter.Holder>() {

        inner class Holder(
            val row: ConstraintLayout,
            val icon: TextView,
            val name: TextView,
            val check: CheckBox,
            val arrow: TextView
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = ConstraintLayout(this@MainActivity).apply {
                layoutParams = RecyclerView.LayoutParams(-1, dp(48))
            }
            val icon = TextView(this@MainActivity).apply {
                id = View.generateViewId()
                textSize = 19f
                gravity = Gravity.CENTER
            }
            val name = TextView(this@MainActivity).apply {
                id = View.generateViewId()
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            }
            val check = CheckBox(this@MainActivity).apply {
                id = View.generateViewId()
                isClickable = true
                isFocusable = false
                buttonTintList = ColorStateList.valueOf(resources.getColor(R.color.bridgefs_accent))
            }
            val arrow = TextView(this@MainActivity).apply {
                id = View.generateViewId()
                text = "›"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            }

            row.addView(icon, ConstraintLayout.LayoutParams(dp(34), dp(48)).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            })
            row.addView(check, ConstraintLayout.LayoutParams(dp(52), dp(48)).apply {
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            })
            row.addView(arrow, ConstraintLayout.LayoutParams(dp(34), dp(48)).apply {
                endToStart = check.id
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            })
            row.addView(name, ConstraintLayout.LayoutParams(0, dp(48)).apply {
                startToEnd = icon.id
                endToStart = arrow.id
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                marginStart = dp(2)
                marginEnd = dp(2)
            })

            return Holder(row, icon, name, check, arrow)
        }

        override fun getItemCount(): Int = files.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val file = files[position]
            val isDir = file.isDirectory
            holder.icon.text = if (isDir) "▰" else "▱"
            holder.name.text = file.name
            holder.name.setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            holder.check.visibility = if (isDir) View.VISIBLE else View.INVISIBLE
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = isRootAdded(file.absolutePath)
            holder.check.setOnCheckedChangeListener { _, checked ->
                if (checked != isRootAdded(file.absolutePath)) {
                    toggleRoot(file.absolutePath)
                }
            }
            holder.arrow.visibility = if (isDir) View.VISIBLE else View.INVISIBLE
            holder.row.background = rowRipple(resources.getColor(R.color.bridgefs_surface))
            holder.row.setOnClickListener {
                if (isDir) {
                    currentPath = file
                    render()
                }
            }
            holder.arrow.setOnClickListener {
                if (isDir) {
                    currentPath = file
                    render()
                }
            }
        }
    }

    private fun pageButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(resources.getColor(R.color.bridgefs_button_text))
        isClickable = true
        background = RippleDrawable(
            ColorStateList.valueOf(resources.getColor(R.color.bridgefs_ripple_orange)),
            rounded(resources.getColor(R.color.bridgefs_button_bg), dp(12)),
            null
        )
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
        }

    private fun rowRipple(fill: Int) =
        RippleDrawable(
            ColorStateList.valueOf(resources.getColor(R.color.bridgefs_ripple_gray)),
            GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(8).toFloat()
            },
            null
        )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

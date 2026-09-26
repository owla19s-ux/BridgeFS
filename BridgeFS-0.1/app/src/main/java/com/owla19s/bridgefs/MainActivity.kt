package com.owla19s.bridgefs

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.Gravity
import android.view.WindowManager
import android.util.TypedValue
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.constraintlayout.widget.ConstraintLayout
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("bridgefs", 0) }
    private val rootsKey = "root_paths"
    private var pickerPath = File("/storage/emulated/0")
    private var firstResume = true

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = resources.getColor(R.color.bridgefs_surface)
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        render()
        autoStartIfNeeded(true)
    }

    override fun onResume() {
        super.onResume()
        if (!firstResume) {
            render()
            autoStartIfNeeded(false)
        }
        firstResume = false
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

    private fun activate(path: String) {
        prefs.edit().putString("root_path", path).apply()
        render()
    }

    private fun candidateDirectories(): List<String> {
        val paths = linkedSetOf<String>()
        Environment.getExternalStorageDirectory().listFiles()?.filter { it.isDirectory }?.forEach { paths.add(it.absolutePath) }
        roots().forEach { paths.add(it) }
        return paths.sortedWith(compareBy<String> { File(it).name.lowercase(Locale.getDefault()) }.thenBy { it })
    }

    private fun normalizedPath(path: String): String =
        runCatching { File(path).canonicalPath.trimEnd('/') }.getOrElse { File(path).absolutePath.trimEnd('/') }

    private fun isRootAdded(path: String): Boolean =
        roots().any { normalizedPath(it).equals(normalizedPath(path), ignoreCase = true) }

    private fun isProtectedWorkspace(path: String): Boolean {
        val candidate = normalizedPath(path).replace('\\', '/').lowercase(Locale.ROOT)
        val storageRoot = normalizedPath(Environment.getExternalStorageDirectory().absolutePath)
            .replace('\\', '/').lowercase(Locale.ROOT)
        if (candidate == storageRoot) return true
        val segments = candidate.split('/').filter { it.isNotEmpty() }
        // Block Android itself as well as its protected data/obb/media trees.
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

    private fun toggleRoot(path: String, onFinished: () -> Unit = {}) {
        val normalized = normalizedPath(path)
        val list = roots()
        val existing = list.firstOrNull { normalizedPath(it).equals(normalized, ignoreCase = true) }
        if (existing != null) {
            list.remove(existing)
            saveRoots(list)
            if (normalizedPath(prefs.getString("root_path", "").orEmpty()).equals(normalized, ignoreCase = true)) {
                val editor = prefs.edit()
                val next = list.firstOrNull()
                if (next == null) editor.remove("root_path") else editor.putString("root_path", next)
                editor.apply()
            }
            onFinished()
        } else {
            addRoot(normalized, onFinished)
        }
    }

    private fun setTextSizeFromDimen(view: TextView, dimen: Int) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(dimen))
    }

    private fun autoStartIfNeeded(showPermissionHint: Boolean) {
        if (!prefs.getBoolean("auto_show_overlay", true) || FileBridgeService.running) return
        if (!Settings.canDrawOverlays(this)) {
            if (showPermissionHint) Toast.makeText(this, "请先在系统设置开启悬浮窗权限；ColorOS 后台自启动也需允许。", Toast.LENGTH_LONG).show()
            return
        }
        runCatching { ContextCompatCompat.startService(this, Intent(this, FileBridgeService::class.java)) }
            .onFailure { Toast.makeText(this, "自动显示悬浮窗失败，请检查系统的悬浮窗/自启动限制。", Toast.LENGTH_LONG).show() }
    }

    private fun render() {
        val page = ScrollView(this).apply { isFillViewport = true }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dimen(R.dimen.main_page_padding), dimen(R.dimen.main_page_padding), dimen(R.dimen.main_page_padding), dimen(R.dimen.main_page_padding))
        }
        ViewCompat.setOnApplyWindowInsetsListener(page) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = "BridgeFS"
            setSingleLine(true)
            maxLines = 1
            textSize = 24f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(-2, dp(48)))
        titleRow.addView(TextView(this).apply {
            text = "v0.1.2"
            setSingleLine(true)
            maxLines = 1
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-2, dp(48)).also { it.marginStart = dp(6) })
        box.addView(titleRow)

        box.addView(TextView(this).apply {
            text = "目录"
            textSize = 15f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
        }, LinearLayout.LayoutParams(-1, dp(32)))

        val paths = candidateDirectories()
        val directoryList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false
            setBackgroundColor(resources.getColor(R.color.bridgefs_surface))
            clipToPadding = false
        }
        directoryList.adapter = DirectoryAdapter(paths, onToggle = { path -> toggleRoot(path) { directoryList.adapter?.notifyDataSetChanged() } })
        val rowHeight = resources.getDimensionPixelSize(R.dimen.directory_row_height)
        val listHeight = (rowHeight * paths.size.coerceAtLeast(1)).coerceAtMost(resources.getDimensionPixelSize(R.dimen.directory_list_max_height))
        box.addView(directoryList, LinearLayout.LayoutParams(-1, listHeight).also { it.topMargin = dp(4) })

        val accessRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val accessStatus = TextView(this).apply {
            text = if (Environment.isExternalStorageManager()) "所有文件访问 ✓" else "所有文件访问 ×"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener {
                if (!Environment.isExternalStorageManager()) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        val addDir = mainButton("+ 添加目录") {
            if (!Environment.isExternalStorageManager()) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            else showDirectoryPicker()
        }
        accessRow.addView(accessStatus, LinearLayout.LayoutParams(0, dp(44), 1f))
        accessRow.addView(addDir, LinearLayout.LayoutParams(dp(120), dp(40)).also { it.marginStart = dp(6) })
        box.addView(accessRow, LinearLayout.LayoutParams(-1, dp(44)).also { it.topMargin = dp(8) })

        val overlayRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val overlayStatus = TextView(this).apply {
            text = if (Settings.canDrawOverlays(this@MainActivity)) "悬浮窗权限 ✓" else "悬浮窗权限 ×"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        val autoSwitch = Switch(this).apply {
            text = "自动显示"
            textSize = 12f
            isChecked = prefs.getBoolean("auto_show_overlay", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("auto_show_overlay", checked).apply() }
        }
        val open = mainButton("打开悬浮窗") { startOverlayManually() }
        overlayRow.addView(overlayStatus, LinearLayout.LayoutParams(0, dp(44), 1f))
        overlayRow.addView(autoSwitch, LinearLayout.LayoutParams(0, dp(44), 1f))
        overlayRow.addView(open, LinearLayout.LayoutParams(0, dp(40), 1f))
        box.addView(overlayRow, LinearLayout.LayoutParams(-1, dp(44)).also { it.topMargin = dp(8) })

        val logRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        logRow.addView(TextView(this).apply {
            text = "运行日志 / 崩溃日志"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        val logSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("run_log_enabled", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("run_log_enabled", checked).apply() }
        }
        logRow.addView(logSwitch, LinearLayout.LayoutParams(dp(64), dp(44)))
        box.addView(logRow, LinearLayout.LayoutParams(-1, dp(44)).also { it.topMargin = dp(8) })

        box.addView(TextView(this).apply {
            text = "若软件自动关闭，请检查：\n· 悬浮窗权限\n· 常驻锁定\n· 后台运行允许"
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(0, dp(8), 0, dp(8))
        }, LinearLayout.LayoutParams(-1, dp(64)).also { it.topMargin = dp(8) })
        page.addView(box)
        setContentView(page)
    }

    private fun startOverlayManually() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!Environment.isExternalStorageManager()) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (prefs.getString("root_path", null).isNullOrBlank()) {
            Toast.makeText(this, "请先添加并激活项目目录", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { ContextCompatCompat.startService(this, Intent(this, FileBridgeService::class.java)) }
            .onFailure { Toast.makeText(this, "启动悬浮窗失败，请检查系统权限。", Toast.LENGTH_SHORT).show() }
    }

    private fun showDirectoryPicker() { pickerPath = File("/storage/emulated/0"); DirectoryDialog().show() }

    private inner class DirectoryDialog {
        private val dialog = AlertDialog.Builder(this@MainActivity).create()
        private val container = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(resources.getDimensionPixelSize(R.dimen.directory_dialog_padding), resources.getDimensionPixelSize(R.dimen.directory_dialog_padding), resources.getDimensionPixelSize(R.dimen.directory_dialog_padding), resources.getDimensionPixelSize(R.dimen.directory_dialog_padding))
            background = rounded(resources.getColor(R.color.bridgefs_surface), dp(12))
        }
        private val pathView = TextView(this@MainActivity).apply {
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }

        fun show() {
            dialog.setView(container)
            renderPicker()
            dialog.show()
            dialog.window?.setLayout(resources.getDimensionPixelSize(R.dimen.panel_width), WindowManager.LayoutParams.WRAP_CONTENT)
        }

        private fun renderPicker() {
            container.removeAllViews()
            pathView.text = pickerPath.absolutePath
            container.addView(pathView, LinearLayout.LayoutParams(-1, -2))
            val dirs = pickerPath.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase(Locale.getDefault()) }.orEmpty()
            val maxHeight = resources.getDimensionPixelSize(R.dimen.directory_list_max_height)
            val rowHeight = resources.getDimensionPixelSize(R.dimen.directory_row_height)
            val listHeight = (rowHeight * dirs.size.coerceAtLeast(1)).coerceAtMost(maxHeight)
            val recycler = RecyclerView(this@MainActivity).apply {
                layoutManager = LinearLayoutManager(this@MainActivity)
                isNestedScrollingEnabled = false
                adapter = DirectoryAdapter(dirs.map { it.absolutePath },
                    onToggle = { path -> toggleRoot(path) { renderPicker() } },
                    onNavigate = { path -> pickerPath = File(path); renderPicker() })
                isVerticalScrollBarEnabled = true
                scrollBarSize = dp(2)
            }
            container.addView(recycler, LinearLayout.LayoutParams(-1, listHeight))
            val actions = LinearLayout(this@MainActivity).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
            val back = dialogButton("← 返回") {
                pickerPath.parentFile?.takeIf { it.absolutePath.startsWith("/storage/emulated/0") && it.absolutePath != "/storage/emulated/0" }?.let { pickerPath = it; renderPicker() }
            }
            val select = dialogButton("选择") {
                addRoot(pickerPath.absolutePath) {
                    render()
                    dialog.dismiss()
                }
            }
            actions.addView(back, LinearLayout.LayoutParams(dimen(R.dimen.dialog_button_width), dimen(R.dimen.dialog_button_height)))
            actions.addView(select, LinearLayout.LayoutParams(dimen(R.dimen.dialog_button_width), dimen(R.dimen.dialog_button_height)).also { it.marginStart = dp(8) })
            container.addView(actions, LinearLayout.LayoutParams(-1, dimen(R.dimen.dialog_button_height)).also { it.topMargin = dp(8) })
        }
    }
private inner class DirectoryAdapter(
        private val paths: List<String>,
        private val onToggle: (String) -> Unit,
        private val onNavigate: ((String) -> Unit)? = null
    ) : RecyclerView.Adapter<DirectoryAdapter.Holder>() {
        inner class Holder(
            val row: ConstraintLayout,
            val icon: ImageView,
            val name: TextView,
            val check: CheckBox,
            val arrow: TextView
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = ConstraintLayout(this@MainActivity).apply {
                layoutParams = RecyclerView.LayoutParams(-1, dimen(R.dimen.directory_row_height))
                background = rowRipple(resources.getColor(R.color.bridgefs_surface))
            }
            val icon = ImageView(this@MainActivity).apply {
                id = View.generateViewId()
                setImageResource(R.drawable.ic_folder)
            }
            val name = TextView(this@MainActivity).apply {
                id = View.generateViewId()
                setTextSizeFromDimen(this, R.dimen.directory_item_text_size)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val check = CheckBox(this@MainActivity).apply {
                id = View.generateViewId()
                isClickable = false
                isFocusable = false
                buttonTintList = ColorStateList.valueOf(resources.getColor(R.color.bridgefs_accent))
            }
            val arrow = TextView(this@MainActivity).apply {
                id = View.generateViewId()
                text = "›"
                setTextSizeFromDimen(this, R.dimen.directory_arrow_size)
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            }

            row.addView(icon, ConstraintLayout.LayoutParams(dimen(R.dimen.directory_icon_size), dimen(R.dimen.directory_icon_size)).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                marginStart = dimen(R.dimen.directory_row_padding)
            })
            row.addView(check, ConstraintLayout.LayoutParams(dimen(R.dimen.directory_check_width), dimen(R.dimen.directory_row_height)).apply {
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                marginEnd = dimen(R.dimen.directory_row_padding)
            })
            if (onNavigate != null) {
                row.addView(arrow, ConstraintLayout.LayoutParams(dimen(R.dimen.directory_arrow_width), dimen(R.dimen.directory_row_height)).apply {
                    endToStart = check.id
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    marginEnd = dp(4)
                })
            }
            row.addView(name, ConstraintLayout.LayoutParams(0, -1).apply {
                startToEnd = icon.id
                endToStart = if (onNavigate != null) arrow.id else check.id
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                marginStart = dimen(R.dimen.directory_icon_gap)
                marginEnd = dimen(R.dimen.directory_icon_gap)
            })
            return Holder(row, icon, name, check, arrow)
        }

        override fun getItemCount(): Int = paths.size.coerceAtLeast(1)

        override fun onBindViewHolder(holder: Holder, position: Int) {
            if (paths.isEmpty()) {
                holder.name.text = "暂无可用目录"
                holder.name.setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
                holder.check.visibility = View.GONE
                holder.icon.visibility = View.GONE
                holder.arrow.visibility = View.GONE
                holder.row.setOnClickListener(null)
                return
            }
            val path = paths[position]
            val added = isRootAdded(path)
            holder.name.text = File(path).name.ifBlank { path }
            holder.name.setTextColor(resources.getColor(if (added) R.color.bridgefs_text_secondary else R.color.bridgefs_text_primary))
            holder.check.visibility = View.VISIBLE
            holder.check.isChecked = added
            holder.icon.visibility = View.VISIBLE
            holder.icon.setColorFilter(resources.getColor(if (added) R.color.bridgefs_text_secondary else R.color.bridgefs_accent))
            holder.arrow.visibility = if (onNavigate != null) View.VISIBLE else View.GONE
            holder.row.background = rowRipple(resources.getColor(if (added) R.color.bridgefs_input_surface else R.color.bridgefs_surface))
            holder.row.setOnClickListener { onToggle(path) }
            holder.arrow.setOnClickListener { onNavigate?.invoke(path) }
        }
    }

        private fun mainButton(label:String,onClick:()->Unit)=dialogButton(label,onClick)
    private fun dialogButton(label:String,onClick:()->Unit)=TextView(this).apply{ text=label; textSize=13f; gravity=Gravity.CENTER; setTextColor(resources.getColor(R.color.bridgefs_button_text)); isClickable=true; background=RippleDrawable(ColorStateList.valueOf(resources.getColor(R.color.bridgefs_ripple_orange)),rounded(resources.getColor(R.color.bridgefs_button_bg),dp(12)),null); setOnClickListener{onClick()} }
    private fun rounded(fill:Int,radius:Int)=GradientDrawable().apply{setColor(fill);cornerRadius=radius.toFloat()}
    private fun dimen(id:Int)=resources.getDimensionPixelSize(id)
    private fun rowRipple(fill:Int)=RippleDrawable(ColorStateList.valueOf(resources.getColor(R.color.bridgefs_ripple_gray)),GradientDrawable().apply{setColor(fill);cornerRadius=dimen(R.dimen.directory_row_corner_radius).toFloat()},null)
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}

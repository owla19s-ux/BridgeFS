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

    private fun toggleRoot(path: String) {
        val list = roots()
        if (list.remove(path)) {
            saveRoots(list)
            if (prefs.getString("root_path", null) == path) {
                val editor = prefs.edit()
                val next = list.firstOrNull()
                if (next == null) editor.remove("root_path") else editor.putString("root_path", next)
                editor.apply()
            }
        } else {
            list.add(path)
            saveRoots(list)
            prefs.edit().putString("root_path", path).apply()
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
            textSize = 24f
            setTextColor(resources.getColor(R.color.bridgefs_text_primary))
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        titleRow.addView(TextView(this).apply {
            text = "v0.1.2"
            textSize = 12f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(dp(56), dp(48)))
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
        directoryList.adapter = DirectoryAdapter(paths, onToggle = { path -> toggleRoot(path); directoryList.adapter?.notifyDataSetChanged() })
        val rowHeight = resources.getDimensionPixelSize(R.dimen.directory_row_height)
        val listHeight = (rowHeight * paths.size.coerceAtLeast(1)).coerceAtMost(resources.getDimensionPixelSize(R.dimen.directory_list_max_height))
        box.addView(directoryList, LinearLayout.LayoutParams(-1, listHeight).also { it.topMargin = dp(4) })

        val accessRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val accessStatus = TextView(this).apply {
            text = if (Environment.isExternalStorageManager()) "所有文件访问 ✓" else "所有文件访问 ×"
            textSize = 13f
            setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            gravity = Gravity.CENTER
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
            gravity = Gravity.CENTER
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
            gravity = Gravity.CENTER_VERTICAL
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
                    onToggle = { path -> toggleRoot(path); renderPicker() },
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
                val path = pickerPath.canonicalPath
                if (!roots().contains(path)) toggleRoot(path)
                else prefs.edit().putString("root_path", path).apply()
                render()
                dialog.dismiss()
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
            val row: LinearLayout,
            val icon: ImageView,
            val name: TextView,
            val badge: TextView,
            val check: CheckBox,
            val arrow: TextView
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(resources.getDimensionPixelSize(R.dimen.directory_row_padding), 0, resources.getDimensionPixelSize(R.dimen.directory_row_padding), 0)
                background = rowRipple(resources.getColor(R.color.bridgefs_surface))
            }
            val icon = ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_folder) }
            val name = TextView(this@MainActivity).apply {
                setTextSizeFromDimen(this, R.dimen.directory_item_text_size)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val badge = TextView(this@MainActivity).apply {
                setTextSizeFromDimen(this, R.dimen.directory_badge_text_size)
                setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
                gravity = Gravity.CENTER_VERTICAL
            }
            val check = CheckBox(this@MainActivity).apply {
                isClickable = false
                isFocusable = false
                buttonTintList = ColorStateList.valueOf(resources.getColor(R.color.bridgefs_accent))
            }
            val arrow = TextView(this@MainActivity).apply {
                text = "›"
                setTextSizeFromDimen(this, R.dimen.directory_arrow_size)
                gravity = Gravity.CENTER
                setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
            }
            row.addView(icon, LinearLayout.LayoutParams(dimen(R.dimen.directory_icon_size), dimen(R.dimen.directory_icon_size)).also { it.marginEnd = dimen(R.dimen.directory_icon_gap) })
            row.addView(name, LinearLayout.LayoutParams(0, -1, 1f))
            row.addView(badge, LinearLayout.LayoutParams(-2, -1).also { it.marginEnd = dp(4) })
            row.addView(check, LinearLayout.LayoutParams(dimen(R.dimen.directory_check_width), dimen(R.dimen.directory_row_height)))
            if (onNavigate != null) row.addView(arrow, LinearLayout.LayoutParams(dimen(R.dimen.directory_arrow_width), dimen(R.dimen.directory_row_height)))
            return Holder(row, icon, name, badge, check, arrow)
        }

        override fun getItemCount(): Int = paths.size.coerceAtLeast(1)

        override fun onBindViewHolder(holder: Holder, position: Int) {
            if (paths.isEmpty()) {
                holder.name.text = "暂无可用目录"
                holder.name.setTextColor(resources.getColor(R.color.bridgefs_text_secondary))
                holder.badge.text = ""
                holder.check.visibility = View.GONE
                holder.icon.visibility = View.GONE
                holder.arrow.visibility = View.GONE
                holder.row.setOnClickListener(null)
                return
            }
            val path = paths[position]
            val added = roots().contains(path)
            holder.name.text = File(path).name.ifBlank { path }
            holder.name.setTextColor(resources.getColor(if (added) R.color.bridgefs_text_secondary else R.color.bridgefs_text_primary))
            holder.badge.text = if (added) "已添加" else ""
            holder.check.visibility = View.VISIBLE
            holder.check.isChecked = added
            holder.icon.visibility = View.VISIBLE
            holder.icon.setColorFilter(resources.getColor(if (added) R.color.bridgefs_text_secondary else R.color.bridgefs_accent))
            holder.arrow.visibility = if (onNavigate != null) View.VISIBLE else View.GONE
            holder.row.background = rowRipple(resources.getColor(if (added) R.color.bridgefs_selected_surface else R.color.bridgefs_surface))
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

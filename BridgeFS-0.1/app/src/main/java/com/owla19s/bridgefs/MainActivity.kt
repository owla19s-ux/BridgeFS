package com.owla19s.bridgefs

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.Gravity
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
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(R.dimen.bridgefs_panel_padding), d(R.dimen.bridgefs_panel_padding), d(R.dimen.bridgefs_panel_padding), d(R.dimen.bridgefs_panel_padding))
        }
        ViewCompat.setOnApplyWindowInsetsListener(box) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(d(R.dimen.bridgefs_panel_padding) + bars.left, d(R.dimen.bridgefs_panel_padding) + bars.top, d(R.dimen.bridgefs_panel_padding) + bars.right, d(R.dimen.bridgefs_panel_padding) + bars.bottom)
            insets
        }

        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(TextView(this).apply {
            text = "BridgeFS"
            textSize = d(R.dimen.bridgefs_title_text_size) / resources.displayMetrics.scaledDensity
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        titleRow.addView(TextView(this).apply {
            text = "v0.1.1"
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(dp(56), dp(48)))
        box.addView(titleRow)

        box.addView(TextView(this).apply { text = "目录"; textSize = d(R.dimen.bridgefs_title_text_size) / resources.displayMetrics.scaledDensity; setTextColor(color(R.color.bridgefs_text_primary)) }, LinearLayout.LayoutParams(-1, d(R.dimen.bridgefs_row_height)))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        roots().forEach { path ->
            val row = TextView(this).apply {
                text = "📂 " + File(path).name.ifBlank { path } + "\n" + path
                textSize = d(R.dimen.bridgefs_body_text_size) / resources.displayMetrics.scaledDensity
                maxLines = 2
                setPadding(dp(10), 0, dp(10), 0)
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(
                    if (path == prefs.getString("root_path", null)) R.color.bridgefs_selected_background else R.color.bridgefs_panel_background,
                    R.dimen.bridgefs_card_corner_radius
                )
                setOnClickListener { activate(path) }
                var downX = 0f
                setOnTouchListener { _, e ->
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> { downX = e.rawX; false }
                        MotionEvent.ACTION_UP -> {
                            if (downX - e.rawX > dp(80)) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("移除目录")
                                    .setMessage("只从 BridgeFS 列表移除，不会删除任何物理文件。")
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("确认") { _, _ ->
                                        val rs = roots()
                                        rs.remove(path)
                                        saveRoots(rs)
                                        if (prefs.getString("root_path", null) == path) prefs.edit().remove("root_path").apply()
                                        render()
                                    }.show()
                                true
                            } else false
                        }
                        else -> false
                    }
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, d(R.dimen.bridgefs_list_row_height)).also { it.bottomMargin = d(R.dimen.bridgefs_section_spacing) })
        }

        box.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val accessRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val accessStatus = TextView(this).apply {
            text = if (Environment.isExternalStorageManager()) "所有文件访问 ✅" else "所有文件访问 ❌"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setOnClickListener {
                if (!Environment.isExternalStorageManager()) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        val addDir = Button(this).apply {
            text = "+ 添加目录"
            textSize = 13f
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                if (!Environment.isExternalStorageManager()) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                else showDirectoryPicker()
            }
        }
        accessRow.addView(accessStatus, LinearLayout.LayoutParams(0, d(R.dimen.bridgefs_button_height), 1f))
        accessRow.addView(addDir, LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width), d(R.dimen.bridgefs_button_height)).also { it.marginStart = d(R.dimen.bridgefs_section_spacing) })
        box.addView(accessRow, LinearLayout.LayoutParams(-1, d(R.dimen.bridgefs_button_height)).also { it.topMargin = d(R.dimen.bridgefs_section_spacing) })

        val overlayRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val overlayStatus = TextView(this).apply {
            text = if (Settings.canDrawOverlays(this@MainActivity)) "悬浮窗权限 ✅" else "悬浮窗权限 ❌"
            textSize = 13f
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
        val open = Button(this).apply {
            text = "打开悬浮窗"
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { startOverlayManually() }
        }
        overlayRow.addView(overlayStatus, LinearLayout.LayoutParams(0, d(R.dimen.bridgefs_button_height), 1f))
        overlayRow.addView(autoSwitch, LinearLayout.LayoutParams(0, d(R.dimen.bridgefs_button_height), 1f))
        overlayRow.addView(open, LinearLayout.LayoutParams(0, d(R.dimen.bridgefs_button_height), 1f))
        box.addView(overlayRow, LinearLayout.LayoutParams(-1, d(R.dimen.bridgefs_button_height)).also { it.topMargin = d(R.dimen.bridgefs_section_spacing) })

        val logRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        logRow.addView(TextView(this).apply {
            text = "运行日志 / 崩溃日志"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, d(R.dimen.bridgefs_button_height), 1f))
        val logSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("run_log_enabled", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("run_log_enabled", checked).apply() }
        }
        logRow.addView(logSwitch, LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width), d(R.dimen.bridgefs_button_height)))
        box.addView(logRow, LinearLayout.LayoutParams(-1, d(R.dimen.bridgefs_button_height)).also { it.topMargin = d(R.dimen.bridgefs_section_spacing) })

        box.addView(TextView(this).apply {
            text = "若软件自动关闭，请检查：\n· 悬浮窗权限\n· 常驻锁定\n· 后台运行允许"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(8), 0, dp(8))
        }, LinearLayout.LayoutParams(-1, dp(64)).also { it.topMargin = d(R.dimen.bridgefs_section_spacing) })
        setContentView(box)
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
            setPadding(d(R.dimen.bridgefs_panel_padding), d(R.dimen.bridgefs_panel_padding), d(R.dimen.bridgefs_panel_padding), d(R.dimen.bridgefs_panel_padding))
            background = rounded(R.color.bridgefs_panel_background, R.dimen.bridgefs_panel_corner_radius)
        }
        private val pathView = TextView(this@MainActivity).apply {
            textSize = d(R.dimen.bridgefs_body_text_size) / resources.displayMetrics.scaledDensity
            setTextColor(color(R.color.bridgefs_text_primary))
            maxLines = 2
        }

        fun show() {
            dialog.setView(container)
            renderPicker()
            dialog.show()
            dialog.window?.setLayout(dynamicPanelWidthPx(), WindowManager.LayoutParams.WRAP_CONTENT)
        }

        private fun renderPicker() {
            pathView.text = "当前目录：\n" + pickerPath.absolutePath
            container.removeAllViews()
            container.addView(pathView, LinearLayout.LayoutParams(-1, d(R.dimen.bridgefs_list_row_height)))
            val scroll = ScrollView(this@MainActivity)
            val list = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val dirs = pickerPath.listFiles()?.filter { it.isDirectory }.orEmpty()
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
            dirs.forEach { dir ->
                list.addView(TextView(this@MainActivity).apply {
                    text = "📂 " + dir.name
                    textSize = d(R.dimen.bridgefs_body_text_size) / resources.displayMetrics.scaledDensity
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, dp(8), 0)
                    setTextColor(color(R.color.bridgefs_text_primary))
                    background = rounded(R.color.bridgefs_button_background, R.dimen.bridgefs_card_corner_radius)
                    setOnClickListener { pickerPath = dir; renderPicker() }
                }, LinearLayout.LayoutParams(-1, d(R.dimen.bridgefs_list_row_height)).also {
                    it.bottomMargin = d(R.dimen.bridgefs_section_spacing)
                })
            }
            scroll.addView(list)
            val availableHeight = (resources.displayMetrics.heightPixels * .36f).toInt()
            val contentHeight = dirs.size * (d(R.dimen.bridgefs_list_row_height) + d(R.dimen.bridgefs_section_spacing))
            val viewport = minOf(availableHeight, maxOf(d(R.dimen.bridgefs_list_row_height), contentHeight))
            container.addView(scroll, LinearLayout.LayoutParams(-1, viewport).also {
                it.topMargin = d(R.dimen.bridgefs_section_spacing)
            })

            val actions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(actionButton("返回") {
                pickerPath.parentFile?.takeIf {
                    it.absolutePath.startsWith("/storage/emulated/0") && it.absolutePath != "/storage/emulated/0"
                }?.let { pickerPath = it; renderPicker() }
            }, LinearLayout.LayoutParams(0, d(R.dimen.bridgefs_button_height), 1f))
            actions.addView(actionButton("选择此目录") {
                val p = pickerPath.canonicalPath
                val rs = roots()
                if (!rs.contains(p)) rs.add(p)
                saveRoots(rs); activate(p); dialog.dismiss()
            }, LinearLayout.LayoutParams(0, d(R.dimen.bridgefs_button_height), 1f).also {
                it.marginStart = d(R.dimen.bridgefs_section_spacing)
            })
            container.addView(actions, LinearLayout.LayoutParams(-1, d(R.dimen.bridgefs_button_height)).also {
                it.topMargin = d(R.dimen.bridgefs_section_spacing)
            })
        }

        private fun actionButton(label: String, click: () -> Unit) = Button(this@MainActivity).apply {
            text = label
            textSize = d(R.dimen.bridgefs_body_text_size) / resources.displayMetrics.scaledDensity
            minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
            setTextColor(color(R.color.bridgefs_text_primary))
            background = rounded(R.color.bridgefs_button_background, R.dimen.bridgefs_button_corner_radius)
            setOnClickListener { click() }
        }
    }

    private fun dynamicPanelWidthPx(): Int {
        val targetDp = (resources.configuration.screenWidthDp * .65f).toInt().coerceIn(220, 300)
        return dp(targetDp).coerceAtMost((resources.displayMetrics.widthPixels - dp(16)).coerceAtLeast(dp(1)))
    }

    private fun d(id: Int) = resources.getDimensionPixelSize(id)
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun rounded(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(color(fill))
        cornerRadius = resources.getDimension(radius)
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}

package com.owla19s.bridgefs

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
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
            if (showPermissionHint) {
                Toast.makeText(this, "请先在系统设置开启悬浮窗权限；ColorOS 后台自启动也需允许。", Toast.LENGTH_LONG).show()
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
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        ViewCompat.setOnApplyWindowInsetsListener(box) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(40 + bars.left, 40 + bars.top, 40 + bars.right, 40 + bars.bottom)
            insets
        }

        box.addView(TextView(this).apply {
            text = "BridgeFS v0.1.0 (1)"
            textSize = 24f
            setTypeface(null, 1)
        }, LinearLayout.LayoutParams(-1, dp(48)))

        box.addView(TextView(this).apply { text = "目录列表："; textSize = 15f })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        roots().forEach { path ->
            val row = TextView(this).apply {
                text = "📂 " + File(path).name.ifBlank { path } + "\n" + path
                textSize = 14f
                setPadding(12, 12, 12, 12)
                setBackgroundColor(
                    if (path == prefs.getString("root_path", null)) Color.rgb(224, 231, 255)
                    else Color.WHITE
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
                                        if (prefs.getString("root_path", null) == path) {
                                            prefs.edit().remove("root_path").apply()
                                        }
                                        render()
                                    }.show()
                                true
                            } else false
                        }
                        else -> false
                    }
                }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, dp(62)).also { it.bottomMargin = dp(6) })
        }

        box.addView(
            ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        box.addView(Button(this).apply {
            text = "＋ 添加目录"
            setOnClickListener {
                if (!Environment.isExternalStorageManager()) {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    showDirectoryPicker()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))

        box.addView(TextView(this).apply {
            text = "权限状态：\n" +
                (if (Settings.canDrawOverlays(this@MainActivity)) "✅ 悬浮窗权限" else "❌ 悬浮窗权限") +
                "\n" +
                (if (Environment.isExternalStorageManager()) "✅ 所有文件访问" else "❌ 所有文件访问")
            textSize = 14f
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else if (!Environment.isExternalStorageManager()) {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        })

        val autoSwitch = Switch(this).apply {
            text = "启动时自动显示悬浮窗"
            textSize = 14f
            isChecked = prefs.getBoolean("auto_show_overlay", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("auto_show_overlay", checked).apply()
            }
        }
        box.addView(autoSwitch, LinearLayout.LayoutParams(-1, dp(48)))

        val logSwitch = Switch(this).apply {
            text = "运行日志（run.log）"
            textSize = 14f
            isChecked = prefs.getBoolean("run_log_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("run_log_enabled", checked).apply()
            }
        }
        box.addView(logSwitch, LinearLayout.LayoutParams(-1, dp(48)))

        box.addView(TextView(this).apply {
            text = "ColorOS：如系统限制后台运行或自启动，请在系统设置中允许 BridgeFS。"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(4), 0, dp(8))
        })

        box.addView(Button(this).apply {
            text = "复制日志路径"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "BridgeFS日志路径",
                        "/sdcard/BridgeFS/logs/latest.log  # 崩溃日志\n" +
                            "/sdcard/BridgeFS/logs/run.log  # 运行日志"
                    )
                )
                Toast.makeText(this@MainActivity, "已复制日志路径", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))

        box.addView(Button(this).apply {
            text = "打开悬浮窗"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    return@setOnClickListener
                }
                if (!Environment.isExternalStorageManager()) {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                    return@setOnClickListener
                }
                if (prefs.getString("root_path", null).isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, "请先添加并激活项目目录", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                runCatching {
                    ContextCompatCompat.startService(this@MainActivity, Intent(this@MainActivity, FileBridgeService::class.java))
                }.onFailure {
                    Toast.makeText(this@MainActivity, "启动悬浮窗失败，请检查系统权限。", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))

        setContentView(box)
    }

    private fun showDirectoryPicker() {
        pickerPath = File("/storage/emulated/0")
        DirectoryDialog().show()
    }

    private inner class DirectoryDialog {
        private val dialog = AlertDialog.Builder(this@MainActivity).create()
        private val container = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        private val pathView = TextView(this@MainActivity).apply { textSize = 13f }

        fun show() {
            dialog.setView(container)
            renderPicker()
            dialog.show()
        }

        private fun renderPicker() {
            pathView.text = "当前目录：\n" + pickerPath.absolutePath
            container.removeAllViews()
            container.addView(pathView)
            val scroll = ScrollView(this@MainActivity)
            val list = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            pickerPath.listFiles()?.filter { it.isDirectory }.orEmpty()
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
                .forEach { dir ->
                    list.addView(Button(this@MainActivity).apply {
                        text = "📂 " + dir.name
                        setOnClickListener { pickerPath = dir; renderPicker() }
                    })
                }
            scroll.addView(list)
            container.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            val actions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(this@MainActivity).apply {
                text = "返回"
                setOnClickListener {
                    pickerPath.parentFile?.takeIf {
                        it.absolutePath.startsWith("/storage/emulated/0") &&
                            it.absolutePath != "/storage/emulated/0"
                    }?.let { pickerPath = it; renderPicker() }
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            actions.addView(Button(this@MainActivity).apply {
                text = "选择此目录"
                setOnClickListener {
                    val p = pickerPath.canonicalPath
                    val rs = roots()
                    if (!rs.contains(p)) rs.add(p)
                    saveRoots(rs)
                    activate(p)
                    dialog.dismiss()
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            container.addView(actions)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

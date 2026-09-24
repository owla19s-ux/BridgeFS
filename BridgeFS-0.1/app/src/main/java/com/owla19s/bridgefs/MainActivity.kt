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
import android.widget.*
import java.io.File
import java.util.Locale

class MainActivity:Activity(){
    private val prefs by lazy{getSharedPreferences("bridgefs",0)}
    private val rootsKey="root_paths"
    private var pickerPath=File("/storage/emulated/0")
    override fun onCreate(s:Bundle?){super.onCreate(s);render()}
    override fun onResume(){super.onResume();render()}
    private fun roots():MutableList<String>{
        val saved=prefs.getStringSet(rootsKey,null)?.toMutableList()?:mutableListOf()
        val current=prefs.getString("root_path",null)
        if(!current.isNullOrBlank()&&!saved.contains(current))saved.add(current)
        return saved.sorted().toMutableList()
    }
    private fun saveRoots(list:List<String>){prefs.edit().putStringSet(rootsKey,list.toSet()).apply()}
    private fun activate(path:String){prefs.edit().putString("root_path",path).apply();render()}
    private fun render(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(40,40,40,40)}
        box.addView(TextView(this).apply{text="BridgeFS";textSize=24f;setTypeface(null,1)},LinearLayout.LayoutParams(-1,dp(48)))
        box.addView(TextView(this).apply{text="目录列表：";textSize=15f})
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        roots().forEach{path->
            val row=TextView(this).apply{
                text="📂 ${File(path).name.ifBlank{path}}\\n${path}"
                textSize=14f;setPadding(12,12,12,12)
                setBackgroundColor(if(path==prefs.getString("root_path",null))Color.rgb(224,231,255)else Color.WHITE)
                setOnClickListener{activate(path)}
                var downX=0f
                setOnTouchListener{_,e->when(e.action){
                    MotionEvent.ACTION_DOWN->{downX=e.rawX;false}
                    MotionEvent.ACTION_UP->{if(downX-e.rawX>dp(80)){AlertDialog.Builder(this@MainActivity).setTitle("移除目录").setMessage("只从 BridgeFS 列表移除，不会删除任何物理文件。").setNegativeButton("取消",null).setPositiveButton("确认"){_,_->
                        val rs=roots();rs.remove(path);saveRoots(rs);if(prefs.getString("root_path",null)==path)prefs.edit().remove("root_path").apply()
                        render();Toast.makeText(this@MainActivity,"已从列表移除",Toast.LENGTH_SHORT).show()
                    }.show();true}else false}
                    else->false}}
            }
            list.addView(row,LinearLayout.LayoutParams(-1,dp(62)).also{it.bottomMargin=dp(6)})
        }
        box.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f))
        box.addView(Button(this).apply{text="＋ 添加目录";setOnClickListener{
            if(!Environment.isExternalStorageManager())startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:$packageName")))else showDirectoryPicker()
        }},LinearLayout.LayoutParams(-1,dp(48)))
        box.addView(TextView(this).apply{
            text="权限状态：\\n${if(Settings.canDrawOverlays(this@MainActivity))"✅ 悬浮窗权限" else "❌ 悬浮窗权限"}\\n${if(Environment.isExternalStorageManager())"✅ 所有文件访问" else "❌ 所有文件访问"}"
            textSize=14f;setPadding(0,dp(12),0,dp(12));setOnClickListener{
                if(!Settings.canDrawOverlays(this@MainActivity))startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
                else if(!Environment.isExternalStorageManager())startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:$packageName")))
            }
        })
        box.addView(Button(this).apply{text="打开悬浮窗";setOnClickListener{
            if(!Settings.canDrawOverlays(this@MainActivity)){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")));return@setOnClickListener}
            if(!Environment.isExternalStorageManager()){startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:$packageName")));return@setOnClickListener}
            if(prefs.getString("root_path",null).isNullOrBlank()){Toast.makeText(this@MainActivity,"请先添加并激活项目目录",Toast.LENGTH_SHORT).show();return@setOnClickListener}
            ContextCompatCompat.startService(this@MainActivity,Intent(this@MainActivity,FileBridgeService::class.java));finish()
        }},LinearLayout.LayoutParams(-1,dp(48)))
        setContentView(box)
    }
    private fun showDirectoryPicker(){pickerPath=File("/storage/emulated/0");DirectoryDialog().show()}
    private inner class DirectoryDialog{
        private val dialog=AlertDialog.Builder(this@MainActivity).create()
        private val container=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12))}
        private val pathView=TextView(this@MainActivity).apply{textSize=13f}
        private val list=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL}
        fun show(){dialog.setView(container);renderPicker();dialog.show()}
        private fun renderPicker(){
            pathView.text="当前目录：\\n${pickerPath.absolutePath}"
            container.removeAllViews();container.addView(pathView)
            val scroll=ScrollView(this@MainActivity)
            scroll.addView(list.apply{
                removeAllViews()
                pickerPath.listFiles()?.filter{it.isDirectory}.orEmpty().sortedBy{it.name.lowercase(Locale.getDefault())}.forEach{dir->
                    addView(Button(this@MainActivity).apply{text="📂 ${dir.name}";setOnClickListener{pickerPath=dir;renderPicker()}})
                }
            })
            container.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
            val actions=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL}
            actions.addView(Button(this@MainActivity).apply{text="返回";setOnClickListener{pickerPath.parentFile?.takeIf{it.absolutePath.startsWith("/storage/emulated/0")&&it.absolutePath!="/storage/emulated/0"}?.let{pickerPath=it;renderPicker()}}},LinearLayout.LayoutParams(0,dp(48),1f))
            actions.addView(Button(this@MainActivity).apply{text="选择此目录";setOnClickListener{
                val p=pickerPath.canonicalPath;val rs=roots();if(!rs.contains(p))rs.add(p);saveRoots(rs);activate(p);dialog.dismiss()
            }},LinearLayout.LayoutParams(0,dp(48),1f))
            container.addView(actions)
        }
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}

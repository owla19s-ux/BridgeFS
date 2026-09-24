package com.owla19s.bridgefs
import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.*
class MainActivity:Activity(){private val prefs by lazy{getSharedPreferences("bridgefs",0)};private val pick=1001
 override fun onCreate(s:Bundle?){super.onCreate(s);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(40,40,40,40)};val t=TextView(this).apply{text="FileBridge";textSize=24f};val st=TextView(this).apply{textSize=14f};val choose=Button(this).apply{text="选择项目目录"};val start=Button(this).apply{text="启动悬浮窗"};box.addView(t);box.addView(st);box.addView(choose);box.addView(start);setContentView(box)
 fun refresh(){st.text="悬浮窗权限：${if(Settings.canDrawOverlays(this))"已开启" else "未开启"}\n所有文件访问：${if(Environment.isExternalStorageManager())"已开启" else "未开启"}\n项目目录：${prefs.getString("root_path","未选择")}"};choose.setOnClickListener{startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),pick)};start.setOnClickListener{if(!Settings.canDrawOverlays(this)){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")));return@setOnClickListener};if(!Environment.isExternalStorageManager()){startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:$packageName")));return@setOnClickListener};if(prefs.getString("root_path",null).isNullOrBlank()){Toast.makeText(this,"请先选择项目目录",Toast.LENGTH_SHORT).show();return@setOnClickListener};ContextCompatCompat.startService(this,Intent(this,FileBridgeService::class.java));finish()};refresh()}
 override fun onResume(){super.onResume()}
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==pick&&c==RESULT_OK){val u=d?.data?:return;contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);val p=TreeUriResolver.resolve(this,u);if(p!=null)prefs.edit().putString("root_path",p).apply() else Toast.makeText(this,"无法解析该目录，请选择内部存储目录",Toast.LENGTH_LONG).show();recreate()}}
}

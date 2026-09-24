package com.owla19s.bridgefs
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.InputType
import android.view.*
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileBridgeService:Service(){private lateinit var wm:WindowManager;private lateinit var ball:TextView;private var panel:LinearLayout?=null;private lateinit var root:File;private lateinit var ballLp:WindowManager.LayoutParams;private lateinit var panelLpRef:WindowManager.LayoutParams;private val logs=ArrayDeque<String>()
override fun onCreate(){super.onCreate();root=File(getSharedPreferences("bridgefs",0).getString("root_path","")!!);channel();startForeground(1,Notification.Builder(this,"filebridge").setContentTitle("FileBridge").setContentText("悬浮文件桥运行中").setSmallIcon(android.R.drawable.ic_menu_manage).build());wm=getSystemService(WINDOW_SERVICE)as WindowManager;showBall()}
private fun channel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("filebridge","FileBridge",NotificationManager.IMPORTANCE_LOW))}
private fun lp(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,-3)
private fun panelLp(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,-3)
private fun showBall(){ball=TextView(this).apply{text="📁";textSize=22f;gravity=17;alpha=.7f;background=bg("#CC1E293B",28,"#6366F1");setOnClickListener{if(alpha<1f){alpha=1f;translationX=0f}else showPanel()}};ballLp=lp(dp(56),dp(56));ballLp.gravity=Gravity.TOP or Gravity.RIGHT;ballLp.x=0;ballLp.y=(resources.displayMetrics.heightPixels*.65).toInt();drag(ball,ballLp);wm.addView(ball,ballLp)}
private fun showPanel(){
try{
if(panel!=null){wm.removeView(panel);panel=null}
ball.visibility=View.GONE
val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12));background=bg("#FFFFFF",16,null)}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val title=TextView(this).apply{text="📁 BridgeFS";textSize=15f;setTypeface(null,1)}
val help=smallButton("📖"){copyInstructions()}
val close=smallButton("⌄"){closePanel()}
top.addView(title,LinearLayout.LayoutParams(0,dp(40),1f))
top.addView(help,LinearLayout.LayoutParams(dp(40),dp(40)).also{it.marginStart=dp(6)})
top.addView(close,LinearLayout.LayoutParams(dp(40),dp(40)).also{it.marginStart=dp(6)})
box.addView(top)
val address=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val dir=TextView(this).apply{text="📂 "+root.name;if(root.name.isBlank())text="📂 "+root.absolutePath;textSize=13f;setTextColor(Color.rgb(99,102,241));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE}
val browse=smallButton("🗂️"){showBrowser()}
val copyPath=smallButton("📋"){copyText("BridgeFS路径",root.absolutePath);toast("已复制到剪贴板")}
address.addView(dir,LinearLayout.LayoutParams(0,dp(40),1f))
address.addView(browse,LinearLayout.LayoutParams(dp(40),dp(40)).also{it.marginStart=dp(6)})
address.addView(copyPath,LinearLayout.LayoutParams(dp(40),dp(40)).also{it.marginStart=dp(6)})
box.addView(address,LinearLayout.LayoutParams(-1,dp(40)).also{it.topMargin=dp(8)})
val input=EditText(this).apply{hint="粘贴 AI 指令到这里...";textSize=13f;gravity=Gravity.TOP;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;setPadding(dp(10),dp(8),dp(10),dp(8));background=bg("#FFFFFF",8,"#E2E8F0")}
val paste=smallButton("📋"){val cm=getSystemService(CLIPBOARD_SERVICE)as ClipboardManager;val clip=cm.primaryClip;if(clip!=null&&clip.itemCount>0)input.setText(clip.getItemAt(0).coerceToText(this))}
val run=smallButton("▶"){val cs=CommandParser.parse(input.text.toString());val receipt=findReceipt(box);receipt?.let{it.text=if(cs.isEmpty())"未发现可执行指令" else cs.map{CommandExecutor(root).execute(it)}.joinToString("\n\n");it.setTextColor(Color.DKGRAY)};addLog("执行 "+cs.size+" 条指令");findLogView(box)?.text=logs.joinToString("\n")}
val inputSide=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(paste,LinearLayout.LayoutParams(dp(56),dp(40)));addView(run,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.topMargin=dp(6)})}
val inputRow=LinearLayout(this).apply{gravity=Gravity.TOP;addView(input,LinearLayout.LayoutParams(0,dp(100),1f));addView(inputSide,LinearLayout.LayoutParams(dp(56),dp(86)).also{it.marginStart=dp(8)})}
box.addView(inputRow,LinearLayout.LayoutParams(-1,dp(100)).also{it.topMargin=dp(8)})
val receipt=TextView(this).apply{text="执行结果会显示在这里";textSize=12f;typeface=android.graphics.Typeface.MONOSPACE;setPadding(dp(10),dp(8),dp(10),dp(8));setTextColor(Color.GRAY);setBackgroundColor(Color.rgb(248,250,252))}
val copyReceipt=smallButton("📋"){copyText("BridgeFS回执",receipt.text.toString());toast("已复制到剪贴板")}
val receiptRow=LinearLayout(this).apply{gravity=Gravity.TOP;addView(receipt,LinearLayout.LayoutParams(0,dp(65),1f));addView(copyReceipt,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(8)})}
box.addView(receiptRow,LinearLayout.LayoutParams(-1,dp(65)).also{it.topMargin=dp(4)})
val logToggle=TextView(this).apply{text="📜 日志";textSize=13f;setTextColor(Color.DKGRAY);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),0,dp(10),0);background=bg("#F1F5F9",8,null)}
val logv=TextView(this).apply{textSize=11f;setTextColor(Color.DKGRAY);setPadding(dp(8),dp(6),dp(8),dp(6));background=bg("#F8FAFC",8,null)}
var logOpen=false
logToggle.setOnClickListener{logOpen=!logOpen;logv.visibility=if(logOpen)View.VISIBLE else View.GONE;logToggle.text=if(logOpen)"📜 日志 －" else "📜 日志"}
box.addView(logToggle,LinearLayout.LayoutParams(-1,dp(40)).also{it.topMargin=dp(8)})
logv.visibility=View.GONE
box.addView(logv,LinearLayout.LayoutParams(-1,dp(56)).also{it.topMargin=dp(4)})
dragPanel(box)
panel=box
val maxH=(resources.displayMetrics.heightPixels*.65f).toInt()
panelLpRef=panelLp(dp(280),maxH)
panelLpRef.gravity=ballLp.gravity
panelLpRef.x=dp(72)
panelLpRef.y=ballLp.y
wm.addView(box,panelLpRef)
}catch(e:Exception){toast("打开面板失败："+e.message)}}private fun toast(msg:String){Handler(Looper.getMainLooper()).post{Toast.makeText(this,msg,Toast.LENGTH_SHORT).show()}}
private fun closePanel(){panel?.let{wm.removeView(it)};panel=null;ball.visibility=View.VISIBLE;ball.alpha=.7f;ball.translationX=0f}
private fun dragPanel(v:View){var sx=0f;var sy=0f;var ox=0;var oy=0;v.setOnTouchListener{_,e->when(e.action){MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;ox=panelLpRef.x;oy=panelLpRef.y;true};MotionEvent.ACTION_MOVE->{panelLpRef.x=ox+(e.rawX-sx).toInt();panelLpRef.y=oy+(e.rawY-sy).toInt();wm.updateViewLayout(panel!!,panelLpRef);true};MotionEvent.ACTION_UP->true;else->false}}}
private fun findReceipt(v:View):TextView?{if(v is TextView&&v.text.toString()=="执行结果会显示在这里")return v;if(v is ViewGroup)for(i in 0 until v.childCount){val r=findReceipt(v.getChildAt(i));if(r!=null)return r};return null}
private fun findLogView(v:View):TextView?{if(v is TextView&&v.text.toString().startsWith("执行 "))return v;if(v is ViewGroup)for(i in 0 until v.childCount){val r=findLogView(v.getChildAt(i));if(r!=null)return r};return null}
private fun smallButton(label:String,onClick:()->Unit)=Button(this).apply{text=label;textSize=14f;minWidth=0;minimumWidth=0;setPadding(0,0,0,0);background=bg("#F1F5F9",8,null);setOnClickListener{onClick()}}
private fun copyText(label:String,text:String){val cm=getSystemService(CLIPBOARD_SERVICE)as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText(label,text))}
private fun copyInstructions(){val text="""我这边有个工具叫 BridgeFS，它可以读写我手机里的文件。
你想操作文件时，请用下面的指令格式，我会执行后把结果贴回来给你。

可用指令：
[list]                            列出项目目录
[read: 相对路径]                   读取文件
[write: 相对路径]...[/write]       新建文件并写入内容
[edit: 相对路径]...====...[/edit]   编辑文件（====分隔旧内容和新内容）
[search: *.xx]                    按文件名搜索
[grep: 关键词]                     按内容搜索
[path: 路径]                      获取完整绝对路径
[copy-path: 路径]                 复制路径到剪贴板

规则：
- 路径一律相对于项目根目录，例如 笔记/今天.txt
- [write] 和 [edit] 必须包在代码块里，语言标记就是 [write: 路径]
- 一次可以发多条指令，我会按顺序执行
- 执行结果会贴回来给你
""";copyText("BridgeFS说明书",text);toast("已复制到剪贴板")}
private fun showBrowser(){if(panel!=null){wm.removeView(panel);panel=null};browserCurrent=root;renderBrowser()}
private var browserCurrent:File?=null
private fun renderBrowser(){
val current=browserCurrent?:root
val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(12));background=bg("#FFFFFF",16,null)}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val back=smallButton("←"){if(current.absolutePath==root.absolutePath)showPanel()else{browserCurrent=current.parentFile?:root;renderBrowser()}}
val rootBtn=TextView(this).apply{text=root.name.ifBlank{root.absolutePath};textSize=13f;gravity=17;setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;setTextColor(Color.DKGRAY);background=bg("#F1F5F9",8,null);setOnClickListener{showRootList()}}
val copy=smallButton("📋"){copyText("BridgeFS目录",browserListing(current));toast("已复制当前层")}
top.addView(back,LinearLayout.LayoutParams(dp(40),dp(40)));top.addView(rootBtn,LinearLayout.LayoutParams(dp(100),dp(40)).also{it.marginStart=dp(6)});top.addView(copy,LinearLayout.LayoutParams(dp(40),dp(40)).also{it.marginStart=dp(6)})
box.addView(top,LinearLayout.LayoutParams(-1,dp(48)))
val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
val result=CommandExecutor(current).execute(Command.ListTree)
val entries=result.lines().drop(1).map{it.trim()}.filter{it.isNotBlank()}.map{it to it.removeSuffix("/")}
val first=entries.filter{it.second.count{c->c=='/'}==0}
first.take(50).forEach{e->val rel=e.second;val row=TextView(this).apply{text=if(e.first.endsWith("/"))"📁 "+File(rel).name else "📄 "+File(rel).name;textSize=14f;setPadding(dp(8),dp(8),dp(8),dp(8));setOnClickListener{val f=File(current,rel);if(f.isDirectory){browserCurrent=f;renderBrowser()}else{copyText("BridgeFS路径",f.relativeTo(root).path);toast("已复制相对路径")}}};list.addView(row,LinearLayout.LayoutParams(-1,dp(40)))}
if(first.size>50)list.addView(TextView(this).apply{text="…等 "+(first.size-50)+" 项";textSize=13f;setTextColor(Color.GRAY);setPadding(dp(8),dp(8),dp(8),dp(8))})
box.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f))
dragPanel(box);panel=box
val maxH=(resources.displayMetrics.heightPixels*.65f).toInt()
panelLpRef=panelLp(dp(280),maxH);panelLpRef.gravity=ballLp.gravity;panelLpRef.x=dp(72);panelLpRef.y=ballLp.y
wm.addView(box,panelLpRef)}
private fun browserListing(current:File):String{val result=CommandExecutor(current).execute(Command.ListTree);val entries=result.lines().drop(1).map{it.trim()}.filter{it.isNotBlank()}.map{it.removeSuffix("/") to it.endsWith("/")};val first=entries.filter{it.first.count{c->c=='/'}==0}.take(50);val files=first.count{!it.second};val dirs=first.count{it.second};return "📁 "+current.relativeToOrSelf(root).path+"/\n含 "+files+" 个文件、"+dirs+" 个文件夹：\n"+first.joinToString("\n"){(n,d)->"  "+(if(d)"📁" else "📄")+" "+n}+(if(entries.size>50)"\n…等 "+(entries.size-50)+" 项" else "")}
private fun showRootList(){val rs:Array<String>=(getSharedPreferences("bridgefs",0).getStringSet("root_paths",emptySet<String>())?:emptySet<String>()).toTypedArray();AlertDialog.Builder(this).setTitle("选择根目录").setItems(rs){_,which->root=File(rs[which]);getSharedPreferences("bridgefs",0).edit().putString("root_path",root.absolutePath).apply();browserCurrent=root;renderBrowser()}.setNegativeButton("取消",null).show()}
private fun addLog(s:String){logs.addLast("${SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())} ✓ $s");while(logs.size>50)logs.removeFirst()}
private fun drag(v:View,p:WindowManager.LayoutParams){var sx=0f;var sy=0f;var ox=0;var oy=0;var moved=false;v.setOnTouchListener{_,e->when(e.action){MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;ox=p.x;oy=p.y;moved=false;v.alpha=1f;v.translationX=0f;true};MotionEvent.ACTION_MOVE->{val dx=e.rawX-sx;val dy=e.rawY-sy;if(kotlin.math.abs(dx)>dp(8)||kotlin.math.abs(dy)>dp(8))moved=true;if(p.gravity and Gravity.RIGHT==Gravity.RIGHT)p.x=ox-dx.toInt()else p.x=ox+dx.toInt();p.y=oy+dy.toInt();wm.updateViewLayout(v,p);true};MotionEvent.ACTION_UP->{if(!moved){v.performClick()}else{val left=e.rawX<resources.displayMetrics.widthPixels/2;p.gravity=Gravity.TOP or if(left)Gravity.LEFT else Gravity.RIGHT;p.x=0;wm.updateViewLayout(v,p);v.translationX=if(left)-dp(28).toFloat()else dp(28).toFloat();v.alpha=.7f};true};else->false}}}
private fun bg(fill:String,r:Int,stroke:String?)=GradientDrawable().apply{setColor(Color.parseColor(fill));cornerRadius=dp(r).toFloat();if(stroke!=null)setStroke(dp(1),Color.parseColor(stroke))};private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt();override fun onBind(i:Intent?)=null;override fun onDestroy(){panel?.let{wm.removeView(it)};if(::ball.isInitialized)wm.removeView(ball);super.onDestroy()}}

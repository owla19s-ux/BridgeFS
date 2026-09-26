package com.owla19s.bridgefs
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.InputType
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileBridgeService:Service(){
private lateinit var wm:WindowManager
private lateinit var ball:PillOrbView
private var panel:LinearLayout?=null
private var commandInput:EditText?=null
private lateinit var root:File
private lateinit var ballLp:WindowManager.LayoutParams
private lateinit var panelLpRef:WindowManager.LayoutParams
private val handler=Handler(Looper.getMainLooper())
companion object{@Volatile var running=false;@Volatile var clipboardCallback:((String)->Unit)?=null}

override fun onCreate(){
super.onCreate();running=true
root=File(getSharedPreferences("bridgefs",0).getString("root_path","")!!)
channel()
startForeground(1,Notification.Builder(this,"filebridge").setContentTitle("FileBridge").setContentText("悬浮文件桥运行中").setSmallIcon(android.R.drawable.ic_menu_manage).build())
wm=getSystemService(WINDOW_SERVICE)as WindowManager
log("Service","onCreate");showBall()
}
private fun channel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("filebridge","FileBridge",NotificationManager.IMPORTANCE_LOW))}
private fun lp(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,-3)
private fun panelLp(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,-3)

private fun showBall(){
if(!::ball.isInitialized){
ball=PillOrbView(this).apply{setOnClickListener{if(panel==null)showPanel()else closePanel()}}
ballLp=lp(dp(40),dp(56));ballLp.gravity=Gravity.TOP or Gravity.LEFT
ballLp.x=resources.displayMetrics.widthPixels-dp(50);ballLp.y=(resources.displayMetrics.heightPixels*.65).toInt()
}
val parent=ball.parent
if(parent is ViewGroup)parent.removeView(ball) else runCatching{wm.removeView(ball)}
ball.alpha=.7f;ball.translationX=0f;ball.translationY=0f;ball.visibility=View.VISIBLE
drag(ball,ballLp);wm.addView(ball,ballLp)
}

private fun showPanel(){
try{
clearPanel();val origin=IntArray(2);ball.getLocationOnScreen(origin);orbTransitionOrigin=origin;runCatching{wm.removeView(ball)};log("UI","showPanel")
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12));background=bg("#FFFFFF",16,null)
}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
top.addView(View(this),LinearLayout.LayoutParams(0,dp(1),1f))
val help=smallButton("帮助"){copyInstructions()}
val close=smallButton("⌄"){closePanel()}
top.addView(help,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(6)})
top.addView(close,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(6)})
box.addView(top)
// Panel movement is handled by the footer robot and BridgeFS label.

val address=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val dir=TextView(this).apply{text="📂 "+root.name;if(root.name.isBlank())text="📂 "+root.absolutePath;textSize=13f;setTextColor(Color.rgb(99,102,241));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE}
address.addView(dir,LinearLayout.LayoutParams(0,dp(40),1f))
address.addView(smallButton("选择"){showBrowser()},LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(6)})
address.addView(smallButton("复制路径"){copyText("BridgeFS路径",root.absolutePath)},LinearLayout.LayoutParams(dp(72),dp(40)).also{it.marginStart=dp(6)})
box.addView(address,LinearLayout.LayoutParams(-1,dp(40)).also{it.topMargin=dp(8)})

val input=EditText(this).apply{
hint="粘贴 AI 指令到这里...";textSize=13f;gravity=Gravity.TOP
inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
setPadding(dp(10),dp(8),dp(42),dp(8));background=bg("#FFFFFF",8,"#E2E8F0")
setOnClickListener{showCommandInputDialog(this)}
}
commandInput=input
clipboardCallback={text->handler.post{commandInput?.setText(text);commandInput?.setSelection(commandInput?.text?.length?:0)}}
val inputFrame=FrameLayout(this).apply{
addView(input,FrameLayout.LayoutParams(-1,-1))
addView(TextView(this@FileBridgeService).apply{text="!";textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.rgb(46,123,224));contentDescription="指令帮助";setOnClickListener{copyInstructions()}},FrameLayout.LayoutParams(dp(28),dp(28),Gravity.TOP or Gravity.RIGHT).also{it.topMargin=dp(4);it.rightMargin=dp(4)})
}
val paste=smallButton("粘贴"){val intent=Intent(this,ClipboardReaderActivity::class.java).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)};startActivity(intent)}
val run=smallButton("执行"){
val raw=input.text.toString();log("Command","收到："+raw.replace("\n","\\n").take(500))
val cs=CommandParser.parse(raw)
val results=if(cs.isEmpty())listOf("未发现可执行指令")else cs.map{CommandExecutor(root,this).execute(it)}
findReceipt(box)?.let{it.text=results.joinToString("\n\n");it.setTextColor(Color.DKGRAY)}
log("Command","执行 "+cs.size+" 条指令："+if(results.none{it.contains("✗")})"成功" else "失败")
}
val inputSide=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(paste,LinearLayout.LayoutParams(dp(56),dp(40)));addView(run,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.topMargin=dp(6)})}
val inputRow=LinearLayout(this).apply{gravity=Gravity.BOTTOM;addView(inputFrame,LinearLayout.LayoutParams(0,dp(86),1f));addView(inputSide,LinearLayout.LayoutParams(dp(56),dp(86)).also{it.marginStart=dp(8)})}
box.addView(inputRow,LinearLayout.LayoutParams(-1,dp(86)).also{it.topMargin=dp(8)})

val receipt=TextView(this).apply{text="执行结果会显示在这里";textSize=12f;typeface=android.graphics.Typeface.MONOSPACE;setPadding(dp(10),dp(8),dp(10),dp(8));setTextColor(Color.GRAY);background=bg("#F8FAFC",8,null)}
val copyReceipt=smallButton("复制"){copyText("BridgeFS回执",receipt.text.toString())}
val receiptRow=LinearLayout(this).apply{gravity=Gravity.TOP;addView(receipt,LinearLayout.LayoutParams(0,dp(48),1f));addView(copyReceipt,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(8)})}
box.addView(receiptRow,LinearLayout.LayoutParams(-1,dp(48)).also{it.topMargin=dp(8)})


addPanelFooter(box);panel=box
val sw=resources.displayMetrics.widthPixels
panelLpRef=panelLp(dp(300),WindowManager.LayoutParams.WRAP_CONTENT)
panelLpRef.gravity=Gravity.TOP or Gravity.LEFT
panelLpRef.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
panelLpRef.x=(sw-dp(300)-dp(72)).coerceAtLeast(0);panelLpRef.y=ballLp.y
wm.addView(box,panelLpRef)
box.post{val maxH=(resources.displayMetrics.heightPixels*.65f).toInt();if(box.height>maxH){panelLpRef.height=maxH;runCatching{wm.updateViewLayout(box,panelLpRef)}}}
}catch(e:Exception){log("Error","showPanel："+e.message);toast("打开面板失败："+e.message)}
}

private var inputDialog:Dialog?=null
private fun releaseInputFocus(){
runCatching{inputDialog?.dismiss();inputDialog=null}
}
private fun toast(msg:String){handler.post{Toast.makeText(this,msg,Toast.LENGTH_SHORT).show()}}
private fun showCommandInputDialog(target:EditText){
 if(inputDialog?.isShowing==true)return
 val dialog=Dialog(this)
 inputDialog=dialog
 dialog.setOnDismissListener{inputDialog=null}
 dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
 val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16))}
 val edit=EditText(this).apply{
  setText(target.text)
  setSelection(text.length)
  hint="输入 AI 指令..."
  textSize=14f
  gravity=Gravity.TOP
  minLines=6
  maxLines=12
  inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
 }
 box.addView(edit,LinearLayout.LayoutParams(-1,dp(180)))
 val actions=LinearLayout(this).apply{gravity=Gravity.END}
 val cancel=Button(this).apply{text="取消";setOnClickListener{dialog.dismiss()}}
 val ok=Button(this).apply{text="确定";setOnClickListener{target.setText(edit.text.toString());target.setSelection(target.text.length);dialog.dismiss()}}
 actions.addView(cancel,LinearLayout.LayoutParams(dp(80),dp(44)))
 actions.addView(ok,LinearLayout.LayoutParams(dp(80),dp(44)).also{it.marginStart=dp(8)})
 box.addView(actions,LinearLayout.LayoutParams(-1,dp(44)).also{it.topMargin=dp(8)})
 dialog.setContentView(box)
 dialog.window?.let{
  it.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
  it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
  it.setLayout(dp(300),WindowManager.LayoutParams.WRAP_CONTENT)
 }
 dialog.show()
 dialog.window?.let{
  it.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
  it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
  it.setLayout(dp(300),WindowManager.LayoutParams.WRAP_CONTENT)
 }
 edit.requestFocus()
 edit.post{(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT)}
}

private fun clearPanel(){
runCatching{panel?.let{wm.removeView(it)}}
panel=null
log("UI","clearPanel")
}
private fun closePanel(){try{log("UI","closePanel");releaseInputFocus();clipboardCallback=null;commandInput=null;clearPanel();handler.removeCallbacksAndMessages(null);showBall()}catch(e:Exception){log("Error","closePanel："+e.message);toast("关闭面板失败："+e.message)}}

private var orbTransitionOrigin:IntArray?=null
private fun addPanelFooter(box:LinearLayout){
box.setPadding(box.paddingLeft,box.paddingTop,box.paddingRight,0)
val handle=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL or Gravity.RIGHT;orientation=LinearLayout.HORIZONTAL;elevation=dp(8).toFloat()}
val brand=TextView(this).apply{text="BridgeFS";textSize=11f;setTextColor(Color.GRAY);gravity=Gravity.CENTER_VERTICAL;setPadding(0,0,dp(4),0)}
handle.addView(brand,LinearLayout.LayoutParams(-2,dp(56)))
handle.addView(ball,LinearLayout.LayoutParams(dp(40),dp(56)).also{it.marginEnd=dp(10)})
val touch=View.OnTouchListener{v,e->panelDragHandler(v,e)}
handle.setOnTouchListener(touch);brand.setOnTouchListener(touch);ball.setOnTouchListener(touch)
box.addView(handle,LinearLayout.LayoutParams(-1,dp(56)))
orbTransitionOrigin?.let{origin->
orbTransitionOrigin=null
box.post{
val target=IntArray(2);ball.getLocationOnScreen(target)
ball.translationX=(origin[0]-target[0]).toFloat()
ball.translationY=(origin[1]-target[1]).toFloat()
ball.animate().translationX(0f).translationY(0f).setDuration(250L).start()
}
}
}
private fun panelDragHandler(@Suppress("UNUSED_PARAMETER") v:View,e:MotionEvent):Boolean{
if(panel==null)return false
return when(e.actionMasked){
MotionEvent.ACTION_DOWN->{dragDownRawX=e.rawX;dragDownRawY=e.rawY;dragStartX=panelLpRef.x;dragStartY=panelLpRef.y;panelDragMoved=false;true}
MotionEvent.ACTION_MOVE->{val dx=e.rawX-dragDownRawX;val dy=e.rawY-dragDownRawY;if(kotlin.math.abs(dx)>dp(4)||kotlin.math.abs(dy)>dp(4))panelDragMoved=true;if(panelDragMoved){panelLpRef.x=(dragStartX+dx).toInt();panelLpRef.y=(dragStartY+dy).toInt();val sw=resources.displayMetrics.widthPixels;val sh=resources.displayMetrics.heightPixels;panelLpRef.x=panelLpRef.x.coerceIn(0,(sw-(panel?.width?:0)).coerceAtLeast(0));panelLpRef.y=panelLpRef.y.coerceIn(0,(sh-(panel?.height?:0)).coerceAtLeast(0));runCatching{wm.updateViewLayout(panel,panelLpRef)}};true}
MotionEvent.ACTION_UP->{if(!panelDragMoved){if(v===ball)closePanel();return true};val sw=resources.displayMetrics.widthPixels;val maxX=(sw-(panel?.width?:0)).coerceAtLeast(0);when{panelLpRef.x<=dp(5)->panelLpRef.x=0;panelLpRef.x>=maxX-dp(5)->panelLpRef.x=maxX};runCatching{panel?.let{wm.updateViewLayout(it,panelLpRef)}};true}
MotionEvent.ACTION_CANCEL->true
else->false
}
}
private var panelDragMoved=false
private var dragDownRawX=0f
private var dragDownRawY=0f
private var dragStartX=0
private var dragStartY=0

private fun findReceipt(v:View):TextView?{if(v is TextView&&v.text.toString()=="执行结果会显示在这里")return v;if(v is ViewGroup)for(i in 0 until v.childCount){val r=findReceipt(v.getChildAt(i));if(r!=null)return r};return null}
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
- 一次可以发多条指令，我会按顺序执行
- 执行结果会贴回来给你
""";copyText("BridgeFS说明书",text);toast("已复制到剪贴板")}
private fun showBrowser(){try{clearPanel();browserCurrent=root;log("UI","showBrowser");renderBrowser()}catch(e:Exception){log("Error","showBrowser："+e.message);toast("打开浏览器失败："+e.message)}}
private var browserCurrent:File?=null
private fun renderBrowser(){
clearPanel();log("UI","renderBrowser")
val current=browserCurrent?:root
val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(12));background=bg("#FFFFFF",16,null)}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val back=smallButton("←"){if(current.absolutePath==root.absolutePath)showPanel()else{browserCurrent=current.parentFile?:root;renderBrowser()}}
val rootBtn=TextView(this).apply{text=root.name.ifBlank{root.absolutePath};textSize=13f;gravity=17;setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;setTextColor(Color.DKGRAY);background=bg("#F1F5F9",8,null);setOnClickListener{showRootList()}}
val copy=smallButton("📋"){copyText("BridgeFS目录",browserListing(current));toast("已复制当前层")}
top.addView(back,LinearLayout.LayoutParams(dp(56),dp(40)));top.addView(rootBtn,LinearLayout.LayoutParams(0,dp(40),1f).also{it.marginStart=dp(6)});top.addView(copy,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(6)})
box.addView(top,LinearLayout.LayoutParams(-1,dp(48)))
val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
val children=current.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase(Locale.getDefault())}).orEmpty()
val first=children.take(50)
first.forEach{f->val row=TextView(this).apply{text=if(f.isDirectory)"📁 "+f.name else "📄 "+f.name;textSize=14f;setPadding(dp(8),dp(8),dp(8),dp(8));setOnClickListener{if(f.isDirectory){browserCurrent=f;renderBrowser()}else{copyText("BridgeFS路径",f.relativeTo(root).path);toast("已复制相对路径")}}};list.addView(row,LinearLayout.LayoutParams(-1,dp(40)))}
if(children.size>50)list.addView(TextView(this).apply{text="…等 "+(children.size-50)+" 项";textSize=13f;setTextColor(Color.GRAY);setPadding(dp(8),dp(8),dp(8),dp(8))})
box.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,dp(360)))
addPanelFooter(box);panel=box
val sw=resources.displayMetrics.widthPixels
panelLpRef=panelLp(dp(300),WindowManager.LayoutParams.WRAP_CONTENT);panelLpRef.gravity=Gravity.TOP or Gravity.LEFT;panelLpRef.x=(sw-dp(300)-dp(72)).coerceAtLeast(0);panelLpRef.y=ballLp.y
wm.addView(box,panelLpRef)
}
private fun browserListing(current:File):String{val entries=current.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase(Locale.getDefault())}).orEmpty();val first=entries.take(50);val files=entries.count{!it.isDirectory};val dirs=entries.count{it.isDirectory};return "📁 "+current.relativeToOrSelf(root).path+"/\n含 "+files+" 个文件、"+dirs+" 个文件夹：\n"+first.joinToString("\n"){f->"  "+(if(f.isDirectory)"📁" else "📄")+" "+f.name}+(if(entries.size>50)"\n…等 "+(entries.size-50)+" 项" else "")}
private fun showRootList(){clearPanel();log("UI","showRootList");val rs=(getSharedPreferences("bridgefs",0).getStringSet("root_paths",emptySet<String>())?:emptySet<String>()).toList();val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12));background=bg("#FFFFFF",16,null)};val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};val title=TextView(this).apply{text="选择根目录";textSize=15f;setTypeface(null,1)};val close=smallButton("×"){try{clearPanel();renderBrowser()}catch(e:Exception){log("Error","关闭根目录列表："+e.message);toast("关闭根目录列表失败："+e.message)}};top.addView(title,LinearLayout.LayoutParams(0,dp(40),1f));top.addView(close,LinearLayout.LayoutParams(dp(56),dp(40)));box.addView(top);/* panel drag is restricted to the footer handle */;val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};rs.forEachIndexed{index,path->val row=TextView(this).apply{text="📂 "+path;textSize=13f;setPadding(dp(10),dp(10),dp(10),dp(10));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;setOnClickListener{root=File(path);getSharedPreferences("bridgefs",0).edit().putString("root_path",root.absolutePath).apply();clearPanel();browserCurrent=root;renderBrowser()}};list.addView(row,LinearLayout.LayoutParams(-1,dp(48)).also{it.topMargin=if(index==0)dp(4) else dp(2)})};if(rs.isEmpty())list.addView(TextView(this).apply{text="暂无已保存的根目录";textSize=13f;setTextColor(Color.GRAY);setPadding(dp(10),dp(12),dp(10),dp(12))});box.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f));addPanelFooter(box);panel=box;panelLpRef=panelLp(dp(300),WindowManager.LayoutParams.WRAP_CONTENT);panelLpRef.gravity=Gravity.TOP or Gravity.LEFT;panelLpRef.x=(resources.displayMetrics.widthPixels-dp(300)-dp(72)).coerceAtLeast(0);panelLpRef.y=ballLp.y;wm.addView(box,panelLpRef)}
private fun runLogFile():File{val dir=File("/sdcard/BridgeFS/logs");return if(dir.exists()||dir.mkdirs())File(dir,"run.log")else File(getExternalFilesDir(null),"logs").apply{mkdirs()}.resolve("run.log")}
private fun log(module:String,message:String){if(!getSharedPreferences("bridgefs",0).getBoolean("run_log_enabled",true))return;val line=SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date())+" ["+module+"] "+message.replace("\n","\\n")+"\n";runCatching{val f=runLogFile();val old=if(f.isFile)f.readLines().takeLast(499)else emptyList();f.parentFile?.mkdirs();f.writeText((old+line).joinToString(""))}.onFailure{android.util.Log.e("BridgeFS","run log failed",it)}}
private fun drag(v:View,p:WindowManager.LayoutParams){var downRawX=0f;var downRawY=0f;var startX=0;var startY=0;var moved=false;val snapPx=dp(5);v.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{downRawX=e.rawX;downRawY=e.rawY;startX=p.x;startY=p.y;moved=false;v.alpha=1f;v.translationX=0f;true};MotionEvent.ACTION_MOVE->{val dx=e.rawX-downRawX;val dy=e.rawY-downRawY;if(kotlin.math.abs(dx)>dp(4)||kotlin.math.abs(dy)>dp(4))moved=true;val sw=resources.displayMetrics.widthPixels;val sh=resources.displayMetrics.heightPixels;p.x=(startX+dx.toInt()).coerceIn(0,(sw-v.width).coerceAtLeast(0));p.y=(startY+dy.toInt()).coerceIn(0,(sh-v.height).coerceAtLeast(0));wm.updateViewLayout(v,p);true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(!moved)v.performClick()else{val sw=resources.displayMetrics.widthPixels;val maxX=(sw-v.width).coerceAtLeast(0);when{p.x<=snapPx->{p.x=0;if(v===ball){v.translationX=-dp(28).toFloat();v.alpha=.7f}};p.x>=maxX-snapPx->{p.x=maxX;if(v===ball){v.translationX=dp(28).toFloat();v.alpha=.7f}};else->{v.translationX=0f;v.alpha=1f}};wm.updateViewLayout(v,p)};true};else->false}}}
private fun bg(fill:String,r:Int,stroke:String?)=GradientDrawable().apply{setColor(Color.parseColor(fill));cornerRadius=dp(r).toFloat();if(stroke!=null)setStroke(dp(1),Color.parseColor(stroke))}
private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
override fun onBind(i:Intent?)=null
override fun onDestroy(){clipboardCallback=null;commandInput=null;log("Service","onDestroy");clearPanel();if(::ball.isInitialized)runCatching{wm.removeView(ball)};running=false;super.onDestroy()}
}

class ClipboardReaderActivity:Activity(){
private var consumed=false
override fun onCreate(savedInstanceState:Bundle?){
super.onCreate(savedInstanceState)
overridePendingTransition(0,0)
window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
}
override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(!hasFocus||consumed)return;consumed=true;val text=runCatching{val cm=getSystemService(CLIPBOARD_SERVICE)as android.content.ClipboardManager;cm.primaryClip?.let{if(it.itemCount>0)it.getItemAt(0).coerceToText(this).toString()else""}?:""}.getOrDefault("");FileBridgeService.clipboardCallback?.invoke(text);overridePendingTransition(0,0);finish()}
override fun onDestroy(){overridePendingTransition(0,0);super.onDestroy()}
}
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
import java.util.concurrent.Executors
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FileBridgeService:Service(){
private lateinit var wm:WindowManager
private lateinit var ball:TextView
private var panel:LinearLayout?=null
private var commandInput:EditText?=null
private lateinit var root:File
private lateinit var ballLp:WindowManager.LayoutParams
private lateinit var panelLpRef:WindowManager.LayoutParams
private val handler=Handler(Looper.getMainLooper())
private val commandExecutor=Executors.newSingleThreadExecutor()
private val logExecutor=Executors.newSingleThreadExecutor()
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
ball=TextView(this).apply{
text="📁";textSize=22f;gravity=17;alpha=.7f;background=bg("#CC1E293B",28,"#6366F1")
setOnClickListener{if(alpha<1f){alpha=1f;translationX=0f}else showPanel()}
}
ballLp=lp(dp(56),dp(56));ballLp.gravity=Gravity.TOP or Gravity.LEFT
ballLp.x=resources.displayMetrics.widthPixels-dp(56);ballLp.y=(resources.displayMetrics.heightPixels*.65).toInt()
drag(ball,ballLp);wm.addView(ball,ballLp)
}

private fun showPanel(){
try{
clearPanel();ball.visibility=View.GONE;log("UI","showPanel")
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
setPadding(d(R.dimen.bridgefs_panel_padding),d(R.dimen.bridgefs_panel_padding),d(R.dimen.bridgefs_panel_padding),d(R.dimen.bridgefs_panel_padding))
background=tokenBg(R.color.bridgefs_panel_background,R.dimen.bridgefs_panel_corner_radius,R.color.bridgefs_panel_border)
}
val address=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val dir=TextView(this).apply{
text="📂 "+root.name.ifBlank{root.absolutePath};textSize=d(R.dimen.bridgefs_body_text_size)/resources.displayMetrics.scaledDensity
setTextColor(color(R.color.bridgefs_accent));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE
setOnClickListener{showBrowser()}
}
address.addView(dir,LinearLayout.LayoutParams(0,d(R.dimen.bridgefs_row_height),1f))
val menuButton=smallButton("⋮"){showPanelMenu(it)}
address.addView(menuButton,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),d(R.dimen.bridgefs_button_height)).also{it.marginStart=d(R.dimen.bridgefs_section_spacing)})
box.addView(address,LinearLayout.LayoutParams(-1,d(R.dimen.bridgefs_row_height)))
installPanelDrag(address)

val input=EditText(this).apply{
hint="粘贴 AI 指令到这里..."
textSize=d(R.dimen.bridgefs_body_text_size)/resources.displayMetrics.scaledDensity
gravity=Gravity.TOP
inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
setPadding(dp(10),dp(8),dp(38),dp(8))
background=tokenBg(R.color.bridgefs_panel_background,R.dimen.bridgefs_input_corner_radius,R.color.bridgefs_input_border)
isFocusable=false;isFocusableInTouchMode=false;isCursorVisible=false
setOnClickListener{showCommandInputDialog(this)}
}
commandInput=input
clipboardCallback={text->handler.post{commandInput?.setText(text);commandInput?.setSelection(commandInput?.text?.length?:0)}}
val inputFrame=FrameLayout(this).apply{
addView(input,FrameLayout.LayoutParams(-1,-1))
addView(TextView(this@FileBridgeService).apply{
text="!";textSize=14f;gravity=Gravity.CENTER;setTextColor(color(R.color.bridgefs_accent))
background=tokenBg(R.color.bridgefs_button_background,R.dimen.bridgefs_button_corner_radius,null)
contentDescription="编辑指令";setOnClickListener{showCommandInputDialog(input)}
},FrameLayout.LayoutParams(dp(28),dp(28),Gravity.TOP or Gravity.RIGHT).also{it.topMargin=dp(6);it.rightMargin=dp(6)})
}
val paste=smallButton("粘贴"){val intent=Intent(this,ClipboardReaderActivity::class.java).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)};startActivity(intent)}
var runButton:Button?=null
val run=smallButton("执行"){
val raw=input.text.toString()
log("Command","收到："+raw.replace("\n","\\n").take(500))
runButton?.isEnabled=false
findReceipt(box)?.apply{text="正在执行…";setTextColor(color(R.color.bridgefs_text_secondary))}
commandExecutor.execute{
val cs=CommandParser.parse(raw)
val results=runCatching{if(cs.isEmpty())listOf("未发现可执行指令")else cs.map{CommandExecutor(root,this).execute(it)}}.getOrElse{listOf("指令执行失败："+(it.message?:it.javaClass.simpleName))}
log("Command","执行 "+cs.size+" 条指令："+if(results.none{it.contains("✗")})"成功" else "失败")
handler.post{
runButton?.isEnabled=true
if(panel===box)findReceipt(box)?.apply{text=results.joinToString("\n\n");setTextColor(Color.DKGRAY)}
}
}
}
runButton=run
val inputSide=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
addView(paste,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),d(R.dimen.bridgefs_button_height)))
addView(run,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),d(R.dimen.bridgefs_button_height)).also{it.topMargin=dp(6)})
}
val inputRow=LinearLayout(this).apply{
gravity=Gravity.BOTTOM
addView(inputFrame,LinearLayout.LayoutParams(0,dp(86),1f))
addView(inputSide,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),dp(86)).also{it.marginStart=d(R.dimen.bridgefs_section_spacing)})
}
box.addView(inputRow,LinearLayout.LayoutParams(-1,dp(86)).also{it.topMargin=d(R.dimen.bridgefs_section_spacing)})

val receipt=TextView(this).apply{
text="执行结果会显示在这里";textSize=11f;typeface=android.graphics.Typeface.MONOSPACE
setPadding(dp(10),dp(8),dp(10),dp(8));setTextColor(color(R.color.bridgefs_text_secondary))
background=tokenBg(R.color.bridgefs_result_background,R.dimen.bridgefs_card_corner_radius,null)
}
val resultScroll=ScrollView(this).apply{isFillViewport=true;addView(receipt,ScrollView.LayoutParams(-1,-2))}
val copyReceipt=smallButton("复制"){copyText("BridgeFS回执",receipt.text.toString())}
val receiptRow=LinearLayout(this).apply{
gravity=Gravity.TOP
addView(resultScroll,LinearLayout.LayoutParams(0,dp(100),1f))
addView(copyReceipt,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),d(R.dimen.bridgefs_button_height)).also{it.marginStart=d(R.dimen.bridgefs_section_spacing)})
}
box.addView(receiptRow,LinearLayout.LayoutParams(-1,dp(100)).also{it.topMargin=d(R.dimen.bridgefs_section_spacing)})

val footer=TextView(this).apply{
text="BridgeFS";textSize=d(R.dimen.bridgefs_aux_text_size)/resources.displayMetrics.scaledDensity
setTextColor(color(R.color.bridgefs_text_secondary));gravity=Gravity.BOTTOM or Gravity.LEFT
}
box.addView(footer,LinearLayout.LayoutParams(-1,dp(56)).also{it.topMargin=d(R.dimen.bridgefs_section_spacing)})

panel=box
val width=panelWidthPx()
panelLpRef=panelLp(width,WindowManager.LayoutParams.WRAP_CONTENT)
panelLpRef.gravity=Gravity.TOP or Gravity.LEFT
panelLpRef.x=panelX(width);panelLpRef.y=ballLp.y
panelLpRef.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
wm.addView(box,panelLpRef)
box.post{
val maxH=(resources.displayMetrics.heightPixels*.65f).toInt()
if(box.height>maxH){panelLpRef.height=maxH;runCatching{wm.updateViewLayout(box,panelLpRef)}}
}
}catch(e:Exception){log("Error","showPanel："+e.message);toast("打开面板失败："+e.message)}
}

private fun showPanelMenu(anchor:View){
PopupMenu(this,anchor).apply{
menu.add("选择目录").setOnMenuItemClickListener{showBrowser();true}
menu.add("复制根目录路径").setOnMenuItemClickListener{copyText("BridgeFS路径",root.absolutePath);true}
menu.add("帮助").setOnMenuItemClickListener{copyInstructions();true}
menu.add("关闭").setOnMenuItemClickListener{closePanel();true}
show()
}
}

private var inputDialog:Dialog?=null
private fun releaseInputFocus(){
runCatching{inputDialog?.dismiss();inputDialog=null}
}
private fun toast(msg:String){handler.post{Toast.makeText(this,msg,Toast.LENGTH_SHORT).show()}}
private fun showCommandInputDialog(target:EditText){
 if(inputDialog?.isShowing==true)return
 runCatching{
 val dialog=Dialog(this)
 inputDialog=dialog
 dialog.setOnDismissListener{inputDialog=null}
 dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
 val box=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL
  setPadding(dp(16),dp(16),dp(16),dp(16))
  background=tokenBg(R.color.bridgefs_panel_background,R.dimen.bridgefs_panel_corner_radius,R.color.bridgefs_panel_border)
 }
 val edit=EditText(this).apply{
  setText(target.text);setSelection(text.length);hint="输入 AI 指令..."
  textSize=14f;gravity=Gravity.TOP;minLines=6;maxLines=12
  inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
  background=tokenBg(R.color.bridgefs_panel_background,R.dimen.bridgefs_input_corner_radius,R.color.bridgefs_input_border)
  setPadding(dp(10),dp(8),dp(10),dp(8))
 }
 box.addView(edit,LinearLayout.LayoutParams(-1,dp(180)))
 val actions=LinearLayout(this).apply{gravity=Gravity.END}
 val cancel=smallButton("取消"){dialog.dismiss()}
 val ok=smallButton("确定"){target.setText(edit.text.toString());target.setSelection(target.text.length);dialog.dismiss()}
 actions.addView(cancel,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),d(R.dimen.bridgefs_button_height)))
 actions.addView(ok,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),d(R.dimen.bridgefs_button_height)).also{it.marginStart=d(R.dimen.bridgefs_section_spacing)})
 box.addView(actions,LinearLayout.LayoutParams(-1,dp(48)).also{it.topMargin=d(R.dimen.bridgefs_section_spacing)})
 dialog.setContentView(box)
 dialog.show()
 dialog.window?.let{
  it.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
  it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
  it.setLayout(panelWidthPx(),minOf(WindowManager.LayoutParams.WRAP_CONTENT,(resources.displayMetrics.heightPixels*.55f).toInt()))
 }
 edit.requestFocus()
 edit.post{(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT)}
 }.onFailure{log("Error","showCommandInputDialog："+it.message);toast("打开指令输入框失败")}
}

private fun clearPanel(){
runCatching{panel?.let{wm.removeView(it)}}
panel=null
log("UI","clearPanel")
}
private fun closePanel(){try{log("UI","closePanel");releaseInputFocus();clipboardCallback=null;commandInput=null;clearPanel();handler.removeCallbacksAndMessages(null);ball.visibility=View.VISIBLE;ball.alpha=.7f;ball.translationX=0f}catch(e:Exception){log("Error","closePanel："+e.message);toast("关闭面板失败："+e.message)}}

private fun installPanelDrag(v:View){v.setOnTouchListener{_,e->panelDragHandler(v,e)}}
private fun panelDragHandler(@Suppress("UNUSED_PARAMETER") v:View,e:MotionEvent):Boolean{
if(panel==null)return false
if(panelDragTarget==null)panelDragTarget=panel
return when(e.actionMasked){
MotionEvent.ACTION_DOWN->{dragDownRawX=e.rawX;dragDownRawY=e.rawY;dragStartX=panelLpRef.x;dragStartY=panelLpRef.y;true}
MotionEvent.ACTION_MOVE->{panelLpRef.x=(dragStartX+(e.rawX-dragDownRawX)).toInt();panelLpRef.y=(dragStartY+(e.rawY-dragDownRawY)).toInt();val sw=resources.displayMetrics.widthPixels;val sh=resources.displayMetrics.heightPixels;panelLpRef.x=panelLpRef.x.coerceIn(0,(sw-(panel?.width?:0)).coerceAtLeast(0));panelLpRef.y=panelLpRef.y.coerceIn(0,(sh-(panel?.height?:0)).coerceAtLeast(0));runCatching{wm.updateViewLayout(panel,panelLpRef)};true}
MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{val sw=resources.displayMetrics.widthPixels;val maxX=(sw-(panel?.width?:0)).coerceAtLeast(0);when{panelLpRef.x<=dp(5)->panelLpRef.x=0;panelLpRef.x>=maxX-dp(5)->panelLpRef.x=maxX};runCatching{wm.updateViewLayout(panel,panelLpRef)};true}
else->false
}
}
private var panelDragTarget:LinearLayout?=null
private var dragDownRawX=0f
private var dragDownRawY=0f
private var dragStartX=0
private var dragStartY=0

private fun findReceipt(v:View):TextView?{if(v is TextView&&v.text.toString()=="执行结果会显示在这里")return v;if(v is ViewGroup)for(i in 0 until v.childCount){val r=findReceipt(v.getChildAt(i));if(r!=null)return r};return null}
private fun smallButton(label:String,onClick:()->Unit)=Button(this).apply{
text=label;textSize=d(R.dimen.bridgefs_body_text_size)/resources.displayMetrics.scaledDensity
minWidth=0;minimumWidth=0;setPadding(0,0,0,0)
setTextColor(color(R.color.bridgefs_text_primary))
background=tokenBg(R.color.bridgefs_button_background,R.dimen.bridgefs_button_corner_radius,null)
setOnClickListener{onClick()}
}
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
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
setPadding(d(R.dimen.bridgefs_panel_padding),d(R.dimen.bridgefs_section_spacing),d(R.dimen.bridgefs_panel_padding),d(R.dimen.bridgefs_panel_padding))
background=tokenBg(R.color.bridgefs_panel_background,R.dimen.bridgefs_panel_corner_radius,R.color.bridgefs_panel_border)
}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val back=smallButton("←"){if(current.absolutePath==root.absolutePath)showPanel()else{browserCurrent=current.parentFile?:root;renderBrowser()}}
val rootBtn=TextView(this).apply{
text=current.name.ifBlank{current.absolutePath};textSize=13f;gravity=Gravity.CENTER
setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE
setTextColor(color(R.color.bridgefs_text_primary))
background=tokenBg(R.color.bridgefs_button_background,R.dimen.bridgefs_card_corner_radius,null)
setOnClickListener{showRootList()}
}
val copy=smallButton("复制"){copyText("BridgeFS目录",browserListing(current));toast("已复制当前层")}
top.addView(back,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),d(R.dimen.bridgefs_button_height)))
top.addView(rootBtn,LinearLayout.LayoutParams(0,d(R.dimen.bridgefs_button_height),1f).also{it.marginStart=d(R.dimen.bridgefs_section_spacing)})
top.addView(copy,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),d(R.dimen.bridgefs_button_height)).also{it.marginStart=d(R.dimen.bridgefs_section_spacing)})
box.addView(top,LinearLayout.LayoutParams(-1,d(R.dimen.bridgefs_list_row_height)))
val children=current.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase(Locale.getDefault())}).orEmpty()
val rows=children.take(50).map< File, Any >{it}.toMutableList<Any>()
if(children.size>50)rows.add("…等 "+(children.size-50)+" 项")
val list=RecyclerView(this).apply{
layoutManager=LinearLayoutManager(this@FileBridgeService)
adapter=BrowserAdapter(rows)
isVerticalScrollBarEnabled=true
}
val listHeight=minOf(d(R.dimen.bridgefs_file_list_max_height),maxOf(d(R.dimen.bridgefs_list_row_height),rows.size*d(R.dimen.bridgefs_list_row_height)))
box.addView(list,LinearLayout.LayoutParams(-1,listHeight).also{it.topMargin=d(R.dimen.bridgefs_section_spacing)})
panel=box
val width=panelWidthPx()
panelLpRef=panelLp(width,WindowManager.LayoutParams.WRAP_CONTENT)
panelLpRef.gravity=Gravity.TOP or Gravity.LEFT
panelLpRef.x=panelX(width);panelLpRef.y=ballLp.y
wm.addView(box,panelLpRef)
}

private inner class BrowserAdapter(private val rows:List<Any>):RecyclerView.Adapter<BrowserAdapter.Holder>(){
inner class Holder(val label:TextView):RecyclerView.ViewHolder(label)
override fun getItemCount()=rows.size
override fun onCreateViewHolder(parent:android.view.ViewGroup,viewType:Int):Holder{
val label=TextView(this@FileBridgeService).apply{
textSize=13f;gravity=Gravity.CENTER_VERTICAL
setPadding(dp(8),0,dp(8),0)
setTextColor(color(R.color.bridgefs_text_primary))
}
return Holder(label)
}
override fun onBindViewHolder(holder:Holder,position:Int){
val item=rows[position]
if(item is File){
holder.label.text=(if(item.isDirectory)"📁 " else "📄 ")+item.name
holder.label.setTextColor(color(R.color.bridgefs_text_primary))
holder.label.setOnClickListener{
if(item.isDirectory){browserCurrent=item;renderBrowser()}
else{copyText("BridgeFS路径",item.relativeTo(root).path);toast("已复制相对路径")}
}
}else{
holder.label.text=item.toString()
holder.label.setTextColor(color(R.color.bridgefs_text_secondary))
holder.label.setOnClickListener(null)
}
}
override fun onCreateViewHolder(parent:android.view.ViewGroup,viewType:Int):Holder=Holder(TextView(this@FileBridgeService))
}
private fun browserListing(current:File):String{val entries=current.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase(Locale.getDefault())}).orEmpty();val first=entries.take(50);val files=entries.count{!it.isDirectory};val dirs=entries.count{it.isDirectory};return "📁 "+current.relativeToOrSelf(root).path+"/\n含 "+files+" 个文件、"+dirs+" 个文件夹：\n"+first.joinToString("\n"){f->"  "+(if(f.isDirectory)"📁" else "📄")+" "+f.name}+(if(entries.size>50)"\n…等 "+(entries.size-50)+" 项" else "")}
private fun showRootList(){
clearPanel();log("UI","showRootList")
val rs=(getSharedPreferences("bridgefs",0).getStringSet("root_paths",emptySet<String>())?:emptySet<String>()).toList()
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
setPadding(d(R.dimen.bridgefs_panel_padding),d(R.dimen.bridgefs_panel_padding),d(R.dimen.bridgefs_panel_padding),d(R.dimen.bridgefs_panel_padding))
background=tokenBg(R.color.bridgefs_panel_background,R.dimen.bridgefs_panel_corner_radius,R.color.bridgefs_panel_border)
}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val title=TextView(this).apply{text="选择根目录";textSize=d(R.dimen.bridgefs_title_text_size)/resources.displayMetrics.scaledDensity;setTypeface(null,1);setTextColor(color(R.color.bridgefs_text_primary))}
val close=smallButton("×"){try{clearPanel();renderBrowser()}catch(e:Exception){log("Error","关闭根目录列表："+e.message);toast("关闭根目录列表失败："+e.message)}}
top.addView(title,LinearLayout.LayoutParams(0,d(R.dimen.bridgefs_row_height),1f))
top.addView(close,LinearLayout.LayoutParams(d(R.dimen.bridgefs_button_width),d(R.dimen.bridgefs_button_height)))
box.addView(top);installPanelDrag(top)
val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
rs.forEachIndexed{index,path->
val row=TextView(this).apply{
text="📂 "+path;textSize=d(R.dimen.bridgefs_body_text_size)/resources.displayMetrics.scaledDensity
setPadding(dp(10),0,dp(10),0);gravity=Gravity.CENTER_VERTICAL
setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE
setTextColor(color(R.color.bridgefs_text_primary))
background=tokenBg(R.color.bridgefs_button_background,R.dimen.bridgefs_card_corner_radius,null)
setOnClickListener{
root=File(path)
getSharedPreferences("bridgefs",0).edit().putString("root_path",root.absolutePath).apply()
clearPanel();browserCurrent=root;renderBrowser()
}
}
list.addView(row,LinearLayout.LayoutParams(-1,d(R.dimen.bridgefs_list_row_height)).also{if(index>0)it.topMargin=d(R.dimen.bridgefs_section_spacing)})
}
if(rs.isEmpty())list.addView(TextView(this).apply{text="暂无已保存的根目录";textSize=13f;setTextColor(color(R.color.bridgefs_text_secondary));gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(-1,d(R.dimen.bridgefs_list_row_height)))
val screenLimit=(resources.displayMetrics.heightPixels*.55f).toInt()-dp(88)
val rowsHeight=rs.size*d(R.dimen.bridgefs_list_row_height)+maxOf(0,rs.size-1)*d(R.dimen.bridgefs_section_spacing)
val viewport=minOf(rowsHeight,screenLimit.coerceAtLeast(d(R.dimen.bridgefs_list_row_height)))
box.addView(ScrollView(this).apply{isFillViewport=true;addView(list)},LinearLayout.LayoutParams(-1,viewport).also{it.topMargin=d(R.dimen.bridgefs_section_spacing)})
panel=box
val width=panelWidthPx()
panelLpRef=panelLp(width,WindowManager.LayoutParams.WRAP_CONTENT)
panelLpRef.gravity=Gravity.TOP or Gravity.LEFT
panelLpRef.x=panelX(width);panelLpRef.y=ballLp.y
wm.addView(box,panelLpRef)
}
private fun readRunLog():String=runCatching{val f=runLogFile();if(f.isFile)f.readLines().takeLast(120).joinToString("\n")else""}.getOrDefault("")
private fun runLogFile():File{val dir=File("/sdcard/BridgeFS/logs");return if(dir.exists()||dir.mkdirs())File(dir,"run.log")else File(getExternalFilesDir(null),"logs").apply{mkdirs()}.resolve("run.log")}
private fun log(module:String,message:String){
if(!getSharedPreferences("bridgefs",0).getBoolean("run_log_enabled",true))return
val line=SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date())+" ["+module+"] "+message.replace("\n","\\n")+"\n"
runCatching{logExecutor.execute{
runCatching{val f=runLogFile();val old=if(f.isFile)f.readLines().takeLast(499)else emptyList();f.parentFile?.mkdirs();f.writeText((old+line).joinToString(""))}.onFailure{android.util.Log.e("BridgeFS","run log failed",it)}
}}
}
private fun drag(v:View,p:WindowManager.LayoutParams){var downRawX=0f;var downRawY=0f;var startX=0;var startY=0;var moved=false;val snapPx=dp(5);v.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{downRawX=e.rawX;downRawY=e.rawY;startX=p.x;startY=p.y;moved=false;v.alpha=1f;v.translationX=0f;true};MotionEvent.ACTION_MOVE->{val dx=e.rawX-downRawX;val dy=e.rawY-downRawY;if(kotlin.math.abs(dx)>dp(4)||kotlin.math.abs(dy)>dp(4))moved=true;val sw=resources.displayMetrics.widthPixels;val sh=resources.displayMetrics.heightPixels;p.x=(startX+dx.toInt()).coerceIn(0,(sw-v.width).coerceAtLeast(0));p.y=(startY+dy.toInt()).coerceIn(0,(sh-v.height).coerceAtLeast(0));wm.updateViewLayout(v,p);true};MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(!moved)v.performClick()else{val sw=resources.displayMetrics.widthPixels;val maxX=(sw-v.width).coerceAtLeast(0);when{p.x<=snapPx->{p.x=0;if(v===ball){v.translationX=-dp(28).toFloat();v.alpha=.7f}};p.x>=maxX-snapPx->{p.x=maxX;if(v===ball){v.translationX=dp(28).toFloat();v.alpha=.7f}};else->{v.translationX=0f;v.alpha=1f}};wm.updateViewLayout(v,p)};true};else->false}}}
private fun d(id:Int)=resources.getDimensionPixelSize(id)
private fun color(id:Int)=ContextCompat.getColor(this,id)
private fun tokenBg(fill:Int,radius:Int,stroke:Int?)=GradientDrawable().apply{
setColor(color(fill));cornerRadius=resources.getDimension(radius)
if(stroke!=null)setStroke(dp(1),color(stroke))
}
private fun panelWidthPx():Int{
val desiredDp=(resources.configuration.screenWidthDp*.65f).toInt().coerceIn(220,300)
return dp(desiredDp).coerceAtMost((resources.displayMetrics.widthPixels-dp(16)).coerceAtLeast(dp(1)))
}
private fun panelX(width:Int)=(resources.displayMetrics.widthPixels-width-dp(72)).coerceAtLeast(0)
private fun bg(fill:String,r:Int,stroke:String?)=GradientDrawable().apply{setColor(Color.parseColor(fill));cornerRadius=dp(r).toFloat();if(stroke!=null)setStroke(dp(1),Color.parseColor(stroke))}
private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
override fun onBind(i:Intent?)=null
override fun onDestroy(){
clipboardCallback=null;commandInput=null;log("Service","onDestroy")
commandExecutor.shutdownNow();logExecutor.shutdown()
clearPanel();if(::ball.isInitialized)runCatching{wm.removeView(ball)}
running=false;super.onDestroy()
}
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
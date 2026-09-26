package com.owla19s.bridgefs
import android.animation.ValueAnimator
import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
private lateinit var bottom_bar:LinearLayout
private lateinit var ball:PillOrbView
private lateinit var bottomBarBrand:TextView
private var panel:LinearLayout?=null
private var isRenderingBrowser=false
private var isRenderingMainPanel=false
private var isUpdatingBottomBarLayout=false
private var isUpdatingPanelLayout=false
private var isClosingPanel=false
private var bottomBarEdgeHidden=0
private var bottomBarRevealTargetX:Int?=null
private var bottomBarSnapAnimator:ValueAnimator?=null
private var commandInput:EditText?=null
private lateinit var root:File
private lateinit var bottomBarLp:WindowManager.LayoutParams
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
private fun mainPanelLp(w:Int,h:Int)=WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,-3)

private fun showBall(){
val previousHiddenEdge=bottomBarEdgeHidden
bottomBarSnapAnimator?.cancel();bottomBarSnapAnimator=null;bottomBarRevealTargetX=null
if(!::bottom_bar.isInitialized){
ball=PillOrbView(this)
bottom_bar=LinearLayout(this).apply{
orientation=LinearLayout.HORIZONTAL
gravity=Gravity.CENTER_VERTICAL or Gravity.RIGHT
isClickable=true
setPadding(dp(10),0,dp(10),0)
bottomBarBrand=TextView(this@FileBridgeService).apply{text="BridgeFS";textSize=11f;setTextColor(resources.getColor(R.color.bridgefs_text_secondary));gravity=Gravity.CENTER_VERTICAL;visibility=View.GONE}
addView(bottomBarBrand,LinearLayout.LayoutParams(-2,resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height)))
addView(ball,LinearLayout.LayoutParams(dp(32),resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height)))
setOnTouchListener{v,e->bottomBarTouchHandler(v,e)}
}
bottomBarLp=lp(WindowManager.LayoutParams.WRAP_CONTENT,resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height))
bottomBarLp.gravity=Gravity.TOP or Gravity.LEFT
bottom_bar.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height),View.MeasureSpec.EXACTLY))
bottomBarLp.x=(resources.displayMetrics.widthPixels-bottom_bar.measuredWidth).coerceAtLeast(0)
bottomBarLp.y=(resources.displayMetrics.heightPixels/2-dp(28)).coerceAtLeast(0)
}
val oldParent=bottom_bar.parent
if(oldParent is ViewGroup)oldParent.removeView(bottom_bar)
else if(oldParent!=null||bottom_bar.isAttachedToWindow)runCatching{wm.removeView(bottom_bar)}
bottom_bar.visibility=View.VISIBLE;bottom_bar.alpha=1f
bottomBarBrand.visibility=View.GONE
(ball.layoutParams as? LinearLayout.LayoutParams)?.let{it.marginStart=0;ball.layoutParams=it}
bottom_bar.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),View.MeasureSpec.makeMeasureSpec(resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height),View.MeasureSpec.EXACTLY))
val maxFloatingX=(resources.displayMetrics.widthPixels-bottom_bar.measuredWidth).coerceAtLeast(0)
bottomBarLp.x=when{previousHiddenEdge<0->0;previousHiddenEdge>0->maxFloatingX;else->bottomBarLp.x.coerceIn(0,maxFloatingX)}
bottom_bar.requestLayout()
ball.visibility=View.VISIBLE;ball.alpha=1f;ball.translationX=0f;ball.translationY=0f
bottom_bar.translationX=0f;bottom_bar.translationY=0f
bottomBarEdgeHidden=0
bottomBarBrand.invalidate();ball.invalidate();bottom_bar.invalidate()
bottom_bar.setOnTouchListener{v,e->bottomBarTouchHandler(v,e)}
wm.addView(bottom_bar,bottomBarLp)
ensureRobotState(false)
}

private fun ensureRobotState(expanded:Boolean){
if(!::bottom_bar.isInitialized)return
bottom_bar.visibility=View.VISIBLE
bottomBarBrand.visibility=if(expanded)View.VISIBLE else View.GONE
ball.visibility=View.VISIBLE
if(expanded){bottomBarBrand.bringToFront();ball.bringToFront()}
bottomBarBrand.invalidate();ball.invalidate();bottom_bar.invalidate()
}

private fun showPanel(){
if(isRenderingMainPanel)return
isRenderingMainPanel=true
try{
bottomBarRevealTargetX?.let{target->bottomBarSnapAnimator?.cancel();bottomBarSnapAnimator=null;bottomBarLp.x=target;bottomBarRevealTargetX=null;updateBottomBarWindow()}
val wasFloating=bottom_bar.isAttachedToWindow&&bottom_bar.parent !is ViewGroup
val origin=if(wasFloating)IntArray(2).also{bottom_bar.getLocationOnScreen(it)}else null
clearPanel()
orbTransitionOrigin=origin
if(wasFloating)runCatching{wm.removeView(bottom_bar)}
log("UI","showPanel")
val box=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
setPadding(dp(8),dp(8),dp(8),dp(8))
background=bg("#FFFFFF",16,"#E0E0E0")
elevation=dp(8).toFloat()
}
val address=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val dir=TextView(this).apply{
text=root.absolutePath
textSize=15f;setTextColor(resources.getColor(R.color.bridgefs_text_primary));setSingleLine(true)
setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder,0,0,0);compoundDrawablePadding=dp(6)
ellipsize=android.text.TextUtils.TruncateAt.MIDDLE
setOnClickListener{showBrowser()}
setOnLongClickListener{copyText("BridgeFS路径",root.absolutePath);toast("已复制路径");true}
}
address.addView(dir,LinearLayout.LayoutParams(-1,dp(36)))
box.addView(address,LinearLayout.LayoutParams(-1,dp(36)))
// Main panel movement is bound only to the bottom bar.
val input=EditText(this).apply{
setText("")
hint="粘贴 AI 指令到这里..."
textSize=13f;gravity=Gravity.TOP
inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
setPadding(dp(10),dp(8),dp(42),dp(8));background=bg("#F5F6F8",10,"#E0E0E0")
isFocusable=false;isFocusableInTouchMode=false;showSoftInputOnFocus=false
setOnClickListener{showCommandInputDialog(this)}
}
commandInput=input
clipboardCallback={text->handler.post{commandInput?.setText(text);commandInput?.setSelection(commandInput?.text?.length?:0)}}
val inputFrame=FrameLayout(this).apply{
addView(input,FrameLayout.LayoutParams(-1,-1))
addView(TextView(this@FileBridgeService).apply{
text="!";textSize=14f;setTypeface(null,1);gravity=Gravity.CENTER;setTextColor(Color.WHITE)
contentDescription="指令帮助";background=bg("#FF7A00",14,null)
setOnClickListener{showHelpDialog()}
},FrameLayout.LayoutParams(dp(32),dp(32),Gravity.TOP or Gravity.RIGHT).also{it.topMargin=dp(4);it.rightMargin=dp(4)})
}
val paste=mainButton("粘贴"){val intent=Intent(this,ClipboardReaderActivity::class.java).apply{addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)};startActivity(intent)}
val run=mainButton("执行"){
val raw=input.text.toString();log("Command","收到："+raw.replace("\n","\\n").take(500))
val cs=CommandParser.parse(raw)
val results=if(cs.isEmpty())listOf("未发现可执行指令")else cs.map{CommandExecutor(root,this).execute(it)}
findReceipt(box)?.let{it.text=results.joinToString("\n\n");it.setTextColor(Color.DKGRAY)}
log("Command","执行 "+cs.size+" 条指令："+if(results.none{it.contains("✗")})"成功" else "失败")
}
val inputSide=LinearLayout(this).apply{
orientation=LinearLayout.VERTICAL
addView(paste,LinearLayout.LayoutParams(dp(56),dp(40)))
addView(run,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.topMargin=dp(5)})
}
val inputRow=LinearLayout(this).apply{
gravity=Gravity.BOTTOM
addView(inputFrame,LinearLayout.LayoutParams(0,dp(85),1f))
addView(inputSide,LinearLayout.LayoutParams(dp(56),dp(85)).also{it.marginStart=dp(8)})
}
box.addView(inputRow,LinearLayout.LayoutParams(-1,dp(85)).also{it.topMargin=dp(8)})

val receipt=TextView(this).apply{
text="执行结果会显示在这里";textSize=11f;typeface=android.graphics.Typeface.MONOSPACE
setPadding(dp(10),dp(8),dp(10),dp(8));setTextColor(resources.getColor(R.color.bridgefs_text_secondary));background=bg("#F5F6F8",10,null);minHeight=dp(40)
}
val resultScroll=object:ScrollView(this){
override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(dp(100),View.MeasureSpec.AT_MOST))
}
}.apply{
isFillViewport=false
isVerticalScrollBarEnabled=true;scrollBarSize=dp(2)
addView(receipt,FrameLayout.LayoutParams(-1,-2))
}
val copyReceipt=mainButton("复制"){copyText("BridgeFS回执",receipt.text.toString())}
val receiptRow=LinearLayout(this).apply{
gravity=Gravity.TOP
addView(resultScroll,LinearLayout.LayoutParams(0,-2,1f))
addView(copyReceipt,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(8)})
}
box.addView(receiptRow,LinearLayout.LayoutParams(-1,-2).also{it.topMargin=dp(8)})

addPanelFooter(box)
check(bottom_bar.parent===box&&bottomBarBrand.parent===bottom_bar&&ball.parent===bottom_bar){"bottom bar children were not attached"}
box.setPadding(box.paddingLeft,dp(8),box.paddingRight,dp(8))
(bottom_bar.layoutParams as? LinearLayout.LayoutParams)?.let{it.topMargin=dp(8);bottom_bar.layoutParams=it}
ensureRobotState(true)
panel=box
box.setOnTouchListener{_,event->
if(event.actionMasked==MotionEvent.ACTION_OUTSIDE){closePanel();true}else false
}
val sw=resources.displayMetrics.widthPixels
val panelWidth=resources.getDimensionPixelSize(R.dimen.panel_width)
panelLpRef=mainPanelLp(panelWidth,WindowManager.LayoutParams.WRAP_CONTENT)
panelLpRef.gravity=Gravity.TOP or Gravity.LEFT
panelLpRef.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
val barCenter=bottomBarLp.x+bottom_bar.width/2
panelLpRef.x=if(barCenter<sw/2)dp(8) else (sw-panelWidth-dp(8)).coerceAtLeast(0)
panelLpRef.y=dp(24)
wm.addView(box,panelLpRef)
}catch(e:Exception){log("Error","showPanel："+e.message);runCatching{clearPanel();showBall()};toast("打开面板失败："+e.message)}finally{isRenderingMainPanel=false}
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
val current=panel
panel=null
runCatching{
if(::bottom_bar.isInitialized){
val oldParent=bottom_bar.parent
if(oldParent is ViewGroup)oldParent.removeView(bottom_bar)
}
current?.let{container->
runCatching{wm.removeView(container)}
container.removeAllViews()
}
}
log("UI","clearPanel")
}
private fun closePanel(){if(isClosingPanel)return;isClosingPanel=true;try{log("UI","closePanel");releaseInputFocus();clipboardCallback=null;commandInput=null;clearPanel();handler.removeCallbacksAndMessages(null);showBall();ensureRobotState(false)}catch(e:Exception){log("Error","closePanel："+e.message);toast("关闭面板失败："+e.message)}finally{isClosingPanel=false}}

private var orbTransitionOrigin:IntArray?=null
private fun addPanelFooter(box:LinearLayout){
if(Looper.myLooper()!=Looper.getMainLooper()){
handler.post{addPanelFooter(box)}
return
}
try{
val height=resources.getDimensionPixelSize(R.dimen.bridgefs_bottom_bar_height)
box.setPadding(box.paddingLeft,box.paddingTop,box.paddingRight,0)
val oldParent=bottom_bar.parent
if(oldParent!=null&&oldParent!==box){
if(oldParent is ViewGroup)oldParent.removeView(bottom_bar)
else runCatching{wm.removeView(bottom_bar)}
}
if(bottom_bar.parent==null&&bottom_bar.isAttachedToWindow)runCatching{wm.removeView(bottom_bar)}
bottom_bar.visibility=View.VISIBLE
bottomBarBrand.visibility=View.VISIBLE
(ball.layoutParams as? LinearLayout.LayoutParams)?.let{it.marginStart=dp(8);ball.layoutParams=it}
ball.visibility=View.VISIBLE
bottomBarBrand.invalidate();ball.invalidate();bottom_bar.invalidate()
bottom_bar.setOnTouchListener{v,e->bottomBarTouchHandler(v,e)}
if(bottom_bar.parent==null)box.addView(bottom_bar,LinearLayout.LayoutParams(-1,height))
if(bottom_bar.parent!==box)throw IllegalStateException("bottom_bar could not be attached to panel")
orbTransitionOrigin?.let{origin->
orbTransitionOrigin=null
box.post{
if(bottom_bar.parent===box){
val target=IntArray(2);bottom_bar.getLocationOnScreen(target)
bottom_bar.translationX=(origin[0]-target[0]).toFloat()
bottom_bar.translationY=(origin[1]-target[1]).toFloat()
bottom_bar.animate().translationX(0f).translationY(0f).setDuration(250L).start()
}
}
}
}catch(e:Exception){
log("Error","addPanelFooter："+e.message)
runCatching{showBall()}
}
}

private fun updateBottomBarWindow(){
if(isUpdatingBottomBarLayout||!::bottom_bar.isInitialized||!bottom_bar.isAttachedToWindow)return
isUpdatingBottomBarLayout=true
try{wm.updateViewLayout(bottom_bar,bottomBarLp)}catch(e:Exception){log("Error","update bottom bar："+e.message)}finally{isUpdatingBottomBarLayout=false}
}
private fun updatePanelWindow(current:LinearLayout){
if(isUpdatingPanelLayout||panel!==current)return
isUpdatingPanelLayout=true
try{wm.updateViewLayout(current,panelLpRef)}catch(e:Exception){log("Error","update panel："+e.message)}finally{isUpdatingPanelLayout=false}
}
private fun animateBottomBarToX(targetX:Int){
if(!::bottom_bar.isInitialized)return
bottomBarSnapAnimator?.cancel()
val startX=bottomBarLp.x
if(startX==targetX){bottomBarLp.x=targetX;updateBottomBarWindow();return}
bottomBarSnapAnimator=ValueAnimator.ofInt(startX,targetX).apply{
duration=180L
addUpdateListener{animator->
bottomBarLp.x=animator.animatedValue as Int
updateBottomBarWindow()
}
start()
}
}
private fun bottomBarTouchHandler(@Suppress("UNUSED_PARAMETER") view:View,e:MotionEvent):Boolean{
return when(e.actionMasked){
MotionEvent.ACTION_DOWN->{
ball.animate().scaleX(.95f).scaleY(.95f).setDuration(80L).start()
if(panel==null&&bottomBarEdgeHidden!=0){
val wasHidden=bottomBarEdgeHidden
bottomBarSnapAnimator?.cancel()
bottomBarSnapAnimator=null
bottomBarEdgeHidden=0
val fullWidthX=if(wasHidden<0)0 else (resources.displayMetrics.widthPixels-bottom_bar.width).coerceAtLeast(0)
bottomBarRevealTargetX=fullWidthX
animateBottomBarToX(fullWidthX)
}
bottomBarDownRawX=e.rawX;bottomBarDownRawY=e.rawY
bottomBarStartX=if(panel!=null)panelLpRef.x else (bottomBarRevealTargetX?:bottomBarLp.x)
bottomBarStartY=if(panel!=null)panelLpRef.y else bottomBarLp.y
bottomBarDragInPanel=panel!=null;bottomBarMoved=false
true
}
MotionEvent.ACTION_MOVE->{
val dx=e.rawX-bottomBarDownRawX;val dy=e.rawY-bottomBarDownRawY
if(kotlin.math.abs(dx)>dp(4)||kotlin.math.abs(dy)>dp(4))bottomBarMoved=true
if(bottomBarMoved){
if(bottomBarDragInPanel){
val current=panel
if(current!=null){
panelLpRef.x=(bottomBarStartX+dx).toInt().coerceIn(0,(resources.displayMetrics.widthPixels-current.width).coerceAtLeast(0))
panelLpRef.y=(bottomBarStartY+dy).toInt().coerceIn(0,(resources.displayMetrics.heightPixels-current.height).coerceAtLeast(0))
updatePanelWindow(current)
}
}else{
bottomBarSnapAnimator?.cancel()
bottomBarSnapAnimator=null
bottomBarRevealTargetX=null
bottomBarLp.x=(bottomBarStartX+dx).toInt().coerceIn(0,(resources.displayMetrics.widthPixels-bottom_bar.width).coerceAtLeast(0))
bottomBarLp.y=(bottomBarStartY+dy).toInt().coerceIn(0,(resources.displayMetrics.heightPixels-bottom_bar.height).coerceAtLeast(0))
updateBottomBarWindow()
}
}
true
}
MotionEvent.ACTION_UP->{
ball.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
if(!bottomBarMoved){if(panel==null)showPanel()else closePanel();return true}
if(bottomBarDragInPanel){
val current=panel
if(current!=null){
val maxX=(resources.displayMetrics.widthPixels-current.width).coerceAtLeast(0)
panelLpRef.x=panelLpRef.x.coerceIn(0,maxX)
updatePanelWindow(current)
}
}else{
val sw=resources.displayMetrics.widthPixels
val x=bottomBarLp.x.coerceIn(0,(sw-bottom_bar.width).coerceAtLeast(0))
bottomBarLp.x=x
when{
x<sw/4->{bottomBarEdgeHidden=-1;animateBottomBarToX(-(dp(10)+dp(16)))}
x>sw*3/4->{bottomBarEdgeHidden=1;animateBottomBarToX(sw-dp(10)-dp(16))}
else->{bottomBarEdgeHidden=0;updateBottomBarWindow()}
}
}
true
}
MotionEvent.ACTION_CANCEL->{ball.animate().scaleX(1f).scaleY(1f).setDuration(100L).start();true}
else->false
}
}

private var bottomBarDownRawX=0f
private var bottomBarDownRawY=0f
private var bottomBarStartX=0
private var bottomBarStartY=0
private var bottomBarDragInPanel=false
private var bottomBarMoved=false

private fun findReceipt(v:View):TextView?{if(v is TextView&&v.text.toString()=="执行结果会显示在这里")return v;if(v is ViewGroup)for(i in 0 until v.childCount){val r=findReceipt(v.getChildAt(i));if(r!=null)return r};return null}
private fun smallButton(label:String,onClick:()->Unit)=Button(this).apply{text=label;textSize=13f;setTextColor(resources.getColor(R.color.bridgefs_button_text));minWidth=0;minimumWidth=0;setPadding(0,0,0,0);background=rippleBg(bg("#F2F3F5",10,null),R.color.bridgefs_ripple_orange);setOnClickListener{onClick()}}
private fun mainButton(label:String,onClick:()->Unit)=Button(this).apply{text=label;textSize=13f;setTextColor(resources.getColor(R.color.bridgefs_button_text));minWidth=0;minimumWidth=0;minimumHeight=0;setPadding(0,0,0,0);background=rippleBg(bg("#F2F3F5",10,null),R.color.bridgefs_ripple_orange);setOnClickListener{onClick()}}
private fun showHelpDialog(){
val message="""可用指令：
[list] 列出项目目录
[read: 相对路径] 读取文件
[write: 相对路径]...[/write] 新建或写入文件
[edit: 相对路径]...====...[/edit] 编辑文件
[search: *.xx] 按文件名搜索
[grep: 关键词] 按内容搜索
[path: 路径] 获取完整绝对路径
[copy-path: 路径] 复制路径

路径默认相对于项目根目录；可以一次发送多条指令。"""
val dialog=AlertDialog.Builder(this).setTitle("指令帮助").setMessage(message).setNegativeButton("关闭",null).setPositiveButton("复制全部指令"){_,_->copyText("BridgeFS说明书",message);toast("已复制全部指令")}.create()
dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
dialog.show()
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
private fun showBrowser(){try{browserCurrent=root;log("UI","showBrowser");renderBrowser()}catch(e:Exception){log("Error","showBrowser："+e.message);toast("打开浏览器失败："+e.message)}}
private var browserCurrent:File?=null
private fun renderBrowser(){
if(Looper.myLooper()!=Looper.getMainLooper()){
handler.post{renderBrowser()}
return
}
if(isRenderingBrowser)return
isRenderingBrowser=true
try{
clearPanel();log("UI","renderBrowser")
val current=browserCurrent?:root
val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(12));background=bg("#FFFFFF",16,null)}
val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
val back=smallButton("←"){if(current.absolutePath==root.absolutePath)showPanel()else{browserCurrent=current.parentFile?:root;renderBrowser()}}
val rootBtn=TextView(this).apply{text=current.absolutePath;textSize=13f;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),0,dp(4),0);setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;setTextColor(resources.getColor(R.color.bridgefs_text_primary));setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder,0,0,0);compoundDrawablePadding=dp(6);background=rippleBg(bg("#FFFFFF",8,null),R.color.bridgefs_ripple_gray);setOnClickListener{showRootList()};setOnLongClickListener{copyText("BridgeFS路径",current.absolutePath);toast("已复制路径");true}}
val copy=smallButton("复制当前层"){copyText("BridgeFS目录",browserListing(current));toast("已复制当前层")}.apply{textSize=11f}
top.addView(back,LinearLayout.LayoutParams(dp(56),dp(40)));top.addView(rootBtn,LinearLayout.LayoutParams(0,dp(40),1f).also{it.marginStart=dp(6)});top.addView(copy,LinearLayout.LayoutParams(dp(56),dp(40)).also{it.marginStart=dp(6)})
box.addView(top,LinearLayout.LayoutParams(-1,dp(48)))
val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
val children=current.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase(Locale.getDefault())}).orEmpty()
val first=children.take(50)
first.forEach{f->val row=TextView(this).apply{text=f.name;textSize=14f;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(4),dp(8),dp(4));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END;if(f.isDirectory){setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder,0,0,0);compoundDrawablePadding=dp(6)};setTextColor(resources.getColor(R.color.bridgefs_text_primary));background=rippleBg(bg("#FFFFFF",8,null),R.color.bridgefs_ripple_gray);setOnClickListener{if(f.isDirectory){browserCurrent=f;renderBrowser()}else{copyText("BridgeFS路径",f.relativeTo(root).path);toast("已复制相对路径")}}};list.addView(row,LinearLayout.LayoutParams(-1,dp(48)))}
if(children.size>50)list.addView(TextView(this).apply{text="…等 "+(children.size-50)+" 项";textSize=13f;setTextColor(Color.GRAY);setPadding(dp(8),dp(8),dp(8),dp(8))})
box.addView(ScrollView(this).apply{isVerticalScrollBarEnabled=true;scrollBarSize=dp(2);addView(list)},LinearLayout.LayoutParams(-1,dp(360)))
addPanelFooter(box);panel=box
val sw=resources.displayMetrics.widthPixels
val panelWidth=resources.getDimensionPixelSize(R.dimen.panel_width)
panelLpRef=panelLp(panelWidth,WindowManager.LayoutParams.WRAP_CONTENT);panelLpRef.gravity=Gravity.TOP or Gravity.LEFT;val barCenter=bottomBarLp.x+bottom_bar.width/2
panelLpRef.x=if(barCenter<sw/2)dp(8) else (sw-panelWidth-dp(8)).coerceAtLeast(0)
panelLpRef.y=dp(24)
wm.addView(box,panelLpRef)
}catch(e:Exception){
log("Error","renderBrowser："+e.message)
runCatching{clearPanel();showBall()}
toast("打开目录失败："+e.message)
}finally{
isRenderingBrowser=false
}
}
private fun browserListing(current:File):String{val entries=current.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase(Locale.getDefault())}).orEmpty();val first=entries.take(50);val files=entries.count{!it.isDirectory};val dirs=entries.count{it.isDirectory};return current.relativeToOrSelf(root).path+"/\n含 "+files+" 个文件、"+dirs+" 个文件夹：\n"+first.joinToString("\n"){f->"  "+(if(f.isDirectory)"文件夹" else "文件")+" "+f.name}+(if(entries.size>50)"\n…等 "+(entries.size-50)+" 项" else "")}
private fun showRootList(){clearPanel();log("UI","showRootList");val rs=(getSharedPreferences("bridgefs",0).getStringSet("root_paths",emptySet<String>())?:emptySet<String>()).toList();val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12));background=bg("#FFFFFF",16,"#E0E0E0");elevation=dp(8).toFloat()};val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};val title=TextView(this).apply{text="选择根目录";textSize=15f;setTypeface(null,1);setTextColor(resources.getColor(R.color.bridgefs_text_primary))};val close=smallButton("×"){try{clearPanel();renderBrowser()}catch(e:Exception){log("Error","关闭根目录列表："+e.message);toast("关闭根目录列表失败："+e.message)}};top.addView(title,LinearLayout.LayoutParams(0,dp(40),1f));top.addView(close,LinearLayout.LayoutParams(dp(56),dp(40)));box.addView(top);/* panel drag is restricted to the footer handle */;val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};rs.forEachIndexed{index,path->val row=TextView(this).apply{text=path;textSize=13f;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(6),dp(10),dp(6));setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder,0,0,0);compoundDrawablePadding=dp(6);setTextColor(resources.getColor(R.color.bridgefs_text_primary));background=rippleBg(bg("#FFFFFF",8,null),R.color.bridgefs_ripple_gray);setOnClickListener{root=File(path);getSharedPreferences("bridgefs",0).edit().putString("root_path",root.absolutePath).apply();clearPanel();browserCurrent=root;renderBrowser()}};list.addView(row,LinearLayout.LayoutParams(-1,dp(48)).also{it.topMargin=if(index==0)dp(4) else dp(2)})};if(rs.isEmpty())list.addView(TextView(this).apply{text="暂无已保存的根目录";textSize=13f;setTextColor(Color.GRAY);setPadding(dp(10),dp(12),dp(10),dp(12))});box.addView(ScrollView(this).apply{isVerticalScrollBarEnabled=true;scrollBarSize=dp(2);addView(list)},LinearLayout.LayoutParams(-1,0,1f));addPanelFooter(box);panel=box;val panelWidth=resources.getDimensionPixelSize(R.dimen.panel_width);panelLpRef=panelLp(panelWidth,WindowManager.LayoutParams.WRAP_CONTENT);panelLpRef.gravity=Gravity.TOP or Gravity.LEFT;val sw=resources.displayMetrics.widthPixels
val barCenter=bottomBarLp.x+bottom_bar.width/2
panelLpRef.x=if(barCenter<sw/2)dp(8) else (sw-panelWidth-dp(8)).coerceAtLeast(0);panelLpRef.y=dp(24);wm.addView(box,panelLpRef)}
private fun runLogFile():File{val dir=File("/sdcard/BridgeFS/logs");return if(dir.exists()||dir.mkdirs())File(dir,"run.log")else File(getExternalFilesDir(null),"logs").apply{mkdirs()}.resolve("run.log")}
private fun log(module:String,message:String){if(!getSharedPreferences("bridgefs",0).getBoolean("run_log_enabled",true))return;val line=SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date())+" ["+module+"] "+message.replace("\n","\\n")+"\n";runCatching{val f=runLogFile();val old=if(f.isFile)f.readLines().takeLast(499)else emptyList();f.parentFile?.mkdirs();f.writeText((old+line).joinToString(""))}.onFailure{android.util.Log.e("BridgeFS","run log failed",it)}}
private fun rippleBg(content:GradientDrawable,colorRes:Int)=RippleDrawable(ColorStateList.valueOf(resources.getColor(colorRes)),content,null)
private fun bg(fill:String,r:Int,stroke:String?)=GradientDrawable().apply{setColor(Color.parseColor(fill));cornerRadius=dp(r).toFloat();if(stroke!=null)setStroke(dp(1),Color.parseColor(stroke))}
private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
override fun onBind(i:Intent?)=null
override fun onDestroy(){clipboardCallback=null;commandInput=null;log("Service","onDestroy");clearPanel();if(::bottom_bar.isInitialized)runCatching{wm.removeView(bottom_bar)};running=false;super.onDestroy()}
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
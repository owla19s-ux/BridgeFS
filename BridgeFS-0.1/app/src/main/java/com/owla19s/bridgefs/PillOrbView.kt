package com.owla19s.bridgefs

import android.content.Context
import android.graphics.Color
import android.view.View

class PillOrbView(context:Context):View(context){
private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
private val density=resources.displayMetrics.density
override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){setMeasuredDimension(resolveSize((40*density).toInt(),widthMeasureSpec),resolveSize((56*density).toInt(),heightMeasureSpec))}
override fun onDraw(canvas:android.graphics.Canvas){
super.onDraw(canvas)
val save=canvas.save()
canvas.scale(width/(40f*density),height/(56f*density))
val bounds=android.graphics.RectF(0f,0f,40f*density,56f*density)
paint.color=Color.rgb(247,128,36)
canvas.drawRoundRect(bounds,20f*density,20f*density,paint)
paint.color=Color.rgb(22,24,28)
canvas.drawRoundRect(android.graphics.RectF(0f,0f,40f*density,36f*density),20f*density,20f*density,paint)
canvas.drawRect(0f,18f*density,40f*density,36f*density,paint)
paint.color=Color.rgb(50,145,255)
canvas.drawCircle(13f*density,17f*density,2.4f*density,paint)
canvas.drawCircle(27f*density,17f*density,2.4f*density,paint)
canvas.restoreToCount(save)
}
}

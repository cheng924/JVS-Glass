package com.example.jvsglass.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import kotlin.math.abs
import kotlin.math.roundToInt

class LanguagePickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val languages = listOf("中文(简体)", "英语", "西班牙语", "德语", "日语", "法语", "韩语", "意大利语", "俄语", "阿拉伯语")
    private var selectedIndex = 1 // 默认选中英语
    private var itemHeight = 80
    private var maxTextSize = 48f
    private var touchSlop = 0
    private var velocityTracker: VelocityTracker? = null
    private var scroller = Scroller(context)
    private var lastY = 0f
    private var totalOffsetY = 0

    private var listener: ((String) -> Unit)? = null

    init {
        val metrics = resources.displayMetrics
        itemHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40f, metrics).roundToInt()
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 计算中间位置
        val centerY = height / 2
        val centerIndex = selectedIndex

        // 绘制文本
        for (i in languages.indices) {
            val distance = i - centerIndex
            val yPos = (centerY + distance * itemHeight + totalOffsetY).toFloat()
            if (yPos < -itemHeight || yPos > height + itemHeight) continue

            // 根据距离调整文字大小
            val scale = 1 - abs(distance) * 0.2f
            val curTextSize = maxTextSize * scale.coerceAtLeast(0.5f)

            val paint = Paint().apply {
                color = if (distance == 0) Color.WHITE else Color.GRAY
                textSize = curTextSize
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val text = languages[i]
            canvas.drawText(text, width / 2f, yPos, paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.y
                velocityTracker = VelocityTracker.obtain()
                scroller.forceFinished(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = (event.y - lastY).toInt()
                totalOffsetY += deltaY
                adjustOffset()
                lastY = event.y
                invalidate()
                velocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityY = velocityTracker?.yVelocity ?: 0f
                val maxOffset = (languages.size - 1 - selectedIndex) * itemHeight
                val minOffset = -selectedIndex * itemHeight
                scroller.fling(0, totalOffsetY, 0, velocityY.toInt(), 0, 0, minOffset, maxOffset)
                invalidate()
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            totalOffsetY = scroller.currY
            adjustOffset()
            invalidate()
        }
    }

    private fun adjustOffset() {
        // 计算新的选中项
        val deltaIndex = -(totalOffsetY / itemHeight)
        val newIndex = (selectedIndex + deltaIndex).coerceIn(0, languages.lastIndex)

        // 更新选中索引
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
            listener?.invoke(languages[selectedIndex])
        }

        // 保持偏移在单条高度范围内
        totalOffsetY -= -deltaIndex * itemHeight
    }

    fun setCurrentLanguage(language: String) {
        selectedIndex = languages.indexOfFirst { it == language }.coerceAtLeast(0)
        invalidate()
    }

    fun getCurrentLanguage(): String {
        return languages[selectedIndex]
    }
}
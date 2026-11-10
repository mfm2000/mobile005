
package com.mfm2000.mobile005

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import android.graphics.Typeface
import kotlin.math.sin
import kotlin.math.PI

class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 숫자 Paint
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 3f, 3f, Color.BLACK)
    }

    // 시침 Paint
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        strokeWidth = 24f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(8f, 3f, 3f, Color.BLACK)
    }

    // 분침 Paint
    private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(7f, 3f, 3f, Color.BLACK)
    }

    // 초침 Paint
    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(6f, 2f, 2f, Color.BLACK)
    }

    // 중앙 원 Paint
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(6f, 2f, 2f, Color.BLACK)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() {

            invalidate()

            //val delay =
            //    1000L - (System.currentTimeMillis() % 1000L)

            // handler.postDelayed(this, delay)
            handler.postDelayed(this, 16L)
        }
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        val radius = min(width, height) * 0.5f

        // 숫자 크기 기존 대비 2배
        numberPaint.textSize = radius * 0.23f

        // 숫자 위치
        val numberDistance = radius * 0.72f

        // 현재 시간
        val calendar = Calendar.getInstance()
        val second = calendar.get(Calendar.SECOND)
        val millisecond = calendar.get(Calendar.MILLISECOND)

        val hour = calendar.get(Calendar.HOUR)
        val minute = calendar.get(Calendar.MINUTE)

        // 현재 초에 해당하는 숫자
        val targetNumber = if (second == 0) 12 else second / 5

        // 숫자 확대 애니메이션 (최대 2배)
        val progress = (millisecond / 600f).coerceIn(0f, 1f)
        val pulseScale = if (millisecond < 600 && second % 5 == 0) {
            1f + sin(progress * PI).toFloat()
        } else {
            1f
        }

        // 숫자 표시
        for (i in 1..12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())

            val x = cx + numberDistance * kotlin.math.cos(angle).toFloat()
            val y = cy + numberDistance * kotlin.math.sin(angle).toFloat()

            val textY = y - (numberPaint.ascent() + numberPaint.descent()) / 2

            // 해당 초의 숫자만 확대
            val scale = if (i == targetNumber) pulseScale else 1f

            canvas.save()
            canvas.scale(scale, scale, x, textY)
            canvas.drawText(i.toString(), x, textY, numberPaint)
            canvas.restore()
        }

        // 초 단위까지 반영
        val smoothSecond = second + millisecond / 1000f

        val smoothMinute = minute + smoothSecond / 60f

        val smoothHour = hour + smoothMinute / 60f

        // 시침 각도
        val hourAngle = Math.toRadians(
            smoothHour * 30.0 - 90.0
        )

        // 분침 각도
        val minuteAngle = Math.toRadians(
            smoothMinute * 6.0 - 90.0
        )

        // 초침 각도
        val secondAngle = Math.toRadians(
            smoothSecond * 6.0 - 90.0
        )

        // 시침 길이
        val hourLength = radius * 0.48f

        // 분침 길이
        val minuteLength = radius * 0.53f

        // 초침 길이
        val secondLength = radius * 0.56f

        // 시침
        canvas.drawLine(
            cx,
            cy,
            cx + cos(hourAngle).toFloat() * hourLength,
            cy + sin(hourAngle).toFloat() * hourLength,
            hourPaint
        )

        // 분침
        canvas.drawLine(
            cx,
            cy,
            cx + cos(minuteAngle).toFloat() * minuteLength,
            cy + sin(minuteAngle).toFloat() * minuteLength,
            minutePaint
        )

        // 초침
        canvas.drawLine(
            cx,
            cy,
            cx + cos(secondAngle).toFloat() * secondLength,
            cy + sin(secondAngle).toFloat() * secondLength,
            secondPaint
        )

        // 중앙 축
        canvas.drawCircle(
            cx,
            cy,
            radius * 0.035f,
            centerPaint
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        handler.removeCallbacks(clockRunnable)
        handler.post(clockRunnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(clockRunnable)

        super.onDetachedFromWindow()
    }
}

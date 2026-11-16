package com.mfm2000.mobile005

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val FRAME_INTERVAL = 16L

        private const val NUMBER_SIZE = 0.23f
        private const val NUMBER_DISTANCE = 0.72f

        private const val HOUR_LENGTH = 0.48f
        private const val MINUTE_LENGTH = 0.53f
        private const val SECOND_LENGTH = 0.56f

        private const val CENTER_RADIUS = 0.035f
    }

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

    private val calendar = Calendar.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    private val clockRunnable = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, FRAME_INTERVAL)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.5f

        if (radius <= 0f) return

        // 숫자 크기 및 위치
        numberPaint.textSize = radius * NUMBER_SIZE
        val numberDistance = radius * NUMBER_DISTANCE

        // 현재 시간
        calendar.timeInMillis = System.currentTimeMillis()

        val second = calendar.get(Calendar.SECOND)
        val millisecond = calendar.get(Calendar.MILLISECOND)

        val hour = calendar.get(Calendar.HOUR)
        val minute = calendar.get(Calendar.MINUTE)

        // 현재 초에 해당하는 숫자
        val targetNumber = if (second == 0) 12 else second / 5

        // 숫자 확대 애니메이션
        val progress = (millisecond / 600f).coerceIn(0f, 1f)

        val pulseScale =
            if (millisecond < 600 && second % 5 == 0) {
                1f + sin(progress * PI).toFloat()
            } else {
                1f
            }

        // 시계 숫자 표시
        drawNumbers(
            canvas,
            cx,
            cy,
            numberDistance,
            targetNumber,
            pulseScale
        )

        // 부드러운 시간 계산
        val smoothSecond = second + millisecond / 1000f
        val smoothMinute = minute + smoothSecond / 60f
        val smoothHour = hour + smoothMinute / 60f

        // 시계 바늘 각도
        val hourAngle = Math.toRadians(smoothHour * 30.0 - 90.0)
        val minuteAngle = Math.toRadians(smoothMinute * 6.0 - 90.0)
        val secondAngle = Math.toRadians(smoothSecond * 6.0 - 90.0)

        // 시침
        drawHand(canvas, cx, cy, hourAngle, radius * HOUR_LENGTH, hourPaint)

        // 분침
        drawHand(canvas, cx, cy, minuteAngle, radius * MINUTE_LENGTH, minutePaint)

        // 초침
        drawHand(canvas, cx, cy, secondAngle, radius * SECOND_LENGTH, secondPaint)

        // 중앙 축
        canvas.drawCircle(
            cx,
            cy,
            radius * CENTER_RADIUS,
            centerPaint
        )
    }

    // 시계 숫자 그리기
    private fun drawNumbers(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        distance: Float,
        targetNumber: Int,
        pulseScale: Float
    ) {
        for (i in 1..12) {

            val angle = Math.toRadians((i * 30 - 90).toDouble())

            val x = cx + distance * cos(angle).toFloat()
            val y = cy + distance * sin(angle).toFloat()

            val textY =
                y - (numberPaint.ascent() + numberPaint.descent()) / 2f

            val scale = if (i == targetNumber) pulseScale else 1f

            canvas.save()
            canvas.scale(scale, scale, x, textY)
            canvas.drawText(i.toString(), x, textY, numberPaint)
            canvas.restore()
        }
    }

    // 시계 바늘 공통 그리기
    private fun drawHand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        angle: Double,
        length: Float,
        paint: Paint
    ) {
        canvas.drawLine(
            cx,
            cy,
            cx + cos(angle).toFloat() * length,
            cy + sin(angle).toFloat() * length,
            paint
        )
    }

    // 화면에 표시될 때 애니메이션 시작
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        handler.removeCallbacks(clockRunnable)
        handler.post(clockRunnable)
    }

    // 화면에서 제거될 때 애니메이션 정지
    override fun onDetachedFromWindow() {
        handler.removeCallbacks(clockRunnable)
        super.onDetachedFromWindow()
    }
}

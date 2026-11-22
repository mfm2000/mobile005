
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

        // 화면을 다시 그리는 간격 (16ms = 약 60FPS)
        private const val FRAME_INTERVAL = 16L

        // 시계 숫자의 크기 (시계 반지름 대비 비율)
        private const val NUMBER_SIZE = 0.23f

        // 시계 중심에서 숫자까지의 거리
        private const val NUMBER_DISTANCE = 0.72f

        // 시침 길이 (시계 반지름 대비 비율)
        private const val HOUR_LENGTH = 0.48f

        // 분침 길이 (시계 반지름 대비 비율)
        private const val MINUTE_LENGTH = 0.53f

        // 초침 길이 (시계 반지름 대비 비율)
        private const val SECOND_LENGTH = 0.56f

        // 시계 중앙 원의 반지름
        private const val CENTER_RADIUS = 0.035f
    }

    // 숫자 표시용 Paint
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE                       // 숫자 색상
        textAlign = Paint.Align.CENTER            // 숫자를 가운데 정렬
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) // 굵은 글씨
        setShadowLayer(8f, 3f, 3f, Color.BLACK)   // 숫자 그림자 효과
    }

    // 시침 표시용 Paint
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE                        // 시침 색상
        strokeWidth = 24f                         // 시침 두께
        strokeCap = Paint.Cap.ROUND               // 시침 끝을 둥글게 처리
        setShadowLayer(8f, 3f, 3f, Color.BLACK)   // 시침 그림자
    }

    // 분침 표시용 Paint
    private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED                         // 분침 색상
        strokeWidth = 12f                         // 분침 두께
        strokeCap = Paint.Cap.ROUND               // 분침 끝을 둥글게 처리
        setShadowLayer(7f, 3f, 3f, Color.BLACK)   // 분침 그림자
    }

    // 초침 표시용 Paint
    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE                       // 초침 색상
        strokeWidth = 4f                          // 초침 두께
        strokeCap = Paint.Cap.ROUND               // 초침 끝을 둥글게 처리
        setShadowLayer(6f, 2f, 2f, Color.BLACK)   // 초침 그림자
    }

    // 시계 중심 원 표시용 Paint
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE                       // 중앙 원 색상
        setShadowLayer(6f, 2f, 2f, Color.BLACK)   // 중앙 원 그림자
    }

    // 현재 날짜와 시간을 가져오기 위한 Calendar 객체
    private val calendar = Calendar.getInstance()

    // 메인 UI 스레드에서 시계 갱신 작업을 실행하는 Handler
    private val handler = Handler(Looper.getMainLooper())

    // 일정 시간마다 화면을 다시 그리는 반복 작업
    private val clockRunnable = object : Runnable {

        override fun run() {

            // onDraw()를 다시 호출하여 시계 화면 갱신
            invalidate()

            // FRAME_INTERVAL 이후 동일 작업 반복 실행
            handler.postDelayed(this, FRAME_INTERVAL)
        }
    }

    // 시계 화면 그리기
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // View의 정중앙 좌표 계산
        val cx = width / 2f
        val cy = height / 2f

        // View의 너비와 높이 중 작은 값을 기준으로 시계 반지름 계산
        // 화면을 벗어나지 않도록 정사각형 영역 기준으로 설정
        val radius = min(width, height) * 0.5f

        // 화면 크기가 아직 결정되지 않은 경우 그리기 중단
        if (radius <= 0f) return

        // 숫자 크기 계산 (시계 반지름에 비례)
        numberPaint.textSize = radius * NUMBER_SIZE

        // 시계 중심에서 숫자까지의 거리 계산
        val numberDistance = radius * NUMBER_DISTANCE

        // 현재 시스템 시간을 Calendar 객체에 반영
        calendar.timeInMillis = System.currentTimeMillis()

        // 현재 초 추출 (0~59)
        val second = calendar.get(Calendar.SECOND)

        // 현재 밀리초 추출 (0~999)
        val millisecond = calendar.get(Calendar.MILLISECOND)

        // 현재 시각 추출 (12시간 기준, 0~11)
        val hour = calendar.get(Calendar.HOUR)

        // 현재 분 추출 (0~59)
        val minute = calendar.get(Calendar.MINUTE)

        // 현재 초에 해당하는 시계 숫자 계산
        // 5초마다 하나의 숫자를 지정
        // 예: 5초 = 1, 10초 = 2, 60초 = 12
        val targetNumber = if (second == 0) 12 else second / 5

        // 숫자 확대 애니메이션 진행률 계산
        // 0~600ms 구간을 0.0~1.0으로 변환
        val progress = (millisecond / 600f).coerceIn(0f, 1f)

        // 매 5초마다 해당 숫자가 커졌다가 원래 크기로 돌아오는 효과
        val pulseScale =
            if (millisecond < 600 && second % 5 == 0) {

                // sin 함수를 이용한 부드러운 확대 효과
                // 기본 크기 1.0에서 최대 2.0까지 확대
                1f + sin(progress * PI).toFloat()

            } else {

                // 확대 대상이 아니면 기본 크기 유지
                1f
            }

        // 시계 숫자 1~12 표시
        // 현재 초에 해당하는 숫자는 확대 효과 적용
        drawNumbers(
            canvas,
            cx,
            cy,
            numberDistance,
            targetNumber,
            pulseScale
        )

        // 부드러운 시계 움직임을 위한 시간 계산

        // 초 + 밀리초를 이용하여 초침의 위치를 부드럽게 계산
        val smoothSecond = second + millisecond / 1000f

        // 초의 진행 정도를 반영하여 분침 위치 계산
        val smoothMinute = minute + smoothSecond / 60f

        // 분의 진행 정도까지 반영하여 시침 위치 계산
        val smoothHour = hour + smoothMinute / 60f

        // 시침 각도 계산
        // 1시간 = 30도, -90도는 12시 방향
        val hourAngle = Math.toRadians(smoothHour * 30.0 - 90.0)

        // 분침 각도 계산
        // 1분 = 6도
        val minuteAngle = Math.toRadians(smoothMinute * 6.0 - 90.0)

        // 초침 각도 계산
        // 1초 = 6도
        val secondAngle = Math.toRadians(smoothSecond * 6.0 - 90.0)

        // 시침 그리기
        drawHand(
            canvas, cx, cy,
            hourAngle,
            radius * HOUR_LENGTH,
            hourPaint
        )

        // 분침 그리기
        drawHand(
            canvas, cx, cy,
            minuteAngle,
            radius * MINUTE_LENGTH,
            minutePaint
        )

        // 초침 그리기
        drawHand(
            canvas, cx, cy,
            secondAngle,
            radius * SECOND_LENGTH,
            secondPaint
        )

        // 시계 중앙에 축 표시
        // 세 바늘이 만나는 중심 부분을 원으로 표현
        canvas.drawCircle(
            cx,
            cy,
            radius * CENTER_RADIUS,
            centerPaint
        )
    }

    // 시계 숫자 1~12를 화면에 그리는 함수
    private fun drawNumbers(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        distance: Float,
        targetNumber: Int,
        pulseScale: Float
    ) {

        // 1부터 12까지 반복하여 숫자 표시
        for (i in 1..12) {

            // 각 숫자가 위치할 각도 계산
            // 12시는 -90도, 3시는 0도, 6시는 90도
            val angle = Math.toRadians((i * 30 - 90).toDouble())

            // 삼각함수를 이용하여 숫자의 X 좌표 계산
            val x = cx + distance * cos(angle).toFloat()

            // 삼각함수를 이용하여 숫자의 Y 좌표 계산
            val y = cy + distance * sin(angle).toFloat()

            // 숫자의 높이를 고려하여 세로 방향 중앙 정렬
            val textY =
                y - (numberPaint.ascent() + numberPaint.descent()) / 2f

            // 현재 초에 해당하는 숫자만 확대
            // 나머지 숫자는 기본 크기 1.0 유지
            val scale = if (i == targetNumber) pulseScale else 1f

            // 현재 Canvas 상태 저장
            canvas.save()

            // 지정된 숫자의 중심점을 기준으로 확대 또는 축소
            canvas.scale(scale, scale, x, textY)

            // 실제 숫자 그리기
            canvas.drawText(i.toString(), x, textY, numberPaint)

            // Canvas 상태 복원
            // 다음 숫자에 확대 효과가 영향을 주지 않도록 함
            canvas.restore()
        }
    }

    // 시계 바늘을 공통으로 그리는 함수
    // 시침, 분침, 초침에서 동일하게 사용
    private fun drawHand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        angle: Double,
        length: Float,
        paint: Paint
    ) {
        // 시계 중심에서 반대 방향으로 20% 연장
        val backLength = length * 0.20f

        // 앞쪽 끝점
        val endX = cx + cos(angle).toFloat() * length
        val endY = cy + sin(angle).toFloat() * length

        // 뒤쪽 시작점
        val startX = cx - cos(angle).toFloat() * backLength
        val startY = cy - sin(angle).toFloat() * backLength

        canvas.drawLine(
            startX,
            startY,
            endX,
            endY,
            paint
        )
    }

    // View가 화면에 부착될 때 호출
    // 시계 갱신 애니메이션 시작
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // 중복 실행 방지를 위해 기존 갱신 작업 제거
        handler.removeCallbacks(clockRunnable)

        // 시계 화면 갱신 시작
        handler.post(clockRunnable)
    }

    // View가 화면에서 제거될 때 호출
    // 불필요한 반복 작업을 중지하여 리소스 낭비 방지
    override fun onDetachedFromWindow() {

        // 예약된 시계 갱신 작업 제거
        handler.removeCallbacks(clockRunnable)

        super.onDetachedFromWindow()
    }
}

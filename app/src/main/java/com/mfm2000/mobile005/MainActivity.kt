package com.mfm2000.mobile005

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.github.usingsky.calendar.KoreanLunarCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    // 앱 전체에서 사용하는 고정 설정값
    companion object {

        // 배경 이미지 자동 변경 간격: 10초
        private const val CHANGE_INTERVAL = 10_000L

        // 배경 이미지 전환 시 페이드 효과 지속 시간: 3초
        private const val FADE_DURATION = 3_000

        // 양력과 음력 날짜 변경 간격: 10초
        private const val DATE_CHANGE_INTERVAL = 10_000L

        // 디지털 시계 표시 전 초기 대기 시간: 0.7초
        private const val INTRO_DELAY = 700L

        // 디지털 시계 글자 크기
        private const val CLOCK_SIZE = 70f

        // 하단 앱 정보 글자 크기
        private const val SMALL_TEXT_SIZE = 20f
    }

    // 메인 스레드에서 시간에 따른 작업을 예약하고 실행
    private val handler = Handler(Looper.getMainLooper())

    // 양력 및 음력 날짜를 표시하는 TextView
    private lateinit var dateView: TextView

    // 해당 연도의 간지와 동물 띠를 표시하는 TextView
    private lateinit var yearGapjaView: TextView

    // 디지털 시계를 표시하는 TextView
    private lateinit var clockView: TextView

    // 아날로그 시계 사용자 정의 View
    private lateinit var analogClock: AnalogClockView

    // 배경 이미지를 자동으로 전환하는 클래스
    private lateinit var slider: BackgroundSlider

    // 스플래시 화면을 유지할지 결정하는 상태 변수
    private var loading = true

    // 현재 표시할 날짜 종류
    // true: 양력, false: 음력
    private var showSolar = true

    // 디지털 시계 표시 형식
    // HH: 24시간 형식, mm: 분, ss: 초
    private val clockFormatter by lazy {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    // 요일 표시 형식
    // EEEE: 월요일, 화요일 등 전체 요일 이름
    private val weekdayFormatter by lazy {
        SimpleDateFormat("EEEE", Locale.KOREAN)
    }

    // 12간지에 해당하는 동물 띠 배열
    // 배열 순서에 따라 쥐띠부터 돼지띠까지 배치
    private val zodiacAnimals = arrayOf(
        "쥐띠", "소띠", "호랑이띠", "토끼띠",
        "용띠", "뱀띠", "말띠", "양띠",
        "원숭이띠", "닭띠", "개띠", "돼지띠"
    )
    private lateinit var weekdayView: TextView

    // --------------------------------------------------
    // 디지털 시계 갱신 작업
    // --------------------------------------------------

    private val clockRunnable = object : Runnable {

        override fun run() {

            // 현재 시스템 시간을 밀리초 단위로 가져옴
            val now = System.currentTimeMillis()

            // 현재 시간을 HH:mm:ss 형식으로 변환하여 화면에 표시
            clockView.text = clockFormatter.format(now)

            // 다음 정각 초에 맞춰 갱신하기 위한 대기 시간 계산
            // 예: 현재 시간이 12:30:15.300이면 700ms 대기
            val delay = 1000L - (now % 1000L)

            // 계산된 시간 후에 시계 갱신 작업을 다시 실행
            handler.postDelayed(this, delay)
        }
    }

    // --------------------------------------------------
    // 양력 / 음력 날짜 및 간지 표시 작업
    // --------------------------------------------------


    private val dateRunnable = object : Runnable {

        override fun run() {

            // 현재 날짜 가져오기
            val now = Calendar.getInstance()

            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1
            val day = now.get(Calendar.DAY_OF_MONTH)

            // 음력 변환
            val lunar = KoreanLunarCalendar.getInstance()

            if (lunar.setSolarDate(year, month, day)) {

                // 간지 계산
                val koreanYear = lunar.gapjaString.substringBefore(" ")
                val chineseYear = lunar.chineseGapJaString.substringBefore(" ")

                // 동물 띠 계산
                val zodiacIndex = Math.floorMod(lunar.lunarYear - 4, 12)
                val zodiac = zodiacAnimals[zodiacIndex]

                yearGapjaView.text = "$koreanYear($chineseYear) ${zodiac}해"

                // 요일 계산
                val weekday = weekdayFormatter.format(now.time)

                // 양력 날짜
                val solarDate = "양력 ${year}년 ${month}월 ${day}일"
                weekdayView.text = weekday

                // 음력 날짜
                val lunarMonth = lunar.lunarMonth
                val lunarDay = lunar.lunarDay

                val monthText = if (lunar.isIntercalation) {
                    "윤${lunarMonth}월"
                } else {
                    "${lunarMonth}월"
                }

                val lunarDate =
                    "음력 ${lunar.lunarYear}년 $monthText ${lunarDay}일"

                val newDate = if (showSolar) solarDate else lunarDate

                // Fade Out 효과
                dateView.animate()
                    .alpha(0f)
                    .setDuration(500L)
                    .withEndAction {
                        dateView.text = newDate
                        dateView.animate()
                            .alpha(1f)
                            .setDuration(500L)
                            .start()
                    }
                    .start()

                // 다음에는 반대 날짜 표시
                showSolar = !showSolar
            }

            // 다음 날짜 전환 예약
            handler.postDelayed(this, DATE_CHANGE_INTERVAL)
        }
    }


    // --------------------------------------------------
    // 좌우 플링 제스처 감지
    // --------------------------------------------------

    // 화면을 좌우로 빠르게 밀었을 때 동작을 처리하는 객체
    private val gestureDetector by lazy {

        GestureDetector(
            this,

            object : GestureDetector.SimpleOnGestureListener() {

                // 손가락을 빠르게 움직였다가 떼는 동작 감지
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {

                    // 최초 터치 위치가 없으면 처리하지 않음
                    if (e1 == null) return false

                    // 가로 방향 이동 거리 계산
                    val dx = e2.x - e1.x

                    // 세로 방향 이동 거리 계산
                    val dy = e2.y - e1.y

                    // 가로 이동이 세로 이동보다 크고
                    // 이동 거리와 속도가 기준값을 넘으면 플링 처리
                    if (
                        abs(dx) > abs(dy) &&
                        abs(dx) > 200 &&
                        abs(velocityX) > 200
                    ) {

                        // 오른쪽으로 밀었을 경우
                        if (dx > 0) {

                            // 다음 배경 이미지로 즉시 변경
                            slider.nextImage()

                        } else {

                            // 왼쪽으로 밀었을 경우 앱 종료
                            finishAndRemoveTask()

                            // 앱 종료 시 페이드 애니메이션 적용
                            overridePendingTransition(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                            )
                        }

                        // 제스처가 정상 처리되었음을 반환
                        return true
                    }

                    // 기준에 맞지 않는 터치 동작은 처리하지 않음
                    return false
                }
            }
        )
    }

    // --------------------------------------------------
    // 액티비티 생성 및 초기 설정
    // --------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {

        // 스플래시 화면 설치
        val splash = installSplashScreen()

        // 부모 클래스의 onCreate 실행
        super.onCreate(savedInstanceState)

        // Edge-to-Edge 화면 모드 활성화
        enableEdgeToEdge()

        // loading이 false가 될 때까지 스플래시 화면 유지
        splash.setKeepOnScreenCondition { loading }

        // 화면이 자동으로 꺼지는 것을 방지
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // 메인 화면 XML 연결
        setContentView(R.layout.activity_main)

        // 0.5초 후 스플래시 화면 종료 허용
        handler.postDelayed({
            loading = false
        }, 500L)

        // 화면에 필요한 모든 기능 초기화
        init()
    }

    // --------------------------------------------------
    // 화면 구성 요소 초기화
    // --------------------------------------------------

    private fun init() {

        // 전체 화면 설정
        setupImmersiveMode()

        // 배경 이미지 설정 및 자동 전환 시작
        setupBackground()

        // 아날로그 시계 설정
        setupAnalogClock()

        // 양력 / 음력 날짜 표시 설정
        setupDateDisplay()

        // 디지털 시계 설정
        setupDigitalClock()

        // 하단 앱 정보 설정
        setupFooterText()

        // 알람 설정 버튼 연결
        setupAlarmButton()

        weekdayView = findViewById(R.id.weekdayView)
    }

    // --------------------------------------------------
    // 알람 설정 버튼
    // --------------------------------------------------

    private fun setupAlarmButton() {

        // XML에 선언된 알람 버튼을 가져옴
        findViewById<Button>(R.id.btnRecentApps).apply {

            // 버튼에 표시할 문구 지정
            text = "알람 설정"

            // 버튼 클릭 이벤트
            setOnClickListener {

                try {

                    // Android 기본 알람 앱의 새 알람 설정 화면 실행
                    startActivity(
                        Intent(AlarmClock.ACTION_SET_ALARM)
                    )

                } catch (e: ActivityNotFoundException) {

                    // 알람 앱을 찾지 못한 경우 메시지 표시
                    Toast.makeText(
                        this@MainActivity,
                        "알람 앱을 찾을 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (e: SecurityException) {

                    // 보안 또는 권한 관련 예외 발생 시 메시지 표시
                    Toast.makeText(
                        this@MainActivity,
                        "알람 설정 권한을 확인해 주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // --------------------------------------------------
    // 전체 화면 모드 설정
    // --------------------------------------------------

    private fun setupImmersiveMode() {

        // 상태 표시줄, 내비게이션 바 숨김
        // 몰입형 모드를 사용하여 전체 화면 유지
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        // 상태 표시줄 배경을 투명하게 설정
        window.statusBarColor = Color.TRANSPARENT
    }

    // --------------------------------------------------
    // 배경 이미지 설정
    // --------------------------------------------------

    private fun setupBackground() {

        // XML의 배경 이미지 ImageView 연결
        val imageView = findViewById<ImageView>(R.id.bgImage)

        // 배경 화면에 사용할 이미지 리소스 목록
        val images = arrayOf(
            R.drawable.autumn001,
            R.drawable.autumn002,
            R.drawable.autumn003,
            R.drawable.autumn004,
            R.drawable.autumn005,
            R.drawable.autumn006,
            R.drawable.autumn007,
            R.drawable.autumn008
        )

        // BackgroundSlider 객체 생성
        // 이미지 목록, ImageView, 전환 간격, 페이드 시간 전달
        slider = BackgroundSlider(
            images,
            imageView,
            CHANGE_INTERVAL,
            FADE_DURATION
        )

        // 배경 이미지 자동 전환 시작
        slider.start()
    }

    // --------------------------------------------------
    // 아날로그 시계 설정
    // --------------------------------------------------

    private fun setupAnalogClock() {

        // XML에 선언된 사용자 정의 아날로그 시계 연결
        analogClock = findViewById(R.id.analogClock)

        // 시계를 화면에 표시하고 투명도를 80%로 설정
        analogClock.apply {
            visibility = View.VISIBLE
            alpha = 0.8f
        }
    }

    // --------------------------------------------------
    // 디지털 시계 설정
    // --------------------------------------------------

    private fun setupDigitalClock() {

        // XML의 디지털 시계 TextView 연결
        clockView = findViewById(R.id.textView1)

        // 초기 표시 내용 및 글자색 설정
        clockView.apply {
            text = getString(R.string.app_exp1)
            setTextColor(Color.WHITE)
        }

        // 초기 화면 표시 후 0.7초 뒤 시계 실행
        clockView.postDelayed({

            // 디지털 시계 화면 설정
            clockView.apply {

                // 시계 표시 활성화
                visibility = View.VISIBLE

                // 글자 크기 지정
                textSize = CLOCK_SIZE

                // 글자에 그림자 효과 적용
                setShadowLayer(
                    8f,
                    4f,
                    4f,
                    Color.BLACK
                )
            }

            // 디지털 시계 갱신 작업 시작
            handler.post(clockRunnable)

        }, INTRO_DELAY)
    }

    // --------------------------------------------------
    // 하단 앱 정보 설정
    // --------------------------------------------------

    private fun setupFooterText() {

        // 버전 정보와 앱 별칭 TextView를 순서대로 처리
        listOf(
            R.id.appVer,
            R.id.appNickName
        ).forEach { id ->

            // 각 TextView의 글자색과 크기 설정
            findViewById<TextView>(id).apply {

                // 흰색 글자 적용
                setTextColor(Color.WHITE)

                // SP 단위로 글자 크기 설정
                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    SMALL_TEXT_SIZE
                )
            }
        }

        // 이메일 주소 TextView 글자색 설정
        findViewById<TextView>(R.id.mailAddress)
            .setTextColor(Color.WHITE)
    }

    // --------------------------------------------------
    // 양력 / 음력 날짜 표시 초기화
    // --------------------------------------------------

    private fun setupDateDisplay() {

        // XML의 날짜 표시 TextView 연결
        dateView = findViewById(R.id.dateView)

        // XML의 간지 표시 TextView 연결
        yearGapjaView = findViewById(R.id.yearGapjaView)

        // 날짜 표시 작업 즉시 시작
        // 최초에는 양력이 표시됨
        handler.post(dateRunnable)
    }

    // --------------------------------------------------
    // 화면 터치 이벤트 전달
    // --------------------------------------------------

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        // 터치 이벤트를 제스처 감지 객체에 전달
        gestureDetector.onTouchEvent(event)

        // 일반적인 화면 터치 이벤트도 정상적으로 처리
        return super.dispatchTouchEvent(event)
    }

    // --------------------------------------------------
    // 액티비티 종료 시 리소스 정리
    // --------------------------------------------------

    override fun onDestroy() {

        // 배경 슬라이더가 초기화된 경우 자동 전환 작업 중지
        if (::slider.isInitialized) {
            slider.stop()
        }

        // Handler에 등록된 모든 예약 작업 제거
        // 디지털 시계 및 날짜 갱신 작업 포함
        handler.removeCallbacksAndMessages(null)

        // 부모 클래스의 종료 처리 실행
        super.onDestroy()
    }
}

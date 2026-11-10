
package com.mfm2000.mobile005

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.enableEdgeToEdge
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CHANGE_INTERVAL = 10_000L
        private const val FADE_DURATION = 3_000
        private const val CLOCK_SIZE = 70f
        private const val SMALL_TEXT_SIZE = 20f
        private const val INTRO_DELAY = 700L
    }

    private val handler = Handler(Looper.getMainLooper())

    private var loading = true

    private lateinit var slider: BackgroundSlider
    private lateinit var clockView: TextView
    private lateinit var analogClock: AnalogClockView

    private val formatter by lazy {
        SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        )
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()

            // 디지털 시계 갱신
            clockView.text = formatter.format(now)

            // 다음 정각 초 경계에 맞춰 실행
            val delay = 1000L - (now % 1000L)

            handler.postDelayed(this, delay)
        }
    }

    private val gestureDetector by lazy {
        GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {

                    if (e1 == null)
                        return false

                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y

                    // 왼쪽 → 오른쪽 : 다음 이미지
                    if (
                        abs(dx) > abs(dy) &&
                        dx > 200 &&
                        abs(velocityX) > 200
                    ) {
                        slider.nextImage()
                        return true
                    }

                    // 오른쪽 → 왼쪽 : 앱 종료
                    if (
                        abs(dx) > abs(dy) &&
                        dx < -200 &&
                        abs(velocityX) > 200
                    ) {
                        finishAndRemoveTask()

                        overridePendingTransition(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                        )

                        return true
                    }

                    return false
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        val splash = installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        splash.setKeepOnScreenCondition {
            loading
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(
            R.layout.activity_main
        )

        handler.postDelayed({
            loading = false
        }, 500)

        init()
    }

    private fun init() {

        setupFullScreen()
        setupBackground()
        setupAnalogClock()
        setupClock()
        setupBottomText()
    }

    private fun setupFullScreen() {

        window.decorView.systemUiVisibility =
            (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )

        window.statusBarColor = Color.TRANSPARENT
    }

    private fun setupBackground() {

        val imageView =
            findViewById<ImageView>(
                R.id.bgImage
            )

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

        slider = BackgroundSlider(
            images,
            imageView,
            CHANGE_INTERVAL,
            FADE_DURATION
        )

        slider.start()
    }

    // 아날로그 시계 설정
    private fun setupAnalogClock() {

        analogClock =
            findViewById(R.id.analogClock)

        analogClock.visibility = View.VISIBLE

        analogClock.alpha = 0.8f
    }

    // 디지털 시계 설정
    private fun setupClock() {

        clockView = findViewById(R.id.textView1)

        clockView.apply {

            text = getString(R.string.app_exp1)

            setTextColor(Color.WHITE)
        }

        clockView.postDelayed({

            clockView.apply {

                visibility = View.VISIBLE

                textSize = CLOCK_SIZE

                setShadowLayer(
                    8f,
                    4f,
                    4f,
                    Color.BLACK
                )
            }

            handler.post(
                clockRunnable
            )

        }, INTRO_DELAY)
    }

    private fun setupBottomText() {

        listOf(
            R.id.appVer,
            R.id.appNickName
        ).forEach {

            findViewById<TextView>(it)
                .apply {

                    setTextColor(Color.WHITE)

                    setTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        SMALL_TEXT_SIZE
                    )
                }
        }

        findViewById<TextView>(
            R.id.mailAddress
        ).setTextColor(Color.WHITE)
    }

    override fun dispatchTouchEvent(
        event: MotionEvent
    ): Boolean {

        gestureDetector.onTouchEvent(event)

        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {

        super.onDestroy()

        if (::slider.isInitialized) {
            slider.stop()
        }

        handler.removeCallbacksAndMessages(null)
    }
}

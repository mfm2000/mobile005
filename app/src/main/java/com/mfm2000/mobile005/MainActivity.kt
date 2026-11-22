package com.mfm2000.mobile005

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CalendarView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.github.usingsky.calendar.KoreanLunarCalendar
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

class MainActivity : AppCompatActivity() {

    // 앱 전체에서 공통으로 사용하는 시간 및 환경설정 상수
    // 앱 전체에서 공통으로 사용하는 기본값과 SharedPreferences 저장 키를 정의한다.
    companion object {
        private const val CHANGE_INTERVAL = 10_000L
        private const val FADE_DURATION = 3_000
        private const val DATE_CHANGE_INTERVAL = 10_000L
        private const val INTRO_DELAY = 700L
        private const val CLOCK_SIZE = 70f
        private const val SMALL_TEXT_SIZE = 20f

        private const val PREFS_NAME = "clock_settings"
        private const val PREF_INTERVAL = "background_interval"
        private const val PREF_SOUND = "background_sound"
        private const val PREF_VOLUME = "background_volume"

        // 음악 목록 및 마지막 재생 위치
        private const val PREF_MUSIC_URI = "selected_music_uri" // 기존 버전 호환
        private const val PREF_MUSIC_URIS = "selected_music_uris"
        private const val PREF_MUSIC_INDEX = "selected_music_index"

        private const val PREF_IMAGE_URIS = "selected_background_images"
        private const val MAX_IMAGE_COUNT = 20
        private const val PREF_ANALOG_CLOCK = "analog_clock_visible"
        private const val PREF_DATE_INFO_VISIBLE = "date_info_visible"
    }

    // 이미지 선택 화면 실행 여부
    private var isImagePickerOpen = false

    // UI 갱신 작업을 메인 스레드에서 실행하는 Handler
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var dateView: TextView
    private lateinit var weekdayView: TextView
    private lateinit var yearGapjaView: TextView
    private lateinit var clockView: TextView
    private lateinit var analogClock: AnalogClockView
    private lateinit var slider: BackgroundSlider

    private var loading = true
    private var showSolar = true
    private var mediaPlayer: MediaPlayer? = null
    private var isMusicPrepared = false
    private var isMusicPreparing = false

    // 선택한 음악 목록
    private var selectedMusicUris = mutableListOf<String>()

    // 현재 재생 중인 곡의 목록상 위치
    private var currentMusicIndex = 0

    // 공통 정보 팝업
    private var infoPopup: PopupWindow? = null

    // 달력 팝업
    private var calendarPopup: PopupWindow? = null
    private var isActivityResumed = false
    // ---------------------------------------------------------------------
    // 간지·십이지 정보 표시 및 팝업
    // ---------------------------------------------------------------------

    // 클릭 가능한 텍스트 생성
    // 텍스트의 특정 부분을 클릭할 수 있도록 ClickableSpan을 생성한다.
    private fun createClickableSpan(
        action: () -> Unit
    ): ClickableSpan {

        return object : ClickableSpan() {

            override fun onClick(widget: View) {
                action()
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = Color.WHITE
                ds.isUnderlineText = false
                ds.typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    // 간지와 띠 텍스트 설정
    // 연도의 간지(한글·한자)와 띠 정보를 하나의 텍스트로 구성하고 각각 클릭 동작을 연결한다.
    private fun setYearInfoText(
        koreanYear: String,
        chineseYear: String,
        zodiac: String
    ) {
        val gapjaText = "$koreanYear($chineseYear)"
        val zodiacText = "${zodiac}해"

        val fullText = SpannableString("$gapjaText $zodiacText")

        // 간지 클릭
        fullText.setSpan(
            createClickableSpan {
                showTenStemsPopup()
            },
            0,
            gapjaText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // 띠 클릭
        fullText.setSpan(
            createClickableSpan {
                showZodiacPopup()
            },
            gapjaText.length + 1,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        yearGapjaView.text = fullText
    }

    // 공통 그리드 팝업
    // 전달받은 항목들을 지정한 열 수에 맞춰 배치하는 공통 중앙 팝업을 만든다.
    private fun showGridPopup(
        title: String,
        items: List<Pair<String, String>>,
        columns: Int,
        itemText: (Pair<String, String>) -> String
    ) {

        // 유효하지 않은 데이터 확인
        if (columns <= 0 || items.isEmpty()) {
            return
        }

        infoPopup?.dismiss()
        infoPopup = null

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DD202020"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), Color.WHITE)
            }
        }

        // 제목
        val titleView = TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(15))
        }

        layout.addView(titleView)

        // 행 개수 계산

        // 전체 항목 수와 열 수를 기준으로 필요한 행 수를 계산한다.
        val rows = (items.size + columns - 1) / columns

        // 데이터 표시
        for (rowIndex in 0 until rows) {

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val startIndex = rowIndex * columns
            val endIndex = minOf(startIndex + columns, items.size)

            for (i in startIndex until endIndex) {

                val item = TextView(this).apply {
                    text = itemText(items[i])
                    textSize = 20f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dp(5), dp(10), dp(5), dp(10))
                }

                row.addView(
                    item,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )
            }

            // 마지막 행의 빈 공간도 유지
            repeat(columns - (endIndex - startIndex)) {
                row.addView(
                    View(this),
                    LinearLayout.LayoutParams(
                        0,
                        1,
                        1f
                    )
                )
            }

            layout.addView(row)
        }

        // 팝업 생성
        val popup = PopupWindow(
            layout,
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                }
            )
            elevation = dp(20).toFloat()
        }

        infoPopup = popup

        popup.setOnDismissListener {
            if (infoPopup === popup) {
                infoPopup = null
            }
        }

        popup.showAtLocation(
            findViewById(R.id.main),
            Gravity.CENTER,
            0,
            0
        )
    }

    // 십간 팝업
    // 갑·을·병·정·무·기·경·신·임·계 십간 정보를 그리드 팝업으로 표시한다.
    private fun showTenStemsPopup() {

        val stems = listOf(
            "甲" to "갑",
            "乙" to "을",
            "丙" to "병",
            "丁" to "정",
            "戊" to "무",
            "己" to "기",
            "庚" to "경",
            "辛" to "신",
            "壬" to "임",
            "癸" to "계"
        )

        showGridPopup(
            title = "십간(十干)",
            items = stems,
            columns = 5,
            itemText = { "${it.first}\n(${it.second})" }
        )
    }

    // 십이지 팝업
    // 자·축·인·묘·진·사·오·미·신·유·술·해 십이지와 해당 띠를 표시한다.
    private fun showZodiacPopup() {

        val animals = listOf(
            "子" to "쥐띠",
            "丑" to "소띠",
            "寅" to "호랑이띠",
            "卯" to "토끼띠",
            "辰" to "용띠",
            "巳" to "뱀띠",
            "午" to "말띠",
            "未" to "양띠",
            "申" to "원숭이띠",
            "酉" to "닭띠",
            "戌" to "개띠",
            "亥" to "돼지띠"
        )

        showGridPopup(
            title = "십이지(十二支)",
            items = animals,
            columns = 4,
            itemText = { "${it.first}\n${it.second}" }
        )
    }

    // ---------------------------------------------------------------------
    // 달력 팝업
    // ---------------------------------------------------------------------
    // 날짜 텍스트를 누르면 이번 달 달력을 화면 중앙에 팝업으로 표시한다.
    private fun setupCalendarPopup() {

        val dateView = findViewById<TextView>(R.id.dateView)

        dateView.setOnClickListener {

            calendarPopup?.dismiss()

            // 팝업 제목
            val title = TextView(this).apply {
                text = SimpleDateFormat(
                    "yyyy년 M월",
                    Locale.KOREA
                ).format(Date())

                textSize = 22f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(10, 20, 10, 20)
            }

            // 이번 달 달력
            val calendarView = CalendarView(this).apply {
                date = System.currentTimeMillis()
                firstDayOfWeek = java.util.Calendar.SUNDAY
                setBackgroundColor(Color.WHITE)
            }

            // 팝업 내부 구성
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(12, 12, 12, 12)

                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DD202020"))
                    cornerRadius = 24f
                    setStroke(2, Color.WHITE)
                }

                addView(
                    title,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                addView(
                    calendarView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            calendarPopup = PopupWindow(
                layout,
                (resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(
                    GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                    }
                )
                elevation = 20f
            }

            // 화면 정중앙 표시
            calendarPopup?.showAtLocation(
                findViewById(R.id.main),
                Gravity.CENTER,
                0,
                0
            )
        }
    }
    // ---------------------------------------------------------------------
    // 음악 파일 선택 및 환경설정 저장
    // ---------------------------------------------------------------------

    // MP3 파일 선택기

    // 사용자가 선택한 여러 음악 파일의 URI 권한을 확보하고 재생 목록으로 등록한다.
    private val musicPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isEmpty()) {
                // 선택 취소 시 기존 음악 목록은 유지
                if (selectedMusicUris.isEmpty()) {
                    soundEnabled = false
                }
                updateMusicButton()
                return@registerForActivityResult
            }

            val validUris = mutableListOf<String>()

            for (uri in uris) {
                try {
                    // 영구 읽기 권한 확보
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    // 파일 접근 확인
                    contentResolver.openFileDescriptor(uri, "r")?.use {
                        // 정상적으로 열리는지 확인
                    } ?: throw IOException("파일에 접근할 수 없습니다.")

                    validUris.add(uri.toString())

                } catch (e: Exception) {
                    android.util.Log.e(
                        "MusicPlayer",
                        "음악 파일 접근 실패: $uri",
                        e
                    )
                }
            }

            if (validUris.isEmpty()) {
                Toast.makeText(
                    this,
                    "선택한 음악 파일에 접근할 수 없습니다.",
                    Toast.LENGTH_LONG
                ).show()

                updateMusicButton()
                return@registerForActivityResult
            }

            // 이전 재생 중지
            stopBackgroundMusic()

            // 새 음악 목록으로 교체
            selectedMusicUris.clear()
            selectedMusicUris.addAll(validUris)

            currentMusicIndex = 0

            // 음악 목록과 재생 위치 저장
            saveMusicList()

            // 음악 ON으로 설정하지만 자동 재생하지 않음
            soundEnabled = true

            updateMusicButton()

            Toast.makeText(
                this,
                "${validUris.size}개의 음악 파일이 등록되었습니다.",
                Toast.LENGTH_SHORT
            ).show()

            // 파일 선택이 완료되면 첫 번째 음악 즉시 재생
            applyBackgroundMusic()
        }

    // 앱 설정을 저장하고 읽는 SharedPreferences 객체를 최초 접근 시 생성한다.
    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    // 갤러리 이미지 복수 선택
    // 갤러리에서 여러 이미지를 선택하고 유효성 검사 후 URI 목록을 저장한다.
    private val imagePicker =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            isImagePickerOpen = false

            if (uris.isEmpty()) return@registerForActivityResult

            val selectedUris = mutableListOf<String>()

            for (uri in uris.take(MAX_IMAGE_COUNT)) {
                try {
                    // 이미지 접근 권한 영구 유지
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    // 이미지 유효성 확인
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }

                    val valid = contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                        options.outWidth > 0 && options.outHeight > 0
                    } ?: false

                    if (valid) {
                        selectedUris.add(uri.toString())
                    }

                } catch (e: Exception) {
                    android.util.Log.e(
                        "ImagePicker",
                        "이미지 접근 실패: $uri",
                        e
                    )
                }
            }

            // 선택한 이미지 URI 저장
            val jsonArray = JSONArray()
            selectedUris.forEach {
                jsonArray.put(it)
            }

            prefs.edit()
                .putString(PREF_IMAGE_URIS, jsonArray.toString())
                .apply()

            // 배경 이미지 다시 구성
            reloadBackgroundImages()

            Toast.makeText(
                this,
                "${selectedUris.size}장의 이미지가 등록되었습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }

    // 배경 이미지 자동 전환 간격을 저장소와 연결하는 속성이다.
    private var backgroundInterval: Long
        get() = prefs.getLong(PREF_INTERVAL, CHANGE_INTERVAL)
        set(value) {
            prefs.edit().putLong(PREF_INTERVAL, value).apply()
        }

    // 아날로그 시계 표시 여부
    // 아날로그 시계 표시 여부를 저장하고 불러온다.
    private var analogClockVisible: Boolean
        get() = prefs.getBoolean(PREF_ANALOG_CLOCK, true)
        set(value) {
            prefs.edit()
                .putBoolean(PREF_ANALOG_CLOCK, value)
                .apply()
        }

    // 간지·날짜·요일 표시 여부
    // 간지·날짜·요일 정보의 표시 여부를 저장하고 불러온다.
    private var dateInfoVisible: Boolean
        get() = prefs.getBoolean(PREF_DATE_INFO_VISIBLE, true)
        set(value) {
            prefs.edit()
                .putBoolean(PREF_DATE_INFO_VISIBLE, value)
                .apply()
        }

    // 배경음악 사용 여부를 저장하고 불러온다.
    private var soundEnabled: Boolean
        get() = prefs.getBoolean(PREF_SOUND, false)
        set(value) {
            prefs.edit().putBoolean(PREF_SOUND, value).apply()
        }

    // 배경음악 음량(0~100)을 저장하고 불러온다.
    private var soundVolume: Int
        get() = prefs.getInt(PREF_VOLUME, 50)
        set(value) {
            prefs.edit().putInt(PREF_VOLUME, value.coerceIn(0, 100)).apply()
        }

    // 디지털 시계에 사용할 시:분:초 형식의 날짜 포맷터를 생성한다.
    private val clockFormatter by lazy {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    // 한국어 요일 이름을 표시하기 위한 포맷터를 생성한다.
    private val weekdayFormatter by lazy {
        SimpleDateFormat("EEEE", Locale.KOREAN)
    }

    // 음력 연도에 해당하는 십이지 띠 이름을 인덱스 순서대로 보관한다.
    private val zodiacAnimals = arrayOf(
        "쥐띠", "소띠", "호랑이띠", "토끼띠",
        "용띠", "뱀띠", "말띠", "양띠",
        "원숭이띠", "닭띠", "개띠", "돼지띠"
    )

    // ---------------------------------------------------------------------
    // 시계·날짜 주기 갱신 작업
    // ---------------------------------------------------------------------

    // 디지털 시계 갱신
    // 디지털 시계를 다음 초 경계에 맞춰 갱신하는 반복 작업이다.
    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            clockView.text = clockFormatter.format(now)

            // 매초 정각에 가까운 시점에 갱신되도록 다음 초까지 남은 시간을 계산한다.
            val delay = 1000L - (now % 1000L)
            handler.postDelayed(this, delay)
        }
    }

    // 양력 / 음력 / 간지 갱신
    // 현재 날짜를 양력·음력·간지로 계산하고 일정 간격으로 화면에 반영한다.
    private val dateRunnable = object : Runnable {
        override fun run() {

            if (dateInfoVisible) {

                val now = Calendar.getInstance()
                val year = now.get(Calendar.YEAR)
                val month = now.get(Calendar.MONTH) + 1
                val day = now.get(Calendar.DAY_OF_MONTH)

                val lunar = KoreanLunarCalendar.getInstance()

                if (lunar.setSolarDate(year, month, day)) {

                    val koreanYear =
                        lunar.gapjaString.substringBefore(" ")

                    val chineseYear =
                        lunar.chineseGapJaString.substringBefore(" ")

                    // 갑자년을 기준점으로 삼아 음력 연도의 십이지 인덱스를 계산한다.
                    val zodiacIndex =
                        Math.floorMod(lunar.lunarYear - 4, 12)

                    val zodiac = zodiacAnimals[zodiacIndex]

                    setYearInfoText(
                        koreanYear,
                        chineseYear,
                        zodiac
                    )

                    weekdayView.text = weekdayFormatter.format(now.time)

                    val solarDate =
                        "양력 ${year}년 ${month}월 ${day}일"

                    val lunarMonth = lunar.lunarMonth
                    val lunarDay = lunar.lunarDay

                    val monthText = if (lunar.isIntercalation) {
                        "윤${lunarMonth}월"
                    } else {
                        "${lunarMonth}월"
                    }

                    val lunarDate =
                        "음력 ${lunar.lunarYear}년 $monthText ${lunarDay}일"

                    // showSolar 값에 따라 양력과 음력 날짜를 번갈아 표시한다.
                    val newDate =
                        if (showSolar) solarDate else lunarDate

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

                    showSolar = !showSolar
                }
            }

            handler.postDelayed(this, DATE_CHANGE_INTERVAL)
        }
    }

    // ---------------------------------------------------------------------
    // 화면 제스처 및 액티비티 초기화
    // ---------------------------------------------------------------------

    // 좌우 플링

    // 좌우 플링 동작을 감지한다. 오른쪽 스와이프는 다음 배경, 왼쪽 스와이프는 앱 종료다.
    private val gestureDetector by lazy {
        GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }


                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {

                    if (e1 == null) return false

                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y

                    if (
                        abs(dx) > abs(dy) &&
                        abs(dx) > dp(60) &&
                        abs(velocityX) > 100
                    ) {

                        if (dx > 0) {

                            // 다음 배경화면
                            if (::slider.isInitialized) {
                                slider.nextImage()
                            }

                        } else {

                            finishAndRemoveTask()

                            overridePendingTransition(
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                            )
                        }

                        return true
                    }

                    return false
                }

            }
        )
    }


    // 액티비티 생성
    // 액티비티 최초 생성 시 스플래시 화면, 전체 화면, 화면 유지 옵션과 레이아웃을 설정한다.
    override fun onCreate(savedInstanceState: Bundle?) {

        val splash = installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        splash.setKeepOnScreenCondition { loading }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_main)

        handler.postDelayed({
            loading = false
        }, 500L)

        init()
    }

    // 초기화
    // 화면의 각 구성 요소와 버튼 이벤트를 초기화한다.
    private fun init() {

        setupImmersiveMode()
        setupBackground()
        setupAnalogClock()
        setupDateDisplay()
        setupDigitalClock()
        setupFooterText()
        setupAlarmButton()
        setupSettingsButton()
        setupMusicButton()
        setupCalendarPopup()
        setupImageButton()

        weekdayView.setShadowLayer(
            5f,
            2f,
            2f,
            Color.BLACK
        )
    }

    // ---------------------------------------------------------------------
    // 화면 버튼 및 설정 다이얼로그
    // ---------------------------------------------------------------------

    // 알람 설정 버튼
    // 알람 설정 버튼을 누르면 안드로이드 기본 알람 앱을 실행한다.
    private fun setupAlarmButton() {

        findViewById<Button>(R.id.btnRecentApps).apply {

            text = "알람 설정"

            setOnClickListener {
                try {
                    startActivity(
                        Intent(AlarmClock.ACTION_SET_ALARM)
                    )
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        this@MainActivity,
                        "알람 앱을 찾을 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: SecurityException) {
                    Toast.makeText(
                        this@MainActivity,
                        "알람 설정 권한을 확인해 주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // 설정 버튼
    // 설정 아이콘에 클릭 이벤트를 연결한다.
    private fun setupSettingsButton() {

        findViewById<ImageView>(R.id.btnSettings)
            .setOnClickListener {
                showSettingsDialog()
            }
    }

    // 설정 팝업
    // 배경 전환 속도, 시계·날짜 표시, 음악 사용 여부와 음량을 설정하는 대화상자를 구성한다.
    private fun showSettingsDialog() {

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }

        // 배경 전환 속도
        val speedTitle = TextView(this).apply {
            text = "배경화면 전환 속도"
            textSize = 18f
        }

        val speedValue = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(8), 0, dp(4))
        }

        val speedSeekBar = SeekBar(this).apply {
            max = 11
            progress = ((backgroundInterval / 5_000L).toInt() - 1)
                .coerceIn(0, 11)
        }

        fun updateSpeedText() {
            val seconds = (speedSeekBar.progress + 1) * 5
            speedValue.text = "${seconds}초"
        }

        speedSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    updateSpeedText()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        updateSpeedText()

        // 아날로그 시계 표시 ON / OFF
        val analogClockSwitch = Switch(this).apply {
            text = "아날로그 시계 표시"
            textSize = 18f
            isChecked = analogClockVisible
            setPadding(0, dp(20), 0, dp(8))
        }

        container.addView(analogClockSwitch)

        // 간지·날짜·요일 표시 ON / OFF
        val dateInfoSwitch = Switch(this).apply {
            text = "간지·날짜·요일 표시"
            textSize = 18f
            isChecked = dateInfoVisible
            setPadding(0, dp(20), 0, dp(8))
        }

        container.addView(dateInfoSwitch)

        container.addView(speedTitle)
        container.addView(speedValue)
        container.addView(speedSeekBar)

        // 음악 ON / OFF
        val soundSwitch = Switch(this).apply {
            text = "배경음악"
            textSize = 18f
            isChecked = soundEnabled
            setPadding(0, dp(20), 0, dp(8))
        }

        container.addView(soundSwitch)

        // 음량
        val volumeTitle = TextView(this).apply {
            text = "음량 ${soundVolume}%"
            textSize = 16f
            setPadding(0, dp(8), 0, dp(4))
        }

        val volumeSeekBar = SeekBar(this).apply {
            max = 100
            progress = soundVolume
        }

        volumeSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    volumeTitle.text = "음량 ${progress}%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        container.addView(volumeTitle)
        container.addView(volumeSeekBar)

        AlertDialog.Builder(this)
            .setTitle("설정")
            .setView(container)

            .setPositiveButton("저장") { _, _ ->

                // 1. 날짜 정보 표시 설정
                dateInfoVisible = dateInfoSwitch.isChecked

                val dateVisibility = if (dateInfoVisible) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }

                dateView.visibility = dateVisibility
                yearGapjaView.visibility = dateVisibility
                weekdayView.visibility = dateVisibility

                if (dateInfoVisible) {
                    dateView.alpha = 1f
                    yearGapjaView.alpha = 1f
                    weekdayView.alpha = 1f

                    handler.removeCallbacks(dateRunnable)
                    handler.post(dateRunnable)
                }

                // 2. 아날로그 시계 표시 설정
                analogClockVisible = analogClockSwitch.isChecked

                analogClock.visibility = if (analogClockVisible) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }

                // 3. 배경화면 전환 속도
                // SeekBar 0~11을 5~60초 간격으로 변환한다.
                val interval = (speedSeekBar.progress + 1) * 5_000L

                backgroundInterval = interval

                if (::slider.isInitialized) {
                    slider.setChangeInterval(interval)
                }


                // 4. 음악 설정
                soundEnabled = soundSwitch.isChecked
                soundVolume = volumeSeekBar.progress

                // 현재 플레이어에도 음량 적용
                val volume = soundVolume / 100f
                mediaPlayer?.setVolume(volume, volume)

                // OFF이면 음악 완전 정지
                if (!soundEnabled) {
                    stopBackgroundMusic()
                }

                // ON이어도 MP3 선택 화면을 호출하지 않음
                updateMusicButton()

                Toast.makeText(
                    this,
                    "설정이 저장되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            .setNegativeButton("취소", null)
            .show()
    }

    // ---------------------------------------------------------------------
    // 공통 유틸리티 및 음악 재생 제어
    // ---------------------------------------------------------------------

    // DP 변환
    // 화면 밀도에 관계없이 일정한 크기로 보이도록 dp 단위를 실제 픽셀로 변환한다.
    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // 선택한 MP3 음악 적용

    // 음악 재생 적용
    // 저장된 음악 목록과 사용 설정을 확인한 뒤 현재 곡을 재생하거나 일시정지 상태에서 재개한다.
    private fun applyBackgroundMusic() {

        if (!soundEnabled) {
            stopBackgroundMusic()
            return
        }

        if (selectedMusicUris.isEmpty()) {
            updateMusicButton()

            Toast.makeText(
                this,
                "음악 파일을 선택해 주세요.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // 이미 준비된 플레이어가 있으면 이어서 재생
        if (mediaPlayer != null) {

            if (isMusicPreparing) return

            if (isMusicPrepared) {
                try {
                    if (mediaPlayer?.isPlaying == false) {
                        mediaPlayer?.start()
                    }
                } catch (e: Exception) {
                    android.util.Log.e(
                        "MusicPlayer",
                        "음악 재개 실패",
                        e
                    )
                }

                updateMusicButton()
                return
            }
        }

        playMusicAt(currentMusicIndex)
    }


    // 지정한 순서의 음악 재생
    // 재생 목록에서 지정한 곡을 비동기 방식으로 준비하고 재생 완료 시 다음 곡으로 이동한다.
    private fun playMusicAt(index: Int) {

        if (!soundEnabled || selectedMusicUris.isEmpty()) {
            return
        }

        // 음수 또는 목록 범위를 벗어난 인덱스도 재생 목록 범위 안으로 순환시킨다.
        currentMusicIndex = Math.floorMod(
            index,
            selectedMusicUris.size
        )

        saveMusicList()

        // 기존 플레이어 정리
        releaseMusicPlayer()

        val musicUri = selectedMusicUris[currentMusicIndex]

        try {

            val player = MediaPlayer()

            mediaPlayer = player

            isMusicPrepared = false
            isMusicPreparing = true

            player.isLooping = false

            val volume = soundVolume / 100f
            player.setVolume(volume, volume)

            // 음악 준비 완료
            player.setOnPreparedListener { mp ->

                if (mediaPlayer !== mp) return@setOnPreparedListener

                isMusicPreparing = false
                isMusicPrepared = true

                if (soundEnabled) {
                    try {
                        mp.start()
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "MusicPlayer",
                            "음악 재생 시작 실패",
                            e
                        )
                    }
                }

                updateMusicButton()
            }

            // 현재 곡이 끝나면 다음 곡 재생
            player.setOnCompletionListener { mp ->

                if (mediaPlayer !== mp) return@setOnCompletionListener

                currentMusicIndex =
                    (currentMusicIndex + 1) % selectedMusicUris.size

                saveMusicList()

                playMusicAt(currentMusicIndex)
            }

            // 재생 오류 처리
            player.setOnErrorListener { mp, what, extra ->

                android.util.Log.e(
                    "MusicPlayer",
                    "재생 오류: URI=$musicUri, what=$what, extra=$extra"
                )

                if (mediaPlayer === mp) {
                    releaseMusicPlayer()
                }

                updateMusicButton()

                Toast.makeText(
                    this,
                    "음악 재생에 실패했습니다. 해당 파일을 확인해 주세요.",
                    Toast.LENGTH_LONG
                ).show()

                true
            }

            // URI를 이용해 음악 파일 열기
            player.setDataSource(
                this,
                Uri.parse(musicUri)
            )

            // 준비 과정은 비동기로 실행하여 UI 스레드가 멈추지 않도록 한다.
            player.prepareAsync()

        } catch (e: Exception) {

            android.util.Log.e(
                "MusicPlayer",
                "음악 파일 열기 실패: $musicUri",
                e
            )

            releaseMusicPlayer()

            updateMusicButton()

            Toast.makeText(
                this,
                "음악 파일 열기 실패: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    // 플레이어 리소스 해제
    // MediaPlayer를 해제하고 준비·재생 상태 플래그를 초기화한다.
    private fun releaseMusicPlayer() {

        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            android.util.Log.e(
                "MusicPlayer",
                "플레이어 해제 실패",
                e
            )
        }

        mediaPlayer = null
        isMusicPrepared = false
        isMusicPreparing = false
    }


    // 음악 완전 정지
    // 현재 음악을 완전히 중지하고 MediaPlayer 리소스를 해제한다.
    private fun stopBackgroundMusic() {

        releaseMusicPlayer()

        updateMusicButton()
    }


    // 화면을 벗어날 때는 일시정지
    // 앱이 화면을 벗어날 때 현재 재생 중인 음악을 일시정지한다.
    private fun pauseBackgroundMusic() {

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "MusicPlayer",
                "음악 일시정지 실패",
                e
            )
        }

        updateMusicButton()
    }

    // ---------------------------------------------------------------------
    // 화면 구성 요소 초기화
    // ---------------------------------------------------------------------

    // 전체 화면 모드
    // 상태 표시줄과 내비게이션 바를 숨겨 몰입형 전체 화면으로 설정한다.
    private fun setupImmersiveMode() {

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        window.statusBarColor = Color.TRANSPARENT
    }

    // 배경 이미지
    // 저장된 배경 이미지 또는 기본 이미지를 읽어 슬라이더를 구성한다.
    private fun setupBackground() {
        reloadBackgroundImages()
    }

    // 저장된 이미지 목록을 불러와 배경 구성
    // 기존 슬라이더를 정지한 뒤 저장된 이미지 목록으로 BackgroundSlider를 다시 생성한다.
    private fun reloadBackgroundImages() {

        val imageView = findViewById<ImageView>(R.id.bgImage)
        val imageViewNext = findViewById<ImageView>(R.id.bgImageNext)

        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageViewNext.scaleType = ImageView.ScaleType.CENTER_CROP

        // 기존 슬라이더 정지
        if (::slider.isInitialized) {
            slider.stop()
        }

        // 저장된 이미지 로딩
        val selectedImages = loadSelectedImages()

        // 사용자 이미지가 없으면 기본 이미지 사용
        val images: Array<Drawable> =
            if (selectedImages.isNotEmpty()) {
                selectedImages.toTypedArray()
            } else {
                arrayOf(
                    requireNotNull(
                        androidx.core.content.ContextCompat.getDrawable(
                            this,
                            R.drawable.defaultimg1
                        )
                    ),
                    requireNotNull(
                        androidx.core.content.ContextCompat.getDrawable(
                            this,
                            R.drawable.defaultimg2
                        )
                    )
                )
            }

        slider = BackgroundSlider(
            images,
            imageView,
            imageViewNext,
            backgroundInterval,
            FADE_DURATION
        )

        slider.start()
    }

    // 저장된 이미지 URI를 Drawable 목록으로 변환

    // 저장된 이미지 URI를 읽고 화면 크기에 맞게 축소·회전 보정하여 Drawable 목록으로 반환한다.
    private fun loadSelectedImages(): List<Drawable> {

        val result = mutableListOf<Drawable>()
        val validUris = mutableListOf<String>()

        val savedJson = prefs.getString(PREF_IMAGE_URIS, null)
            ?: return result

        val jsonArray = try {
            JSONArray(savedJson)
        } catch (e: Exception) {
            return result
        }

        // 현재 기기의 화면 픽셀 크기를 기준으로 이미지 디코딩 크기를 제한한다.
        val targetWidth = resources.displayMetrics.widthPixels
        val targetHeight = resources.displayMetrics.heightPixels

        for (i in 0 until jsonArray.length()) {

            val uriString = jsonArray.optString(i)
            if (uriString.isBlank()) continue

            try {
                val uri = Uri.parse(uriString)

                // 1. 원본 이미지 크기 확인
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                val boundsValid = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                    bounds.outWidth > 0 && bounds.outHeight > 0
                } ?: false

                if (!boundsValid) continue

                // 2. 화면 크기에 맞는 이미지 축소 비율 계산
                var sampleSize = 1

                // 화면보다 지나치게 큰 원본은 메모리 사용량을 줄이도록 2의 배수로 축소한다.
                while (
                    bounds.outWidth / sampleSize > targetWidth * 2 ||
                    bounds.outHeight / sampleSize > targetHeight * 2
                ) {
                    sampleSize *= 2
                }

                // 3. 축소된 이미지 로딩
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }

                val bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: continue

                // EXIF 회전 정보에 따라 이미지 방향 보정
                // 촬영 기기의 EXIF 방향 정보가 있으면 실제 표시 방향에 맞게 회전한다.
                val correctedBitmap = correctExifRotation(uri, bitmap)

                // 보정된 이미지로 Drawable 생성
                result.add(BitmapDrawable(resources, correctedBitmap))

                validUris.add(uriString)

            } catch (e: Exception) {
                android.util.Log.e(
                    "ImagePicker",
                    "이미지 로딩 실패: $uriString",
                    e
                )
            }
        }

        // 4. 유효하지 않은 이미지 URI 정리
        if (validUris.size != jsonArray.length()) {

            val updatedJson = JSONArray()

            validUris.forEach {
                updatedJson.put(it)
            }

            prefs.edit()
                .putString(PREF_IMAGE_URIS, updatedJson.toString())
                .apply()
        }

        return result
    }

    // 아날로그 시계 설정
    // 레이아웃의 아날로그 시계를 연결하고 저장된 표시 여부와 투명도를 적용한다.
    private fun setupAnalogClock() {

        analogClock = findViewById(R.id.analogClock)

        analogClock.apply {
            visibility = if (analogClockVisible) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }

            alpha = 0.8f
        }
    }

    // 디지털 시계
    // 디지털 시계 텍스트를 초기화하고 인트로 지연 후 1초 단위 갱신을 시작한다.
    private fun setupDigitalClock() {

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

            handler.post(clockRunnable)

        }, INTRO_DELAY)
    }

    // 하단 앱 정보
    // 화면 하단의 앱 버전·닉네임·메일 주소 색상과 글자 크기를 설정한다.
    private fun setupFooterText() {

        listOf(
            R.id.appVer,
            R.id.appNickName
        ).forEach { id ->

            findViewById<TextView>(id).apply {
                setTextColor(Color.WHITE)

                setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    SMALL_TEXT_SIZE
                )
            }
        }

        findViewById<TextView>(R.id.mailAddress)
            .setTextColor(Color.WHITE)
    }

    // 날짜 표시 초기화
    // 날짜·간지·요일 뷰를 연결하고 클릭 이벤트와 저장된 표시 설정을 적용한다.
    private fun setupDateDisplay() {

        dateView = findViewById(R.id.dateView)
        yearGapjaView = findViewById(R.id.yearGapjaView)
        weekdayView = findViewById(R.id.weekdayView)

        // 간지 클릭 이벤트 활성화
        yearGapjaView.movementMethod =
            LinkMovementMethod.getInstance()

        yearGapjaView.highlightColor = Color.TRANSPARENT

        // 저장된 표시 설정 적용
        val visibility = if (dateInfoVisible) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }

        dateView.visibility = visibility
        yearGapjaView.visibility = visibility
        weekdayView.visibility = visibility

        handler.post(dateRunnable)
    }

    // 터치 이벤트

    // 터치 이벤트를 제스처 감지기에도 전달한 뒤 기존 뷰의 터치 처리도 유지한다.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        // 모든 터치 이벤트를 제스처 감지기에 전달
        gestureDetector.onTouchEvent(event)

        // 기존 화면 터치 이벤트도 정상 처리
        return super.dispatchTouchEvent(event)
    }


    // ---------------------------------------------------------------------
    // 액티비티 생명주기 및 음악 버튼
    // ---------------------------------------------------------------------

    // 앱이 화면에 다시 나타날 때 음악 적용

    // 액티비티가 다시 활성화되면 배경 이미지와 음악 버튼 상태를 갱신한다.
    override fun onResume() {
        super.onResume()

        isActivityResumed = true

        if (::slider.isInitialized) {
            reloadBackgroundImages()
        }

        updateMusicButton()
    }

    // 앱이 화면에서 벗어날 때 음악 일시 정지
    // 액티비티가 비활성화되면 음악을 일시정지하고 화면 활성 상태를 해제한다.
    override fun onPause() {
        isActivityResumed = false

        // 화면을 벗어나면 일시정지
        pauseBackgroundMusic()

        super.onPause()
    }
    // 종료 시 리소스 정리

    // 액티비티 종료 시 슬라이더, Handler, 팝업과 음악 플레이어 리소스를 정리한다.
    override fun onDestroy() {

        // 배경 이미지 슬라이더 정지
        if (::slider.isInitialized) {
            slider.stop()
        }

        // 모든 Handler 작업 종료
        handler.removeCallbacksAndMessages(null)

        // 공통 정보 팝업 종료
        infoPopup?.dismiss()
        infoPopup = null

        // 달력 팝업 종료
        calendarPopup?.dismiss()
        calendarPopup = null

        // 음악 플레이어 해제
        releaseMusicPlayer()

        super.onDestroy()
    }

    private fun setupImageButton() {
        findViewById<android.widget.ImageButton>(R.id.btnImages).setOnClickListener {

            // 이미 선택 화면이 열려 있으면 무시
            if (isImagePickerOpen) return@setOnClickListener

            // 실행 상태로 변경
            isImagePickerOpen = true

            try {
                imagePicker.launch(arrayOf("image/*"))
            } catch (e: Exception) {
                // 실행 실패 시 다시 버튼 사용 가능
                isImagePickerOpen = false

                Toast.makeText(
                    this,
                    "이미지 선택 화면을 실행할 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 음악 선택 및 재생 버튼
    // 음악 선택 및 재생 버튼
    private fun setupMusicButton() {

        val btnMusic = findViewById<ImageView>(R.id.btnMusic)

        loadMusicList()

        // 저장된 음악 파일 접근 권한 확인
        val validUris = mutableListOf<String>()

        for (uriString in selectedMusicUris) {
            try {
                contentResolver.openFileDescriptor(
                    Uri.parse(uriString),
                    "r"
                )?.use {
                    validUris.add(uriString)
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "MusicPlayer",
                    "저장된 음악 파일 접근 실패: $uriString",
                    e
                )
            }
        }

        if (validUris.size != selectedMusicUris.size) {
            selectedMusicUris.clear()
            selectedMusicUris.addAll(validUris)
            currentMusicIndex = 0
            saveMusicList()
        }

        if (selectedMusicUris.isEmpty()) {
            soundEnabled = false
        }

        updateMusicButton()

        // 짧게 누르면 재생 / 일시정지

        btnMusic.setOnClickListener {

            if (!soundEnabled) return@setOnClickListener

            if (mediaPlayer?.isPlaying == true) {

                // 재생 중이면 일시정지
                pauseBackgroundMusic()

            } else {

                // 등록된 음악이 없으면 MP3 선택 화면 실행
                if (selectedMusicUris.isEmpty()) {

                    musicPicker.launch(arrayOf("audio/*"))

                } else {

                    // 등록된 음악이 있으면 바로 재생
                    applyBackgroundMusic()
                }
            }

            updateMusicButton()
        }


        // 길게 누르면 음악 목록 변경
        btnMusic.setOnLongClickListener {

            musicPicker.launch(arrayOf("audio/*"))

            true
        }
    }

    // 음악 아이콘 상태 변경
    private fun updateMusicButton() {

        val btnMusic = findViewById<ImageView>(R.id.btnMusic)

        // 음악 설정 OFF이면 아이콘 숨김
        if (!soundEnabled) {
            btnMusic.visibility = View.GONE
            return
        }

        // 음악 설정 ON이면 항상 표시
        btnMusic.visibility = View.VISIBLE

        val isPlaying = mediaPlayer?.isPlaying == true

        btnMusic.setImageResource(
            if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
        )

        // 재생과 일시정지 아이콘 모두 흰색
        btnMusic.setColorFilter(Color.WHITE)
    }

    // 음악 목록 저장
    private fun saveMusicList() {

        val jsonArray = JSONArray()

        selectedMusicUris.forEach {
            jsonArray.put(it)
        }

        prefs.edit()
            .putString(PREF_MUSIC_URIS, jsonArray.toString())
            .putInt(PREF_MUSIC_INDEX, currentMusicIndex)
            .apply()
    }

    // 음악 목록 불러오기
    private fun loadMusicList() {

        selectedMusicUris.clear()

        val savedJson = prefs.getString(PREF_MUSIC_URIS, null)

        if (!savedJson.isNullOrBlank()) {

            try {
                val jsonArray = JSONArray(savedJson)

                for (i in 0 until jsonArray.length()) {
                    val uri = jsonArray.optString(i)

                    if (uri.isNotBlank()) {
                        selectedMusicUris.add(uri)
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(
                    "MusicPlayer",
                    "음악 목록 읽기 실패",
                    e
                )
            }
        }

        // 이전 버전의 단일 음악 URI를 복수 목록으로 이전
        if (selectedMusicUris.isEmpty()) {

            val oldUri = prefs.getString(PREF_MUSIC_URI, null)

            if (!oldUri.isNullOrBlank()) {
                selectedMusicUris.add(oldUri)
                saveMusicList()
            }
        }

        currentMusicIndex = prefs.getInt(
            PREF_MUSIC_INDEX,
            0
        )

        if (selectedMusicUris.isNotEmpty()) {
            currentMusicIndex = currentMusicIndex.coerceIn(
                0,
                selectedMusicUris.lastIndex
            )
        } else {
            currentMusicIndex = 0
        }
    }

    private fun correctExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: return bitmap

            val exif = inputStream.use {
                ExifInterface(it)
            }

            val rotation = exif.rotationDegrees

            if (rotation == 0) {
                bitmap
            } else {
                val matrix = Matrix().apply {
                    postRotate(rotation.toFloat())
                }

                Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageRotation", "EXIF 회전 보정 실패", e)
            bitmap
        }
    }
}

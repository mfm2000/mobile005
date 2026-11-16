package com.mfm2000.mobile005

import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.core.content.ContextCompat
import kotlin.random.Random

class BackgroundSlider(
    private val images: Array<Int>,
    private val imageView: ImageView,
    private val changeInterval: Long = 10_000L,
    private val fadeDuration: Int = 3_000
) {

    private val handler = Handler(Looper.getMainLooper())

    // Drawable 원본 보관
    private val drawables: List<Drawable> = images.mapNotNull {
        ContextCompat.getDrawable(imageView.context, it)
    }

    private var currentIndex = 0
    private var isRunning = false

    init {
        if (drawables.isNotEmpty()) {
            currentIndex = Random.nextInt(drawables.size)
        }
    }

    // 자동 이미지 변경
    private val runnable = object : Runnable {
        override fun run() {

            if (!isRunning || drawables.size < 2) return

            showNextImage()

            handler.postDelayed(this, changeInterval)
        }
    }

    // 다음 이미지 인덱스
    private fun getNextIndex(): Int {

        if (drawables.size < 2) return currentIndex

        var nextIndex: Int

        do {
            nextIndex = Random.nextInt(drawables.size)
        } while (nextIndex == currentIndex)

        return nextIndex
    }

    // 이미지 전환 공통 함수
    private fun changeImage(nextIndex: Int) {

        if (drawables.isEmpty()) return

        val currentDrawable =
            drawables[currentIndex].constantState
                ?.newDrawable(imageView.resources)
                ?.mutate()
                ?: drawables[currentIndex]

        val nextDrawable =
            drawables[nextIndex].constantState
                ?.newDrawable(imageView.resources)
                ?.mutate()
                ?: drawables[nextIndex]

        val transition = TransitionDrawable(
            arrayOf(currentDrawable, nextDrawable)
        ).apply {
            isCrossFadeEnabled = true
        }

        imageView.setImageDrawable(transition)

        transition.startTransition(fadeDuration)

        currentIndex = nextIndex
    }

    // 랜덤 이미지 전환
    private fun showNextImage() {

        if (drawables.size < 2) return

        changeImage(getNextIndex())
    }

    // 왼쪽 → 오른쪽 스와이프
    fun nextImage() {

        if (drawables.size < 2) return

        changeImage((currentIndex + 1) % drawables.size)

        // 자동 변경 시간 초기화
        if (isRunning) {
            handler.removeCallbacks(runnable)
            handler.postDelayed(runnable, changeInterval)
        }
    }

    // 슬라이더 시작
    fun start() {

        if (isRunning || drawables.isEmpty()) return

        isRunning = true

        imageView.setImageDrawable(drawables[currentIndex])

        if (drawables.size >= 2) {
            handler.postDelayed(runnable, changeInterval)
        }
    }

    // 슬라이더 정지
    fun stop() {

        isRunning = false
        handler.removeCallbacks(runnable)
    }
}

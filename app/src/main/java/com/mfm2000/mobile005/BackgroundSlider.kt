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
    private val changeInterval: Long = 10000L,
    private val fadeDuration: Int = 3000

) {

    private val handler = Handler(Looper.getMainLooper())

    private val drawables: List<Drawable>

    private var currentIndex = 0

    init {

        val list = mutableListOf<Drawable>()

        for (image in images) {
            val drawable = ContextCompat.getDrawable(imageView.context, image)
            if (drawable != null) {
                list.add(drawable)
            }
        }

        drawables = list

        if (drawables.isNotEmpty()) {
            currentIndex = Random.nextInt(drawables.size)
        }
    }

    private val runnable = object : Runnable {

        override fun run() {

            if (drawables.size < 2) return

            var nextIndex: Int

            do {
                nextIndex = Random.nextInt(drawables.size)
            } while (nextIndex == currentIndex)

            val transition = TransitionDrawable(
                arrayOf(
                    drawables[currentIndex],
                    drawables[nextIndex]
                )
            )

            transition.isCrossFadeEnabled = true

            imageView.setImageDrawable(transition)

            transition.startTransition(fadeDuration)

            currentIndex = nextIndex

            handler.postDelayed(this, changeInterval)
        }
    }

    // ========================================
    // 왼쪽 → 오른쪽 스와이프
    // 다음 이미지로 변경
    // ========================================
    fun nextImage() {

        if (drawables.size < 2)
            return

        val nextIndex =
            (currentIndex + 1) % drawables.size

        val transition =
            TransitionDrawable(
                arrayOf(
                    drawables[currentIndex],
                    drawables[nextIndex]
                )
            )

        transition.isCrossFadeEnabled = true

        imageView.setImageDrawable(
            transition
        )

        transition.startTransition(
            fadeDuration
        )

        currentIndex = nextIndex

        // 기존 자동 변경 예약을 취소하고
        // 10초를 새로 시작
        handler.removeCallbacks(runnable)

        handler.postDelayed(
            runnable,
            changeInterval
        )
    }

    fun start() {

        if (drawables.isEmpty()) return

        imageView.setImageDrawable(drawables[currentIndex])

        handler.postDelayed(runnable, changeInterval)
    }

    fun stop() {

        handler.removeCallbacksAndMessages(null)
    }
}
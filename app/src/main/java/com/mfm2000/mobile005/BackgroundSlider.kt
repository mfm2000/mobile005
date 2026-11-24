
package com.mfm2000.mobile005

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.exifinterface.media.ExifInterface
import java.util.concurrent.Executors

// 이미지 원본 정보만 보관한다.
// 실제 Bitmap은 필요한 시점에만 생성한다.
sealed class BackgroundImageSource {

    data class UriSource(
        val uri: Uri
    ) : BackgroundImageSource()

    data class ResourceSource(
        @DrawableRes val resId: Int
    ) : BackgroundImageSource()
}

class BackgroundSlider(
    private val context: Context,
    private val images: List<BackgroundImageSource>,
    private val imageView: ImageView,
    private val imageViewNext: ImageView,
    private var changeInterval: Long,
    private val fadeDuration: Int,
    private val onFirstImageLoaded: () -> Unit
){

    private val handler = Handler(Looper.getMainLooper())

    // 이미지 디코딩 전용 스레드
    private val executor = Executors.newSingleThreadExecutor()

    // 현재 화면에 표시 중인 Bitmap
    private var currentBitmap: Bitmap? = null

    // 다음 화면에 표시할 Bitmap
    private var nextBitmap: Bitmap? = null

    private var currentIndex = -1
    private var nextIndex = -1

    @Volatile
    private var isRunning = false

    private var isTransitioning = false
    private var isLoadingNext = false
    private var pendingAdvance = false

    private val slideRunnable = object : Runnable {
        override fun run() {

            if (!isRunning) return

            nextImage()

            handler.postDelayed(
                this,
                changeInterval
            )
        }
    }

    // ---------------------------------------------------------
    // 슬라이드 시작
    // ---------------------------------------------------------


    // ---------------------------------------------------------
// 슬라이드 시작
// 첫 번째 이미지가 완전히 로딩된 후 메인 화면 표시
// ---------------------------------------------------------
    fun start() {

        // 이미 실행 중이면 중복 실행 방지
        if (isRunning) return

        // 이미지가 없는 경우 화면 표시 콜백 실행
        if (images.isEmpty()) {
            onFirstImageLoaded()
            return
        }

        isRunning = true

        // 기존 배경 이미지는 유지한다.
        // 다음 이미지 뷰만 초기화한다.
        imageViewNext.setImageDrawable(null)
        imageViewNext.alpha = 0f

        imageView.alpha = 1f
        imageViewNext.alpha = 0f
        imageViewNext.visibility = View.VISIBLE

        // 첫 번째 이미지를 백그라운드 스레드에서 로딩
        executor.execute {

            val first = decodeNextValid(0)

            handler.post {

                // 로딩 도중 슬라이더가 종료된 경우
                if (!isRunning) {
                    first?.second?.let {
                        if (!it.isRecycled) it.recycle()
                    }
                    return@post
                }

                // 정상적인 첫 이미지 로딩 실패
                if (first == null) {
                    android.util.Log.e(
                        "BackgroundSlider",
                        "첫 번째 배경 이미지 로딩 실패"
                    )

                    // 앱 화면이 계속 숨겨지는 것을 방지
                    onFirstImageLoaded()
                    return@post
                }

                // 첫 이미지 설정
                currentIndex = first.first
                currentBitmap = first.second

                imageView.setImageDrawable(
                    BitmapDrawable(
                        context.resources,
                        first.second
                    )
                )

                imageView.alpha = 1f

                // 첫 번째 이미지가 준비된 후 메인 화면 표시
                onFirstImageLoaded()

                // 다음 이미지 미리 로딩
                preloadNextImage()

                // 자동 이미지 전환 시작
                handler.postDelayed(
                    slideRunnable,
                    changeInterval
                )
            }
        }
    }


    // ---------------------------------------------------------
    // 다음 이미지 미리 준비
    // ---------------------------------------------------------

    private fun preloadNextImage() {

        if (!isRunning || isLoadingNext || isTransitioning) return

        if (currentIndex < 0) return

        isLoadingNext = true

        val startIndex =
            (currentIndex + 1) % images.size

        executor.execute {

            val result = decodeNextValid(startIndex)

            handler.post {

                isLoadingNext = false

                if (!isRunning) {
                    result?.second?.recycle()
                    return@post
                }

                if (result == null) return@post

                nextIndex = result.first
                nextBitmap = result.second

                // 이미지 로딩 중 수동 전환 요청이 있었다면 실행
                if (pendingAdvance) {
                    pendingAdvance = false
                    nextImage()
                }
            }
        }
    }

    // ---------------------------------------------------------
    // 다음 이미지 디코딩
    // ---------------------------------------------------------

    private fun decodeNextValid(
        startIndex: Int
    ): Pair<Int, Bitmap>? {

        if (images.isEmpty()) return null

        // 오류 이미지가 있으면 건너뛰고 다음 이미지 검색
        for (offset in images.indices) {

            if (!isRunning) return null

            val index = (startIndex + offset) % images.size

            try {

                val bitmap = decodeImage(images[index])

                if (bitmap != null) {
                    return index to bitmap
                }

            } catch (e: Exception) {

                android.util.Log.e(
                    "BackgroundSlider",
                    "이미지 디코딩 실패: $index",
                    e
                )
            }
        }

        return null
    }

    // ---------------------------------------------------------
    // 실제 이미지 디코딩
    // ---------------------------------------------------------

    private fun decodeImage(
        source: BackgroundImageSource
    ): Bitmap? {

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        when (source) {

            is BackgroundImageSource.UriSource -> {

                context.contentResolver
                    .openInputStream(source.uri)
                    ?.use {
                        BitmapFactory.decodeStream(
                            it,
                            null,
                            bounds
                        )
                    }
            }

            is BackgroundImageSource.ResourceSource -> {

                BitmapFactory.decodeResource(
                    context.resources,
                    source.resId,
                    bounds
                )
            }
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val targetWidth =
            context.resources.displayMetrics.widthPixels

        val targetHeight =
            context.resources.displayMetrics.heightPixels

        // 화면보다 큰 이미지를 단계적으로 축소
        var sampleSize = 1

        while (
            bounds.outWidth / sampleSize > targetWidth * 2 ||
            bounds.outHeight / sampleSize > targetHeight * 2
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val bitmap = when (source) {

            is BackgroundImageSource.UriSource -> {

                context.contentResolver
                    .openInputStream(source.uri)
                    ?.use {
                        BitmapFactory.decodeStream(
                            it,
                            null,
                            options
                        )
                    }
            }

            is BackgroundImageSource.ResourceSource -> {

                BitmapFactory.decodeResource(
                    context.resources,
                    source.resId,
                    options
                )
            }
        } ?: return null

        // URI 이미지의 EXIF 회전 정보 보정
        if (source is BackgroundImageSource.UriSource) {

            try {

                val rotation = context.contentResolver
                    .openInputStream(source.uri)
                    ?.use {
                        ExifInterface(it).rotationDegrees
                    } ?: 0

                if (rotation != 0) {

                    val matrix = Matrix().apply {
                        postRotate(rotation.toFloat())
                    }

                    return Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        bitmap.width,
                        bitmap.height,
                        matrix,
                        true
                    ).also {
                        if (it !== bitmap) {
                            bitmap.recycle()
                        }
                    }
                }

            } catch (e: Exception) {

                android.util.Log.e(
                    "BackgroundSlider",
                    "EXIF 회전 보정 실패",
                    e
                )
            }
        }

        return bitmap
    }

    // ---------------------------------------------------------
    // 다음 이미지 표시
    // ---------------------------------------------------------

    fun nextImage() {

        if (!isRunning || isTransitioning) return

        // 다음 이미지 준비가 끝나지 않았다면 요청만 저장
        if (nextBitmap == null) {

            pendingAdvance = true

            if (!isLoadingNext) {
                preloadNextImage()
            }

            return
        }

        val newBitmap = nextBitmap ?: return

        isTransitioning = true

        val oldBitmap = currentBitmap

        imageViewNext.setImageDrawable(
            BitmapDrawable(
                context.resources,
                newBitmap
            )
        )

        imageViewNext.alpha = 0f
        imageViewNext.visibility = View.VISIBLE

        // 다음 이미지 페이드 인
        imageViewNext.animate()
            .alpha(1f)
            .setDuration(fadeDuration.toLong())
            .withEndAction {

                if (!isRunning) return@withEndAction

                // 현재 이미지 교체
                imageView.setImageDrawable(
                    BitmapDrawable(
                        context.resources,
                        newBitmap
                    )
                )

                imageView.alpha = 1f

                // 보조 ImageView가 이전 Bitmap을 붙잡지 않도록 해제
                imageViewNext.animate().cancel()
                imageViewNext.setImageDrawable(null)
                imageViewNext.alpha = 0f

                currentBitmap = newBitmap
                currentIndex = nextIndex

                nextBitmap = null
                nextIndex = -1

                isTransitioning = false

                // 이전 이미지 메모리 해제
                if (oldBitmap != null && oldBitmap !== newBitmap) {
                    oldBitmap.recycle()
                }

                // 다음 이미지 미리 준비
                preloadNextImage()
            }
            .start()
    }

    // ---------------------------------------------------------
    // 전환 간격 변경
    // ---------------------------------------------------------

    fun setChangeInterval(interval: Long) {

        changeInterval = interval.coerceAtLeast(1000L)

        if (isRunning) {

            handler.removeCallbacks(slideRunnable)

            handler.postDelayed(
                slideRunnable,
                changeInterval
            )
        }
    }

    // ---------------------------------------------------------
    // 슬라이드 종료 및 메모리 해제
    // ---------------------------------------------------------

    // ---------------------------------------------------------
// 슬라이드 종료 및 메모리 해제
// preserveCurrentImage = true이면 현재 배경 이미지는 유지
// ---------------------------------------------------------
    fun stop(preserveCurrentImage: Boolean = false) {

        isRunning = false
        pendingAdvance = false

        handler.removeCallbacks(slideRunnable)

        imageView.animate().cancel()
        imageViewNext.animate().cancel()

        // 다음 이미지 뷰는 항상 정리
        imageViewNext.setImageDrawable(null)
        imageViewNext.alpha = 0f

        // 다음 이미지 메모리 해제
        nextBitmap?.let {
            if (!it.isRecycled) it.recycle()
        }

        nextBitmap = null
        nextIndex = -1

        // 현재 이미지 유지 여부
        if (!preserveCurrentImage) {

            imageView.setImageDrawable(null)

            currentBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
        }

        // 현재 이미지를 유지할 때는 ImageView가 Bitmap을 계속 참조
        currentBitmap = null
        currentIndex = -1

        isTransitioning = false
        isLoadingNext = false

        executor.shutdownNow()
    }
}

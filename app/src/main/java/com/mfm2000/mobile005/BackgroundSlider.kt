package com.mfm2000.mobile005

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import java.util.ArrayDeque
import kotlin.random.Random

class BackgroundSlider(
    private val images: Array<Drawable>,       // 배경화면으로 사용할 이미지 목록
    private val firstImageView: ImageView,     // 첫 번째 배경 이미지 뷰
    private val secondImageView: ImageView,    // 두 번째 배경 이미지 뷰
    changeInterval: Long = 10_000L,            // 자동 이미지 변경 간격 (기본 10초)
    private val fadeDuration: Int = 3_000     // 이미지 페이드 전환 시간 (기본 3초)
) {

    // 메인 UI 스레드에서 실행할 작업을 관리하는 Handler
    private val handler = Handler(Looper.getMainLooper())

    // 자동 이미지 변경 간격
    private var changeInterval = changeInterval

    // 현재 화면에 표시 중이거나 표시 예정인 이미지의 인덱스
    private var currentIndex = 0

    // 슬라이더 실행 상태
    private var isRunning = false

    // 이미지 전환 애니메이션 진행 여부
    private var isTransitioning = false

    // 현재 앞쪽에서 이미지를 표시하는 ImageView
    private var frontView = firstImageView

    // 다음 이미지를 준비하는 뒤쪽 ImageView
    private var backView = secondImageView

    // 애니메이션 진행 중 추가로 요청된 이미지 인덱스를 저장하는 대기열
    private val pendingIndices = ArrayDeque<Int>()

    // 객체 생성 시 최초 표시할 이미지를 무작위로 선택
    init {
        if (images.isNotEmpty()) {
            currentIndex = Random.nextInt(images.size)
        }
    }

    // 지정된 시간마다 자동으로 다음 이미지를 변경하는 작업
    private val runnable = object : Runnable {
        override fun run() {

            // 슬라이더가 정지했거나 이미지가 2장 미만이면 종료
            if (!isRunning || images.size < 2) return

            // 다음 이미지를 무작위로 표시
            showNextImage()

            // 설정된 시간 후 다시 자동 실행
            handler.postDelayed(this, changeInterval)
        }
    }

    // 현재 이미지와 다른 다음 이미지의 인덱스를 무작위로 선택
    private fun getNextIndex(): Int {

        // 이미지가 2장 미만이면 현재 인덱스 유지
        if (images.size < 2) return currentIndex

        var nextIndex: Int

        // 현재 이미지와 동일한 이미지가 선택되지 않도록 반복
        do {
            nextIndex = Random.nextInt(images.size)
        } while (nextIndex == currentIndex)

        return nextIndex
    }

    // Drawable을 복제하여 ImageView 간 이미지 공유 문제 방지
    private fun copyDrawable(drawable: Drawable): Drawable {
        return drawable.constantState
            ?.newDrawable(frontView.resources)
            ?.mutate()
            ?: drawable
    }

    // 지정된 인덱스의 이미지로 화면 전환
    private fun changeImage(nextIndex: Int) {

        // 이미지가 2장 미만이면 전환하지 않음
        if (images.size < 2) return

        // 이전 이미지의 페이드 애니메이션이 끝나지 않았다면
        // 새로운 전환 요청을 대기열에 저장
        if (isTransitioning) {
            pendingIndices.addLast(nextIndex)
            currentIndex = nextIndex
            return
        }

        // 이미지 전환 시작 상태로 변경
        isTransitioning = true

        // 현재 이미지 인덱스 갱신
        currentIndex = nextIndex

        // 기존에 화면에 표시 중인 ImageView
        val oldFront = frontView

        // 새로운 이미지를 표시할 ImageView
        val newFront = backView

        // 혹시 남아 있는 애니메이션이 있다면 취소
        newFront.animate().cancel()
        oldFront.animate().cancel()

        // 뒤쪽 ImageView에 다음 이미지 설정
        newFront.setImageDrawable(copyDrawable(images[nextIndex]))

        // 새로운 이미지는 투명한 상태에서 시작
        newFront.alpha = 0f

        // 새로운 ImageView를 화면에 표시
        newFront.visibility = View.VISIBLE

        // 기존 이미지는 완전히 보이는 상태로 설정
        oldFront.alpha = 1f
        oldFront.visibility = View.VISIBLE

        // 새로운 이미지의 투명도를 0에서 1로 변경
        // 즉, 새로운 이미지가 서서히 나타나는 효과
        newFront.animate()
            .alpha(1f)
            .setDuration(fadeDuration.toLong())
            .start()

        // 기존 이미지의 투명도를 1에서 0으로 변경
        // 즉, 기존 이미지가 서서히 사라지는 효과
        oldFront.animate()
            .alpha(0f)
            .setDuration(fadeDuration.toLong())
            .withEndAction {

                // 기존 이미지 뷰를 화면에서 숨김
                oldFront.visibility = View.GONE

                // 다음 사용을 위해 투명도 초기화
                oldFront.alpha = 1f

                // 앞쪽과 뒤쪽 ImageView의 역할 교체
                // 다음 전환에서는 현재 뒤쪽 ImageView가 앞쪽이 됨
                frontView = newFront
                backView = oldFront

                // 현재 이미지 전환 완료
                isTransitioning = false

                // 전환 대기 중인 요청이 있다면
                // 가장 먼저 입력된 이미지부터 순서대로 처리
                if (pendingIndices.isNotEmpty()) {

                    // 대기열에서 다음 이미지 인덱스 가져오기
                    val nextPending = pendingIndices.removeFirst()

                    // 다음 이미지 전환 실행
                    changeImage(nextPending)
                }
            }
            .start()
    }

    // 자동 이미지 전환 기능
    private fun showNextImage() {

        // 이미지가 2장 미만이면 실행하지 않음
        if (images.size < 2) return

        // 현재 이미지와 다른 이미지를 무작위로 선택하여 전환
        changeImage(getNextIndex())
    }

    // 사용자의 스와이프 동작으로 다음 이미지 표시
    fun nextImage() {

        // 이미지가 2장 미만이면 실행하지 않음
        if (images.size < 2) return

        // 현재 이미지의 다음 인덱스 계산
        // 마지막 이미지 다음에는 첫 번째 이미지로 순환
        val nextIndex = (currentIndex + 1) % images.size

        // 다음 이미지로 전환
        // 애니메이션 중이라면 대기열에 저장됨
        changeImage(nextIndex)

        // 수동 전환이 발생하면 자동 전환 시간 초기화
        if (isRunning) {
            handler.removeCallbacks(runnable)
            handler.postDelayed(runnable, changeInterval)
        }
    }

    // 자동 이미지 변경 간격 설정
    fun setChangeInterval(interval: Long) {

        // 최소 변경 간격을 1초로 제한
        changeInterval = interval.coerceAtLeast(1_000L)

        // 슬라이더 실행 중이면 기존 예약 작업을 취소하고
        // 변경된 시간으로 자동 전환 작업 다시 예약
        if (isRunning) {
            handler.removeCallbacks(runnable)
            handler.postDelayed(runnable, changeInterval)
        }
    }

    // 현재 설정된 자동 이미지 변경 간격 반환
    fun getChangeInterval(): Long = changeInterval

    // 이미지 슬라이더 시작
    fun start() {

        // 이미 실행 중이거나 이미지가 없으면 시작하지 않음
        if (isRunning || images.isEmpty()) return

        // 실행 상태로 변경
        isRunning = true

        // 전환 상태 초기화
        isTransitioning = false

        // 이전에 저장된 전환 요청 제거
        pendingIndices.clear()

        // 첫 번째와 두 번째 ImageView 역할 초기화
        frontView = firstImageView
        backView = secondImageView

        // 기존에 실행 중인 애니메이션 제거
        firstImageView.animate().cancel()
        secondImageView.animate().cancel()

        // 최초 선택된 이미지를 첫 번째 ImageView에 표시
        firstImageView.setImageDrawable(
            copyDrawable(images[currentIndex])
        )

        // 첫 번째 이미지는 완전히 표시
        firstImageView.alpha = 1f
        firstImageView.visibility = View.VISIBLE

        // 두 번째 이미지는 숨김 상태로 초기화
        secondImageView.alpha = 0f
        secondImageView.visibility = View.GONE

        // 이미지가 2장 이상이면 자동 전환 시작
        if (images.size >= 2) {
            handler.removeCallbacks(runnable)
            handler.postDelayed(runnable, changeInterval)
        }
    }

    // 이미지 슬라이더 정지 및 상태 초기화
    fun stop() {

        // 실행 상태 해제
        isRunning = false

        // 이미지 전환 상태 해제
        isTransitioning = false

        // 예약된 자동 전환 작업 제거
        handler.removeCallbacks(runnable)

        // 대기 중인 수동 전환 요청 제거
        pendingIndices.clear()

        // 두 ImageView의 애니메이션 취소
        firstImageView.animate().cancel()
        secondImageView.animate().cancel()

        // 첫 번째 ImageView는 표시 상태로 복원
        firstImageView.alpha = 1f
        firstImageView.visibility = View.VISIBLE

        // 두 번째 ImageView는 숨김 상태로 복원
        secondImageView.alpha = 0f
        secondImageView.visibility = View.GONE

        // 앞쪽과 뒤쪽 ImageView 역할 초기화
        frontView = firstImageView
        backView = secondImageView
    }
}

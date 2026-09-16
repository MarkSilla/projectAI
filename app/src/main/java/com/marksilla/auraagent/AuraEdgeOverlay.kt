package com.marksilla.auraagent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.view.animation.AccelerateDecelerateInterpolator

class AuraEdgeOverlay(
    private val context: Context
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: AuraOverlayFrame? = null

    fun show(
        message: String = "AURA is listening..."
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            return false
        }

        overlayView?.let {
            it.setMessage(message)
            return true
        }

        val view = AuraOverlayFrame(context).apply {
            setMessage(message)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            setTitle("AURA listening overlay")
        }

        return try {
            windowManager.addView(view, params)
            view.start()
            overlayView = view
            true
        } catch (e: RuntimeException) {
            view.stop()
            false
        }
    }

    fun setMessage(message: String) {
        overlayView?.setMessage(message)
    }

    fun hide() {
        val view = overlayView ?: return
        overlayView = null
        view.stop()

        try {
            windowManager.removeView(view)
        } catch (e: RuntimeException) {
            // The system may already have detached the overlay during shutdown.
        }
    }
}

private class AuraOverlayFrame(
    context: Context
) : FrameLayout(context) {
    private val glowView = AuraEdgeGlowView(context)
    private val messageView =
        TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            includeFontPadding = false
            letterSpacing = 0.08f
            setShadowLayer(24f, 0f, 0f, Color.rgb(0, 229, 255))
        }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        isFocusable = false

        addView(
            glowView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        addView(
            messageView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    fun start() {
        glowView.start()
    }

    fun stop() {
        glowView.stop()
    }

    fun setMessage(message: String) {
        messageView.text = message
    }
}

private class AuraEdgeGlowView(
    context: Context
) : View(context) {
    private val bounds = RectF()
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    private var pulse = 0.35f
    private var animator: ValueAnimator? = null

    fun start() {
        if (animator?.isRunning == true) {
            return
        }

        animator =
            ValueAnimator.ofFloat(0.35f, 1f).apply {
                duration = 1150L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    pulse = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawGlowStroke(
            canvas = canvas,
            inset = 15f,
            strokeWidth = 20f + pulse * 12f,
            alpha = (40 + pulse * 80).toInt(),
            color = Color.rgb(139, 92, 246)
        )

        drawGlowStroke(
            canvas = canvas,
            inset = 18f,
            strokeWidth = 12f + pulse * 8f,
            alpha = (70 + pulse * 110).toInt(),
            color = Color.rgb(0, 229, 255)
        )

        drawGlowStroke(
            canvas = canvas,
            inset = 23f,
            strokeWidth = 3.5f + pulse * 2.5f,
            alpha = (160 + pulse * 80).toInt(),
            color = Color.rgb(88, 166, 255)
        )
    }

    private fun drawGlowStroke(
        canvas: Canvas,
        inset: Float,
        strokeWidth: Float,
        alpha: Int,
        color: Int
    ) {
        bounds.set(
            inset,
            inset,
            width - inset,
            height - inset
        )

        paint.color = color
        paint.alpha = alpha.coerceIn(0, 255)
        paint.strokeWidth = strokeWidth

        canvas.drawRoundRect(
            bounds,
            44f,
            44f,
            paint
        )
    }
}

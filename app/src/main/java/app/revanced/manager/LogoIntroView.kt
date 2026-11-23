package app.revanced.manager

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/**
 * ~3 saniyelik açılış animasyonu: halka kendini çizer, ses dalgası yaylanarak oluşur.
 * Tamamen vektör (Canvas/Path) çizim: PNG gerekmez.
 */
class LogoIntroView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    var onFinished: (() -> Unit)? = null
    var durationMs = 3200L

    private val c = 512f
    private val ringGrad = LinearGradient(
        0f,
        0f,
        1024f,
        1024f,
        intArrayOf(0xFFF4F8FC.toInt(), 0xFFB4C6D8.toInt(), 0xFF7D92A8.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val waveGrad = LinearGradient(
        0f,
        330f,
        0f,
        650f,
        intArrayOf(0xFFF7FAFD.toInt(), 0xFFC3D1DF.toInt(), 0xFF8DA1B6.toInt()),
        floatArrayOf(0f, 0.6f, 1f),
        Shader.TileMode.CLAMP,
    )

    private fun stroke(w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = w
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val ringPaint = stroke(58f).apply {
        shader = ringGrad
        setShadowLayer(14f, 0f, 6f, 0x4750647A)
    }
    private val wavePaint = stroke(48f).apply {
        shader = waveGrad
        setShadowLayer(14f, 0f, 6f, 0x4750647A)
    }
    private val hi1 = stroke(9f).apply { color = 0xBFFFFFFF.toInt() }
    private val hi2 = stroke(9f).apply { color = 0x80FFFFFF.toInt() }
    private val waveHi = stroke(10f).apply { color = 0xB3FFFFFF.toInt() }

    private val arc1 = arc(300f, 170f, 338f)
    private val arc2 = arc(300f, 352f, 165f)
    private val arcH1 = arc(280f, 176f, 330f)
    private val arcH2 = arc(280f, 358f, 160f)

    private var t = 0f
    private val seg = Path()
    private val pm = PathMeasure()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedFraction * durationMs / 1000f
            invalidate()
        }
        addListener(
            object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onFinished?.invoke()
                }
            },
        )
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun start() {
        animator.duration = durationMs
        animator.cancel()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun arc(radius: Float, startAngle: Float, endAngle: Float) = Path().apply {
        val sweep = (endAngle - startAngle + 360f) % 360f
        arcTo(RectF(c - radius, c - radius, c + radius, c + radius), startAngle, sweep, true)
    }

    private fun ease(value: Float): Float {
        val v = value.coerceIn(0f, 1f)
        return 1f - (1f - v).pow(3)
    }

    private fun spring(time: Float, delay: Float): Float {
        val s = time - delay
        return if (s <= 0f) 0f else 1f - exp(-4.2f * s) * cos(11f * s)
    }

    private fun partial(source: Path, progress: Float): Path {
        pm.setPath(source, false)
        seg.rewind()
        if (progress > 0.001f) {
            pm.getSegment(0f, pm.length * progress, seg, true)
        }
        return seg
    }

    private fun wave(k1: Float, k2: Float, k3: Float): Path {
        fun y(v: Float, k: Float) = 540f + (v - 540f) * k
        return Path().apply {
            moveTo(230f, 572f)
            cubicTo(290f, 572f, 300f, y(478f, k1), 355f, y(478f, k1))
            cubicTo(410f, y(478f, k1), 395f, y(625f, k1), 440f, y(625f, k1))
            cubicTo(490f, y(625f, k1), 470f, y(360f, k2), 515f, y(360f, k2))
            cubicTo(560f, y(360f, k2), 540f, y(600f, k2), 590f, y(600f, k2))
            cubicTo(640f, y(600f, k3), 640f, y(425f, k3), 710f, y(410f, k3))
            cubicTo(750f, y(400f, k3), 775f, 400f, 790f, 400f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val scale = min(width, height) / 1024f
        canvas.translate((width - 1024f * scale) / 2f, (height - 1024f * scale) / 2f)
        canvas.scale(scale, scale)

        val ring = ease(t / 0.9f)
        val waveProgress = ease((t - 0.5f) / 0.8f)

        canvas.save()
        canvas.rotate(-120f * (1f - ring), c, c)
        canvas.drawPath(partial(arc1, ease(t / 0.9f)), ringPaint)
        canvas.drawPath(partial(arc2, ease((t - 0.1f) / 0.9f)), ringPaint)
        canvas.drawPath(partial(arcH1, ease(t / 0.9f)), hi1)
        canvas.drawPath(partial(arcH2, ease((t - 0.1f) / 0.9f)), hi2)
        canvas.restore()

        val w = wave(spring(t, 0.7f), spring(t, 0.8f), spring(t, 0.9f))
        canvas.drawPath(partial(w, waveProgress), wavePaint)
        canvas.save()
        canvas.translate(0f, -9f)
        canvas.drawPath(partial(w, waveProgress), waveHi)
        canvas.restore()
    }
}

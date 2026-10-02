package com.whocalled.android.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.whocalled.android.R
import com.whocalled.android.data.ReportCategory
import com.whocalled.android.util.OverlayPermission

/**
 * Caller-ID overlay for WARN calls. [android.telecom.CallScreeningService] cannot
 * label the default dialer's incoming-call screen, so Android's documented path
 * is to show our own UI from onScreenCall.
 *
 * Held on the application context so the window survives the screening service
 * unbind. Classic views (not Compose) so WindowManager wrap_content measures
 * correctly. Non-focusable / non-modal: Answer / Reject stay tappable underneath.
 *
 * Only one banner at a time: a new WARN replaces the previous (last number
 * shown). Auto-dismiss after [DISPLAY_MS] — typical ring window — because the
 * screening service is unbound after respondToCall and we do not hold
 * READ_PHONE_STATE to watch hang-up.
 */
object WarnOverlay {
    private const val TAG = "WarnOverlay"
    const val DISPLAY_MS = 45_000L

    private const val PANEL = 0xFF1A140C.toInt()
    private const val INK_SOFT = 0xFFFDE68A.toInt()
    private const val AMBER = 0xFFF59E0B.toInt()

    private val main = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }

    private var view: View? = null

    /** @return true if the banner is on screen (caller must skip the heads-up). */
    fun show(context: Context, phone: String, score: Int, category: String?): Boolean {
        if (!OverlayPermission.isGranted(context)) return false
        val app = context.applicationContext
        val categoryLabel = ReportCategory.fromApi(category)
            ?.takeIf { it != ReportCategory.OTHER }?.label
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return attach(app, phone, score, categoryLabel)
        }
        main.post { attach(app, phone, score, categoryLabel) }
        return true
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { hide() }
            return
        }
        detach()
    }

    private fun attach(app: Context, phone: String, score: Int, categoryLabel: String?): Boolean {
        detach()
        val wm = app.getSystemService(WindowManager::class.java) ?: return false
        val banner = buildBanner(app, phone, score, categoryLabel)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            title = "Who Called warn"
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val added = runCatching { wm.addView(banner, params) }
        if (added.isFailure) {
            Log.w(TAG, "Could not add overlay", added.exceptionOrNull())
            return false
        }
        view = banner
        main.removeCallbacks(hideRunnable)
        main.postDelayed(hideRunnable, DISPLAY_MS)
        return true
    }

    private fun detach() {
        main.removeCallbacks(hideRunnable)
        val current = view
        view = null
        if (current != null) {
            val wm = current.context.getSystemService(WindowManager::class.java)
            runCatching { wm?.removeViewImmediate(current) }
        }
    }

    private fun buildBanner(
        context: Context,
        phone: String,
        score: Int,
        categoryLabel: String?,
    ): View {
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val amber = AMBER
        val white = 0xFFFFFFFF.toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 18 * d
                setColor(PANEL)
            }
            setPadding(0, dp(10), dp(6), dp(10))
            elevation = 8 * d
        }

        val rail = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(5), dp(56)).apply {
                marginEnd = dp(10)
            }
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(0f, 0f, 4 * d, 4 * d, 4 * d, 4 * d, 0f, 0f)
                setColor(amber)
            }
        }
        card.addView(rail)

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_notification_shield)
            imageTintList = android.content.res.ColorStateList.valueOf(amber)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                marginEnd = dp(8)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(icon)

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(
            TextView(context).apply {
                text = context.getString(R.string.overlay_title)
                setTextColor(white)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
        )
        val detail = buildString {
            append(phone)
            if (!categoryLabel.isNullOrBlank()) {
                append(" · ")
                append(categoryLabel)
            }
        }
        texts.addView(
            TextView(context).apply {
                text = detail
                setTextColor(INK_SOFT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
        )
        texts.addView(
            TextView(context).apply {
                text = context.getString(R.string.overlay_not_blocked)
                setTextColor(ColorUtils.setAlphaComponent(white, 184))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
        )
        card.addView(texts)

        card.addView(
            TextView(context).apply {
                text = "$score%"
                setTextColor(amber)
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                gravity = Gravity.CENTER
                setPadding(dp(6), 0, dp(2), 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )
        card.addView(
            TextView(context).apply {
                text = "✕"
                contentDescription = context.getString(R.string.overlay_dismiss)
                setTextColor(ColorUtils.setAlphaComponent(white, 180))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                isClickable = true
                isFocusable = false
                setOnClickListener { main.post { hide() } }
            },
        )

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), statusBarHeight(context) + dp(6), dp(10), 0)
            addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
}

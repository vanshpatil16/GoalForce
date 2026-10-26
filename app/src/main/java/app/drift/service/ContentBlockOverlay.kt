package app.drift.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A cover overlay drawn over the *video region* of a detected Reels/Shorts
 * viewer using `SYSTEM_ALERT_WINDOW`. Only the viewer is dimmed — the app's
 * bottom nav bar and top bar (e.g. Instagram's DM icon) stay visible and
 * tappable, so the user can navigate to DMs or other tabs without leaving the
 * app.
 */
class ContentBlockOverlay {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    fun isShowing(): Boolean = overlayView != null

    /**
     * @param target the screen region to cover (the short-form viewer); pass
     *   null to fall back to a full-screen cover.
     */
    fun show(context: Context, appName: String, target: Rect?, onGoBack: () -> Unit) {
        if (overlayView != null) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = buildView(context, appName, onGoBack)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = if (target != null && target.width() > 0 && target.height() > 0) {
            WindowManager.LayoutParams(
                target.width(),
                target.height(),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = target.left
                y = target.top
            }
        } else {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
        }

        runCatching { wm.addView(view, params) }
            .onSuccess {
                overlayView = view
                windowManager = wm
            }
    }

    fun hide() {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        runCatching { wm.removeView(view) }
        overlayView = null
        windowManager = null
    }

    private fun buildView(context: Context, appName: String, onGoBack: () -> Unit): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(205, 16, 16, 22))
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1F2430"))
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        val title = TextView(context).apply {
            text = "Reels blocked"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(context).apply {
            text = "Take a breath — the nav bar below still works, so you can head to DMs or another tab."
            setTextColor(Color.parseColor("#B8C0D0"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(14))
        }
        val back = Button(context).apply {
            text = "Go back"
            setOnClickListener { onGoBack() }
        }
        card.addView(title)
        card.addView(subtitle)
        card.addView(back)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )
        root.addView(card, lp)
        return root
    }

    private fun dp(value: Int): Int =
        (value * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

    companion object {
        /**
         * Shrinks the full viewer bounds so the top bar (e.g. DM icon) and bottom
         * nav bar remain uncovered and tappable. Clamps rather than blindly
         * insetting, so a viewer that already excludes the chrome is untouched.
         */
        fun videoRegion(bounds: Rect, screenWidth: Int, screenHeight: Int, density: Float): Rect {
            val topInset = (56 * density).toInt()
            val bottomInset = (64 * density).toInt()
            val left = bounds.left.coerceAtLeast(0)
            val top = bounds.top.coerceAtLeast(topInset)
            val right = bounds.right.coerceAtMost(screenWidth)
            val bottom = bounds.bottom.coerceAtMost(screenHeight - bottomInset)
            return if (bottom > top && right > left) Rect(left, top, right, bottom) else bounds
        }
    }
}

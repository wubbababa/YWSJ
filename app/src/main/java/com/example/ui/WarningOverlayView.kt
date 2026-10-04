package com.example.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.example.R

/**
 * Warning UI hosted in a WindowManager overlay window owned by the monitoring
 * service.
 *
 * [WarningActivity] can only be brought to the front when Android allows a
 * background activity start: on Android 10+ that requires the "display over
 * other apps" permission and several OEM ROMs still block it. A window of type
 * `TYPE_APPLICATION_OVERLAY` is not subject to those restrictions, so an
 * overlay is the reliable way to put the warning in front of whatever app the
 * user is looking at.
 */
class WarningOverlayView(context: Context) : LinearLayout(context) {

    private val actionView: TextView = TextView(context)

    /** Invoked when the user taps the dismiss action after the countdown. */
    var onDismiss: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        setBackgroundColor(BACKGROUND_COLOR)
        setPadding(dp(24), dp(24), dp(24), dp(24))

        addView(
            TextView(context).apply {
                text = "!"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ALERT_COLOR)
                    setStroke(dp(4), Color.WHITE)
                }
            },
            LinearLayout.LayoutParams(dp(140), dp(140))
        )

        addView(
            label("DETOX WARNING", 11f, ALERT_COLOR, bold = true),
            contentParams(topMargin = dp(36))
        )

        addView(
            label(context.getString(R.string.warning_main_header), 32f, TEXT_MAIN_COLOR, bold = true),
            contentParams(topMargin = dp(12))
        )

        addView(
            label(context.getString(R.string.warning_sub_header), 15f, TEXT_MUTED_COLOR, bold = false),
            contentParams(topMargin = dp(16))
        )

        actionView.apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setOnClickListener { onDismiss?.invoke() }
        }
        addView(actionView, contentParams(topMargin = dp(48), height = dp(56)))
        showCountdown(DEFAULT_ACTION_DELAY_SECONDS)
    }

    /** Shows the locked dismiss action while the forced review countdown runs. */
    fun showCountdown(seconds: Int) {
        actionView.text = context.getString(R.string.warning_exit_countdown, seconds)
        actionView.isEnabled = false
        actionView.setTextColor(TEXT_MUTED_COLOR)
        actionView.background = roundedBackground(DISABLED_COLOR, dp(24))
    }

    /** Unlocks the dismiss action once the countdown has elapsed. */
    fun showDismissAction() {
        actionView.text = context.getString(R.string.warning_exit_active)
        actionView.isEnabled = true
        actionView.setTextColor(Color.WHITE)
        actionView.background = roundedBackground(ACCENT_COLOR, dp(24))
    }

    private fun label(text: String, sizeSp: Float, color: Int, bold: Boolean): TextView =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) {
                typeface = Typeface.DEFAULT_BOLD
            }
            setLineSpacing(dp(6).toFloat(), 1f)
        }

    private fun contentParams(
        topMargin: Int,
        height: Int = LinearLayout.LayoutParams.WRAP_CONTENT
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        height
    ).apply { this.topMargin = topMargin }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val DEFAULT_ACTION_DELAY_SECONDS = 3

        val BACKGROUND_COLOR = 0xFFFDFCFB.toInt()
        val TEXT_MAIN_COLOR = 0xFF1C1B1F.toInt()
        val TEXT_MUTED_COLOR = 0xFF49454F.toInt()
        val ACCENT_COLOR = 0xFF6750A4.toInt()
        val ALERT_COLOR = 0xFFFF4C4C.toInt()
        val DISABLED_COLOR = 0xFFE0E2EC.toInt()
    }
}

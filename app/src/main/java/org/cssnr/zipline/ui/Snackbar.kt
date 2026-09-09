package org.cssnr.zipline.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.core.graphics.toColorInt
import com.google.android.material.snackbar.Snackbar
import org.cssnr.zipline.R

/**
 * Shows a Snackbar anchored to the activity-level CoordinatorLayout so it survives
 * fragment navigation (unlike a Toast, a Snackbar must be attached to a live view).
 *
 * When the bottom navigation is present the Snackbar is placed just above it.
 *
 * A Close action is always provided.
 *
 * When [error] is true the message is shown in red for a longer duration.
 */
fun Context.showSnackbar(
    message: CharSequence,
    error: Boolean = false,
) {
    val activity = findActivity() ?: return
    if (activity.isFinishing || activity.isDestroyed) return
    val coordinator =
        activity.findViewById<View>(R.id.coordinator_layout)
            ?: activity.findViewById(android.R.id.content)
            ?: return

    val length = if (error) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
    val snackbar = Snackbar.make(coordinator, message, length)
    if (error) snackbar.setTextColor("#D32F2F".toColorInt())
    snackbar.setAction("Close") {}
    val bottomNav = activity.findViewById<View>(R.id.bottom_nav)
    if (bottomNav != null) {
        snackbar.anchorView = bottomNav
        snackbar.isAnchorViewLayoutListenerEnabled = true
    }
    snackbar.show()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

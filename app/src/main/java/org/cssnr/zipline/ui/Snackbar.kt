package org.cssnr.zipline.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import com.google.android.material.snackbar.Snackbar
import org.cssnr.zipline.R

/**
 * Shows a Snackbar anchored to the activity-level CoordinatorLayout so it survives
 * fragment navigation (unlike a Toast, a Snackbar must be attached to a live view).
 *
 * When the bottom navigation is visible the Snackbar is placed just above it.
 */
fun Context.showSnackbar(
    message: CharSequence,
    length: Int = Snackbar.LENGTH_LONG,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    textColor: Int? = null,
) {
    val activity = findActivity() ?: return
    val coordinator =
        activity.findViewById<View>(R.id.coordinator_layout)
            ?: activity.findViewById(android.R.id.content)
            ?: return
    if (!coordinator.isAttachedToWindow) return
    val snackbar = Snackbar.make(coordinator, message, length)
    if (textColor != null) snackbar.setTextColor(textColor)
    if (actionText != null) snackbar.setAction(actionText) { onAction?.invoke() }
    activity.findViewById<View>(R.id.bottom_nav)
        ?.takeIf { it.isShown }
        ?.let { snackbar.setAnchorView(it) }
    snackbar.show()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

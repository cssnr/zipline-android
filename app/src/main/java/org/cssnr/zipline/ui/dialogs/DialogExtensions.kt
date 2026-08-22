package org.cssnr.zipline.ui.dialogs

import android.app.Dialog
import android.os.Build
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Shows the soft keyboard for this dialog window.
 *
 * Copied from androidx.preference PreferenceDialogFragmentCompat.requestInputMethod()
 * which is how EditTextPreference dialogs shows the keyboard when a dialog is shown.
 *
 * https://github.com/androidx/androidx/blob/androidx-main/preference/preference/src/main/java/androidx/preference/PreferenceDialogFragmentCompat.java
 */
fun Dialog.showKeyboard() {
    val window: Window = window ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Same as androidx.preference Api30Impl.showIme(window)
        window.decorView.windowInsetsController?.show(WindowInsets.Type.ime())
    } else {
        // Pre-R legacy fallback: request the keyboard when the window gains focus
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}

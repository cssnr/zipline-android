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
 *
 * AI NOTE: Call AFTER create() and BEFORE show() (like the library calls requestInputMethod
 * in onCreateDialog). The focused editor and window flags must be in place before the
 * dialog window gains focus or the keyboard will not show reliably.
 */
fun Dialog.showKeyboard() {
    val window: Window = window ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Same as androidx.preference Api30Impl.showIme(window)
        window.decorView.windowInsetsController?.show(WindowInsets.Type.ime())
    } else {
        // Fixing AI Changes
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        )
    }
}

package org.cssnr.zipline.ui.setup

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.cssnr.zipline.MainActivity
import org.cssnr.zipline.R

//import androidx.navigation.findNavController

fun Activity.showTapTargets(view: View) {
    Log.d("showTapTargets", "start")
    val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
    val navItemHome = bottomNav.getChildAt(0).findViewById<View>(R.id.nav_item_home)
    val navItemFiles = bottomNav.getChildAt(0).findViewById<View>(R.id.nav_item_files)
    // NOTE: I believe this icon has to be set because its in a highlighted state
    val icon = AppCompatResources.getDrawable(this, R.drawable.md_dashboard_24px)

    val target1 = TapTarget.forView(
        navItemHome,
        "Web View",
        "The Home Button takes you to the Full Website in the Application."
    )
        .titleTextSize(32)
        .descriptionTextSize(18)
        .textTypeface(Typeface.SANS_SERIF)
        .textColorInt(Color.WHITE)
        .dimColorInt(Color.TRANSPARENT)
        .outerCircleColor(R.color.tap_target_background)
        .outerCircleAlpha(0.96f)
        .icon(icon, true)
        .drawShadow(true)
        .transparentTarget(true)
        .targetRadius(56)

    val target2 = TapTarget.forView(
        navItemFiles,
        "File List",
        "The File List lets you View, Edit and Download your Files."
    )
        .titleTextSize(32)
        .descriptionTextSize(18)
        .textTypeface(Typeface.SANS_SERIF)
        .textColorInt(Color.WHITE)
        .dimColorInt(Color.TRANSPARENT)
        .outerCircleColor(R.color.tap_target_background)
        .outerCircleAlpha(0.96f)
        .drawShadow(true)
        .transparentTarget(true)
        .targetRadius(56)

    val displayMetrics = Resources.getSystem().displayMetrics
    val screenHeight = displayMetrics.heightPixels
    val centerY = screenHeight / 2

    val target3 = TapTarget.forBounds(
        Rect(0, centerY - 1, 1, centerY + 1),
        "Navigation Drawer",
        "Swipe from the left to access the Navigation Drawer."
    )
        .titleTextSize(32)
        .descriptionTextSize(18)
        .textTypeface(Typeface.SANS_SERIF)
        .textColorInt(Color.WHITE)
        .dimColorInt(Color.TRANSPARENT)
        .outerCircleColor(R.color.tap_target_background)
        .outerCircleAlpha(0.96f)
        .drawShadow(true)
        .transparentTarget(true)
        .targetRadius(56)


    //val allTargets = listOf<TapTarget>(target1, target2)

    val sequenceListener = object : TapTargetSequence.Listener {
        override fun onSequenceFinish() {
            Log.d("onSequenceFinish", "TapTargetSequence Done.")
        }

        override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {
            Log.d("onSequenceStep", "lastTarget: $lastTarget - clicked: $targetClicked")
            //if (lastTarget == target2 && targetClicked) {
            //    Log.i("onSequenceStep", "findNavController().navigate: nav_item_files")
            //    view.findNavController().navigate(R.id.nav_item_files)
            //}
            if (lastTarget == target3 && targetClicked) {
                Log.i("onSequenceStep", "MainActivity).toggleDrawer()")
                (this@showTapTargets as MainActivity).toggleDrawer()
            }
        }

        override fun onSequenceCanceled(lastTarget: TapTarget?) {
            Log.d("onSequenceCanceled", "lastTarget: $lastTarget")
        }
    }

    TapTargetSequence(this)
        .targets(target1, target2, target3)
        .listener(sequenceListener)
        .start()
}

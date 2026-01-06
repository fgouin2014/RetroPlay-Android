package com.retroplay

import android.app.Activity

/**
 * Show modern slot selection dialog from GameDetailsActivity
 */
fun showModernSlotDialogFromDetails(
    activity: Activity,
    console: String,
    gameName: String,
    romPath: String = ""
) {
    // Use AppCompatActivity to ensure Fragment support
    val fragmentActivity = activity as androidx.fragment.app.FragmentActivity
    
    // Show DialogFragment
    SlotSelectionDialogFragment.newInstance(console, gameName, romPath)
        .show(fragmentActivity.supportFragmentManager, "SlotSelectionDialog")
}



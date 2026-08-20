package com.rfabbrini.wristmarkread.gesture

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * The one-handed gesture framework (double pinch / wrist turn) arrived in Wear OS 7, API level 37,
 * and is initially available on Pixel Watch 3 and later.
 */
const val WEAR_OS_7_SDK_INT: Int = 37

/** What this device can do with [androidx.wear.compose.material3.onehandedgesture.oneHandedGesture]. */
enum class GestureSupport {
    /** Wear OS 7 or newer: the gesture handler can fire, provided hand gestures are enabled. */
    SUPPORTED,

    /** A watch, but older than Wear OS 7 - the gesture modifier is a no-op. */
    UNSUPPORTED_OS_VERSION,

    /** Not a watch at all (phone/emulator image); gestures never fire. */
    NOT_A_WATCH;

    val isSupported: Boolean get() = this == SUPPORTED
}

/**
 * Feature detection, so the UI can tell the user why nothing happens instead of silently failing.
 *
 * Note this is only a *capability* check. Whether the user has actually turned hand gestures on in
 * Settings is not readable by third-party apps, and the AndroidX gesture API safely no-ops when the
 * framework is unavailable, so no call site needs to branch on this.
 */
fun detectGestureSupport(context: Context): GestureSupport = when {
    !context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) -> GestureSupport.NOT_A_WATCH
    Build.VERSION.SDK_INT < WEAR_OS_7_SDK_INT -> GestureSupport.UNSUPPORTED_OS_VERSION
    else -> GestureSupport.SUPPORTED
}

package com.ltq.count.ui.quick

/** Bridge between the full screen counter and the activity, so volume keys are captured. */
object QuickKeyBus {
    @Volatile
    var adjust: ((Int) -> Unit)? = null
}

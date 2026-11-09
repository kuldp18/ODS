package org.ods.scanner

/** One photo per camera visit. Only a failed capture permits a retry. */
class CaptureLatch {
    private var claimed = false
    @Synchronized fun begin(): Boolean {
        if (claimed) return false
        claimed = true
        return true
    }
    @Synchronized fun failed() { claimed = false }
}

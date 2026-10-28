package com.greenaltambe.vela.debug

import android.os.StrictMode

/** Debug builds only: main-thread network or disk I/O and leaked closeables are logged (and kill the process in a self-test run). */
object DebugStrictMode {
    fun install(death: Boolean) {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().detectDiskReads().detectDiskWrites().penaltyLog()
            .apply { if (death) penaltyDeathOnNetwork() }.build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build())
    }
}

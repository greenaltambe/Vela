package com.greenaltambe.vela.debug

import android.app.Application

/** Debug builds only: StrictMode (log only) for the whole app; the self-test switches the death penalty on. */
class DebugApp : Application() {
    override fun onCreate() { super.onCreate(); DebugStrictMode.install(death = false) }
}

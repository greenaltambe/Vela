package com.greenaltambe.vela

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
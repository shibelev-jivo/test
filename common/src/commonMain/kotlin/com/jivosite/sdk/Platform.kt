package com.jivosite.sdk

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
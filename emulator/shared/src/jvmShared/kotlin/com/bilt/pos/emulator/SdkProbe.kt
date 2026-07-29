package com.bilt.pos.emulator

import com.bilt.pos.session.CheckoutSession

/**
 * Lives in the jvmShared source set (Android + desktop) where the Java SDK
 * is on the classpath, unlike commonMain. Proves the SDK linkage on both
 * targets; real session management replaces this in a follow-up.
 */
object SdkProbe {
    fun describe(): String =
        "SDK linked: ${CheckoutSession::class.java.name} (${CheckoutSession::class.java.protectionDomain?.codeSource?.location?.path?.substringAfterLast('/') ?: "embedded"})"
}

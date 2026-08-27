package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.TerminalAddressDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The address parse over `ip -f inet addr show` output: first global
 *  IPv4, any interface — loopback and link-local scopes skipped. */
class TerminalAddressDetectorTest {

    @Test
    fun picksTheGlobalAddressAcrossInterfaces() {
        val output = """
            1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536
                inet 127.0.0.1/8 scope host lo
            11: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500
                inet 10.0.20.7/24 brd 10.0.20.255 scope global eth0
            30: wlan0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500
                inet 192.168.1.57/24 brd 192.168.1.255 scope global wlan0
        """.trimIndent()
        assertEquals("10.0.20.7", TerminalAddressDetector.parseGlobalIpv4(output))
    }

    @Test
    fun noGlobalAddressMeansNull() {
        // a USB-only terminal: loopback only — the adb tunnel's case
        val output = """
            1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536
                inet 127.0.0.1/8 scope host lo
        """.trimIndent()
        assertNull(TerminalAddressDetector.parseGlobalIpv4(output))
        assertNull(TerminalAddressDetector.parseGlobalIpv4(""))
    }
}

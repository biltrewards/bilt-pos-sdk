package com.bilt.pos.emulator

import com.bilt.pos.emulator.session.AdbTunnel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The tunnel's pure logic: `adb devices` parsing, the device pick for an
 * address, and the forwarded-port parse. The subprocess plumbing itself
 * needs a live adb and stays untested.
 */
class AdbTunnelTest {

    @Test
    fun parseSerialsSkipsHeaderOfflineAndUnauthorized() {
        val output = """
            List of devices attached
            192.168.1.57:5555	device
            R58M123ABC	device
            emulator-5554	offline
            0A1B2C3D	unauthorized

        """.trimIndent()
        assertEquals(
            listOf("192.168.1.57:5555", "R58M123ABC"),
            AdbTunnel.parseSerials(output),
        )
    }

    @Test
    fun picksTheWifiAdbSerialCarryingTheAddress() {
        val serials = listOf("R58M123ABC", "192.168.1.57:5555")
        assertEquals("192.168.1.57:5555", AdbTunnel.pickSerial(serials, "192.168.1.57"))
    }

    @Test
    fun fallsBackToTheSingleDeviceWhenNoSerialMatches() {
        // USB-only terminal: opaque serial, never matching the wlan address
        assertEquals("R58M123ABC", AdbTunnel.pickSerial(listOf("R58M123ABC"), "192.168.1.57"))
    }

    @Test
    fun ambiguousWhenSeveralDevicesAndNoneMatches() {
        assertNull(AdbTunnel.pickSerial(listOf("R58M123ABC", "R58M456DEF"), "192.168.1.57"))
    }

    @Test
    fun noDevicesPicksNothing() {
        assertNull(AdbTunnel.pickSerial(emptyList(), "192.168.1.57"))
    }

    @Test
    fun parsesTheAllocatedPort() {
        assertEquals(45678, AdbTunnel.parseForwardedPort("45678\n"))
        // adb may echo daemon-start noise before the port line
        assertEquals(
            45678,
            AdbTunnel.parseForwardedPort("* daemon started successfully\n45678\n"),
        )
        assertNull(AdbTunnel.parseForwardedPort(""))
        assertNull(AdbTunnel.parseForwardedPort("error: device offline"))
    }
}

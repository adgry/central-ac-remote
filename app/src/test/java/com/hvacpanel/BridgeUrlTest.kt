package com.hvacpanel

import com.hvacpanel.transport.IrTransport.Companion.isBridgeStatus
import com.hvacpanel.transport.IrTransport.Companion.normalizeUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * People type an address, not a URL. Every shape below is one someone would
 * plausibly enter, and two of them used to fail: a bare IP threw
 * MalformedURLException, and a pasted endpoint path made the connection test GET
 * /send and collect a 405 from our own bridge.
 */
class BridgeUrlTest {

    @Test
    fun `a bare address gets a scheme`() {
        assertEquals("http://192.168.1.50", normalizeUrl("192.168.1.50"))
        assertEquals("http://192.168.1.50", normalizeUrl("  192.168.1.50  "))
    }

    @Test
    fun `an address that already has a scheme is left alone`() {
        assertEquals("http://192.168.1.50", normalizeUrl("http://192.168.1.50"))
        assertEquals("https://bridge.lan", normalizeUrl("https://bridge.lan"))
    }

    @Test
    fun `a trailing slash is dropped`() {
        assertEquals("http://192.168.1.50", normalizeUrl("http://192.168.1.50/"))
        assertEquals("http://192.168.1.50", normalizeUrl("192.168.1.50///"))
    }

    @Test
    fun `a pasted endpoint path is trimmed back to the base`() {
        assertEquals("http://192.168.1.50", normalizeUrl("http://192.168.1.50/send"))
        assertEquals("http://192.168.1.50", normalizeUrl("192.168.1.50/capture"))
        assertEquals("http://192.168.1.50", normalizeUrl("http://192.168.1.50/send/"))
    }

    @Test
    fun `a port survives`() {
        assertEquals("http://192.168.1.50:8080", normalizeUrl("192.168.1.50:8080"))
    }

    @Test
    fun `empty stays empty`() {
        assertEquals("", normalizeUrl(""))
        assertEquals("", normalizeUrl("   "))
    }
}

/**
 * The network sweep knocks on port 80 of every address on the subnet, so a
 * printer, a router admin page or a camera will all answer. Only our bridge
 * names itself.
 */
class BridgeIdentityTest {

    @Test
    fun `our bridge is recognised`() {
        assertTrue(
            isBridgeStatus("""{"ok":true,"device":"hvacpanel-ir-bridge","ip":"192.168.1.50"}"""),
        )
    }

    @Test
    fun `some other device on port 80 is not`() {
        assertFalse(isBridgeStatus("""{"device":"printer"}"""))
        assertFalse(isBridgeStatus("""{"ok":true}"""))
        assertFalse(isBridgeStatus("""{"device":"hvacpanel-ir-bridge-ish"}"""))
    }

    @Test
    fun `a page that is not json is not a bridge`() {
        assertFalse(isBridgeStatus("<html><title>Router</title></html>"))
        assertFalse(isBridgeStatus(""))
        assertFalse(isBridgeStatus("hvacpanel-ir-bridge"))
    }
}

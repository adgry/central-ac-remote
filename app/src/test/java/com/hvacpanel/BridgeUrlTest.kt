package com.hvacpanel

import com.hvacpanel.transport.IrTransport.Companion.normalizeUrl
import org.junit.Assert.assertEquals
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

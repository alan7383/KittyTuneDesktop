import com.alananasss.kittytune.data.sync.ConnectCloud
import com.alananasss.kittytune.data.sync.ConnectCredentials
import com.alananasss.kittytune.data.sync.ConnectErrors
import com.alananasss.kittytune.data.sync.ConnectTransportPolicy
import com.alananasss.kittytune.data.sync.WindowsFirewall
import com.alananasss.kittytune.data.together.TogetherRelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The connection rework: both devices dial on the LAN and agree on one link, failures say what they
 * mean, the firewall script touches only what it should, and Together reaches every broker by every route.
 */
class ConnectionRedesignTest {

    private val pc = "b-pc"
    private val phone = "a-phone" // the smaller id: its dial wins a tie

    @Test
    fun bothSidesKeepTheSameLanLinkWhenBothDialsSucceed() {
        // On the PC: the phone's dial is live, the PC's own dial lands second -> keep the phone's.
        assertTrue(ConnectTransportPolicy.keepExistingLan("LAN", phone, true, "LAN", pc, pc, phone))
        // On the phone: its own dial is live, the PC's dial arrives second -> keep its own (same socket).
        assertTrue(ConnectTransportPolicy.keepExistingLan("LAN", phone, true, "LAN", pc, phone, pc))
        // Reverse order: the PC's dial was live first, the phone's (preferred) one replaces it on both sides.
        assertFalse(ConnectTransportPolicy.keepExistingLan("LAN", pc, true, "LAN", phone, pc, phone))
        assertFalse(ConnectTransportPolicy.keepExistingLan("LAN", pc, true, "LAN", phone, phone, pc))
    }

    @Test
    fun aDeadOrDifferentLinkIsAlwaysReplaced() {
        assertFalse(ConnectTransportPolicy.keepExistingLan("LAN", phone, false, "LAN", pc, pc, phone))
        assertFalse(ConnectTransportPolicy.keepExistingLan("Internet", phone, true, "LAN", pc, pc, phone))
        assertFalse(ConnectTransportPolicy.keepExistingLan(null, null, false, "LAN", pc, pc, phone))
        // A reconnect from the same dialer replaces its own stale socket.
        assertFalse(ConnectTransportPolicy.keepExistingLan("LAN", phone, true, "LAN", phone, pc, phone))
    }

    @Test
    fun connectErrorsAreClassified() {
        val timeout = ConnectErrors.parse(ConnectErrors.code("LAN", "SocketTimeoutException"))
        assertEquals("LAN", timeout.transport)
        assertEquals(ConnectErrors.Kind.TIMEOUT, timeout.kind)
        assertEquals("SocketTimeoutException", timeout.raw)
        assertEquals(ConnectErrors.Kind.REFUSED, ConnectErrors.kindOf("ConnectException"))
        assertEquals(ConnectErrors.Kind.UNAUTHORIZED, ConnectErrors.kindOf("WebSocket 1008"))
        assertEquals(ConnectErrors.Kind.HTTP, ConnectErrors.kindOf("HTTP 502"))
        assertEquals(ConnectErrors.Kind.DNS, ConnectErrors.kindOf("UnknownHostException"))
        // An error saved by an older version (no route recorded) still reads.
        assertEquals(ConnectErrors.Kind.TIMEOUT, ConnectErrors.parse("SocketTimeoutException").kind)
    }

    @Test
    fun theFirewallScriptOpensOnlyTheLocalSubnetAndRemovesOnlyBlockRulesForThisApp() {
        val script = WindowsFirewall.script(listOf(47653, 47654, 47654), 47654, "C:\\Program Files\\KittyTune\\KittyTune.exe")
        assertTrue(script.contains("-Protocol TCP -LocalPort 47653,47654 -RemoteAddress LocalSubnet"))
        assertTrue(script.contains("-Protocol UDP -LocalPort 47654 -RemoteAddress LocalSubnet"))
        assertTrue(script.contains("Get-NetFirewallApplicationFilter -Program 'C:\\Program Files\\KittyTune\\KittyTune.exe'"))
        assertTrue(script.contains("\$_.Action -eq 'Block'"))
        assertTrue(script.contains("\$_.Direction -eq 'Inbound'"))
        // A quote in the path cannot break out of the string.
        val tricky = WindowsFirewall.script(listOf(1), 2, "C:\\it's\\app.exe")
        assertTrue(tricky.contains("'C:\\it''s\\app.exe'"))
        // No program known: no rule removal at all.
        assertFalse(WindowsFirewall.script(listOf(1), 2, null).contains("Remove-NetFirewallRule -Direction"))
        assertFalse(WindowsFirewall.script(listOf(1), 2, null).contains("Get-NetFirewallApplicationFilter"))
    }

    @Test
    fun togetherReachesEveryBrokerOverTlsAndWebSockets() {
        val brokers = TogetherRelay.BROKERS
        assertEquals(2, brokers.size)
        for (broker in brokers) {
            assertTrue(broker.routes.first().startsWith("ssl://"))
            assertTrue(broker.routes.any { it.startsWith("wss://") && it.endsWith("/mqtt") })
        }
        // The website's routes are among them, so browsers and apps share at least one broker.
        val routes = brokers.flatMap { it.routes }
        assertTrue("wss://broker.emqx.io:8084/mqtt" in routes)
        assertTrue("wss://broker.hivemq.com:8884/mqtt" in routes)
        assertEquals("broker.emqx.io over WebSocket (8084)", TogetherRelay.describe("wss://broker.emqx.io:8084/mqtt"))
    }

    @Test
    fun copiesOfOneMessageShareADigestAndOthersDoNot() {
        val a = byteArrayOf(1, 2, 3, 4)
        assertEquals(TogetherRelay.digest(a), TogetherRelay.digest(a.copyOf()))
        assertNotEquals(TogetherRelay.digest(a), TogetherRelay.digest(byteArrayOf(1, 2, 3, 5)))
    }

    @Test
    fun bothDevicesComputeTheSameCloudTopicsAndNoneNamesThem() {
        val pcSecret = "p".repeat(24); val phoneSecret = "q".repeat(24)
        val onPc = ConnectCredentials.derive(pc, pcSecret, phone, phoneSecret)
        val onPhone = ConnectCredentials.derive(phone, phoneSecret, pc, pcSecret)
        // The PC writes to the phone's inbox; the phone reads its own: same topic on both sides.
        assertEquals(ConnectCloud.inboxTopic(onPc.room, phone), ConnectCloud.inboxTopic(onPhone.room, phone))
        assertNotEquals(ConnectCloud.inboxTopic(onPc.room, phone), ConnectCloud.inboxTopic(onPc.room, pc))
        // Presence is computable only with the device's secret, and never contains the id or the secret.
        val presence = ConnectCloud.presenceTopic(phone, phoneSecret)
        assertNotEquals(presence, ConnectCloud.presenceTopic(phone, pcSecret))
        for (topic in listOf(presence, ConnectCloud.inboxTopic(onPc.room, phone))) {
            assertFalse(topic.contains(phone) || topic.contains(phoneSecret) || topic.contains(onPc.room))
        }
    }
}

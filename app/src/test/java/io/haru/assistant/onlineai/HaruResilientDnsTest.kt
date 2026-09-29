package io.haru.assistant.onlineai

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

class HaruResilientDnsTest {
    @Test
    fun systemDnsSuccessSkipsSecureDns() {
        val expected =
            listOf(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)))
        val secureCalls = AtomicInteger(0)

        val dns =
            HaruResilientDns(
                systemDns = Dns { expected },
                secureDns =
                    Dns {
                        secureCalls.incrementAndGet()
                        emptyList()
                    },
                retryDelayMs = 0L,
            )

        assertSame(expected, dns.lookup("example.com"))
        assertEquals(0, secureCalls.get())
    }

    @Test
    fun systemDnsRetriesThenUsesSecureDns() {
        val systemCalls = AtomicInteger(0)
        val secureCalls = AtomicInteger(0)
        val expected =
            listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)))

        val dns =
            HaruResilientDns(
                systemDns =
                    Dns {
                        systemCalls.incrementAndGet()
                        throw UnknownHostException("system down")
                    },
                secureDns =
                    Dns {
                        secureCalls.incrementAndGet()
                        expected
                    },
                retryDelayMs = 0L,
            )

        assertEquals(expected, dns.lookup("example.com"))
        assertEquals(2, systemCalls.get())
        assertEquals(1, secureCalls.get())
    }

    @Test(expected = UnknownHostException::class)
    fun secureDnsFailureIsPropagatedAfterSystemRetries() {
        val systemCalls = AtomicInteger(0)
        val secureCalls = AtomicInteger(0)

        val dns =
            HaruResilientDns(
                systemDns =
                    Dns {
                        systemCalls.incrementAndGet()
                        throw UnknownHostException("system down")
                    },
                secureDns =
                    Dns {
                        secureCalls.incrementAndGet()
                        throw UnknownHostException("secure down")
                    },
                retryDelayMs = 0L,
            )

        try {
            dns.lookup("example.com")
        } finally {
            assertEquals(2, systemCalls.get())
            assertEquals(1, secureCalls.get())
        }
    }
}

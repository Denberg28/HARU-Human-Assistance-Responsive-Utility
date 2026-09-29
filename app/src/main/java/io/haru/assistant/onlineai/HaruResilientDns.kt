package io.haru.assistant.onlineai

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException

internal class HaruResilientDns(
    private val systemDns: Dns,
    private val secureDns: Dns,
    private val retryDelayMs: Long = SYSTEM_DNS_RETRY_DELAY_MS,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        var firstFailure: UnknownHostException? = null

        repeat(SYSTEM_DNS_ATTEMPTS) { attempt ->
            try {
                return systemDns.lookup(hostname)
                    .takeIf { it.isNotEmpty() }
                    ?: throw UnknownHostException(
                        "System DNS returned no addresses for $hostname"
                    )
            } catch (exc: UnknownHostException) {
                if (firstFailure == null) {
                    firstFailure = exc
                }
                if (
                    attempt + 1 < SYSTEM_DNS_ATTEMPTS &&
                    retryDelayMs > 0L
                ) {
                    sleeper(retryDelayMs)
                }
            }
        }

        try {
            return secureDns.lookup(hostname)
                .takeIf { it.isNotEmpty() }
                ?: throw UnknownHostException(
                    "Secure DNS returned no addresses for $hostname"
                )
        } catch (exc: UnknownHostException) {
            firstFailure?.let(exc::addSuppressed)
            throw exc
        }
    }

    companion object {
        private const val SYSTEM_DNS_ATTEMPTS = 2
        private const val SYSTEM_DNS_RETRY_DELAY_MS = 250L

        fun create(): HaruResilientDns {
            val bootstrapClient =
                OkHttpClient.Builder()
                    .retryOnConnectionFailure(true)
                    .build()

            val secureDns =
                DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url("https://dns.google/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("8.8.4.4"),
                        InetAddress.getByName("2001:4860:4860::8888"),
                        InetAddress.getByName("2001:4860:4860::8844"),
                    )
                    .includeIPv6(true)
                    .post(true)
                    .build()

            return HaruResilientDns(
                systemDns = Dns.SYSTEM,
                secureDns = secureDns,
            )
        }
    }
}

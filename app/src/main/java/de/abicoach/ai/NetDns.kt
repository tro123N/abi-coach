package de.abicoach.ai

import android.content.Context
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Name resolution that survives a flaky or filtered DNS on the phone (weak network, a failing "Private DNS",
 * a VPN or ad blocker, a school Wi-Fi filter): first the phone's own DNS; if that fails, the addresses that worked
 * last time (kept across app starts); then DNS over HTTPS directly at Google and Cloudflare.
 */
class ResilientDns(context: Context) : Dns {
    private val prefs = context.getSharedPreferences("dns_cache", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, List<InetAddress>>()

    /** What went wrong with the phone's own DNS the last time (null = never). */
    @Volatile var lastProblem: String? = null
        private set

    /** How often a replacement (remembered addresses or DNS over HTTPS) was used. */
    @Volatile var fallbacks = 0
        private set

    private val overHttps: List<Dns> by lazy {
        val bootstrap = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
        listOf(
            DnsOverHttps.Builder().client(bootstrap).url("https://dns.google/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
                .build(),
            DnsOverHttps.Builder().client(bootstrap).url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
                .build(),
        )
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val system =
            try {
                // A blocking DNS (ad blocker, filter) often answers with 0.0.0.0 or a local address.
                Dns.SYSTEM.lookup(hostname).filter { !it.isAnyLocalAddress && !it.isLoopbackAddress }.also {
                    if (it.isEmpty()) lastProblem = "Die Adresse von $hostname wird blockiert (Werbeblocker, VPN oder WLAN-Filter?)"
                }
            } catch (e: UnknownHostException) {
                lastProblem = e.message ?: "Namensauflösung fehlgeschlagen"
                emptyList()
            }
        if (system.isNotEmpty()) {
            remember(hostname, system)
            return system
        }
        memory[hostname]?.let {
            fallbacks++
            return it
        }
        stored(hostname)?.let {
            fallbacks++
            memory[hostname] = it
            return it
        }
        for (resolver in overHttps) {
            val found = runCatching { resolver.lookup(hostname) }.getOrNull()?.filter { !it.isAnyLocalAddress && !it.isLoopbackAddress }
            if (!found.isNullOrEmpty()) {
                fallbacks++
                remember(hostname, found)
                return found
            }
        }
        throw UnknownHostException(
            "Keine Verbindung zu $hostname – das Handy findet den Server nicht. Prüfe WLAN oder mobile Daten, " +
                "„Privates DNS“ (auf „Automatisch“), VPN oder Werbeblocker.",
        )
    }

    private fun remember(hostname: String, addresses: List<InetAddress>) {
        memory[hostname] = addresses
        val text = addresses.mapNotNull { it.hostAddress }.joinToString(",")
        if (text.isNotBlank() && prefs.getString(hostname, null) != text) prefs.edit().putString(hostname, text).apply()
    }

    private fun stored(hostname: String): List<InetAddress>? =
        prefs.getString(hostname, null)?.split(",")?.filter { it.isNotBlank() }
            ?.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
}

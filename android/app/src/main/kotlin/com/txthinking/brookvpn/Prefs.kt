package com.txthinking.brookvpn

import android.content.Context
import android.content.SharedPreferences

/** Small persistence layer over SharedPreferences. */
object Prefs {
    private const val FILE = "brook"
    private const val KEY_LINK = "link"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_DNS = "dns"

    /** Foreign resolvers only; editable in-app, read when the VPN is built. */
    const val DEFAULT_DNS = "1.1.1.1, 8.8.8.8"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun link(context: Context): String =
        prefs(context).getString(KEY_LINK, "").orEmpty()

    fun saveLink(context: Context, link: String) =
        prefs(context).edit().putString(KEY_LINK, link).apply()

    /** Package names of apps allowed to use the VPN. */
    fun whitelist(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_WHITELIST, emptySet())?.toSet() ?: emptySet()

    fun saveWhitelist(context: Context, packages: Set<String>) =
        prefs(context).edit().putStringSet(KEY_WHITELIST, packages).apply()

    /** Raw DNS string as entered by the user. */
    fun dnsRaw(context: Context): String =
        prefs(context).getString(KEY_DNS, DEFAULT_DNS)?.takeIf { it.isNotBlank() } ?: DEFAULT_DNS

    fun saveDns(context: Context, raw: String) =
        prefs(context).edit().putString(KEY_DNS, raw.trim()).apply()

    /**
     * Parsed DNS server IPs for VpnService.Builder.addDnsServer. Invalid
     * entries are dropped; empty result means the caller should fall back
     * to DEFAULT_DNS's valid addresses.
     */
    fun dns(context: Context): List<String> =
        dnsRaw(context).split(',', ';', ' ').map { it.trim() }.filter { isValidIp(it) }.distinct()

    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    private fun isValidIp(s: String): Boolean {
        val m = ipv4.matchEntire(s) ?: return s.contains(':') && s.all {
            it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':'
        }
        return m.groupValues.drop(1).all { it.toInt() in 0..255 }
    }
}

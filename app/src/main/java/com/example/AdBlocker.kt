package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class AdBlocker(private val context: Context) {

    companion object {
        private const val TAG = "AdBlock"
        const val DEFAULT_REMOTE_BLOCKLIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        const val ADGUARD_DNS_FILTER_URL = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"
        private const val PREFS_NAME = "adblocker_prefs"
        private const val PREF_BLOCKED_COUNT = "pref_blocked_count"
        private const val PREF_LAST_UPDATE = "pref_last_update"
        private const val PREF_ENABLED = "pref_adblock_enabled"

        private const val USER_ALLOWLIST_FILE = "allowlist_user.txt"
        private const val USER_BLOCKLIST_FILE = "blocklist_user.txt"
        private const val ADGUARD_DNS_FILTER_FILE = "adguard_dns_filter.txt"

        private val ADGUARD_DNS_RULE_REGEX =
            Regex("^(?:(@@))?\\|\\|([A-Za-z0-9.-]+)\\^(?:\\$(badfilter))?\\|?$", RegexOption.IGNORE_CASE)

        private val EMPTY_RESPONSE = WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(ByteArray(0))
        )

        internal fun parseAdGuardDnsRule(rawLine: String): Pair<String, Boolean>? {
            val match = ADGUARD_DNS_RULE_REGEX.matchEntire(rawLine.trim()) ?: return null
            if (match.groupValues[3].equals("badfilter", ignoreCase = true)) return null

            val domain = match.groupValues[2].lowercase(Locale.ROOT)
            if (domain.startsWith(".") || domain.endsWith(".") ||
                domain.split(".").any { it.isEmpty() || it.startsWith("-") || it.endsWith("-") }) {
                return null
            }
            return domain to (match.groupValues[1].isNotEmpty())
        }
    }

    private val allowlist = Collections.synchronizedSet(HashSet<String>())
    private val blocklist = Collections.synchronizedSet(HashSet<String>())
    private val adGuardDnsBlocklist = Collections.synchronizedSet(HashSet<String>())
    private val adGuardDnsAllowlist = Collections.synchronizedSet(HashSet<String>())
    val blockedCount = AtomicInteger(0)

    var currentProfile: SiteProfile? = null
    var siteAddressManager: SiteAddressManager? = null

    var isEnabled: Boolean = true
        set(value) {
            field = value
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_ENABLED, value)
                .apply()
            Log.d(TAG, "AdBlocker enabled state set to: $value")
        }

    var baseCosmeticScript: String = ""
        private set

    init {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        blockedCount.set(prefs.getInt(PREF_BLOCKED_COUNT, 0))
        isEnabled = prefs.getBoolean(PREF_ENABLED, true)

        ensureFilesInitialized()
        loadAllowlist()
        loadBlocklist()
        loadAdGuardDnsFilter()
        loadCosmeticScript()
    }

    private fun ensureFilesInitialized() {
        val allowFile = File(context.filesDir, USER_ALLOWLIST_FILE)
        if (!allowFile.exists()) {
            try {
                val assetContent = context.assets.open("allowlist_hosts.txt").bufferedReader().use { it.readText() }
                allowFile.writeText(assetContent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy default allowlist to filesDir", e)
            }
        }

        val blockFile = File(context.filesDir, USER_BLOCKLIST_FILE)
        if (!blockFile.exists()) {
            try {
                val assetContent = context.assets.open("blocklist_hosts.txt").bufferedReader().use { it.readText() }
                blockFile.writeText(assetContent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy default blocklist to filesDir", e)
            }
        }
    }

    fun loadAllowlist() {
        try {
            val list = mutableSetOf<String>()
            // Always include baseline allowlist from assets
            try {
                context.assets.open("allowlist_hosts.txt").bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val clean = cleanDomainLine(line)
                        if (clean.isNotEmpty()) {
                            list.add(clean)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load base allowlist from assets", e)
            }

            // Merge with user-edited allowlist
            val file = File(context.filesDir, USER_ALLOWLIST_FILE)
            if (file.exists()) {
                file.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val clean = cleanDomainLine(line)
                        if (clean.isNotEmpty()) {
                            list.add(clean)
                        }
                    }
                }
            }
            allowlist.clear()
            allowlist.addAll(list)
            Log.d(TAG, "Loaded ${allowlist.size} total allowlist domains.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load allowlist", e)
        }
    }

    fun loadBlocklist() {
        try {
            val list = mutableSetOf<String>()
            val file = File(context.filesDir, USER_BLOCKLIST_FILE)
            if (file.exists()) {
                file.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val clean = cleanDomainLine(line)
                        if (clean.isNotEmpty()) {
                            list.add(clean)
                        }
                    }
                }
            }
            blocklist.clear()
            blocklist.addAll(list)
            Log.d(TAG, "Loaded ${blocklist.size} user blocklist domains.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load blocklist", e)
        }
    }

    private fun loadAdGuardDnsFilter() {
        try {
            val blocks = mutableSetOf<String>()
            val allows = mutableSetOf<String>()
            val file = File(context.filesDir, ADGUARD_DNS_FILTER_FILE)
            if (file.exists()) {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("@@")) {
                            allows.add(line.removePrefix("@@").lowercase(Locale.ROOT))
                        } else if (line.isNotBlank()) {
                            blocks.add(line.lowercase(Locale.ROOT))
                        }
                    }
                }
            }
            adGuardDnsBlocklist.clear()
            adGuardDnsBlocklist.addAll(blocks)
            adGuardDnsAllowlist.clear()
            adGuardDnsAllowlist.addAll(allows)
            Log.d(TAG, "Loaded ${blocks.size} AdGuard DNS blocks and ${allows.size} exceptions.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load AdGuard DNS filter", e)
        }
    }

    private fun findAdGuardDnsBlock(host: String): String? {
        val parts = host.lowercase(Locale.ROOT).split(".")
        val candidates = parts.indices.map { parts.subList(it, parts.size).joinToString(".") }
        if (candidates.any { adGuardDnsAllowlist.contains(it) }) return null
        return candidates.firstOrNull { adGuardDnsBlocklist.contains(it) }
    }

    private fun loadCosmeticScript() {
        try {
            baseCosmeticScript = context.assets.open("cosmetic_adblock.js")
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cosmetic script", e)
            baseCosmeticScript = "console.log('AdBlock cosmetic loaded');"
        }
    }

    fun getEffectiveCosmeticScript(): String {
        if (!isEnabled) return ""

        val extra = currentProfile?.extraCssHideSelectors
        return if (!extra.isNullOrBlank()) {
            // Belge başında document.head henüz yoksa DOMContentLoaded'a kadar bekler; seçici metni
            // JSON olarak güvenle kaçışlanır (tırnak/satır sonu içerse bile bozulmaz).
            val quoted = org.json.JSONObject.quote("$extra { display: none !important; }")
            "$baseCosmeticScript\n\n(function() { var css = $quoted; function add() { try { var s = document.createElement('style'); s.setAttribute('data-adblock-extra', 'true'); s.textContent = css; (document.head || document.documentElement).appendChild(s); } catch(e){} } if (document.head) add(); else document.addEventListener('DOMContentLoaded', add); })();"
        } else {
            baseCosmeticScript
        }
    }

    private fun cleanDomainLine(rawLine: String): String {
        var line = rawLine.trim()
        val hashIdx = line.indexOf('#')
        if (hashIdx != -1) {
            line = line.substring(0, hashIdx).trim()
        }
        if (line.isEmpty()) return ""

        val parts = line.split("\\s+".toRegex())
        val domain = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
            parts[1]
        } else {
            parts[0]
        }

        return domain.lowercase(Locale.ROOT)
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trim('/')
    }

    fun isAllowed(host: String?): Boolean {
        if (host.isNullOrBlank()) return true
        val lowerHost = host.lowercase(Locale.ROOT)

        // 1. Profile specific allowlist
        currentProfile?.let { profile ->
            for (pAllow in profile.allowHosts) {
                if (lowerHost == pAllow || lowerHost.endsWith(".$pAllow")) {
                    return true
                }
            }
        }

        // 2. Dynamic Same-Site check
        if (siteAddressManager?.isSameSite(lowerHost) == true) {
            return true
        }

        // 3. Global allowlist
        val parts = lowerHost.split(".")
        for (i in parts.indices) {
            val candidate = parts.subList(i, parts.size).joinToString(".")
            if (allowlist.contains(candidate)) {
                return true
            }
        }
        return false
    }

    fun isBlocked(host: String?): String? {
        if (!isEnabled) return null
        if (host.isNullOrBlank()) return null
        val lowerHost = host.lowercase(Locale.ROOT)

        // 1. Profile specific blocklist overrides
        currentProfile?.let { profile ->
            for (pBlock in profile.blockHosts) {
                if (lowerHost == pBlock || lowerHost.endsWith(".$pBlock")) {
                    return "ProfileBlock($pBlock)"
                }
            }
        }

        // 2. Allowlist checked before blocklist!
        if (isAllowed(lowerHost)) {
            return null
        }

        // AdGuard DNS exceptions apply only to AdGuard rules, not user/profile block rules.
        findAdGuardDnsBlock(lowerHost)?.let { return "AdGuardDNS($it)" }

        // Check blocklists including all parent domains (subdomains match)
        val parts = lowerHost.split(".")
        for (i in parts.indices) {
            val candidate = parts.subList(i, parts.size).joinToString(".")
            if (blocklist.contains(candidate)) {
                return candidate
            }
        }

        // Check for ad keyword indicators in host for pop-under / redirect domains
        if (lowerHost.contains("adsterra") || lowerHost.contains("popads") ||
            lowerHost.contains("propellerads") || lowerHost.contains("exoclick") ||
            lowerHost.contains("juicyads") || lowerHost.contains("doubleclick") ||
            lowerHost.contains("googleads") || lowerHost.contains("trafficjunky")) {
            return lowerHost
        }

        return null
    }

    fun shouldIntercept(uri: Uri, requestType: String = "other"): WebResourceResponse? {
        val host = uri.host ?: return null
        val urlString = uri.toString()

        if (!isEnabled) {
            NetworkLogger.logRequest(host, urlString, requestType, isBlocked = false, reason = "AdBlock Disabled")
            return null
        }

        if (isKnownAdPath(uri)) {
            return blockedResponse(host, urlString, requestType, "SiteAdPath(/allads/)")
        }

        if (isAllowed(host)) {
            NetworkLogger.logRequest(host, urlString, requestType, isBlocked = false, reason = "Allowlisted")
            return null
        }

        val matchedRule = isBlocked(host)
        if (matchedRule != null) {
            return blockedResponse(host, urlString, requestType, matchedRule)
        }

        NetworkLogger.logRequest(host, urlString, requestType, isBlocked = false, reason = "Passed")
        return null
    }

    private fun isKnownAdPath(uri: Uri): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val isMacellanHost = host == "macellan.online" || host.endsWith(".macellan.online")
        return isMacellanHost && uri.path.orEmpty().startsWith("/allads/", ignoreCase = true)
    }

    private fun blockedResponse(
        host: String,
        url: String,
        requestType: String,
        rule: String
    ): WebResourceResponse {
        val count = blockedCount.incrementAndGet()
        saveBlockedCount(count)
        NetworkLogger.logRequest(host, url, requestType, isBlocked = true, reason = rule)
        Log.d(
            TAG,
            "BLOCKED: host='$host' uri='${NetworkLogger.redactUrl(url)}' " +
                "[Rule: $rule, Total: $count]"
        )
        return EMPTY_RESPONSE
    }

    // 1-Click Rule Additions
    fun addAllowRule(domain: String): Boolean {
        val clean = cleanDomainLine(domain)
        if (clean.isBlank()) return false
        allowlist.add(clean)
        try {
            val file = File(context.filesDir, USER_ALLOWLIST_FILE)
            file.appendText("\n$clean\n")
            // Also remove from blocklist if present
            blocklist.remove(clean)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error appending to allowlist file", e)
            return false
        }
    }

    fun addBlockRule(domain: String): Boolean {
        val clean = cleanDomainLine(domain)
        if (clean.isBlank()) return false
        blocklist.add(clean)
        try {
            val file = File(context.filesDir, USER_BLOCKLIST_FILE)
            file.appendText("\n$clean\n")
            // Also remove from allowlist if present
            allowlist.remove(clean)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error appending to blocklist file", e)
            return false
        }
    }

    // Editable text operations
    fun getAllowlistText(): String {
        return try {
            File(context.filesDir, USER_ALLOWLIST_FILE).readText()
        } catch (e: Exception) {
            ""
        }
    }

    fun setAllowlistText(content: String) {
        try {
            File(context.filesDir, USER_ALLOWLIST_FILE).writeText(content)
            loadAllowlist()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing allowlist text", e)
        }
    }

    fun getBlocklistText(): String {
        return try {
            File(context.filesDir, USER_BLOCKLIST_FILE).readText()
        } catch (e: Exception) {
            ""
        }
    }

    fun setBlocklistText(content: String) {
        try {
            File(context.filesDir, USER_BLOCKLIST_FILE).writeText(content)
            loadBlocklist()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing blocklist text", e)
        }
    }

    fun resetToDefaultLists() {
        try {
            val allowContent = context.assets.open("allowlist_hosts.txt").bufferedReader().use { it.readText() }
            File(context.filesDir, USER_ALLOWLIST_FILE).writeText(allowContent)
            loadAllowlist()

            val blockContent = context.assets.open("blocklist_hosts.txt").bufferedReader().use { it.readText() }
            File(context.filesDir, USER_BLOCKLIST_FILE).writeText(blockContent)
            loadBlocklist()

            File(context.filesDir, ADGUARD_DNS_FILTER_FILE).delete()
            loadAdGuardDnsFilter()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting lists to default", e)
        }
    }

    private fun saveBlockedCount(count: Int) {
        if (count % 5 == 0) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_BLOCKED_COUNT, count)
                .apply()
        }
    }

    fun resetBlockedCount() {
        blockedCount.set(0)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_BLOCKED_COUNT, 0)
            .apply()
    }

    fun getLastUpdatedTime(): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_LAST_UPDATE, 0L)
    }

    fun getRulesCount(): Int {
        return blocklist.size + adGuardDnsBlocklist.size
    }

    fun getAllowlistCount(): Int {
        return allowlist.size
    }

    @WorkerThread
    suspend fun updateRemoteBlocklist(urlStr: String = DEFAULT_REMOTE_BLOCKLIST_URL): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating blocklist from $urlStr...")
            val downloaded = downloadHostBlocklist(urlStr)
            val (adGuardBlocks, adGuardAllows) = downloadAdGuardDnsFilter()

            if (downloaded.isNotEmpty() && adGuardBlocks.isNotEmpty()) {
                // Kullanıcının ve varsayılan kuralları koru: indirilen liste mevcutlara EKLENİR
                val merged = HashSet<String>(blocklist.size + downloaded.size)
                synchronized(blocklist) { merged.addAll(blocklist) }
                merged.addAll(downloaded)

                // Geçici dosyaya yaz, başarılıysa asıl dosyayla değiştir (atomik)
                val file = File(context.filesDir, USER_BLOCKLIST_FILE)
                val tmp = File(context.filesDir, "$USER_BLOCKLIST_FILE.tmp")
                tmp.bufferedWriter().use { writer ->
                    for (d in merged) {
                        writer.write(d)
                        writer.newLine()
                    }
                }
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }

                val adGuardFile = File(context.filesDir, ADGUARD_DNS_FILTER_FILE)
                val adGuardTmp = File(context.filesDir, "$ADGUARD_DNS_FILTER_FILE.tmp")
                adGuardTmp.bufferedWriter().use { writer ->
                    for (domain in adGuardBlocks) {
                        writer.write(domain)
                        writer.newLine()
                    }
                    for (domain in adGuardAllows) {
                        writer.write("@@$domain")
                        writer.newLine()
                    }
                }
                if (!adGuardTmp.renameTo(adGuardFile)) {
                    adGuardFile.writeText(adGuardTmp.readText())
                    adGuardTmp.delete()
                }

                blocklist.clear()
                blocklist.addAll(merged)
                adGuardDnsBlocklist.clear()
                adGuardDnsBlocklist.addAll(adGuardBlocks)
                adGuardDnsAllowlist.clear()
                adGuardDnsAllowlist.addAll(adGuardAllows)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(PREF_LAST_UPDATE, System.currentTimeMillis())
                    .apply()

                val totalRules = merged.size + adGuardBlocks.size
                Log.d(
                    TAG,
                    "Blocklists updated: ${downloaded.size} hosts, ${adGuardBlocks.size} AdGuard DNS rules, " +
                        "${adGuardAllows.size} exceptions, $totalRules total blocking rules."
                )
                return@withContext Result.success(totalRules)
            } else {
                return@withContext Result.failure(
                    Exception("A downloaded hosts list or AdGuard DNS filter was empty")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating blocklist", e)
            return@withContext Result.failure(e)
        }
    }

    private fun downloadHostBlocklist(urlStr: String): Set<String> {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) {
                throw Exception("HTTP Error from hosts list: ${connection.responseCode}")
            }

            val downloaded = mutableSetOf<String>()
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val clean = cleanDomainLine(line)
                    if (clean.isNotEmpty() && clean.contains('.') && clean != "0.0.0.0" &&
                        clean.any { it.isLetter() }) {
                        downloaded.add(clean)
                    }
                    line = reader.readLine()
                }
            }
            return downloaded
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAdGuardDnsFilter(): Pair<Set<String>, Set<String>> {
        val connection = URL(ADGUARD_DNS_FILTER_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        try {
            if (connection.responseCode !in 200..299) {
                throw Exception("HTTP Error from AdGuard DNS filter: ${connection.responseCode}")
            }

            val blocks = mutableSetOf<String>()
            val allows = mutableSetOf<String>()
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.forEachLine { line ->
                    val (domain, isException) = parseAdGuardDnsRule(line) ?: return@forEachLine
                    if (isException) allows.add(domain) else blocks.add(domain)
                }
            }
            if (blocks.isEmpty()) throw Exception("AdGuard DNS filter contained no supported domain rules")
            return blocks to allows
        } finally {
            connection.disconnect()
        }
    }
}

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
        private const val PREFS_NAME = "adblocker_prefs"
        private const val PREF_BLOCKED_COUNT = "pref_blocked_count"
        private const val PREF_LAST_UPDATE = "pref_last_update"
        private const val PREF_ENABLED = "pref_adblock_enabled"

        private const val USER_ALLOWLIST_FILE = "allowlist_user.txt"
        private const val USER_BLOCKLIST_FILE = "blocklist_user.txt"

        private val EMPTY_RESPONSE = WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private val allowlist = Collections.synchronizedSet(HashSet<String>())
    private val blocklist = Collections.synchronizedSet(HashSet<String>())
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
        val extra = currentProfile?.extraCssHideSelectors
        return if (!extra.isNullOrBlank()) {
            "$baseCosmeticScript\n\n(function() { try { var s = document.createElement('style'); s.innerHTML = '${extra.replace("'", "\\'")} { display: none !important; }'; document.head.appendChild(s); } catch(e){} })();"
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

        // 3. Check blocklist including all parent domains (subdomains match)
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

        if (isAllowed(host)) {
            NetworkLogger.logRequest(host, urlString, requestType, isBlocked = false, reason = "Allowlisted")
            return null
        }

        val matchedRule = isBlocked(host)
        if (matchedRule != null) {
            val count = blockedCount.incrementAndGet()
            saveBlockedCount(count)
            NetworkLogger.logRequest(host, urlString, requestType, isBlocked = true, reason = matchedRule)
            Log.d(TAG, "BLOCKED: host='$host' uri='$uri' [Rule: $matchedRule, Total: $count]")
            return EMPTY_RESPONSE
        }

        NetworkLogger.logRequest(host, urlString, requestType, isBlocked = false, reason = "Passed")
        return null
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
        return blocklist.size
    }

    fun getAllowlistCount(): Int {
        return allowlist.size
    }

    @WorkerThread
    suspend fun updateRemoteBlocklist(urlStr: String = DEFAULT_REMOTE_BLOCKLIST_URL): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating blocklist from $urlStr...")
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP Error: ${connection.responseCode}"))
            }

            val targetFile = File(context.filesDir, USER_BLOCKLIST_FILE)
            val tempFile = File(context.filesDir, "$USER_BLOCKLIST_FILE.tmp")

            // Keep existing custom rules and default asset rules
            val existingRules = mutableSetOf<String>()
            try {
                context.assets.open("blocklist_hosts.txt").bufferedReader().useLines { lines ->
                    for (l in lines) {
                        val c = cleanDomainLine(l)
                        if (c.isNotEmpty() && c.contains('.')) existingRules.add(c)
                    }
                }
            } catch (_: Exception) {}

            if (targetFile.exists()) {
                try {
                    targetFile.bufferedReader().useLines { lines ->
                        for (l in lines) {
                            val c = cleanDomainLine(l)
                            if (c.isNotEmpty() && c.contains('.')) existingRules.add(c)
                        }
                    }
                } catch (_: Exception) {}
            }

            val downloadedDomains = mutableSetOf<String>()
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                tempFile.bufferedWriter().use { writer ->
                    var line = reader.readLine()
                    while (line != null) {
                        val clean = cleanDomainLine(line)
                        if (clean.isNotEmpty() && clean.contains('.')) {
                            downloadedDomains.add(clean)
                            writer.write(clean)
                            writer.newLine()
                        }
                        line = reader.readLine()
                    }
                    // Append merged existing custom rules not present in downloaded list
                    for (custom in existingRules) {
                        if (!downloadedDomains.contains(custom)) {
                            downloadedDomains.add(custom)
                            writer.write(custom)
                            writer.newLine()
                        }
                    }
                }
            }

            if (downloadedDomains.isNotEmpty()) {
                // Atomic replace
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                blocklist.clear()
                blocklist.addAll(downloadedDomains)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(PREF_LAST_UPDATE, System.currentTimeMillis())
                    .apply()

                Log.d(TAG, "Blocklist successfully updated with ${downloadedDomains.size} rules.")
                return@withContext Result.success(downloadedDomains.size)
            } else {
                tempFile.delete()
                return@withContext Result.failure(Exception("Downloaded list was empty"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating blocklist", e)
            return@withContext Result.failure(e)
        }
    }
}

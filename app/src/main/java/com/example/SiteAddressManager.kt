package com.example

import android.content.Context
import android.net.Uri
import android.util.Patterns
import org.json.JSONArray
import java.util.Locale

enum class SameSiteMode {
    STRICT,
    FLEXIBLE
}

/** TV modu: AUTO = cihaz TV ise açık (leanback / televizyon arayüz modu / dokunmatik yok). */
enum class TvMode { AUTO, ON, OFF }

/** Kullanıcı ajanı: AUTO = TV'de masaüstü, telefonda mobil (site profili de dikkate alınır). */
enum class UaMode { AUTO, MOBILE, DESKTOP }

class SiteAddressManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "dizilla_address_prefs"
        private const val KEY_ACTIVE_URL = "active_site_url"
        private const val KEY_RECENT_URLS = "recent_site_urls"
        private const val KEY_BOOKMARKED_URLS = "bookmarked_site_urls"
        private const val KEY_SAME_SITE_MODE = "same_site_mode"
        private const val KEY_TV_MODE = "tv_mode"
        private const val KEY_UA_MODE = "ua_mode"
        const val MAX_RECENT_URLS = 10
        const val MAX_BOOKMARKED_URLS = 30
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var currentUrl: String?
        get() = prefs.getString(KEY_ACTIVE_URL, null)
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove(KEY_ACTIVE_URL).apply()
            } else {
                val normalized = normalizeUrl(value)
                prefs.edit().putString(KEY_ACTIVE_URL, normalized).apply()
                addRecentUrl(normalized)
            }
        }

    val currentHost: String?
        get() {
            val url = currentUrl ?: return null
            return try {
                Uri.parse(url).host?.lowercase(Locale.ROOT)
            } catch (e: Exception) {
                null
            }
        }

    var sameSiteMode: SameSiteMode
        get() {
            val modeStr = prefs.getString(KEY_SAME_SITE_MODE, SameSiteMode.STRICT.name)
            return try {
                SameSiteMode.valueOf(modeStr ?: SameSiteMode.STRICT.name)
            } catch (e: Exception) {
                SameSiteMode.STRICT
            }
        }
        set(value) {
            prefs.edit().putString(KEY_SAME_SITE_MODE, value.name).apply()
        }

    var tvMode: TvMode
        get() = try {
            TvMode.valueOf(prefs.getString(KEY_TV_MODE, TvMode.AUTO.name) ?: TvMode.AUTO.name)
        } catch (e: Exception) {
            TvMode.AUTO
        }
        set(value) {
            prefs.edit().putString(KEY_TV_MODE, value.name).apply()
        }

    var uaMode: UaMode
        get() = try {
            UaMode.valueOf(prefs.getString(KEY_UA_MODE, UaMode.AUTO.name) ?: UaMode.AUTO.name)
        } catch (e: Exception) {
            UaMode.AUTO
        }
        set(value) {
            prefs.edit().putString(KEY_UA_MODE, value.name).apply()
        }

    fun getRecentUrls(): List<String> {
        val jsonStr = prefs.getString(KEY_RECENT_URLS, null) ?: return emptyList()
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return list
    }

    fun addRecentUrl(url: String) {
        val normalized = normalizeUrl(url)
        val currentList = getRecentUrls().toMutableList()
        currentList.remove(normalized)
        currentList.add(0, normalized)
        val trimmed = if (currentList.size > MAX_RECENT_URLS) {
            currentList.subList(0, MAX_RECENT_URLS)
        } else {
            currentList
        }

        val arr = JSONArray()
        trimmed.forEach { arr.put(it) }
        prefs.edit().putString(KEY_RECENT_URLS, arr.toString()).apply()
    }

    fun clearRecentUrls() {
        prefs.edit().remove(KEY_RECENT_URLS).apply()
    }

    fun getBookmarkedUrls(): List<String> {
        val jsonStr = prefs.getString(KEY_BOOKMARKED_URLS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length())
                .map { normalizeUrl(arr.getString(it)) }
                .filter(::isValidUrl)
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addBookmark(url: String): Boolean {
        val normalized = normalizeUrl(url)
        if (!isValidUrl(normalized)) return false

        val bookmarks = getBookmarkedUrls().toMutableList()
        if (normalized in bookmarks) return true
        if (bookmarks.size >= MAX_BOOKMARKED_URLS) return false
        bookmarks.add(0, normalized)
        saveBookmarks(bookmarks)
        return true
    }

    fun removeBookmark(url: String): Boolean {
        val normalized = normalizeUrl(url)
        val bookmarks = getBookmarkedUrls().toMutableList()
        if (!bookmarks.remove(normalized)) return false
        saveBookmarks(bookmarks)
        return true
    }

    private fun saveBookmarks(bookmarks: List<String>) {
        val arr = JSONArray()
        bookmarks.forEach(arr::put)
        prefs.edit().putString(KEY_BOOKMARKED_URLS, arr.toString()).apply()
    }

    fun normalizeUrl(raw: String): String {
        var trimmed = raw.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }

    fun isValidUrl(url: String): Boolean {
        return try {
            val normalized = normalizeUrl(url)
            val uri = Uri.parse(normalized)
            val host = uri.host
            !host.isNullOrBlank() && host.contains(".") && Patterns.WEB_URL.matcher(normalized).matches()
        } catch (e: Exception) {
            false
        }
    }

    fun isSameSite(targetHost: String?): Boolean {
        if (targetHost.isNullOrBlank()) return false
        val activeHost = currentHost ?: return false

        val tHost = targetHost.lowercase(Locale.ROOT)
        val cHost = activeHost.lowercase(Locale.ROOT)

        // Strict: exact match or subdomain
        if (tHost == cHost || tHost.endsWith(".$cHost") || cHost.endsWith(".$tHost")) {
            return true
        }

        // Subdomain matching ignoring www
        val cleanT = tHost.removePrefix("www.")
        val cleanC = cHost.removePrefix("www.")
        if (cleanT == cleanC || cleanT.endsWith(".$cleanC") || cleanC.endsWith(".$cleanT")) {
            return true
        }

        // Flexible mode: matches same brand tag (e.g. dizilla.now vs dizilla.nl)
        if (sameSiteMode == SameSiteMode.FLEXIBLE) {
            val brandT = ProfileManager.extractBrandTag(cleanT)
            val brandC = ProfileManager.extractBrandTag(cleanC)
            if (brandT.isNotEmpty() && brandT == brandC) {
                return true
            }
        }

        return false
    }
}

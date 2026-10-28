package com.example

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class SiteProfile(
    val brandTag: String,
    val allowHosts: MutableSet<String> = mutableSetOf(),
    val blockHosts: MutableSet<String> = mutableSetOf(),
    var extraCssHideSelectors: String = "",
    var userAgentMode: String = "mobile",
    var desktopMode: Boolean = false
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("brandTag", brandTag)
        val allowArr = JSONArray()
        allowHosts.forEach { allowArr.put(it) }
        obj.put("allowHosts", allowArr)

        val blockArr = JSONArray()
        blockHosts.forEach { blockArr.put(it) }
        obj.put("blockHosts", blockArr)

        obj.put("extraCssHideSelectors", extraCssHideSelectors)
        obj.put("userAgentMode", userAgentMode)
        obj.put("desktopMode", desktopMode)
        return obj
    }

    companion object {
        fun fromJson(json: JSONObject): SiteProfile {
            val brandTag = json.optString("brandTag", "default")
            val allowSet = mutableSetOf<String>()
            json.optJSONArray("allowHosts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val h = arr.optString(i).trim().lowercase(Locale.ROOT)
                    if (h.isNotEmpty()) allowSet.add(h)
                }
            }

            val blockSet = mutableSetOf<String>()
            json.optJSONArray("blockHosts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val h = arr.optString(i).trim().lowercase(Locale.ROOT)
                    if (h.isNotEmpty()) blockSet.add(h)
                }
            }

            return SiteProfile(
                brandTag = brandTag,
                allowHosts = allowSet,
                blockHosts = blockSet,
                extraCssHideSelectors = json.optString("extraCssHideSelectors", ""),
                userAgentMode = json.optString("userAgentMode", "mobile"),
                desktopMode = json.optBoolean("desktopMode", false)
            )
        }
    }
}

class ProfileManager(private val context: Context) {

    companion object {
        private const val TAG = "ProfileManager"
        private const val PROFILES_DIR = "profiles"

        fun extractBrandTag(host: String): String {
            val clean = host.lowercase(Locale.ROOT)
                .removePrefix("www.")
                .split(":")[0]

            val parts = clean.split(".")
            return when {
                parts.isEmpty() -> "default"
                parts.size == 1 -> parts[0]
                parts.size == 2 -> parts[0] // e.g. dizilla.now -> dizilla
                else -> {
                    // e.g. sub.dizilla.com -> dizilla
                    // or co.uk handling
                    if (parts[parts.size - 2].length <= 3 && parts.last().length <= 2) {
                        if (parts.size >= 3) parts[parts.size - 3] else parts[0]
                    } else {
                        parts[parts.size - 2]
                    }
                }
            }
        }
    }

    private val profilesDir: File by lazy {
        val dir = File(context.filesDir, PROFILES_DIR)
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun getProfileForHost(host: String): SiteProfile {
        val brand = extractBrandTag(host)
        // 1. Try to load user customized profile for this brand
        val brandFile = File(profilesDir, "$brand.json")
        if (brandFile.exists()) {
            try {
                val jsonStr = brandFile.readText()
                return SiteProfile.fromJson(JSONObject(jsonStr))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse profile for brand: $brand", e)
            }
        }

        // 2. Try default asset profile if matching brand
        try {
            val defaultStr = context.assets.open("profiles/default.json").bufferedReader().use { it.readText() }
            val defaultJson = JSONObject(defaultStr)
            val defaultProfile = SiteProfile.fromJson(defaultJson)
            if (defaultProfile.brandTag.equals(brand, ignoreCase = true)) {
                return defaultProfile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load default asset profile", e)
        }

        // 3. Fallback generic profile for unknown sites
        return SiteProfile(
            brandTag = brand,
            allowHosts = mutableSetOf(
                "accounts.google.com",
                "accounts.youtube.com",
                "ssl.gstatic.com"
            )
        )
    }

    fun saveProfile(profile: SiteProfile) {
        try {
            val brandFile = File(profilesDir, "${profile.brandTag}.json")
            brandFile.writeText(profile.toJson().toString(2))
            Log.d(TAG, "Saved profile for brand: ${profile.brandTag}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile", e)
        }
    }
}

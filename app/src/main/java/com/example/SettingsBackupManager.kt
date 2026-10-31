package com.example

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SettingsBackupManager {
    private const val FORMAT_VERSION = 1
    private const val ADDRESS_PREFERENCES = "dizilla_address_prefs"
    private const val ADBLOCK_PREFERENCES = "adblocker_prefs"
    private const val MAX_BACKUP_SIZE = 15 * 1024 * 1024
    private val managedPreferences = setOf(ADDRESS_PREFERENCES, ADBLOCK_PREFERENCES)
    private val managedListFiles = setOf(
        "allowlist_user.txt",
        "blocklist_user.txt",
        "adguard_dns_filter.txt"
    )

    fun createBackup(context: Context): String {
        val root = JSONObject()
            .put("format", "dizilla-settings")
            .put("version", FORMAT_VERSION)
            .put("createdAt", System.currentTimeMillis())

        val preferencesJson = JSONObject()
        managedPreferences.forEach { name ->
            preferencesJson.put(
                name,
                encodePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE))
            )
        }
        root.put("preferences", preferencesJson)

        val filesJson = JSONObject()
        managedListFiles.forEach { name ->
            val file = File(context.filesDir, name)
            if (file.isFile) filesJson.put(name, file.readText())
        }

        val profilesDir = File(context.filesDir, "profiles")
        if (profilesDir.isDirectory) {
            profilesDir.listFiles()
                ?.filter { it.isFile && it.name.matches(Regex("[a-zA-Z0-9._-]+\\.json")) }
                ?.forEach { filesJson.put("profiles/${it.name}", it.readText()) }
        }
        root.put("files", filesJson)

        return root.toString(2)
    }

    fun restoreBackup(context: Context, content: String) {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_SIZE) {
            "Yedek dosyası çok büyük"
        }

        val root = JSONObject(content)
        require(root.optString("format") == "dizilla-settings") { "Geçersiz Dizilla yedeği" }
        require(root.optInt("version", -1) == FORMAT_VERSION) { "Desteklenmeyen yedek sürümü" }

        val preferences = root.getJSONObject("preferences")
        val decodedPreferences = managedPreferences.associateWith { name ->
            decodePreferences(preferences.getJSONObject(name))
        }
        val fileContents = decodeFiles(root.getJSONObject("files"))

        decodedPreferences.forEach { (name, values) ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit()) { "Ayarlar kaydedilemedi: $name" }
        }

        fileContents.forEach { (relativePath, text) ->
            val destination = File(context.filesDir, relativePath)
            check(destination.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)) {
                "Yedekte geçersiz dosya yolu var"
            }
            destination.parentFile?.let { parent ->
                check(parent.isDirectory || parent.mkdirs()) { "Yedek klasörü oluşturulamadı" }
            }
            val temporary = File(destination.parentFile, "${destination.name}.restore.tmp")
            temporary.writeText(text)
            check(temporary.renameTo(destination)) { "Yedek dosyası geri yüklenemedi: ${destination.name}" }
        }

        managedListFiles.filterNot(fileContents::containsKey).forEach { name ->
            File(context.filesDir, name).delete()
        }
        val restoredProfiles = fileContents.keys
            .filter { it.startsWith("profiles/") }
            .map { it.removePrefix("profiles/") }
            .toSet()
        File(context.filesDir, "profiles").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") && it.name !in restoredProfiles }
            ?.forEach { file ->
                check(file.delete()) { "Eski profil silinemedi: ${file.name}" }
            }
    }

    private fun encodePreferences(preferences: SharedPreferences): JSONObject {
        val encoded = JSONObject()
        preferences.all.forEach { (key, value) ->
            val typeAndValue = JSONObject()
            when (value) {
                is String -> typeAndValue.put("type", "string").put("value", value)
                is Boolean -> typeAndValue.put("type", "boolean").put("value", value)
                is Int -> typeAndValue.put("type", "int").put("value", value)
                is Long -> typeAndValue.put("type", "long").put("value", value)
                is Float -> typeAndValue.put("type", "float").put("value", value.toDouble())
                is Set<*> -> {
                    val strings = JSONArray()
                    value.filterIsInstance<String>().forEach(strings::put)
                    typeAndValue.put("type", "stringSet").put("value", strings)
                }
                else -> throw IllegalStateException("Desteklenmeyen ayar türü: $key")
            }
            encoded.put(key, typeAndValue)
        }
        return encoded
    }

    private fun decodePreferences(encoded: JSONObject): Map<String, Any> {
        val values = mutableMapOf<String, Any>()
        val keys = encoded.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = encoded.getJSONObject(key)
            val value = item.get("value")
            values[key] = when (item.getString("type")) {
                "string" -> value as? String ?: error("Geçersiz string ayarı: $key")
                "boolean" -> value as? Boolean ?: error("Geçersiz boolean ayarı: $key")
                "int" -> (value as? Number)?.toInt() ?: error("Geçersiz int ayarı: $key")
                "long" -> (value as? Number)?.toLong() ?: error("Geçersiz long ayarı: $key")
                "float" -> (value as? Number)?.toFloat() ?: error("Geçersiz float ayarı: $key")
                "stringSet" -> {
                    val array = value as? JSONArray ?: error("Geçersiz liste ayarı: $key")
                    buildSet {
                        for (index in 0 until array.length()) add(array.getString(index))
                    }
                }
                else -> error("Bilinmeyen ayar türü: $key")
            }
        }
        return values
    }

    private fun decodeFiles(files: JSONObject): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val keys = files.keys()
        while (keys.hasNext()) {
            val path = keys.next()
            val isManagedList = path in managedListFiles
            val isProfile = path.startsWith("profiles/") &&
                path.removePrefix("profiles/").matches(Regex("[a-zA-Z0-9._-]+\\.json"))
            require(isManagedList || isProfile) { "Yedekte izin verilmeyen dosya var: $path" }
            val text = files.getString(path)
            require(text.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) {
                "Yedek içindeki dosya çok büyük: $path"
            }
            if (isProfile) JSONObject(text)
            result[path] = text
        }
        return result
    }
}

package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `settings backup restores preferences and custom blocklists`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val addressPrefs = context.getSharedPreferences("dizilla_address_prefs", Context.MODE_PRIVATE)
    val adBlockPrefs = context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE)
    val blocklistFile = File(context.filesDir, "blocklist_user.txt")
    val originalBlocklist = blocklistFile.takeIf { it.exists() }?.readText()
    try {
      addressPrefs.edit().clear().putString("active_site_url", "https://before.example").commit()
      adBlockPrefs.edit().clear().putBoolean("pref_adblock_enabled", false).commit()
      blocklistFile.writeText("before.example\n")
      val backup = SettingsBackupManager.createBackup(context)

      addressPrefs.edit().putString("active_site_url", "https://after.example").commit()
      adBlockPrefs.edit().putBoolean("pref_adblock_enabled", true).commit()
      blocklistFile.writeText("after.example\n")

      SettingsBackupManager.restoreBackup(context, backup)

      assertEquals("https://before.example", addressPrefs.getString("active_site_url", null))
      assertEquals(false, adBlockPrefs.getBoolean("pref_adblock_enabled", true))
      assertEquals("before.example\n", blocklistFile.readText())
    } finally {
      addressPrefs.edit().clear().commit()
      adBlockPrefs.edit().clear().commit()
      if (originalBlocklist == null) blocklistFile.delete() else blocklistFile.writeText(originalBlocklist)
    }
  }

  @Test
  fun `invalid settings backup is rejected without changing preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("dizilla_address_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().putString("active_site_url", "https://keep.example").commit()

    try {
      SettingsBackupManager.restoreBackup(context, """{"format":"not-dizilla","version":1}""")
      throw AssertionError("Invalid backup should have been rejected")
    } catch (_: IllegalArgumentException) {
      assertEquals("https://keep.example", prefs.getString("active_site_url", null))
    } finally {
      prefs.edit().clear().commit()
    }
  }

  @Test
  fun `site bookmarks persist normalized URLs and can be removed`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("dizilla_address_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    try {
      val manager = SiteAddressManager(context)

      assertTrue(manager.addBookmark("https://saved.example.com/watch"))
      assertTrue(manager.addBookmark("https://saved.example.com/watch"))
      assertEquals(listOf("https://saved.example.com/watch"), manager.getBookmarkedUrls())
      assertEquals(
        listOf("https://saved.example.com/watch"),
        SiteAddressManager(context).getBookmarkedUrls()
      )

      assertTrue(manager.removeBookmark("https://saved.example.com/watch"))
      assertTrue(manager.getBookmarkedUrls().isEmpty())
      assertFalse(manager.addBookmark("not a URL"))
    } finally {
      prefs.edit().clear().commit()
    }
  }

  @Test
  fun `known Macellan allads banner is blocked before allowlist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    NetworkLogger.clearLogs()
    val adBlocker = AdBlocker(context)

    val response = adBlocker.shouldIntercept(
      Uri.parse("https://olfs.macellan.online/allads/bndbetcup728x90.gif"),
      "image"
    )

    assertTrue(response != null)
    assertTrue(NetworkLogger.getRecentLogs().last().isBlocked)
    assertEquals("SiteAdPath(/allads/)", NetworkLogger.getRecentLogs().last().ruleOrReason)
  }

  @Test
  fun `known Macellan slotbar video banner is blocked`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    NetworkLogger.clearLogs()
    val adBlocker = AdBlocker(context)

    val response = adBlocker.shouldIntercept(
      Uri.parse("https://olfs.macellan.online/allads/slotbarvd.png"),
      "image"
    )

    assertTrue(response != null)
    assertEquals("SiteAdPath(/allads/)", NetworkLogger.getRecentLogs().last().ruleOrReason)
  }

  @Test
  fun `AdGuard DNS domain rules block and exceptions allow matching hosts`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    val filterFile = File(context.filesDir, "adguard_dns_filter.txt")
    filterFile.delete()
    try {
      filterFile.writeText("example.test\n@@allowed.example.test\n")
      NetworkLogger.clearLogs()
      val adBlocker = AdBlocker(context)

      val blocked = adBlocker.shouldIntercept(
        Uri.parse("https://tracker.example.test/pixel"),
        "image"
      )
      val allowed = adBlocker.shouldIntercept(
        Uri.parse("https://allowed.example.test/resource"),
        "other"
      )

      assertTrue(blocked != null)
      assertEquals("AdGuardDNS(example.test)", NetworkLogger.getRecentLogs()[0].ruleOrReason)
      assertEquals(null, allowed)
    } finally {
      filterFile.delete()
    }
  }

  @Test
  fun `AdGuard parser accepts domain rules and skips unsupported rules`() {
    assertEquals("ads.example.test" to false, AdBlocker.parseAdGuardDnsRule("||ads.example.test^"))
    assertEquals("allowed.example.test" to true, AdBlocker.parseAdGuardDnsRule("@@||allowed.example.test^|"))
    assertEquals(null, AdBlocker.parseAdGuardDnsRule("||ads-*.example.test^"))
    assertEquals(null, AdBlocker.parseAdGuardDnsRule("||ads.example.test^\$badfilter"))
    assertEquals(null, AdBlocker.parseAdGuardDnsRule("||ads.example.test^\$important"))
    assertEquals(null, AdBlocker.parseAdGuardDnsRule("/banner/"))
  }

  @Test
  fun `Macellan assets outside ad path remain allowed`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    NetworkLogger.clearLogs()
    val adBlocker = AdBlocker(context)

    val response = adBlocker.shouldIntercept(
      Uri.parse("https://olfs.macellan.online/images/logo.png"),
      "image"
    )

    assertEquals(null, response)
    assertFalse(NetworkLogger.getRecentLogs().last().isBlocked)
  }

  @Test
  fun `network logs redact URL query credentials`() {
    NetworkLogger.clearLogs()

    NetworkLogger.logRequest(
      host = "media.example",
      url = "https://media.example/watch?token=secret-value",
      type = "document",
      isBlocked = false,
      reason = "Passed"
    )

    val log = NetworkLogger.getRecentLogs().single().toString()
    assertTrue(log.contains("https://media.example/watch"))
    assertFalse(log.contains("secret-value"))
  }

  @Test
  fun `disabled ad blocker does not inject cosmetic script`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    val adBlocker = AdBlocker(context)

    adBlocker.isEnabled = false

    assertEquals("", adBlocker.getEffectiveCosmeticScript())
  }

  @Test
  fun `enabled ad blocker injects cosmetic script`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    val adBlocker = AdBlocker(context)

    adBlocker.isEnabled = true

    assertTrue(adBlocker.getEffectiveCosmeticScript().isNotEmpty())
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Dizilla", appName)
  }
}

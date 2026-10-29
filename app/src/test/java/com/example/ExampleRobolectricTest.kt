package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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

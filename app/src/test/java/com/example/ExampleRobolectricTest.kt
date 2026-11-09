package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SiteCrew", appName)
  }

  @Test
  fun `verify attendance wage multipliers`() {
    assertEquals(1.0, AttendanceStatus.PRESENT.multiplier, 0.001)
    assertEquals(0.5, AttendanceStatus.HALF_DAY.multiplier, 0.001)
    assertEquals(0.0, AttendanceStatus.ABSENT.multiplier, 0.001)
  }
}

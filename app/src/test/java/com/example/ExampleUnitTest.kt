package com.example

import com.example.data.model.AttendanceStatus
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun verifyAttendanceStatusMultipliers() {
    assertEquals(1.0, AttendanceStatus.PRESENT.multiplier, 0.001)
    assertEquals(0.5, AttendanceStatus.HALF_DAY.multiplier, 0.001)
    assertEquals(0.0, AttendanceStatus.ABSENT.multiplier, 0.001)
  }

  @Test
  fun verifyJoinCodeFormat() {
    val sampleCode = "SC-8492"
    assertTrue(sampleCode.startsWith("SC-"))
    assertEquals(7, sampleCode.length)
  }
}

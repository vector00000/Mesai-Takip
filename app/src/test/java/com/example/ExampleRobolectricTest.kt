package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mesai Takip", appName)
    }

    @Test
    fun `overtime calculation is accurate`() {
        // Test standard daytime overtime: 18:00 to 22:30 -> 4 hours 30 mins = 270 mins
        val minutes = TimeUtils.calculateManualMinutes("18:00", "22:30")
        assertEquals(270, minutes)

        // 200 TL / hour -> 4.5 hours * 200 = 900 TL
        val amount = TimeUtils.calculateEarnedAmount(minutes, 200.0)
        assertEquals(900.0, amount, 0.01)
    }

    @Test
    fun `midnight crossing calculation is accurate`() {
        // Test midnight crossing: 18:00 to 02:30 -> 8 hours 30 mins = 510 mins
        val minutes = TimeUtils.calculateManualMinutes("18:00", "02:30")
        assertEquals(510, minutes)

        val amount = TimeUtils.calculateEarnedAmount(minutes, 200.0)
        assertEquals(1700.0, amount, 0.01)
    }
}

package com.closer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NotifierTest {

    // We can't instantiate Notifier (needs Context) but we can test the quiet hour logic directly.
    // Extract it to a standalone function here to match the implementation.

    private fun isQuietHour(fromHour: Int, toHour: Int, hour: Int): Boolean {
        return if (fromHour <= toHour) hour in fromHour until toHour
        else hour >= fromHour || hour < toHour
    }

    // Non-wrapping range: 9am–17pm (daytime quiet hours for testing)
    @Test fun `non-wrapping quiet range – hour inside returns true`() {
        assertTrue(isQuietHour(9, 17, 12))
    }

    @Test fun `non-wrapping quiet range – hour at start returns true`() {
        assertTrue(isQuietHour(9, 17, 9))
    }

    @Test fun `non-wrapping quiet range – hour at end returns false`() {
        assertFalse(isQuietHour(9, 17, 17))
    }

    @Test fun `non-wrapping quiet range – hour outside returns false`() {
        assertFalse(isQuietHour(9, 17, 20))
    }

    // Wrapping range: 23–7 (night quiet hours, the default)
    @Test fun `wrapping quiet range – 23 to 7 – at 23 returns true`() {
        assertTrue(isQuietHour(23, 7, 23))
    }

    @Test fun `wrapping quiet range – 23 to 7 – at 2 returns true`() {
        assertTrue(isQuietHour(23, 7, 2))
    }

    @Test fun `wrapping quiet range – 23 to 7 – at 7 returns false`() {
        assertFalse(isQuietHour(23, 7, 7))
    }

    @Test fun `wrapping quiet range – 23 to 7 – at 10 returns false`() {
        assertFalse(isQuietHour(23, 7, 10))
    }
}

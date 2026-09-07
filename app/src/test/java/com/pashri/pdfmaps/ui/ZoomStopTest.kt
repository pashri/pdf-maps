package com.pashri.pdfmaps.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Scale at which a page fits the viewport, for these tests. */
private const val FIT = 0.2f

/** Tolerance for comparing scales. */
private const val EPSILON = 0.0001f

/**
 * Covers the double-tap zoom cycle: fit, then 3x, then 6x, then
 * back to fit.
 */
class ZoomStopTest {

    @Test
    fun `first tap from fit goes to 3x`() {
        assertEquals(FIT * 3f, nextZoomStop(FIT, FIT), EPSILON)
    }

    @Test
    fun `second tap goes to 6x`() {
        assertEquals(FIT * 6f, nextZoomStop(FIT * 3f, FIT), EPSILON)
    }

    @Test
    fun `third tap returns to fit`() {
        assertEquals(FIT, nextZoomStop(FIT * 6f, FIT), EPSILON)
    }

    @Test
    fun `a tap beyond the last stop returns to fit`() {
        assertEquals(FIT, nextZoomStop(FIT * 20f, FIT), EPSILON)
    }

    @Test
    fun `pinching between stops advances to the next one up`() {
        // Pinched to 4x by hand: the next stop up is 6x, not fit.
        assertEquals(FIT * 6f, nextZoomStop(FIT * 4f, FIT), EPSILON)
    }

    @Test
    fun `a scale just under a stop still advances past it`() {
        // Guards the tolerance: 2.99x must not count as "below 3x"
        // and send the user back to a stop they are already at.
        assertEquals(FIT * 6f, nextZoomStop(FIT * 2.99f, FIT), EPSILON)
    }
}

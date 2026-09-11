package com.pashri.pdfmaps.render

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** An arbitrary tile, since these tests never look at its fields. */
private val KEY = TileKey(pageIndex = 0, level = 2, col = 1, row = 1)

/**
 * Covers the claim bookkeeping that stops a tile being rendered
 * twice.
 *
 * The cancellation case documents the contract rather than guarding
 * a reachable regression: an uncontended [kotlinx.coroutines.sync.Mutex]
 * acquires without suspending, so it would pass even if [TileClaims]
 * released without [kotlinx.coroutines.NonCancellable].
 */
class TileClaimsTest {

    @Test
    fun `an unclaimed tile can be claimed`() = runTest {
        assertTrue(TileClaims().claim(KEY))
    }

    @Test
    fun `a claimed tile cannot be claimed again`() = runTest {
        val claims = TileClaims()
        claims.claim(KEY)
        assertFalse(claims.claim(KEY))
    }

    @Test
    fun `releasing a claim makes the tile claimable again`() = runTest {
        val claims = TileClaims()
        claims.claim(KEY)
        claims.release(KEY)
        assertTrue(claims.claim(KEY))
    }

    @Test
    fun `a claim is not held once released`() = runTest {
        val claims = TileClaims()
        claims.claim(KEY)
        claims.release(KEY)
        assertFalse(claims.isHeld(KEY))
    }

    @Test
    fun `a render cancelled mid-flight still gives up its claim`() =
        runTest {
            val claims = TileClaims()
            val rendering = CompletableDeferred<Unit>()

            // Mirrors TileRenderer.render: claim, render, release in
            // a finally. The render never completes, so the only way
            // out is cancellation.
            val job = launch {
                claims.claim(KEY)
                try {
                    rendering.complete(Unit)
                    CompletableDeferred<Unit>().await()
                } finally {
                    claims.release(KEY)
                }
            }
            rendering.await()
            job.cancelAndJoin()

            assertFalse(claims.isHeld(KEY))
            assertTrue(claims.claim(KEY))
        }
}

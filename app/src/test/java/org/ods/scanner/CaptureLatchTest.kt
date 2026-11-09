package org.ods.scanner

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class CaptureLatchTest {
    @Test fun queuedAutoCaptureEventsCannotCreateExtraPhotos() {
        val latch = CaptureLatch()
        assertTrue(latch.begin())
        repeat(100) { assertFalse(latch.begin()) }
    }
    @Test fun failureAllowsRetryButSuccessfulVisitStaysClosed() {
        val latch = CaptureLatch()
        assertTrue(latch.begin()); latch.failed()
        assertTrue(latch.begin()); assertFalse(latch.begin())
        assertTrue(CaptureLatch().begin()) // Explicitly opening camera again starts a new visit.
    }
    @Test fun concurrentShutterRequestsHaveOneWinner() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val latch = CaptureLatch()
            val results = pool.invokeAll(List(100) { Callable { latch.begin() } })
            assertEquals(1,results.count { it.get() })
        } finally { pool.shutdownNow() }
    }
}

package hk.hkucs.qline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueTest {
    @Test
    fun newRestaurantQueueStartsOpen() {
        val queue = Queue.seed("Harbour Grill")

        assertTrue(queue.isOpen)
        assertEquals(true, queue.toMap()["isOpen"])
    }

    @Test
    fun pausedStateIsPersisted() {
        val queue = Queue.seed("Harbour Grill").copy(isOpen = false)

        assertEquals(false, queue.toMap()["isOpen"])
    }
}

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

    @Test
    fun waitingNumbersArePersistedForCustomerPositionUpdates() {
        val queue = Queue.seed("Harbour Grill").copy(waitingNumbers = listOf(1L, 2L))

        assertEquals(listOf(1L, 2L), queue.toMap()["waitingNumbers"])
    }

    @Test
    fun peopleAheadCountsOnlyWaitingNumbersAfterCurrentTicket() {
        val queue = Queue.seed("Harbour Grill").copy(
            currentTicketNumber = 2,
            waitingNumbers = listOf(1, 3, 5, 6)
        )

        assertEquals(2, queue.peopleAheadOf(6))
    }
}

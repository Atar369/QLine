package hk.hkucs.qline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueReportLocationTest {
    @Test
    fun acceptsLocationWhoseAccuracyCircleIsWithinRadius() {
        assertTrue(isWithinQueueReportRadius(distanceMeters = 200.0, accuracyMeters = 30f))
    }

    @Test
    fun rejectsLocationOutsideRadiusAfterAccountingForAccuracy() {
        assertFalse(isWithinQueueReportRadius(distanceMeters = 230.0, accuracyMeters = 21f))
    }

    @Test
    fun rejectsLocationWithInsufficientAccuracy() {
        assertFalse(isWithinQueueReportRadius(distanceMeters = 20.0, accuracyMeters = 101f))
    }
}

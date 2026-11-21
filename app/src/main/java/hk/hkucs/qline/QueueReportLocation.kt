package hk.hkucs.qline

internal const val QUEUE_REPORT_RADIUS_METERS = 250.0
internal const val MAX_QUEUE_REPORT_LOCATION_ACCURACY_METERS = 100f
internal const val QUEUE_REPORT_COOLDOWN_MILLIS = 15 * 60 * 1000L

internal fun isWithinQueueReportRadius(distanceMeters: Double, accuracyMeters: Float): Boolean =
    distanceMeters.isFinite() &&
        accuracyMeters.isFinite() &&
        distanceMeters >= 0.0 &&
        accuracyMeters in 0f..MAX_QUEUE_REPORT_LOCATION_ACCURACY_METERS &&
        distanceMeters + accuracyMeters <= QUEUE_REPORT_RADIUS_METERS

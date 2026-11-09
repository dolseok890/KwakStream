package kr.kwakstream.app

/** Official Camera2 relative rotation (degrees). Sensor data is the only authority. */
internal fun cameraRelativeRotation(sensorDegrees: Int, displayDegrees: Int, front: Boolean): Int {
    require(sensorDegrees in setOf(0, 90, 180, 270))
    require(displayDegrees in setOf(0, 90, 180, 270))
    val sign = if (front) 1 else -1
    return Math.floorMod(sensorDegrees - displayDegrees * sign, 360)
}

/** Orientation is based on the actual camera buffer and relative rotation. */
internal fun orientedPreviewSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> {
    require(width > 0 && height > 0)
    require(rotationDegrees % 90 == 0)
    return if (Math.floorMod(rotationDegrees, 180) == 90) height to width else width to height
}

/** One uniform scale factor: no crop, no distortion. */
internal fun fitCenterScale(contentWidth: Int, contentHeight: Int, viewWidth: Int, viewHeight: Int): Float {
    require(contentWidth > 0 && contentHeight > 0 && viewWidth > 0 && viewHeight > 0)
    return minOf(viewWidth.toFloat() / contentWidth, viewHeight.toFloat() / contentHeight)
}

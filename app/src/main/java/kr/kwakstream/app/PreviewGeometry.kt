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


/**
 * TextureView CAMERA2 preview geometry.
 *
 * Android already transforms a camera SurfaceTexture to the device's NATURAL
 * orientation using SENSOR_ORIENTATION (including front-camera convention).
 * Applying cameraRelativeRotation() to TextureView again double-rotates it.
 * Only undo the DISPLAY rotation in the view transform, then FIT_CENTER the
 * sensor-oriented output. This is not a recording/video-encoder rotation.
 * See Android Developers: camera2/camera-preview / android-camera2-preview.
 */
internal fun textureViewDisplayCorrection(displayDegrees: Int): Int {
    require(displayDegrees in setOf(0, 90, 180, 270))
    return Math.floorMod(-displayDegrees, 360)
}

internal fun sensorOrientedTextureSize(bufferWidth: Int, bufferHeight: Int, sensorDegrees: Int): Pair<Int, Int> {
    require(sensorDegrees in setOf(0, 90, 180, 270))
    return orientedPreviewSize(bufferWidth, bufferHeight, sensorDegrees)
}

data class TexturePreviewFit(
    val displayCorrection: Int,
    val sensorOrientedSize: Pair<Int, Int>,
    val displayedSize: Pair<Int, Int>,
    val scale: Float,
    val destinationCorners: FloatArray
)

/** Calculate the quadrilateral of the complete, uncropped TextureView camera frame.
 * Source rectangle for Matrix.setPolyToPoly() is the measured TextureView area;
 * destination vertices are generated from the frame dimensions after the
 * platform's implicit sensor orientation and app display correction.
 */
internal fun texturePreviewFit(
    bufferWidth: Int, bufferHeight: Int, sensorDegrees: Int, displayDegrees: Int,
    viewWidth: Int, viewHeight: Int
): TexturePreviewFit {
    val natural = sensorOrientedTextureSize(bufferWidth, bufferHeight, sensorDegrees)
    val correction = textureViewDisplayCorrection(displayDegrees)
    val displayed = orientedPreviewSize(natural.first, natural.second, correction)
    val scale = fitCenterScale(displayed.first, displayed.second, viewWidth, viewHeight)
    val halfWidth = natural.first / 2f
    val halfHeight = natural.second / 2f
    val centerX = viewWidth / 2f
    val centerY = viewHeight / 2f
    // Exact 90-degree quarter turns: do not introduce trig/rounding distortion.
    val sourceCorners = floatArrayOf(
        -halfWidth, -halfHeight,
         halfWidth, -halfHeight,
         halfWidth,  halfHeight,
        -halfWidth,  halfHeight
    )
    val corners = FloatArray(8)
    for (i in 0 until 4) {
        val x = sourceCorners[2 * i]
        val y = sourceCorners[2 * i + 1]
        val (rx, ry) = when (correction) {
            0 -> x to y
            90 -> -y to x
            180 -> -x to -y
            270 -> y to -x
            else -> error("Unreachable display correction $correction")
        }
        corners[2 * i] = centerX + rx * scale
        corners[2 * i + 1] = centerY + ry * scale
    }
    return TexturePreviewFit(correction, natural, displayed, scale, corners)
}

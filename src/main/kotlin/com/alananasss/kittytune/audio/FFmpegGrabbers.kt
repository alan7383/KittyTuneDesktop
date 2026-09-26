package com.alananasss.kittytune.audio

import org.bytedeco.javacv.FFmpegFrameGrabber

/**
 * Frees the grabber's demuxer and codec contexts.
 *
 * Those live in native memory that no garbage collection will ever reclaim, so every grabber has to
 * reach this on every path, errors included. `release()` alone, because `stop()` is an alias for it
 * and the old `stop(); release()` pair skipped the release whenever `stop()` threw.
 */
internal fun FFmpegFrameGrabber.releaseQuietly() {
    runCatching { release() }
}

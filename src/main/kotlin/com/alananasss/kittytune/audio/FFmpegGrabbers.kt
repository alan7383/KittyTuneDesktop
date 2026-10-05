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

/**
 * Opens the stream without JavaCV's process-wide lock.
 *
 * `start()` and `release()` synchronize on one class object for every grabber in the app, and the
 * network work of opening a stream happens inside it. So a single open that stalls (a loudness scan
 * or beat analysis waiting on an HLS segment, a canvas video on a slow CDN) left every track the
 * listener clicked queued behind it: nothing played until the stalled one gave up. FFmpeg has done its
 * own codec locking since 4.0, which is what that lock once stood in for, so playback opens and frees
 * its streams outside it and never waits on anybody else's.
 */
internal fun FFmpegFrameGrabber.startUnlocked() {
    startUnsafe()
}

/** [releaseQuietly] without the process-wide lock; see [startUnlocked]. */
internal fun FFmpegFrameGrabber.releaseUnlockedQuietly() {
    runCatching { releaseUnsafe() }
}

/**
 * Socket timeout for analysis grabbers, in microseconds.
 *
 * `rw_timeout` rather than `timeout`: FFmpeg's HLS demuxer forwards `rw_timeout` to every playlist and
 * segment request it makes, while `timeout` stops at the first connection. With only `timeout`, one
 * stalled segment kept `start()` blocked for as long as the socket stayed open.
 */
internal const val ANALYSIS_RW_TIMEOUT_US = "8000000"

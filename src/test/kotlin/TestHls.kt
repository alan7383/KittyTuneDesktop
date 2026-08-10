package com.alananasss.kittytune

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.Test

class TestHls {
    @Test
    fun testHlsSeek() {
        val url = "hls+https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val grabber = FFmpegFrameGrabber(url)
        grabber.start()
        
        val f = grabber.grabSamples()
        println("First frame at ${f?.timestamp}")
        
        grabber.timestamp = 60_000_000L // 60 seconds
        
        val f2 = grabber.grabSamples()
        println("Seek frame at ${f2?.timestamp}")
        
        grabber.stop()
        grabber.release()
    }
}

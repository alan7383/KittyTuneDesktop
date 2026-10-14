package com.alananasss.kittytune.utils

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform

/**
 * Manages off-heap C/C++ native allocator state across operating systems (glibc on Linux, Windows heap).
 *
 * Prevents native memory drift (issue #66) where glibc creates dozens of 64MB arenas across 100+
 * threads and never returns freed memory to the OS, or where JavaCPP / FFmpeg / Skiko hold uncollected
 * native buffers because the JVM heap has plenty of free headroom.
 */
object NativeMemoryManager {

    private interface LibC : Library {
        fun malloc_trim(pad: Long): Int
        fun mallopt(param: Int, value: Int): Int
    }

    private val libc: LibC? = runCatching {
        if (Platform.isLinux()) {
            Native.load("c", LibC::class.java)
        } else null
    }.getOrNull()

    /**
     * One-shot native allocator tuning at application startup.
     * Restricts glibc to 2 arenas (M_ARENA_MAX = -8), preventing multi-gigabyte virtual
     * and physical memory fragmentation across 100+ threads.
     */
    fun init() {
        if (Platform.isLinux()) {
            runCatching {
                // M_ARENA_MAX is -8 in glibc malloc.h: limit to 2 arenas
                libc?.mallopt(-8, 2)
                // M_MMAP_THRESHOLD is -1 in glibc: allocate large buffers (>64KB) via mmap so they unmap instantly on free
                libc?.mallopt(-1, 65536)
                // M_TRIM_THRESHOLD is -2 in glibc: release free pages back to kernel when 128KB top of heap is free
                libc?.mallopt(-2, 131072)
            }
        }
    }

    /**
     * Immediately reclaims native memory back to the operating system:
     * 1. Forces JavaCPP to deallocate unreferenced native pointers (FFmpeg / LiteRT).
     * 2. Runs JavaCPP Pointer.trimMemory() (calls malloc_trim(0) on Linux, HeapCompact on Windows).
     * 3. On Linux: explicitly invokes glibc malloc_trim(0) to release unmapped arena pages back to the kernel.
     */
    fun trim() {
        runCatching {
            org.bytedeco.javacpp.Pointer.deallocateReferences()
        }
        runCatching {
            org.bytedeco.javacpp.Pointer.trimMemory()
        }
        if (Platform.isLinux()) {
            runCatching {
                libc?.malloc_trim(0L)
            }
        }
    }
}

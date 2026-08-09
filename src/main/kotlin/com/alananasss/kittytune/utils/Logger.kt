package com.alananasss.kittytune.utils

import com.alananasss.kittytune.BuildConfig

object Logger {
    var isDebug: Boolean = BuildConfig.DEBUG

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (isDebug) {
            if (tr != null) {
                System.err.println("[$tag] $msg")
                tr.printStackTrace(System.err)
            } else {
                System.err.println("[$tag] $msg")
            }
        }
    }

    fun d(tag: String, msg: String) {
        if (isDebug) {
            println("[$tag] $msg")
        }
    }

    fun i(tag: String, msg: String) {
        if (isDebug) {
            println("[$tag] $msg")
        }
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (isDebug) {
            if (tr != null) {
                System.err.println("[$tag] $msg")
                tr.printStackTrace(System.err)
            } else {
                System.err.println("[$tag] $msg")
            }
        }
    }
}

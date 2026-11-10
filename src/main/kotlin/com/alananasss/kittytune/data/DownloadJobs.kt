package com.alananasss.kittytune.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class DownloadJobs(private val scope: CoroutineScope) {
    private val jobs = ConcurrentHashMap<Long, Job>()

    fun contains(id: Long): Boolean = jobs.containsKey(id)

    fun launch(id: Long, block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        jobs[id] = job
        job.invokeOnCompletion { jobs.remove(id, job) }
        job.start()
        return job
    }

    fun cancel(id: Long) {
        jobs.remove(id)?.cancel()
    }
}

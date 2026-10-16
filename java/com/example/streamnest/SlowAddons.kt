package com.example.streamnest

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Addons whose server keeps collecting links after it first answers (CNCVerse: the first answer is often empty, the
 * links show up on the next requests). For each title being looked at, such addons are asked again every 2 seconds —
 * and keep being asked until a video of that title has really started playing (or after 10 minutes).
 * New links appear by themselves in Sources, Quick Start and instant play.
 */
object SlowAddons {
    /** videoId → links found by the repeated requests so far. */
    val links = MutableStateFlow<Map<String, List<StreamItem>>>(emptyMap())
    private val jobs = ConcurrentHashMap<String, Job>()
    private val played = ConcurrentHashMap.newKeySet<String>()

    fun isSlow(a: Addon): Boolean = a.manifest.name.contains("cncverse", true) || a.manifest.id.contains("cncverse", true) ||
        a.baseUrl.contains("cncverse", true)

    fun watching(id: String): Boolean = jobs[id]?.isActive == true

    /** Starts the 5-second refresh for this title (no-op when there is no slow addon, it already runs, or it already played). */
    fun watch(vm: AppViewModel, type: String, id: String) {
        if (id in played || watching(id)) return
        val slow = vm.slowStreamAddons(type, id)
        if (slow.isEmpty()) return
        // Only a few titles at a time (the ones you looked at last).
        if (jobs.size >= 3) jobs.entries.sortedBy { it.key == id }.take(jobs.size - 2).forEach { it.value.cancel(); jobs.remove(it.key) }
        jobs[id] = vm.viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            while (isActive && id !in played && System.currentTimeMillis() - t0 < 10 * 60_000L) {
                for (a in slow) {
                    val got = runCatching {
                        Api.fetch<StreamResponse>(vm.streamUrl(a, type, id)).streams
                            .filter { !it.url.isNullOrBlank() }.map { StreamItem(a.manifest.name, it).also { si -> StreamHeaders.remember(si) } }
                    }.getOrDefault(emptyList())
                    if (got.isNotEmpty()) {
                        val cur = links.value[id].orEmpty()
                        val fresh = got.filter { g -> cur.none { it.stream.url == g.stream.url } }
                        if (fresh.isNotEmpty()) {
                            links.value = links.value + (id to (cur + fresh))
                            appendCached(id, fresh, vm.qsAddons())
                        }
                    }
                }
                delay(2_000)
            }
            jobs.remove(id)
        }
    }

    /** A video of this title started: stop refreshing it. */
    fun played(id: String) {
        played += id
        jobs.remove(id)?.cancel()
    }
}

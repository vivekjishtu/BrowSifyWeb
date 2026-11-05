package jp.hazuki.yuzubrowser.adblock

/** In-memory, per-tab counters. Request URLs and query strings are deliberately not retained. */
object PageBlockingStats {
    data class HostStats(val host: String, val count: Int, val rules: List<String>)
    data class Snapshot(val total: Int, val hosts: List<HostStats>)

    private data class MutableHost(var count: Int = 0, val rules: LinkedHashSet<String> = LinkedHashSet())
    private val tabs = HashMap<Long, LinkedHashMap<String, MutableHost>>()

    @Synchronized fun reset(tabId: Long) { tabs.remove(tabId) }
    @Synchronized fun record(tabId: Long, host: String, rule: String) {
        if (!tabs.containsKey(tabId) && tabs.size >= 100) tabs.remove(tabs.keys.first())
        val hosts = tabs.getOrPut(tabId) { LinkedHashMap() }
        if (!hosts.containsKey(host) && hosts.size >= 100) return
        val hostStats = hosts.getOrPut(host) { MutableHost() }
        hostStats.count++
        if (hostStats.rules.size < 3) hostStats.rules.add(rule)
    }
    @Synchronized fun snapshot(tabId: Long): Snapshot {
        val entries = tabs[tabId].orEmpty()
        return Snapshot(entries.values.sumOf { it.count }, entries.map { (host, value) ->
            HostStats(host, value.count, value.rules.toList())
        }.sortedByDescending { it.count })
    }
}

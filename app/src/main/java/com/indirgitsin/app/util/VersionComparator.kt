package com.indirgitsin.app.util

object VersionComparator {
    private data class Version(val numbers: List<Long>, val pre: List<String>)
    private fun parse(raw: String): Version? {
        val match = Regex("^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$").matchEntire(raw.trim()) ?: return null
        val parts = match.groupValues[1].split('.')
        val numbers = parts.map { it.toLongOrNull() ?: return null }
        return Version(numbers, match.groupValues[2].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList())
    }
    fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest) ?: return false
        val c = parse(current) ?: return false
        for (i in 0 until maxOf(l.numbers.size, c.numbers.size)) {
            val cmp = l.numbers.getOrElse(i) { 0L }.compareTo(c.numbers.getOrElse(i) { 0L })
            if (cmp != 0) return cmp > 0
        }
        if (l.pre.isEmpty() || c.pre.isEmpty()) return l.pre.isEmpty() && c.pre.isNotEmpty()
        for (i in 0 until minOf(l.pre.size, c.pre.size)) {
            val ln = l.pre[i].toLongOrNull()
            val cn = c.pre[i].toLongOrNull()
            val cmp = when {
                ln != null && cn != null -> ln.compareTo(cn)
                ln != null -> -1
                cn != null -> 1
                else -> l.pre[i].compareTo(c.pre[i])
            }
            if (cmp != 0) return cmp > 0
        }
        return l.pre.size > c.pre.size
    }
}

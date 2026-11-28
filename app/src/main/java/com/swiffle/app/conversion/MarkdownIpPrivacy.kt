package com.swiffle.app.conversion

/** Literal IP addresses only. Parsing does not perform network or DNS lookups. */
internal object MarkdownIpPrivacy {
    const val marker = "[IP removed]"
    private const val octet = "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9]?[0-9])"
    private const val v4 = "$octet(?:\\.$octet){3}"
    private const val h = "[0-9A-Fa-f]{1,4}"
    private const val tail = "(?:$h:$h|$v4)"
    private val ipv6 = buildList {
        add("(?:$h:){6}$tail")
        add("::(?:$h:){5}$tail")
        for (left in 1..5) {
            val prefix = "(?:(?:$h:){0," + (left-1) + "}$h)?"
            val right = 5-left
            add(prefix + "::(?:$h:){" + right + "}$tail")
        }
        add("(?:(?:$h:){0,5}$h)?::$h")
        add("(?:(?:$h:){0,6}$h)?::")
    }.joinToString("|")
    private val pattern = Regex("(?:(?<![a-zA-Z0-9:])(?:$ipv6)(?:%[a-zA-Z0-9_-]+(?:\\.[a-zA-Z0-9_-]+)*)?(?![a-zA-Z0-9:])|(?<![0-9])$v4(?![0-9]|\\.[0-9]))")
    fun redact(text: String): String {
        if ('.' !in text && ':' !in text) return text
        return pattern.replace(text, marker)
    }
    // Preserve token identities while masking matches that span several PDF glyphs.
    fun redactPieces(parts: List<String>): List<String> {
        if (parts.isEmpty()) return parts
        val joined = parts.joinToString("")
        val matches = pattern.findAll(joined).iterator()
        if (!matches.hasNext()) return parts
        var match: MatchResult? = matches.next()
        var offset = 0
        return parts.map { part ->
            buildString {
                for (c in part) {
                    val range = match?.range
                    if (range == null || offset !in range) append(c)
                    else if (offset == range.first) append(marker)
                    if (range != null && offset == range.last) match = if (matches.hasNext()) matches.next() else null
                    offset++
                }
            }
        }
    }
}

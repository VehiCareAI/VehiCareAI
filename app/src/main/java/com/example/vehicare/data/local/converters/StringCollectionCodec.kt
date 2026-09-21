package com.example.vehicare.data.local.converters

/**
 * Hand-rolled, escape-safe text codec for collections persisted in single Room columns.
 *
 * Format (v1), all segments are plain text with the escape rules below:
 *
 * ```
 * list/set:  ""                        -> empty collection
 *            "<count>;<e1>;<e2>;..."   -> exactly <count> elements
 * map:       "<count>;<k1>=<v1>;..."   -> exactly <count> key/value pairs
 * ```
 *
 * Escaping (applies to every element, key and value):
 * `\` -> `\\`, `;` -> `\;`, `=` -> `\=`, LF -> `\n`, CR -> `\r`.
 *
 * The leading element count makes the format lossless for the awkward cases
 * (`[""]`, `["", ""]`, a single blank answer value) while the escaping makes it safe for
 * arbitrary user text such as notes containing a semicolon or a backslash.
 *
 * Decoding is deliberately defensive: persisted text is untrusted input, so malformed content
 * degrades to the elements that could be parsed instead of throwing and crashing a Room read.
 * Nothing here depends on Android, `org.json` or any serialization library, so the format is
 * unit-testable on the JVM.
 */
internal object StringCollectionCodec {

    private const val LIST_SEPARATOR = ';'
    private const val MAP_SEPARATOR = '='
    private const val MAX_ELEMENTS = 100_000

    fun encodeList(values: Collection<String>?): String {
        if (values.isNullOrEmpty()) return ""
        return buildString {
            append(values.size)
            values.forEach { value ->
                append(LIST_SEPARATOR)
                appendEscape(value)
            }
        }
    }

    fun decodeList(encoded: String?): List<String> {
        if (encoded.isNullOrEmpty()) return emptyList()
        val (count, body) = readCount(encoded) ?: return emptyList()
        if (count == 0) return emptyList()
        return splitBody(body, count).map { unescape(it) }
    }

    fun encodeSet(values: Collection<String>?): String =
        encodeList(values?.toCollection(LinkedHashSet()))

    fun decodeSet(encoded: String?): Set<String> = LinkedHashSet(decodeList(encoded))

    fun encodeMap(values: Map<String, String>?): String {
        if (values.isNullOrEmpty()) return ""
        return buildString {
            append(values.size)
            values.forEach { (key, value) ->
                append(LIST_SEPARATOR)
                appendEscape(key)
                append(MAP_SEPARATOR)
                appendEscape(value)
            }
        }
    }

    fun decodeMap(encoded: String?): Map<String, String> {
        if (encoded.isNullOrEmpty()) return emptyMap()
        val (count, body) = readCount(encoded) ?: return emptyMap()
        if (count == 0) return emptyMap()
        val result = LinkedHashMap<String, String>(count)
        splitBody(body, count).forEach { pair ->
            val separatorIndex = indexOfUnescaped(pair, MAP_SEPARATOR)
            if (separatorIndex >= 0) {
                val key = unescape(pair.substring(0, separatorIndex))
                val value = unescape(pair.substring(separatorIndex + 1))
                result[key] = value
            }
        }
        return result
    }

    private fun StringBuilder.appendEscape(raw: String) {
        raw.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                LIST_SEPARATOR -> append("\\;")
                MAP_SEPARATOR -> append("\\=")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
    }

    private fun unescape(escaped: String): String {
        if (!escaped.contains('\\')) return escaped
        val out = StringBuilder(escaped.length)
        var index = 0
        while (index < escaped.length) {
            val char = escaped[index]
            if (char == '\\' && index + 1 < escaped.length) {
                when (val next = escaped[index + 1]) {
                    '\\' -> out.append('\\')
                    ';' -> out.append(';')
                    '=' -> out.append('=')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    else -> out.append(next)
                }
                index += 2
            } else {
                out.append(char)
                index++
            }
        }
        return out.toString()
    }

    private fun readCount(encoded: String): Pair<Int, String>? {
        val separatorIndex = encoded.indexOf(LIST_SEPARATOR)
        if (separatorIndex <= 0) return null
        val count = encoded.substring(0, separatorIndex).toIntOrNull() ?: return null
        if (count < 0 || count > MAX_ELEMENTS) return null
        return count to encoded.substring(separatorIndex + 1)
    }

    /** Splits on unescaped separators and yields at most [max] elements. */
    private fun splitBody(body: String, max: Int): List<String> {
        val elements = ArrayList<String>(max)
        val current = StringBuilder()
        var index = 0
        while (index < body.length && elements.size < max) {
            val char = body[index]
            when {
                char == '\\' && index + 1 < body.length -> {
                    current.append(char).append(body[index + 1])
                    index += 2
                }
                char == LIST_SEPARATOR -> {
                    elements.add(current.toString())
                    current.setLength(0)
                    index++
                }
                else -> {
                    current.append(char)
                    index++
                }
            }
        }
        if (elements.size < max) {
            elements.add(current.toString())
        }
        return elements
    }

    private fun indexOfUnescaped(text: String, target: Char): Int {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\') {
                index += 2
                continue
            }
            if (char == target) return index
            index++
        }
        return -1
    }
}

package io.github.fanfeast.anonpdf.pdf

/**
 * Just enough JSON to read and write a recipe file.
 *
 * Values are plain Kotlin: maps, lists, strings, numbers as [Double], booleans
 * and null. A library would bring a compiler plugin and a dependency for what is
 * a hundred lines, and the files it handles are small and written by this app.
 */
internal object Json {

    fun write(value: Any?): String = StringBuilder().also { write(value, it, "") }.toString()

    private fun write(value: Any?, out: StringBuilder, indent: String) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is Int, is Long -> out.append(value)
            is Number -> {
                val d = value.toDouble()
                require(d.isFinite()) { "not a JSON number: $d" }
                if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) out.append(d.toLong()) else out.append(d)
            }
            is String -> string(value, out)
            is Map<*, *> -> {
                if (value.isEmpty()) { out.append("{}"); return }
                val inner = "$indent  "
                out.append("{\n")
                value.entries.forEachIndexed { i, (k, v) ->
                    out.append(inner)
                    string(k as String, out)
                    out.append(": ")
                    write(v, out, inner)
                    out.append(if (i < value.size - 1) ",\n" else "\n")
                }
                out.append(indent).append('}')
            }
            is List<*> -> {
                // Lists of plain values stay on one line, so a stroke of points or
                // a list of pages does not run to a screenful.
                if (value.none { it is Map<*, *> || it is List<*> && it.any { x -> x is Map<*, *> } }) {
                    out.append('[')
                    value.forEachIndexed { i, v -> if (i > 0) out.append(", "); write(v, out, indent) }
                    out.append(']')
                    return
                }
                val inner = "$indent  "
                out.append("[\n")
                value.forEachIndexed { i, v ->
                    out.append(inner)
                    write(v, out, inner)
                    out.append(if (i < value.size - 1) ",\n" else "\n")
                }
                out.append(indent).append(']')
            }
            else -> error("cannot write ${value::class.simpleName} as JSON")
        }
    }

    private fun string(s: String, out: StringBuilder) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    /** Throws [IllegalArgumentException] on anything that is not well-formed JSON. */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value(0)
        reader.skipSpace()
        require(reader.at == text.length) { "trailing text at ${reader.at}" }
        return value
    }

    private class Reader(val s: String) {
        var at = 0

        fun skipSpace() {
            while (at < s.length && s[at].isWhitespace()) at++
        }

        fun value(depth: Int): Any? {
            require(depth < 64) { "nested too deeply" }
            skipSpace()
            require(at < s.length) { "unexpected end" }
            return when (val c = s[at]) {
                '{' -> obj(depth)
                '[' -> list(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> {
                    require(c == '-' || c.isDigit()) { "unexpected '$c' at $at" }
                    number()
                }
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, at)) { "unexpected text at $at" }
            at += word.length
            return value
        }

        private fun number(): Double {
            val start = at
            while (at < s.length && (s[at].isDigit() || s[at] in "+-.eE")) at++
            return s.substring(start, at).toDoubleOrNull() ?: throw IllegalArgumentException("bad number at $start")
        }

        private fun string(): String {
            at++ // opening quote
            val out = StringBuilder()
            while (true) {
                require(at < s.length) { "unterminated string" }
                when (val c = s[at++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(at < s.length) { "unterminated string" }
                        when (val e = s[at++]) {
                            '"', '\\', '/' -> out.append(e)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'u' -> {
                                require(at + 4 <= s.length) { "bad escape" }
                                out.append(s.substring(at, at + 4).toIntOrNull(16)?.toChar()
                                    ?: throw IllegalArgumentException("bad escape"))
                                at += 4
                            }
                            else -> throw IllegalArgumentException("bad escape \\$e")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            at++
            val map = LinkedHashMap<String, Any?>()
            skipSpace()
            if (at < s.length && s[at] == '}') { at++; return map }
            while (true) {
                skipSpace()
                require(at < s.length && s[at] == '"') { "expected a key at $at" }
                val key = string()
                skipSpace()
                require(at < s.length && s[at] == ':') { "expected ':' at $at" }
                at++
                map[key] = value(depth + 1)
                skipSpace()
                require(at < s.length) { "unexpected end" }
                when (s[at++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw IllegalArgumentException("expected ',' or '}' at ${at - 1}")
                }
            }
        }

        private fun list(depth: Int): List<Any?> {
            at++
            val list = ArrayList<Any?>()
            skipSpace()
            if (at < s.length && s[at] == ']') { at++; return list }
            while (true) {
                list += value(depth + 1)
                skipSpace()
                require(at < s.length) { "unexpected end" }
                when (s[at++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw IllegalArgumentException("expected ',' or ']' at ${at - 1}")
                }
            }
        }
    }
}

package com.example.vehicare.data.local.converters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests of the persisted text format. These cover the properties that matter for data integrity:
 * lossless round trips, delimiter/escape safety, order preservation and defensive decoding of
 * malformed values (a corrupted row must never crash a read).
 */
class ConvertersTest {

    private val converters = Converters()

    // --- List<String> ---------------------------------------------------------------------------

    @Test
    fun `empty list round trips`() {
        val encoded = converters.fromStringList(emptyList())
        assertEquals("", encoded)
        assertEquals(emptyList<String>(), converters.toStringList(encoded))
    }

    @Test
    fun `null collection round trips as empty`() {
        assertEquals("", converters.fromStringList(null))
        assertEquals(emptyList<String>(), converters.toStringList(null))
        assertEquals(emptyMap<String, String>(), converters.toStringMap(null))
        assertEquals(emptySet<String>(), converters.toStringSet(null))
    }

    @Test
    fun `list round trips including a single blank element`() {
        assertEquals(listOf(""), converters.toStringList(converters.fromStringList(listOf(""))))
        assertEquals(listOf("", ""), converters.toStringList(converters.fromStringList(listOf("", ""))))
    }

    @Test
    fun `list preserves order and duplicates`() {
        val values = listOf("b", "a", "b", "c")
        val decoded = converters.toStringList(converters.fromStringList(values))
        assertEquals(values, decoded)
    }

    @Test
    fun `list values containing separators and escapes round trip`() {
        val values = listOf(
            "semi;colon",
            "back\\slash",
            "key=value",
            "line\nbreak",
            "carriage\rreturn",
            "\\\\;=",
            "unicode – ünïcødé 日本語"
        )
        val decoded = converters.toStringList(converters.fromStringList(values))
        assertEquals(values, decoded)
    }

    @Test
    fun `encoded list keeps the element count prefix`() {
        assertEquals("1;a", converters.fromStringList(listOf("a")))
        assertEquals("2;a;b", converters.fromStringList(listOf("a", "b")))
        assertEquals("1;semi\\;colon", converters.fromStringList(listOf("semi;colon")))
    }

    @Test
    fun `malformed list text decodes defensively`() {
        assertEquals(emptyList<String>(), converters.toStringList(""))
        assertEquals(emptyList<String>(), converters.toStringList("not-a-count;a;b"))
        assertEquals(emptyList<String>(), converters.toStringList("0;a;b"))
        assertEquals(emptyList<String>(), converters.toStringList("-3;a"))
        // More declared elements than present: only the parsable ones are returned.
        assertEquals(listOf("a"), converters.toStringList("3;a"))
    }

    // --- Set<String> ----------------------------------------------------------------------------

    @Test
    fun `set round trips and drops duplicates`() {
        val encoded = converters.fromStringSet(linkedSetOf("x", "y", "x"))
        assertEquals(setOf("x", "y"), converters.toStringSet(encoded))
    }

    @Test
    fun `set keeps insertion order so the first reported symptom stays stable`() {
        val ordered = linkedSetOf("slow_cranking", "dim_lights", "cranks_no_start")
        val decoded = converters.toStringSet(converters.fromStringSet(ordered))
        assertTrue(decoded is LinkedHashSet)
        assertEquals(listOf("slow_cranking", "dim_lights", "cranks_no_start"), decoded.toList())
    }

    // --- Map<String, String> --------------------------------------------------------------------

    @Test
    fun `empty map round trips`() {
        assertEquals("", converters.fromStringMap(emptyMap()))
        assertEquals(emptyMap<String, String>(), converters.toStringMap(""))
    }

    @Test
    fun `map round trips with stable key order including blank values`() {
        val values = linkedMapOf(
            "q_starting" to "slow_crank",
            "q_severity" to "3",
            "q_optional_blank" to "",
            "q_onset" to "within_week"
        )
        val decoded = converters.toStringMap(converters.fromStringMap(values))
        assertEquals(values, decoded)
        assertEquals(values.keys.toList(), decoded.keys.toList())
    }

    @Test
    fun `map keys and values containing separators round trip`() {
        val values = linkedMapOf(
            "key;with=separators" to "value;with=separators",
            "back\\slash" to "new\nline"
        )
        assertEquals(values, converters.toStringMap(converters.fromStringMap(values)))
    }

    @Test
    fun `malformed map text decodes defensively`() {
        assertEquals(emptyMap<String, String>(), converters.toStringMap("garbage"))
        assertEquals(emptyMap<String, String>(), converters.toStringMap("0;x=1"))
        // A pair without a separator is skipped instead of producing a bogus key.
        assertEquals(emptyMap<String, String>(), converters.toStringMap("1;no-separator"))
        assertEquals(mapOf("k" to "v"), converters.toStringMap("2;k=v;broken"))
    }
}

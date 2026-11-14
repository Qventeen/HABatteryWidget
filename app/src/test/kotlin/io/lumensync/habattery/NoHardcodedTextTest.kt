package io.lumensync.habattery

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class NoHardcodedTextTest {
    @Test
    fun noStringLiteralsInTextCalls() {
        val patterns = listOf(
            Regex("""\bText\(\s*(text\s*=\s*)?""""),
            Regex("""contentDescription\s*=\s*""""),
        )
        val hits = File("src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line ->
                if (patterns.any { it.containsMatchIn(line) }) "${f.path}:${i + 1}" else null } }
            .toList()
        assertEquals("Hard-coded UI text:\n" + hits.joinToString("\n"), emptyList<String>(), hits)
    }
}

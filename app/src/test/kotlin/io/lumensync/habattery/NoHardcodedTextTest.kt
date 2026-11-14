package io.lumensync.habattery

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoHardcodedTextTest {
    @Test
    fun noStringLiteralsInTextCalls() {
        val sources = File("src/main/kotlin")
        // A wrong working directory would make the scan find nothing and pass.
        assertTrue(sources.absolutePath, sources.isDirectory)
        val patterns = listOf(
            Regex("""\bText\(\s*(text\s*=\s*)?""""),
            Regex("""contentDescription\s*=\s*""""),
            Regex("""\b(label|hint|title)\s*=\s*""""),
            Regex("""\bsetContent(Title|Text)\(\s*""""),
        )
        val hits = sources.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line ->
                if (patterns.any { it.containsMatchIn(line) }) "${f.path}:${i + 1}" else null } }
            .toList()
        assertEquals("Hard-coded UI text:\n" + hits.joinToString("\n"), emptyList<String>(), hits)
    }
}

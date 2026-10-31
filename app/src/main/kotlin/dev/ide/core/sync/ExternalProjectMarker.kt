package dev.ide.core.sync

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Minimal on-disk marker used by the static Gradle compatibility importer. */
internal object ExternalProjectMarker {
    private const val MARKER = "external-project"
    private const val LEGACY = "imported-from-gradle"
    private fun markerFile(root: Path) = root.resolve(".platform").resolve(MARKER)
    fun write(root: Path, importerId: String, summary: String, notes: List<String> = emptyList()) {
        val file = markerFile(root)
        Files.createDirectories(file.parent)
        (listOf("importer=$importerId", summary) + notes.filter(String::isNotBlank))
            .joinToString("\n", postfix = "\n").let(file::writeText)
    }
    fun exists(root: Path): Boolean = Files.isRegularFile(markerFile(root)) || Files.isRegularFile(root.resolve(".platform").resolve(LEGACY))
    fun clear(root: Path) {
        runCatching { Files.deleteIfExists(markerFile(root)) }
        runCatching { Files.deleteIfExists(root.resolve(".platform").resolve(LEGACY)) }
    }
    @Suppress("unused")
    fun readSummary(root: Path): String? = runCatching { markerFile(root).readText().lineSequence().drop(1).firstOrNull() }.getOrNull()
}

package org.javacs.kt.classpath

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.BiPredicate
import kotlin.math.min
import org.javacs.kt.util.findCommandOnPath
import org.javacs.kt.util.tryResolving
import org.javacs.kt.LOG

/** Backup classpath that find Kotlin in the user's Maven/Gradle home or kotlinc's libraries folder. */
object BackupClassPathResolver : ClassPathResolver {
    override val resolverType: String = "Backup"
    override val classpath: ClassPathResult get() = ClassPathResult(findKotlinStdlib()?.let { setOf(it) }.orEmpty().map { ClassPathEntry(it, null) }.toSet())
}

fun findKotlinStdlib(): Path? =
    findLocalArtifact("org.jetbrains.kotlin", "kotlin-stdlib")
    ?: findKotlinCliCompilerLibrary("kotlin-stdlib")
    ?: findAlternativeLibraryLocation("kotlin-stdlib")

private fun findLocalArtifact(group: String, artifact: String) =
    tryResolving("$artifact using Maven") { tryFindingLocalArtifactUsing(group, artifact, findLocalArtifactDirUsingMaven(group, artifact)) }
    ?: tryResolving("$artifact using Gradle") { tryFindingLocalArtifactUsing(group, artifact, findLocalArtifactDirUsingGradle(group, artifact)) }

private fun tryFindingLocalArtifactUsing(@Suppress("UNUSED_PARAMETER") group: String, artifact: String, artifactDirResolution: LocalArtifactDirectoryResolution): Path? {
    val isCorrectArtifact = BiPredicate<Path, BasicFileAttributes> { file, _ ->
        val name = file.fileName.toString()
        when (artifactDirResolution.buildTool) {
            "Maven" -> {
                val version = file.parent.fileName.toString()
                val expected = "${artifact}-${version}.jar"
                name == expected
            }
            else -> name.startsWith(artifact) && ("-sources" !in name) && name.endsWith(".jar")
        }
    }
    val versions = Files.list(artifactDirResolution.artifactDir)
        .sorted(::compareVersions)
    LOG.info("Looking for $artifact in $artifactDirResolution")
    LOG.info("Versions found: ${versions.toList()}")
    return versions.findFirst()
        .orElse(null)
        ?.let { Files.find(artifactDirResolution.artifactDir, 3, isCorrectArtifact).findFirst().orElse(null) }
}

private data class LocalArtifactDirectoryResolution(val artifactDir: Path?, val buildTool: String)

/** Tries to find the Kotlin command line compiler's standard library. */
private fun findKotlinCliCompilerLibrary(name: String): Path? =
    findCommandOnPath("kotlinc")
        ?.toRealPath()
        ?.parent // bin
        ?.parent // libexec or "top-level" dir
        ?.let {
            // either in libexec or a top-level directory (that may contain libexec, or just a lib-directory directly)
            val possibleLibDir = it.resolve("lib")
            if (Files.exists(possibleLibDir)) {
                possibleLibDir
            } else {
                it.resolve("libexec").resolve("lib")
            }
        }
        ?.takeIf { Files.exists(it) }
        ?.let(Files::list)
        ?.filter { it.fileName.toString() == "$name.jar" }
        ?.findFirst()
        ?.orElse(null)


// alternative library locations like for snap
// (can probably just use elvis operator and multiple similar expressions for other install directories)
private fun findAlternativeLibraryLocation(name: String): Path? =
    Paths.get("/snap/kotlin/current/lib/${name}.jar").existsOrNull()

private fun Path.existsOrNull() =
    if (Files.exists(this)) this else null

private fun findLocalArtifactDirUsingMaven(group: String, artifact: String) =
    LocalArtifactDirectoryResolution(
        mavenRepository
            ?.resolve(group.replace('.', File.separatorChar))
            ?.resolve(artifact)
            ?.existsOrNull(), "Maven")

private fun findLocalArtifactDirUsingGradle(group: String, artifact: String) =
    LocalArtifactDirectoryResolution(gradleCaches.resolve(group)
        ?.resolve(artifact)
        ?.existsOrNull(), "Gradle")


private val gradleCaches by lazy {
    gradleHome.resolve("caches")
        .resolveStartingWith("modules")
        .resolveStartingWith("files")
}

private fun Path.resolveStartingWith(prefix: String): Path {
    return Files.list(this)
        .filter { Files.isDirectory(it) && it.fileName.toString().startsWith(prefix) }
        .findFirst()
        .orElseThrow { IllegalStateException("Directory starting with $prefix not found in $this") }
}

private fun compareVersions(left: Path, right: Path): Int {
    val leftVersion = extractVersion(left)
    val rightVersion = extractVersion(right)

    for (i in 0 until min(leftVersion.size, rightVersion.size)) {
        val leftRev = leftVersion[i].reversed()
        val rightRev = rightVersion[i].reversed()
        val compare = leftRev.compareTo(rightRev)
        if (compare != 0)
            return -compare
    }

    return -leftVersion.size.compareTo(rightVersion.size)
}
private fun extractVersion(artifactVersionDir: Path): List<String> {
    return artifactVersionDir.fileName.toString().split(".")
}

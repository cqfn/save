/**
 * Utility methods for common operations with file system using okio.
 */

@file:Suppress(
    "FILE_NAME_MATCH_CLASS",
    "MatchingDeclarationName",
    "TooManyFunctions")

package org.cqfn.save.core.files

import org.cqfn.save.core.config.OutputStreamType
import org.cqfn.save.core.logging.logError
import org.cqfn.save.core.utils.writeToStream

import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Timeout

/**
 * A simple okio [Sink] that writes it's input to stdout/stderr
 */
@Suppress("INLINE_CLASS_CAN_BE_USED")
class StdStreamsSink(private val outputType: OutputStreamType) : Sink {
    override fun close() = Unit

    override fun flush() = Unit

    override fun timeout(): Timeout = Timeout.NONE

    /**
     * Writes a UTF-8 representation of [source] to stdout
     */
    override fun write(source: Buffer, byteCount: Long) {
        val msg = source.readByteString(byteCount).utf8()
        writeToStream(msg, outputType)
    }
}

/**
 * Find all descendant files in the directory denoted by [this] [Path], that match [condition].
 * Files will appear in the returned list grouped by directories, while directories are visited in depth-first manner.
 *
 * @param condition a condition to match
 * @return a list of files, grouped by directory
 */
fun Path.findAllFilesMatching(condition: (Path) -> Boolean): List<List<Path>> = FileSystem.SYSTEM.list(this)
    .partition { FileSystem.SYSTEM.metadata(it).isDirectory }
    .let { (directories, files) ->
        val filesInCurrentDir = files.filter(condition).takeIf { it.isNotEmpty() }
        val resultFromNestedDirs = directories.flatMap {
            it.findAllFilesMatching(condition)
        }
        filesInCurrentDir?.let {
            listOf(it) + resultFromNestedDirs
        } ?: resultFromNestedDirs
    }

/**
 * @param condition a condition to match
 * @return a matching child file or null
 */
fun Path.findChildByOrNull(condition: (Path) -> Boolean): Path? {
    // Some top-level directories, like /tmp and /var in Linux and MacOS are actually a sticky directories
    // Although, in Linux all is ok, but `okio` can't check it in MacOS by `isDirectory`, so we use `!isRegularFile` instead
    require(!FileSystem.SYSTEM.metadata(this).isRegularFile)
    return FileSystem.SYSTEM.list(this).firstOrNull(condition)
}

/**
 * @return a [Sequence] of file parent directories
 */
fun Path.parents(): Sequence<Path> = generateSequence(parent) { it.parent }

/**
 * @param condition a condition to match
 */
fun Path.findAllParentsMatching(condition: (Path) -> Boolean) = parents().filter(condition)

/**
 * Create file in [this] [FileSystem], denoted by path [pathString]
 *
 * @param pathString path to a new file
 * @return [Path] denoting the created file
 */
fun FileSystem.createFile(pathString: String): Path = createFile(pathString.toPath())

/**
 * Create file in [this] [FileSystem], denoted by [Path] [path]
 *
 * @param path path to a new file
 * @return [path]
 */
fun FileSystem.createFile(path: Path): Path {
    sink(path).close()
    return path
}

/**
 * @param path a path to a file
 * @return list of strings from the file
 */
fun FileSystem.readLines(path: Path): List<String> = this.read(path) {
    generateSequence { readUtf8Line() }.toList()
}

/**
 * @param path a path to a file
 * @return string from the file
 */
fun FileSystem.readFile(path: Path): String = this.read(path) {
    this.readUtf8()
}

/**
 * Returns a sequence of underlying directories, filtering on every level by [directoryPredicate].
 * Example:
 * ```
 * directory1
 * |-- file1
 * |-- directory11
 * |   |-- file2
 * |   `-- directory21
 * |-- directory12
 * |-- directory13
 * |   |-- directory23
 * |   |   `-- file33
 * ```
 * If predicate returns `true` when `Path` is a directory which contains only other directories or is empty, then `directory11` will be filtered, as well as
 * `directory21`, which is empty, but is a descendant of already filtered out one, and `directory 23`, which is not empty.
 * So, the result would be `sequence(directory12, directory13)`
 *
 * @param withSelf whether [this] path should be included in the resulting sequence
 * @param directoryPredicate a predicate to match directories
 * @return a sequence of matching directories
 */
fun Path.findDescendantDirectoriesBy(withSelf: Boolean = false, directoryPredicate: (Path) -> Boolean): Sequence<Path> =
        sequence {
            if (withSelf) {
                yield(this@findDescendantDirectoriesBy)
            }
            FileSystem.SYSTEM.list(this@findDescendantDirectoriesBy)
                .asSequence()
                .filter { FileSystem.SYSTEM.metadata(it).isDirectory }
                .filter(directoryPredicate)
                .flatMap { it.findDescendantDirectoriesBy(withSelf = true, directoryPredicate) }
                .let { yieldAll(it) }
        }

/**
 * Wrapper function in case of incorrect usage:
 * rootPath should be hierarchically higher than [this], also it should be in the same
 * branch of the file tree
 *
 * @param rootPath root of the file tree, relates to which path will be created
 * @return string representation of relative path
 */
@Suppress("TooGenericExceptionCaught")
fun Path.createRelativePathToTheRoot(rootPath: Path) = try {
    createRelativePathFromThisToTheRoot(this, rootPath)
} catch (ex: NullPointerException) {
    logError("Incorrect usage of `createRelativePathToTheRoot`: rootPath should be hierarchically higher than [this], " +
            "also it should be in the same branch of the file tree")
    throw ex
}

/**
 * @return if provided path is file then return parent directory, otherwise return itself
 */
fun Path.getCurrentDirectory() = if (FileSystem.SYSTEM.metadata(this).isRegularFile) {
    this.parent!!
} else {
    this
}

/**
 * Create relative path from the current path to the root
 *
 * @param currentPath current path
 * @param rootPath root of the file tree, relates to which path will be created
 * @return string representation of relative path
 */
private fun createRelativePathFromThisToTheRoot(currentPath: Path, rootPath: Path): String {
    val rootDirectory = rootPath.getCurrentDirectory()
    var parentDirectory = currentPath.parent!!

    // Files located at the same directory, no need additional operations
    if (rootDirectory == parentDirectory) {
        return ""
    }
    // Goes through all intermediate dirs and construct relative path
    var relativePath = ""
    while (parentDirectory != rootDirectory) {
        relativePath = parentDirectory.name + Path.DIRECTORY_SEPARATOR + relativePath
        parentDirectory = parentDirectory.parent!!
    }
    return relativePath
}

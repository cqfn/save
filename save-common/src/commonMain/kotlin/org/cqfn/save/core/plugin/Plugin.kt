package org.cqfn.save.core.plugin

import org.cqfn.save.core.config.TestConfig
import org.cqfn.save.core.config.isSaveTomlConfig
import org.cqfn.save.core.files.createRelativePathToTheRoot
import org.cqfn.save.core.files.findDescendantDirectoriesBy
import org.cqfn.save.core.logging.logDebug
import org.cqfn.save.core.result.TestResult
import org.cqfn.save.core.utils.ProcessBuilder

import okio.FileSystem
import okio.Path

/**
 * Plugin that can be injected into SAVE during execution. Plugins accept contents of configuration file and then perform some work.
 * @property testConfig
 * @property testFiles a list of files (test resources or save.toml configs)
 * @property useInternalRedirections whether to redirect stdout/stderr for internal purposes
 */
abstract class Plugin(
    open val testConfig: TestConfig,
    protected val testFiles: List<String>,
    private val useInternalRedirections: Boolean) {
    /**
     * Singleton, that describes the current file system
     */
    protected val fs = FileSystem.SYSTEM

    /**
     * Instance that is capable of executing processes
     */
    val pb = ProcessBuilder(useInternalRedirections)

    /**
     * Perform plugin's work.
     *
     * @return a sequence of [TestResult]s for each group of test resources
     */
    fun execute(): Sequence<TestResult> {
        clean()
        // todo: pass individual groups of files to handleFiles? Or it will play bad with batch mode?
        val testFilesSequence = discoverTestFiles(testConfig.directory)
        return if (testFilesSequence.any()) {
            logDebug("Discovered the following test resources: ${testFilesSequence.toList()}")
            handleFiles(testFilesSequence)
        } else {
            emptySequence()
        }
    }

    /**
     * Perform plugin's work on a set of files.
     *
     * @param files a sequence of file groups, corresponding to tests.
     * @return a sequence of [TestResult]s for each group of test resources
     */
    abstract fun handleFiles(files: Sequence<List<Path>>): Sequence<TestResult>

    /**
     * Discover groups of resource files which will be used to run tests, applying additional filtering
     * for execution of individual tests.
     *
     * @param root root [Path], from where discovering should be started
     * @return a sequence of files, grouped by test
     */
    fun discoverTestFiles(root: Path): Sequence<List<Path>> {
        val rawTestFiles = rawDiscoverTestFiles(root.resourceDirectories())
        return if (testFiles.isNotEmpty()) {
            rawTestFiles.filter { resourcesGroup ->
                // test can be specified by the name of one of it's files
                resourcesGroup.any { path ->
                    testFiles.any { it in path.toString() }
                }
            }
        } else {
            rawTestFiles
        }
    }

    /**
     * Discover groups of resource files which will be used to run tests.
     *
     * @param resourceDirectories a sequence of [Path]s, which contain this plugin's resources
     * @return a sequence of files, grouped by test
     */
    abstract fun rawDiscoverTestFiles(resourceDirectories: Sequence<Path>): Sequence<List<Path>>

    /**
     * Method for cleaning up a directory.
     */
    abstract fun cleanupTempDir()

    /**
     * Method for call cleanupTempDir().
     *
     * @throws TempDirException when deleting temp dir
     */
    @Suppress(
        "TooGenericExceptionCaught",
        "SwallowedException"
    )
    private fun clean() {
        try {
            cleanupTempDir()
        } catch (e: Exception) {
            throw TempDirException("Could not delete temp dir, cause: ${e.message}")
        }
    }

    /**
     * Method for creating temp dir.
     *
     * @param tmpDir
     * @throws TempDirException when creating temp dir
     */
    @Suppress(
        "TooGenericExceptionCaught",
        "SwallowedException"
    )
    protected fun createTempDir(tmpDir: Path) {
        try {
            if (!fs.exists(tmpDir)) {
                fs.createDirectories(tmpDir)
            }
        } catch (e: Exception) {
            throw TempDirException("Could not create temp dir, cause: ${e.message}")
        }
    }

    /**
     *  Construct path for copy of test file, over which the plugins will be working on
     *
     *  @param dirName name of the tmp subdirectory
     *  @param path original path of test file
     *  @return path for copy of test file
     */
    protected fun constructPathForCopyOfTestFile(dirName: String, path: Path): Path {
        val tmpDir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / dirName)
        val relativePath = path.createRelativePathToTheRoot(testConfig.getRootConfig().location)
        return tmpDir / relativePath / path.name
    }

    /**
     * Returns a sequence of directories, where resources for this plugin may be located.
     * This takes into account, that if underlying directory contains it's own SAVE config,
     * then this plugin shouldn't touch these resources; it should be done by plugins from that config.
     *
     * @param fs a [FileSystem] which is used to traverse the directory hierarchy
     * @return a sequence of directories possibly containing this plugin's test resources
     */
    fun Path.resourceDirectories(fs: FileSystem = FileSystem.SYSTEM): Sequence<Path> = findDescendantDirectoriesBy(true) { file ->
        // this matches directories which contain their own SAVE config
        fs.metadata(file).isRegularFile || fs.list(file).none { it.isSaveTomlConfig() }
    }
}

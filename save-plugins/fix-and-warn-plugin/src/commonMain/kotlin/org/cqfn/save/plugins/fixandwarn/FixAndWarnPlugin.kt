package org.cqfn.save.plugins.fixandwarn

import okio.FileSystem
import okio.Path
import org.cqfn.save.core.config.TestConfig
import org.cqfn.save.core.config.TestConfigSections
import org.cqfn.save.core.files.readLines
import org.cqfn.save.core.logging.logDebug
import org.cqfn.save.core.plugin.GeneralConfig
import org.cqfn.save.core.plugin.Plugin
import org.cqfn.save.core.result.TestResult
import org.cqfn.save.plugin.warn.WarnPlugin

import org.cqfn.save.plugins.fix.FixPlugin

class FixAndWarnPlugin(
    testConfig: TestConfig,
    testFiles: List<String>,
    useInternalRedirections: Boolean = true) : Plugin(
    testConfig,
    testFiles,
    useInternalRedirections) {

    private val fixPluginConfig = testConfig.pluginConfigs.filterIsInstance<FixAndWarnPluginConfig>().single().fixPluginConfig

    private val warnPluginConfig = testConfig.pluginConfigs.filterIsInstance<FixAndWarnPluginConfig>().single().warnPluginConfig

    private val fixPlugin = FixPlugin(
        createTestConfigForPlugins(TestConfigSections.FIX),
        testFiles
    )
    private val warnPlugin = WarnPlugin(
        createTestConfigForPlugins(TestConfigSections.WARN),
        testFiles
    )


    /**
     * Create TestConfig same as current, but with corresponding plugin configs list for nested [fix] and [warn] sections
     *
     * @param type type of nested section
     * @return TestConfig for corresponding section
     */
    private fun createTestConfigForPlugins(type: TestConfigSections): TestConfig {
        return TestConfig(
            testConfig.location,
            testConfig.parentConfig,
            mutableListOf(
                testConfig.pluginConfigs.filterIsInstance<GeneralConfig>().single(),
                if (type == TestConfigSections.FIX) {
                    fixPluginConfig
                } else {
                    warnPluginConfig
                }
            )
        )
    }

    /**
     * Filter test resources
     *
     * @param suffix regex pattern of test resource
     * @param match whether to keep elements which matches current pattern or to keep elements, which is not
     * @return filtered list of files
     */
    private fun Sequence<List<Path>>.filterTestResources(suffix: Regex, match: Boolean): List<Path> {
        val filteredFiles = mutableListOf<Path>()
        this.forEach { resources ->
            filteredFiles.add(resources.single { path ->
                if (match) {
                    suffix.matchEntire(path.toString()) != null
                } else {
                    suffix.matchEntire(path.toString()) == null
                }
            })
        }
        return filteredFiles
    }


    override fun handleFiles(files: Sequence<List<Path>>): Sequence<TestResult> {
        testConfig.validateAndSetDefaults()

        // Test files for warn plugin could be obtained by filtering of `fix` resources: excluding `expected` files
        val testFilePattern = warnPluginConfig.resourceNamePattern
        val warnTestFiles = files.filterTestResources(testFilePattern, match = true)

        // Warn plugin should process the files, which were fixed by fix plugin
        // We can get the paths of fixed files from warn test files (since they are the same for fix plugin),
        // and then just add relative path from FixPlugin tmp dir
        val testFilesAfterFix = mutableListOf<List<Path>>()
        warnTestFiles.forEach { path ->
            // TODO change location from hardcoded FixPlugin::simpleName after https://github.com/cqfn/save/issues/156
            testFilesAfterFix.add(listOf(constructPathForCopyOfTestFile(FixPlugin::class.simpleName!!, path)))
        }

        logDebug("FixPlugin test resources: ${files.toList()}")
        logDebug("WarnPlugin test resources: ${testFilesAfterFix.toList()}")

        // Remove (in place) warnings from test files before fix plugin execution
        val expectedFiles = files.filterTestResources(testFilePattern, match = false)

        // TODO Don't hold all original data in memory, it will be enough to hold just warning and number of line of this warning
        val filesAndTheirOriginalDataMap = removeWarningsFromExpectedFiles(expectedFiles)

        val fixTestResults = fixPlugin.handleFiles(files)

        // Fill back original data with warnings
        filesAndTheirOriginalDataMap.forEach { (filePath, originalData) ->
            fs.write(filePath) {
                originalData.forEach {
                    write((it + "\n").encodeToByteArray())
                }
            }
        }

        // TODO: If we receive just one command for execution, then warn plugin should look at the fix plugin output
        //  for warnings, and not execute command one more time.
        //  Current approach works too, but in this case we have extra actions, which is not good.
        //  For the proper work it should be produced refactoring of warn plugin https://github.com/cqfn/save/issues/164,
        //  after which methods of warning comparison will be separated from the common logic
        val warnTestResults = warnPlugin.handleFiles(testFilesAfterFix.asSequence())
        return fixTestResults + warnTestResults
    }

    override fun rawDiscoverTestFiles(resourceDirectories: Sequence<Path>): Sequence<List<Path>> {
        // Test files for fix and warn plugin should be the same, so this will be enough
        return fixPlugin.rawDiscoverTestFiles(resourceDirectories)
    }

    /**
     * Remove warnings from the given files, which satisfy pattern from [warn] plugin and save original data of file
     *
     * @files files to be modified
     * @return map of files and theirs original data
     */
    private fun removeWarningsFromExpectedFiles(files: List<Path>): MutableMap<Path, List<String>> {
        val originalData: MutableMap<Path, List<String>> = mutableMapOf()
        files.forEach { filePath ->
            val originalFile = fs.readLines(filePath)
            originalData.put(filePath, originalFile)
            val fileWithoutWarnings = originalFile.filter { warnPluginConfig.warningsInputPattern!!.find(it)?.groups == null }
            fs.write(filePath) {
                fileWithoutWarnings.forEach {
                    write((it + "\n").encodeToByteArray())
                }
            }
        }
        return originalData
    }

    override fun cleanupTempDir() {
        val tmpDir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / FixAndWarnPlugin::class.simpleName!!)
        if (fs.exists(tmpDir)) {
            fs.deleteRecursively(tmpDir)
        }
    }
}
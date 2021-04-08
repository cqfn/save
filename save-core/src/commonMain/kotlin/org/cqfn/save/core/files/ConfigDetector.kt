package org.cqfn.save.core.files

import org.cqfn.save.core.config.TestSuiteConfig
import org.cqfn.save.core.config.isSaveTomlConfig
import org.cqfn.save.core.logging.logDebug
import org.cqfn.save.core.logging.logError

import okio.FileSystem
import okio.Path

/**
 * A class that is capable of discovering config files hierarchy.
 */
class ConfigDetector {
    /**
     * Try to create SAVE config file from [file].
     *
     * @param file a [Path] from which SAVE config file should be built.
     * @return [TestSuiteConfig] or null if no suitable config file has been found.
     */
    fun configFromFile(file: Path): TestSuiteConfig? = discoverConfigWithParents(file)
        ?.also { config ->
            // fill children for parent configs
            config.parentConfigs(wihSelf = true).toList().reversed()
                .zipWithNext().forEach { (parent, child) ->
                    parent.childConfigs.add(child)
                }
            // discover all descendant configs of [config]
            val locationsFlattened = config.location.parent!!.findAllFilesMatching { it.isSaveTomlConfig() }.flatten()
            val configs = mutableListOf(config)
            locationsFlattened
                .drop(1)  // because [config] will be discovered too
                .forEachIndexed { index, path ->
                    val parentConfig = configs.find { discoveredConfig ->
                        discoveredConfig.location ==
                                locationsFlattened.take(index + 1).reversed().find { it.parent in path.parents() }!!
                    }!!
                    configs.add(
                        TestSuiteConfig(
                            "todo: read from file",
                            "todo: read from file",
                            path,
                            parentConfig,
                        ).also {
                            logDebug("Found config file at $path, adding as a child for ${parentConfig.location}")
                            it.parentConfig!!.childConfigs.add(it)
                        }
                    )
                }
        }
        ?: run {
            logError("Config file was not found in $file")
            null
        }

    private fun discoverConfigWithParents(file: Path): TestSuiteConfig? = when {
        // if provided file is a directory, try to find save.toml inside it
        FileSystem.SYSTEM.metadata(file).isDirectory -> file
            .findChildByOrNull { it.isSaveTomlConfig() }
            ?.let { discoverConfigWithParents(it) }
        // if provided file is an individual test file, we search a config file in this and parent directories
        file.name.matches(testResourceFilePattern) -> file.parents()
            .mapNotNull { dir ->
                dir.findChildByOrNull { it.isSaveTomlConfig() }
            }
            .firstOrNull()
            ?.let { discoverConfigWithParents(it) }
        // if provided file is save.toml, create config from it
        file.isSaveTomlConfig() -> testSuiteConfigFromFile(file)
        else -> null
    }

    private fun testSuiteConfigFromFile(file: Path): TestSuiteConfig {
        val parentConfig = file.parents()
            .drop(1)  // because immediate parent already contains [this] config
            .mapNotNull { parentDir ->
                parentDir.findChildByOrNull {
                    it.isSaveTomlConfig()
                }
            }
            .firstOrNull()
            ?.let { testSuiteConfigFromFile(it) }
        return TestSuiteConfig(
            "todo: read from file",
            "todo: read from file",
            file,
            parentConfig
        )
            .also {
                logDebug("Discovered config file at $file")
            }
    }
}
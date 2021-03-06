@file:UseSerializers(RegexSerializer::class)

package org.cqfn.save.plugins.fix

import org.cqfn.save.core.config.TestConfigSections
import org.cqfn.save.core.plugin.PluginConfig
import org.cqfn.save.core.utils.RegexSerializer

import okio.Path
import okio.Path.Companion.toPath

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers

/**
 * Some fields by default are null, instead of some natural value, because of the fact, that in stage of merging
 * of nested configs, we can't detect whether the value are passed by user, or taken from default.
 * The logic of the default value processing will be provided in stage of validation
 *
 * @property execFlags a command that will be executed to mutate test file contents
 * @property batchSize it controls how many files execCmd will process at a time.
 * @property resourceNameTestSuffix suffix name of the test file.
 * @property resourceNameExpectedSuffix suffix name of the expected file.
 * @property batchSeparator
 */
@Serializable
data class FixPluginConfig(
    val execFlags: String? = null,
    val batchSize: Int? = null,
    val batchSeparator: String? = null,
    val resourceNameTestSuffix: String? = null,
    val resourceNameExpectedSuffix: String? = null,
) : PluginConfig {
    override val type = TestConfigSections.FIX

    @Transient
    override var configLocation: Path = "undefined_toml_location".toPath()

    /**
     *  @property resourceNameTest
     */
    val resourceNameTest: String = resourceNameTestSuffix ?: "Test"

    /**
     *  @property resourceNameExpected
     */
    val resourceNameExpected: String = resourceNameExpectedSuffix ?: "Expected"

    /**
     *  @property resourceNamePattern regex for the name of the test files.
     */
    val resourceNamePattern: Regex = Regex("""(.+)($resourceNameExpected|$resourceNameTest)\.[\w\d]+""")

    override fun mergeWith(otherConfig: PluginConfig): PluginConfig {
        val other = otherConfig as FixPluginConfig
        return FixPluginConfig(
            this.execFlags ?: other.execFlags,
            this.batchSize ?: other.batchSize,
            this.batchSeparator ?: other.batchSeparator,
            this.resourceNameTestSuffix ?: other.resourceNameTestSuffix,
            this.resourceNameExpectedSuffix ?: other.resourceNameExpectedSuffix,
        )
    }

    override fun validateAndSetDefaults() = FixPluginConfig(
        execFlags ?: "",
        batchSize ?: 1,
        batchSeparator ?: ", ",
        resourceNameTest,
        resourceNameExpected
    )
}

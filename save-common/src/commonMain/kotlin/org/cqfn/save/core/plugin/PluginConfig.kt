/**
 * Configuration classes for SAVE plugins.
 */

package org.cqfn.save.core.plugin

import org.cqfn.save.core.config.TestConfigSections

import kotlinx.serialization.Serializable

/**
 * Core interface for plugin configuration (like warnPlugin/fixPluin/e.t.c)
 */
@Suppress("INLINE_CLASS_CAN_BE_USED")
interface PluginConfig {
    /**
     * type of the config (usually related to the class: WARN/FIX/e.t.c)
     */
    val type: TestConfigSections

    /**
     * @param otherConfig - 'this' will be merged with 'other'
     * @return merged config
     */
    fun mergeWith(otherConfig: PluginConfig): PluginConfig

    /**
     * Method, which validates config and provides the default values for fields, if possible
     *
     * @return new validated instance obtained from [this]
     */
    fun validateAndSetDefaults(): PluginConfig
}

/**
 * General configuration for test suite.
 * Some fields by default are null, instead of some natural value, because of the fact, that in stage of merging
 * of nested configs, we can't detect whether the value are passed by user, or taken from default.
 * The logic of the default value processing will be provided in stage of validation
 *
 * @property tags FixMe: after ktoml will support lists we should change it
 * @property description
 * @property suiteName
 * @property excludedTests FixMe: after ktoml will support lists we should change it
 * @property includedTests FixMe: after ktoml will support lists we should change it
 * @property ignoreSaveComments if true then ignore warning comments
 */
@Serializable
data class GeneralConfig(
    val tags: String? = null,
    val description: String? = null,
    val suiteName: String? = null,
    val excludedTests: String? = null,
    val includedTests: String? = null,
    val ignoreSaveComments: Boolean? = null
) : PluginConfig {
    override val type = TestConfigSections.GENERAL

    override fun mergeWith(otherConfig: PluginConfig): PluginConfig {
        val other = otherConfig as GeneralConfig
        val mergedTag = other.tags?.let {
            this.tags?.let {
                val parentTags = other.tags.split(", ")
                val childTags = this.tags.split(", ")
                parentTags.union(childTags).joinToString(", ")
            } ?: other.tags
        } ?: this.tags

        return GeneralConfig(
            mergedTag,
            this.description ?: other.description,
            this.suiteName ?: other.suiteName,
            this.excludedTests ?: other.excludedTests,
            this.includedTests ?: other.includedTests,
            this.ignoreSaveComments ?: other.ignoreSaveComments
        )
    }

    override fun validateAndSetDefaults(): GeneralConfig {
        requireNotNull(tags) {
            "Error: Couldn't found `tags` in [general] section. Please provide it in this, " +
                    "or at least in one of the parent configs"
        }
        requireNotNull(description) {
            "Error: Couldn't found `description` in [general] section. Please provide it in this, " +
                    "or at least in one of the parent configs"
        }
        requireNotNull(suiteName) {
            "Error: Couldn't found `suiteName` in [general] section. Please provide it in this, " +
                    "or at least in one of the parent configs"
        }
        val newExcludedTests = excludedTests ?: ""
        val newIncludedTests = includedTests ?: ""
        val newIgnoreSaveComments = ignoreSaveComments ?: false
        return GeneralConfig(
            tags,
            description,
            suiteName,
            newExcludedTests,
            newIncludedTests,
            newIgnoreSaveComments
        )
    }
}

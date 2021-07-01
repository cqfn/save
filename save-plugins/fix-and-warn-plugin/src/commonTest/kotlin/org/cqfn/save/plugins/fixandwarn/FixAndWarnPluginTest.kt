package org.cqfn.save.plugins.fixandwarn

import io.github.petertrr.diffutils.diff
import okio.FileSystem
import org.cqfn.save.core.config.TestConfig
import org.cqfn.save.core.files.createFile
import org.cqfn.save.core.files.readLines
import org.cqfn.save.core.plugin.GeneralConfig
import org.cqfn.save.core.result.DebugInfo
import org.cqfn.save.core.result.Pass
import org.cqfn.save.core.result.TestResult
import org.cqfn.save.core.utils.isCurrentOsWindows
import org.cqfn.save.plugin.warn.WarnPlugin
import org.cqfn.save.plugin.warn.WarnPluginConfig
import org.cqfn.save.plugins.fix.FixPlugin
import org.cqfn.save.plugins.fix.FixPluginConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class FixAndWarnPluginTest {
    private val fs = FileSystem.SYSTEM
    private val tmpDir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / FixAndWarnPluginTest::class.simpleName!!)

    @BeforeTest
    fun setUp() {
        if (fs.exists(tmpDir)) {
            fs.deleteRecursively(tmpDir)
        }
        fs.createDirectory(tmpDir)
    }

    // FixME: make it work
    @Test
    fun `base test`() {
        val config = fs.createFile(tmpDir / "save.toml")
        fs.write(fs.createFile(tmpDir / "resource")) {
            write(
                """
                |Test1Test.java:4:6: Class name should be in PascalCase
                """.trimMargin().encodeToByteArray()
            )
        }
        val testFile = fs.createFile(tmpDir / "Test1Test.java")
        fs.write(testFile) {
            write("Original file".encodeToByteArray())
        }
        val expectedFile = fs.createFile(tmpDir / "Test1Expected.java")
        fs.write(expectedFile) {
            write("Expected file".encodeToByteArray())
        }

        val text =
            """
                package org.cqfn.save.example
                
                // ;warn:4:6: Class name should be in PascalCase
                class example {
                    int foo = 42;
                }
            """.trimIndent()

        fs.write(fs.createFile(tmpDir / "Test1Test.java")) {
            write(text.encodeToByteArray())
        }

        val catCmd = if (isCurrentOsWindows()) "type" else "cat"
        val warnExecutionCmd = "$catCmd ${tmpDir / "resource"} && set stub="

        val diskWithTmpDir = if (isCurrentOsWindows()) "${tmpDir.toString().substringBefore("\\").lowercase()} && " else ""
        val fixExecutionCmd = "${diskWithTmpDir}cd $tmpDir && echo Expected file >"

        val results = FixAndWarnPlugin(
            TestConfig(config,
            null,
            mutableListOf(
                FixPluginConfig(fixExecutionCmd),
                WarnPluginConfig(warnExecutionCmd,
                    Regex("// ;warn:(\\d+):(\\d+): (.*)"),
                    Regex("(.+):(\\d+):(\\d+): (.+)"),
                    true, true, 1, 1, 2, 3, 1, 2, 3, 4
                ),
                GeneralConfig("", "", "", "")
            )),
            testFiles = emptyList(),
            useInternalRedirections = false
        ).execute()

        println(results)

        assertEquals(1, results.count(), "Size of results should equal number of pairs")
        assertEquals(
            TestResult(listOf(expectedFile, testFile), Pass(null),
            DebugInfo(results.single().debugInfo?.stdout, results.single().debugInfo?.stderr, null)
            ), results.single())
        val tmpDir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / FixAndWarnPlugin::class.simpleName!!)
        assertTrue("Files should be identical") {
            diff(fs.readLines(tmpDir / "Test1Test.java"), fs.readLines(expectedFile))
                .deltas.isEmpty()
        }
        fs.delete(tmpDir / "resource")
    }

    @AfterTest
    fun tearDown() {
        fs.deleteRecursively(tmpDir)
    }
}
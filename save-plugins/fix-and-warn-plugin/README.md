## Save "fix and warn" plugin
Plugin that runs provided executable on the initial file with a test source code
and combines two actions during one execution: 
[fix](save-plugins/fix-plugin/README.md) and [warn](save-plugins/warn-plugin/README.md).\
Plugin fixes test file, warns if something couldn't be auto-corrected after fix
and compares output with expected output during one execution.

Plugin config should contain subsections `[fix]` and `[warn]`, which should be configured,
like it described in corresponding README files, additionally requiring the following:

* Expected warnings should be specified in expected files
* Test resources should have the same postfixes in `[fix]` and `[warn]` sections.\
  By the default for test file it is `Test`, for the file with expected result - it is `Expected`.
* If your application use the same flag for fix and warn actions, this flag should be provided explicitly in both subsections (for now)

## Configuration
Follow the instructions for `[fix]` and `[warn]` plugins and just add
their configuration as a subsections of `["fix and warn"]` plugin.
```toml
[general]
execCmd="./ktlint -R diktat-0.4.2.jar"
description = "My suite description"
suiteName = "DocsCheck"

["fix and warn"]
    [fix]
        execFlags="-F"
        testFilePattern="*Test.kt"
        expectedFilePattern="*Expected.kt"
        batchSize = 1
        batchSeparator = ", "
        resourceNameTestSuffix = "Test"
        resourceNameExpectedSuffix = "Expected"
    [warn]
        execFlags = "--build-upon-default-config -i"
        warningsInputPattern = "// ;warn:(\\d+):(\\d+): (.*)"
        warningsOutputPattern = "\\w+ - (\\d+)/(\\d+) - (.*)$"
        testNameSuffix = "Test"
```
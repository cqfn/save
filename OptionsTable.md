Most (except for `-h` and `-prop`) of the options below can be passed to a SAVE via `save.properties` file

| Short name | Long name  | Description   | Default |
|------------|------------|---------------|---------------|
| h | help | Usage info | - |
| parallel | parallel-mode | Whether to enable parallel mode | false |
| t | threads | Number of threads | 1 |
| d | debug | Turn on debug logging | false |
| q | quiet | Do not log anything | false |
| - | report-type | Type of generated report with execution results | JSON |
| b | baseline | Path to the file with baseline data | - |
| e | exclude-suites | Test suites, which won't be checked | - |
| i | include-suites | Test suites, only which ones will be checked | - |
| l | language | Language that you are developing analyzer for | UNDEFINED |
| - | test-root-path | Path to directory with root save.toml and save.properties | - |
| out | result-output | Data output stream | STDOUT |
| - | config-inheritance | Whether configuration files should inherit configurations from the previous level of directories | true |
| - | report-dir | Path to directory, where to store output (when `resultOutput` is set to `FILE`) | save-reports |
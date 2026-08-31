param(
    [string]$GradleCache = (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$compilerPaths = @(
    (Join-Path $GradleCache 'org.jetbrains.kotlin/kotlin-compiler-embeddable/2.0.0'),
    (Join-Path $GradleCache 'org.jetbrains.kotlin/kotlin-stdlib/2.0.21'),
    (Join-Path $GradleCache 'org.jetbrains.kotlin/kotlin-reflect/2.0.21'),
    (Join-Path $GradleCache 'org.jetbrains.intellij.deps/trove4j'),
    (Join-Path $GradleCache 'org.jetbrains/annotations')
)
$compilerClassPath = ($compilerPaths | ForEach-Object {
    Get-ChildItem -LiteralPath $_ -Recurse -Filter *.jar
} | Select-Object -ExpandProperty FullName) -join ';'
$outputDirectory = Join-Path $projectRoot '.local-tools'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sourcePaths = @(
    'app/src/main/java/com/indirgitsin/app/data/model/Models.kt',
    'app/src/main/java/com/indirgitsin/app/data/model/StreamSelector.kt',
    'app/src/main/java/com/indirgitsin/app/data/downloader/ContentRange.kt',
    'app/src/main/java/com/indirgitsin/app/data/downloader/TransferProgress.kt',
    'app/src/main/java/com/indirgitsin/app/util/VersionComparator.kt',
    'app/src/main/java/com/indirgitsin/app/util/YoutubeLinkHelper.kt',
    'app/src/test/java/com/indirgitsin/app/CoreRegressionChecks.kt'
) | ForEach-Object { Join-Path $projectRoot $_ }
$outputJar = Join-Path $outputDirectory 'core-checks.jar'
& java -cp $compilerClassPath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $compilerClassPath -jvm-target 17 -d $outputJar @sourcePaths
if ($LASTEXITCODE -ne 0) { throw 'Kotlin compilation failed.' }
& java -cp "$outputJar;$compilerClassPath" com.indirgitsin.app.CoreRegressionChecks
if ($LASTEXITCODE -ne 0) { throw 'Regression checks failed.' }

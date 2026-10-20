# Builds the portable Windows download: a zip with AnonPDF.exe inside.
#
#   pwsh desktop/packaging/windows/build-zip.ps1 -Version <version> -Out <dir>
#
# Expects `./gradlew :desktop:createReleaseDistributable` to have run. Nothing
# is installed: unzip anywhere, run AnonPDF.exe, delete the folder to remove it.
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$Out
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "../../..")
$dist = Join-Path $root "desktop/build/compose/binaries/main-release/app/AnonPDF"
if (-not (Test-Path (Join-Path $dist "AnonPDF.exe"))) { throw "No release distributable at $dist" }

$work = Join-Path $root "desktop/build/windows-zip"
$stage = Join-Path $work "AnonPDF-$Version"
if (Test-Path $work) { Remove-Item -Recurse -Force $work }
New-Item -ItemType Directory -Force -Path $stage, $Out | Out-Null
Copy-Item -Recurse (Join-Path $dist "*") $stage
@"
AnonPDF $Version for Windows

Run AnonPDF.exe to start. Nothing to install: delete this folder and it is
gone. Your documents never leave this computer.

Windows may say "Windows protected your PC" because this build is not signed
with a paid certificate. Choose "More info", then "Run anyway".
"@ | Set-Content -Encoding UTF8 (Join-Path $stage "README.txt")

$zip = Join-Path (Resolve-Path $Out) "AnonPDF-$Version-windows-x64.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path $stage -DestinationPath $zip
Write-Output $zip

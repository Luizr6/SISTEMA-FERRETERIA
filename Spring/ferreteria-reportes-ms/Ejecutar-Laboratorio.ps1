param([string]$Maven = 'mvn')
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & $Maven test
    if ($LASTEXITCODE -ne 0) { throw 'Las pruebas fallaron. Revisar target/surefire-reports.' }
} finally {
    Pop-Location
}

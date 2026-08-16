[CmdletBinding()]
param(
    [switch]$ResetCredential
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $projectRoot 'frontend'
$backendDir = Join-Path $projectRoot 'backend'
$localStateDir = Join-Path $projectRoot '.local\deploy'
$credentialFile = Join-Path $localStateDir 'production-credential.xml'
$pythonScript = Join-Path $PSScriptRoot 'deploy-prod.py'
$server = '146.56.204.72'
$username = 'Administrator'

function Write-Step([string]$message) {
    Write-Host "`n==> $message" -ForegroundColor Cyan
}

function Invoke-Checked([string]$command, [string[]]$arguments, [string]$workingDirectory) {
    Push-Location $workingDirectory
    try {
        & $command @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$command failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $localStateDir)) {
    New-Item -ItemType Directory -Path $localStateDir -Force | Out-Null
}

if ($ResetCredential -and (Test-Path -LiteralPath $credentialFile)) {
    Remove-Item -LiteralPath $credentialFile -Force
}

if (-not (Test-Path -LiteralPath $credentialFile)) {
    Write-Host 'First deployment: save the server credential. It is encrypted for the current Windows account under .local.' -ForegroundColor Yellow
    $credential = Get-Credential -UserName $username -Message "Enter the password for $server"
    if ($null -eq $credential) {
        throw 'No server credential was entered. Deployment cancelled.'
    }
    $credential | Export-Clixml -LiteralPath $credentialFile
}

$credential = Import-Clixml -LiteralPath $credentialFile
if ($credential.UserName -ne $username) {
    throw "Saved username is not $username. Run with -ResetCredential to replace it."
}

Write-Host "PMS production target: http://${server}:8001/pms/" -ForegroundColor Green
$gitRevision = (& git -C $projectRoot rev-parse --short HEAD 2>$null)
$gitDirty = (& git -C $projectRoot status --porcelain 2>$null)
if ($gitRevision) {
    $dirtyText = if ($gitDirty) { '(includes uncommitted changes)' } else { '' }
    Write-Host "Source revision: $gitRevision $dirtyText"
}

Write-Step 'Building frontend dist'
if (-not (Test-Path -LiteralPath (Join-Path $frontendDir 'node_modules'))) {
    Invoke-Checked 'npm.cmd' @('ci') $frontendDir
}
Invoke-Checked 'npm.cmd' @('run', 'build') $frontendDir

Write-Step 'Building backend JAR'
Invoke-Checked 'mvn.cmd' @('clean', 'package', '-DskipTests') $backendDir

$jarPath = Join-Path $backendDir 'target\pms-backend-1.0.0.jar'
$distDir = Join-Path $frontendDir 'dist'
if (-not (Test-Path -LiteralPath (Join-Path $distDir 'index.html'))) {
    throw "Frontend artifact is incomplete: $distDir"
}
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Backend artifact does not exist: $jarPath"
}

$releaseId = Get-Date -Format 'yyyyMMdd-HHmmss'
$archivePath = Join-Path $localStateDir "pms-frontend-$releaseId.zip"
Write-Step 'Packaging frontend artifact'
Compress-Archive -Path (Join-Path $distDir '*') -DestinationPath $archivePath -CompressionLevel Optimal -Force

Write-Step "Uploading and updating server $server"
$env:PMS_DEPLOY_PASSWORD = $credential.GetNetworkCredential().Password
try {
    Invoke-Checked 'python.exe' @(
        $pythonScript,
        '--host', $server,
        '--username', $username,
        '--frontend', $archivePath,
        '--backend', $jarPath,
        '--release-id', $releaseId
    ) $projectRoot
}
finally {
    Remove-Item Env:PMS_DEPLOY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $archivePath -Force -ErrorAction SilentlyContinue
}

Write-Step 'Verifying the production URL from this computer'
$response = Invoke-WebRequest -Uri "http://${server}:8001/pms/" -UseBasicParsing -TimeoutSec 20
if ($response.StatusCode -ne 200) {
    throw "Production frontend verification failed. HTTP status: $($response.StatusCode)"
}

Write-Host "`nDeployment complete: $($response.StatusCode) http://${server}:8001/pms/" -ForegroundColor Green
Write-Host 'The previous frontend and JAR have been backed up on the server.'

param(
    [Parameter(Mandatory = $true)]
    [string]$HookScript
)

$ErrorActionPreference = "Continue"

if ($env:CODEX_MEMORY_HOOKS_ENABLED -ne "1") {
    if ($HookScript -eq "session-start.py") {
        Write-Output "{}"
    }
    exit 0
}

$repoRoot = (Resolve-Path (Split-Path -Path (Split-Path -Path $PSScriptRoot -Parent) -Parent)).Path
$memoryDir = Join-Path $repoRoot ".memory"

$env:UV_CACHE_DIR = Join-Path $memoryDir ".uv-cache"
$env:UV_PYTHON_INSTALL_DIR = Join-Path $memoryDir ".uv-python"
$env:UV_PYTHON_DOWNLOADS = "never"

$scriptPath = Join-Path $memoryDir ("hooks\" + $HookScript)
$logPath = Join-Path $memoryDir "scripts\flush.log"

$uvOutput = & uv run --directory $memoryDir python $scriptPath 2>&1
if ($LASTEXITCODE -eq 0) {
    if ($null -ne $uvOutput) {
        Write-Output $uvOutput
    }
    exit 0
}

$timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
$errorText = "$timestamp [hook-wrapper] '$HookScript' fallback path used. uv exit=$LASTEXITCODE"
Add-Content -Path $logPath -Value $errorText -Encoding utf8 -ErrorAction SilentlyContinue
if ($uvOutput) {
    Add-Content -Path $logPath -Value $uvOutput -Encoding utf8 -ErrorAction SilentlyContinue
}

if ($HookScript -ne "session-start.py") {
    Write-Output "{`"hookError`": `"Codex memory hooks backend failed. uv exit=$LASTEXITCODE. Check .memory/scripts/flush.log for details.`"}"
    exit 1
}

$knowledgeDir = Join-Path $memoryDir "knowledge"
$dailyDir = Join-Path $memoryDir "daily"
$indexFile = Join-Path $knowledgeDir "index.md"

$parts = @()
$today = Get-Date
$invariantCulture = [System.Globalization.CultureInfo]::InvariantCulture
$parts += "## Today"
$parts += $today.ToString("dddd, MMMM dd, yyyy", $invariantCulture)

if (Test-Path $indexFile) {
    $parts += "## Knowledge Base Index`n`n$((Get-Content $indexFile -Raw -Encoding utf8))"
} else {
    $parts += "## Knowledge Base Index`n`n(empty - no articles compiled yet)"
}

$recentLog = "(no recent daily log)"
for ($offset = 0; $offset -lt 2; $offset++) {
    $dateName = $today.AddDays(-$offset).ToString("yyyy-MM-dd")
    $logPathDate = Join-Path $dailyDir ("$dateName.md")
    if (Test-Path $logPathDate) {
        $lines = Get-Content -Path $logPathDate -Encoding utf8
        $recentLog = ($lines | Select-Object -Last 30) -join "`n"
        break
    }
}

$parts += "## Recent Daily Log`n`n$recentLog"

$context = [string]::Join("`n`n---`n`n", $parts)
if ($context.Length -gt 20000) {
    $context = $context.Substring(0, 20000) + "`n`n...(truncated)"
}

$output = [ordered]@{
    hookSpecificOutput = [ordered]@{
        hookEventName = "SessionStart"
        additionalContext = $context
    }
}

Write-Output ($output | ConvertTo-Json -Depth 4)
exit 0

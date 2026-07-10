param(
    [string]$RemoteName = "origin",
    [string]$Branch = "main",
    [string]$Message = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

if (-not (Test-Path ".git")) {
    Write-Error "未找到 Git 仓库，请先运行 scripts/gitee/setup-remote.ps1"
}

$status = git status --porcelain
if ($status) {
    if (-not $Message) {
        $Message = "chore: sync workspace $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    }
    git add -A
    git commit -m $Message
}

Write-Host "推送到 Gitee ($RemoteName/$Branch)..."
git push -u $RemoteName $Branch
Write-Host "完成。"

param(
    [string]$RemoteName = "origin",
    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

if (-not (Test-Path ".git")) {
    Write-Error "未找到 Git 仓库，请先运行 scripts/gitee/setup-remote.ps1"
}

Write-Host "从 Gitee 拉取 ($RemoteName/$Branch)..."
git pull $RemoteName $Branch --rebase
Write-Host "完成。"

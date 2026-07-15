param(
    [Parameter(Mandatory = $true)]
    [string]$RemoteUrl,

    [string]$RemoteName = "origin",

    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

if (-not (Test-Path ".git")) {
    Write-Host "初始化 Git 仓库..."
    git init -b $Branch
}

$existing = git remote get-url $RemoteName 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "更新远程 $RemoteName -> $RemoteUrl"
    git remote set-url $RemoteName $RemoteUrl
} else {
    Write-Host "添加远程 $RemoteName -> $RemoteUrl"
    git remote add $RemoteName $RemoteUrl
}

Write-Host ""
Write-Host "Done. Next:"
Write-Host "  .\scripts\gitee\push.ps1"
Write-Host "  .\scripts\gitee\pull.ps1"

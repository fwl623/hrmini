Write-Host "启动 HRMS 本地基础设施 (MySQL / Redis / RabbitMQ)..."
$compose = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "config\docker\docker-compose.yml"
docker compose -f $compose up -d
Write-Host "MySQL: localhost:3306  Redis: localhost:6379  RabbitMQ 管理台: http://localhost:15672"

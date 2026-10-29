# 一键用 Docker 启动开发环境：MySQL、Redis、后端、前端、AI 编排服务。
# 用法：powershell -ExecutionPolicy Bypass -File scripts\docker-dev.ps1
#   首次运行生成 .env：随机填写三个后端密钥，导入演示账号（platform / admin123），
#   AI 编排服务默认用 stub 假数据（Key 等在「业务设置 → AI 设置」中按租户填写）。已有 .env 只补空密钥，其他配置不动。
# 默认不持久化：停止或重建容器后数据清空。详见 docs/06-部署运维/00-快速开始.md。
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
$utf8 = New-Object System.Text.UTF8Encoding($false)

function New-Secret {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    ($bytes | ForEach-Object { $_.ToString('x2') }) -join ''
}
function Get-EnvValue([string]$key) {
    $line = [IO.File]::ReadAllLines("$PWD\.env", $utf8) | Where-Object { $_ -match "^$key=" } | Select-Object -Last 1
    if ($line) { $line.Substring($key.Length + 1) } else { '' }
}
function Set-EnvValue([string]$key, [string]$value) {
    $lines = [Collections.Generic.List[string]]([IO.File]::ReadAllLines("$PWD\.env", $utf8))
    $index = $lines.FindIndex({ param($l) $l -match "^$key=" })
    if ($index -ge 0) { $lines[$index] = "$key=$value" } else { $lines.Add("$key=$value") }
    [IO.File]::WriteAllLines("$PWD\.env", $lines, $utf8)
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw '未找到 docker，请先安装 Docker Desktop' }
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker 没有运行，请先启动 Docker Desktop' }

if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
    Set-EnvValue 'FLYWAY_LOCATIONS' 'classpath:db/migration,classpath:db/dev-data'
    Set-EnvValue 'AI_ORCHESTRATOR_MODE' 'stub'
    Write-Host '== 已生成 .env（开发配置：导入演示账号，AI 使用 stub 假数据）'
}
foreach ($key in 'JWT_SECRET', 'AES_KEY', 'AI_TASK_CALLBACK_TOKEN') {
    if (-not (Get-EnvValue $key)) {
        Set-EnvValue $key (New-Secret)
        Write-Host "== 已生成 $key"
    }
}
Write-Host '== AI 的服务商与 Key 在「业务设置 → AI 设置」中按租户填写；未填写时 AI 解析会提示未配置'

Write-Host '== 构建并启动（首次需要下载镜像和依赖，可能要几分钟）'
docker compose --profile app up -d --build
if ($LASTEXITCODE -ne 0) { throw 'docker compose 启动失败' }

$port = Get-EnvValue 'WEB_PORT'
if (-not $port) { $port = '8000' }
Write-Host '== 等待后端就绪'
for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 "http://localhost:$port/api/v1/public/health" | Out-Null
        Write-Host ''
        Write-Host "已就绪：http://localhost:$port"
        Write-Host '演示账号：platform / admin123（仅首次生成 .env 时导入）'
        Write-Host "AI 编排：$(Get-EnvValue 'AI_ORCHESTRATOR_MODE') 模式"
        Write-Host '注意：默认不持久化，停止或重建容器后数据清空。日志：docker compose logs -f backend'
        exit 0
    } catch {
        Start-Sleep -Seconds 5
    }
}
Write-Error '5 分钟内后端没有就绪，查看日志：docker compose logs --tail=100 backend'
exit 1

<#
.SYNOPSIS
  Stops all PulseFit Spring Boot microservices started for this project.
#>
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogDir = Join-Path $Root 'logs'
$PidFile = Join-Path $LogDir 'pulsefit-pids.json'

Write-Host "============================================"
Write-Host " PulseFit - Stopping microservices"
Write-Host "============================================"

$ports = @(8080, 8081, 8082, 8083, 8084, 8761)
$stopped = @()

# Prefer PID file
if (Test-Path $PidFile) {
  try {
    $recs = Get-Content $PidFile -Raw | ConvertFrom-Json
    foreach ($r in @($recs)) {
      if ($r.pid -and $r.pid -gt 0) {
        try {
          Stop-Process -Id $r.pid -Force -ErrorAction Stop
          Write-Host "Stopped $($r.name) PID $($r.pid)"
          $stopped += $r.name
        } catch {
          Write-Host "PID $($r.pid) ($($r.name)) already stopped"
        }
      }
    }
  } catch {
    Write-Warning "Could not read PID file: $_"
  }
}

# Also stop any java process whose command line contains PulseFit service jars
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | ForEach-Object {
  $cmd = $_.CommandLine
  if (-not $cmd) { return }
  if ($cmd -match 'PulseFit\\(eureka-server|api-gateway|auth-service|member-service|subscription-service|attendance-service)\\target\\') {
    try {
      Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop
      Write-Host "Stopped java PID $($_.ProcessId) ($($Matches[1]))"
      $stopped += $Matches[1]
    } catch {}
  }
}

# Fallback: free known ports if still occupied by java
foreach ($port in $ports) {
  try {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
      $proc = Get-Process -Id $c.OwningProcess -ErrorAction SilentlyContinue
      if ($proc -and $proc.ProcessName -eq 'java') {
        Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        Write-Host "Freed port $port (java PID $($proc.Id))"
      }
    }
  } catch {}
}

Remove-Item $PidFile -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "Done. Remaining listeners on PulseFit ports:"
foreach ($port in $ports) {
  $still = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
  if ($still) { Write-Host "  Port $port still in use" }
  else { Write-Host "  Port $port free" }
}
Write-Host "============================================"

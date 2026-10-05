<#
.SYNOPSIS
  Starts all PulseFit Spring Boot microservices in the correct order.
#>
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

$JavaHome = $env:JAVA_HOME
if (-not $JavaHome -or -not (Test-Path "$JavaHome\bin\java.exe")) {
  $JavaHome = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
}
$Java = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path $Java)) {
  Write-Error "Java 17 not found. Install Microsoft OpenJDK 17 or set JAVA_HOME."
}

$Maven = 'C:\Tools\apache-maven-3.9.9\bin\mvn.cmd'
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;C:\Tools\apache-maven-3.9.9\bin;" + $env:Path

$LogDir = Join-Path $Root 'logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$PidFile = Join-Path $LogDir 'pulsefit-pids.json'

if (-not (Test-Path (Join-Path $Root 'auth-service\config\secrets\jwt-private.pem'))) {
  Write-Error "Missing auth-service\config\secrets\jwt-private.pem"
}

function Get-ServiceJar([string]$Name) {
  $jar = Get-ChildItem (Join-Path $Root "$Name\target\*-SNAPSHOT.jar") -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-javadoc.jar' } |
    Select-Object -First 1
  if (-not $jar) {
    Write-Host "Building $Name ..."
    Push-Location (Join-Path $Root $Name)
    & $Maven -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "Build failed for $Name" }
    Pop-Location
    $jar = Get-ChildItem (Join-Path $Root "$Name\target\*-SNAPSHOT.jar") |
      Where-Object { $_.Name -notlike '*-sources.jar' } | Select-Object -First 1
  }
  if (-not $jar) { throw "JAR not found for $Name" }
  return $jar
}

function Test-Port([int]$Port) {
  try {
    $c = New-Object System.Net.Sockets.TcpClient
    $c.Connect('127.0.0.1', $Port)
    $c.Close()
    return $true
  } catch { return $false }
}

function Wait-Port([int]$Port, [string]$Name, [int]$TimeoutSec = 120) {
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.Elapsed.TotalSeconds -lt $TimeoutSec) {
    if (Test-Port $Port) {
      Write-Host "  $Name is UP on port $Port"
      return
    }
    Start-Sleep -Seconds 2
  }
  Write-Warning "$Name did not open port $Port. Check logs\$Name.log"
}

function Start-PulseService([string]$Name, [int]$Port) {
  if (Test-Port $Port) {
    Write-Host "  $Name already running on $Port - skipping"
    return $null
  }
  $jar = Get-ServiceJar $Name
  $workDir = Join-Path $Root $Name
  $out = Join-Path $LogDir "$Name.log"
  $err = Join-Path $LogDir "$Name.err.log"
  Write-Host "Starting $Name (port $Port) ..."
  $p = Start-Process -FilePath $Java `
    -ArgumentList @('-jar', $jar.FullName) `
    -WorkingDirectory $workDir `
    -RedirectStandardOutput $out `
    -RedirectStandardError $err `
    -PassThru -WindowStyle Hidden
  return @{ name = $Name; port = $Port; pid = $p.Id; jar = $jar.Name }
}

Write-Host "============================================"
Write-Host " PulseFit - Starting microservices"
$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& $Java -version 2>&1 | Select-Object -First 1)
$ErrorActionPreference = $prevEap
Write-Host " Java: $javaVersion"
Write-Host "============================================"

$records = @()

$r = Start-PulseService 'eureka-server' 8761
if ($r) { $records += $r }
Wait-Port 8761 'Eureka'
Start-Sleep -Seconds 3

foreach ($pair in @(
  @{ n = 'member-service'; p = 8081 },
  @{ n = 'auth-service'; p = 8084 },
  @{ n = 'subscription-service'; p = 8082 },
  @{ n = 'attendance-service'; p = 8083 }
)) {
  $r = Start-PulseService $pair.n $pair.p
  if ($r) { $records += $r }
  Start-Sleep -Seconds 2
}

Wait-Port 8081 'Member'
Wait-Port 8084 'Auth'
Wait-Port 8082 'Subscription'
Wait-Port 8083 'Attendance'

$r = Start-PulseService 'api-gateway' 8080
if ($r) { $records += $r }
Wait-Port 8080 'Gateway'

# Merge with existing pid file if present
$all = @()
if (Test-Path $PidFile) {
  try { $all = @(Get-Content $PidFile -Raw | ConvertFrom-Json) } catch { $all = @() }
}
foreach ($rec in $records) {
  $all = @($all | Where-Object { $_.name -ne $rec.name }) + $rec
}
# Also record already-running services by scanning ports
$known = @(
  @{ n = 'eureka-server'; p = 8761 },
  @{ n = 'api-gateway'; p = 8080 },
  @{ n = 'member-service'; p = 8081 },
  @{ n = 'subscription-service'; p = 8082 },
  @{ n = 'attendance-service'; p = 8083 },
  @{ n = 'auth-service'; p = 8084 }
)
foreach ($k in $known) {
  if (-not ($all | Where-Object { $_.name -eq $k.n })) {
    if (Test-Port $k.p) {
      $all += @{ name = $k.n; port = $k.p; pid = 0; jar = 'already-running' }
    }
  }
}
$all | ConvertTo-Json | Set-Content -Path $PidFile -Encoding UTF8

Write-Host ""
Write-Host "============================================"
Write-Host " All services started"
Write-Host " Eureka dashboard : http://localhost:8761"
Write-Host " API Gateway      : http://localhost:8080"
Write-Host " Logs             : $LogDir"
Write-Host " Wait ~10s for Eureka registration before API calls."
Write-Host "============================================"

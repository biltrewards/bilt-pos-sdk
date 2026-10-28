#
#    ____  _ _ _
#   | __ )(_) | |_
#   |  _ \| | | __|
#   | |_) | | | |_
#   |____/|_|_|\__|
#
#   Bilt POS SDK
#
# Windows counterpart of scripts/browser-emulator.sh: starts the browser register
# emulator (js/apps/emulator) together with a Terminal Bridge. Same steps, options
# and defaults; see that script for the details. Runs on Windows PowerShell 5.1 and
# PowerShell 7.
#
# Usage:
#   .\scripts\browser-emulator.ps1 --terminal 192.168.4.108
#   .\scripts\browser-emulator.ps1 --terminal 192.168.4.108:8443
#   .\scripts\browser-emulator.ps1 --adb
#   .\scripts\browser-emulator.ps1 --adb --terminal 192.168.4.108
#   .\scripts\browser-emulator.ps1 --local
#
# Options:
#   --terminal <ip[:port]>  Terminal on the LAN (port defaults to 8443). With
#                           --adb it only picks the device, by serial or
#                           wifi-adb ip (a port is ignored).
#   --adb                   Reach the terminal through `adb forward` instead of
#                           the LAN.
#   --local                 No terminal; use the emulator's local-session mode.
#   --port <port>           Bridge port (default 48333).
#   --web-port <port>       Vite dev server port (default 5173).
#   --no-open               Do not open the browser.
#   -h, --help              Show this help.
#
# Env:
#   SKIP_BUILD=1   Reuse the existing bridge launcher and JS build (fast iteration).
#
# If PowerShell refuses to run scripts, start it as
#   powershell -ExecutionPolicy Bypass -File scripts\browser-emulator.ps1 --local

$ErrorActionPreference = 'Stop'
$onWindows = [Environment]::OSVersion.Platform -eq 'Win32NT'
$repoRoot = Split-Path -Parent $PSScriptRoot
$launcherName = if ($onWindows) { 'bridge.bat' } else { 'bridge' }
$bridgeLauncher = Join-Path $repoRoot "bridge/build/install/bridge/bin/$launcherName"

function Show-Usage {
  $lines = Get-Content $PSCommandPath
  $from = [Array]::IndexOf($lines, '# Usage:')
  $to = [Array]::IndexOf($lines, '# Env:')
  $lines[$from..($to - 1)] | ForEach-Object { $_ -replace '^# ?', '' }
}

# Windows PowerShell 5.1 turns a native command's stderr into an error record, which
# 'Stop' makes fatal even when the command succeeds (adb logs its daemon start there).
function Invoke-Native([scriptblock]$command) {
  $ErrorActionPreference = 'Continue'
  & $command
}

function Fail([string]$message) {
  [Console]::Error.WriteLine($message)
  exit 1
}

$terminal = ''
$localOnly = $false
$useAdb = $false
$bridgePort = 48333
$webPort = 5173
$openBrowser = $true

for ($i = 0; $i -lt $args.Count; $i++) {
  switch ($args[$i]) {
    '--terminal' { $i++; $terminal = [string]$args[$i] }
    '--local' { $localOnly = $true }
    '--adb' { $useAdb = $true }
    '--port' { $i++; $bridgePort = [int]$args[$i] }
    '--web-port' { $i++; $webPort = [int]$args[$i] }
    '--no-open' { $openBrowser = $false }
    { $_ -in '-h', '--help' } { Show-Usage; exit 0 }
    default { [Console]::Error.WriteLine("Unknown option: $($args[$i])"); Show-Usage; exit 2 }
  }
}

if (-not $terminal -and -not $localOnly -and -not $useAdb) {
  [Console]::Error.WriteLine('Pass --terminal <ip[:port]>, --adb or --local.')
  Show-Usage
  exit 2
}
if ($localOnly -and ($terminal -or $useAdb)) {
  [Console]::Error.WriteLine('--local runs without a terminal; drop --terminal / --adb.')
  exit 2
}

# adb from PATH, else the SDK locations the desktop emulator also tries.
$adb = $null
if ($useAdb) {
  $exe = if ($onWindows) { 'adb.exe' } else { 'adb' }
  $candidates = @()
  $onPath = Get-Command adb -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($onPath) { $candidates += $onPath.Source }
  foreach ($sdk in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
    if ($sdk) { $candidates += Join-Path $sdk "platform-tools/$exe" }
  }
  if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/$exe" }
  $candidates += Join-Path $HOME "Library/Android/sdk/platform-tools/$exe"
  $candidates += Join-Path $HOME "Android/Sdk/platform-tools/$exe"
  $adb = $candidates | Where-Object { Test-Path $_ -PathType Leaf } | Select-Object -First 1
  if (-not $adb) {
    Fail ("--adb: no adb found on PATH, ANDROID_HOME, ANDROID_SDK_ROOT or the default SDK`n" +
      'locations; install platform-tools or set ANDROID_HOME.')
  }
}

# pnpm from PATH, otherwise through corepack (version pinned in js/package.json).
if (Get-Command pnpm -ErrorAction SilentlyContinue) {
  $pnpm = 'pnpm'
} elseif (Get-Command corepack -ErrorAction SilentlyContinue) {
  $pnpm = 'corepack pnpm'
} else {
  Fail "Neither pnpm nor corepack is on PATH; install Node 20+ and run 'corepack enable'."
}

# Through cmd on Windows, so pnpm.cmd, corepack.cmd and bridge.bat resolve as they do at a
# prompt. The command line is passed verbatim, since PowerShell 5.1 and 7 quote native
# arguments differently.
function Start-Shell([string]$command, [string]$workDir) {
  $info = New-Object System.Diagnostics.ProcessStartInfo
  if ($onWindows) {
    $info.FileName = 'cmd.exe'
    $info.Arguments = "/d /s /c `"$command`""
  } else {
    $info.FileName = '/bin/sh'
    $info.Arguments = "-c `"exec $($command -replace '"', '\"')`""
  }
  $info.UseShellExecute = $false
  $info.WorkingDirectory = $workDir
  return [System.Diagnostics.Process]::Start($info)
}

function Invoke-Shell([string]$command, [string]$workDir) {
  $process = Start-Shell $command $workDir
  $process.WaitForExit()
  if ($process.ExitCode -ne 0) { Fail "Failed ($($process.ExitCode)): $command" }
}

# Stops a process and its descendants; pnpm runs vite as a child, which would
# otherwise outlive pnpm and keep the port.
function Stop-Tree($process) {
  if (-not $process -or $process.HasExited) { return }
  if ($onWindows) {
    Invoke-Native { & taskkill.exe /T /F /PID $process.Id 2>&1 | Out-Null }
  } else {
    $process.Kill($true)
  }
}

if ($env:SKIP_BUILD -ne '1') {
  Write-Host 'Building the bridge launcher...'
  $gradlew = if ($onWindows) { 'gradlew.bat' } else { './gradlew' }
  Invoke-Shell "$gradlew -q :bridge:installDist" $repoRoot
  Write-Host 'Installing and building the JS workspace...'
  $js = Join-Path $repoRoot 'js'
  Invoke-Shell "$pnpm install --frozen-lockfile" $js
  # Quoted: cmd would eat the ^ otherwise.
  Invoke-Shell "$pnpm --filter `"@biltorg/pos-emulator^...`" build" $js
}

if (-not (Test-Path $bridgeLauncher -PathType Leaf)) {
  Fail "Bridge launcher not found at $bridgeLauncher (run without SKIP_BUILD=1)."
}

# The bridge moves to the next free port when its own is taken, so a check on
# --port could answer for another bridge (the tray app, say) with another config.
function Test-PortInUse([int]$port) {
  $client = New-Object System.Net.Sockets.TcpClient
  try { $client.Connect('127.0.0.1', $port); return $true } catch { return $false } finally { $client.Close() }
}
foreach ($p in @($bridgePort, $webPort)) {
  if (Test-PortInUse $p) {
    Fail ("Port $p on 127.0.0.1 is already in use (another bridge or dev server?).`n" +
      'Stop it, or pick another with --port / --web-port.')
  }
}

if ($onWindows) {
  # Under Gradle, Ctrl-C ends this process without running `finally`; a job that
  # kills its members when its last handle closes takes the bridge and vite with it.
  Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class BiltEmulatorJob {
  [StructLayout(LayoutKind.Sequential)] struct Basic {
    public long PerProcessUserTimeLimit; public long PerJobUserTimeLimit; public uint LimitFlags;
    public UIntPtr MinimumWorkingSetSize; public UIntPtr MaximumWorkingSetSize; public uint ActiveProcessLimit;
    public UIntPtr Affinity; public uint PriorityClass; public uint SchedulingClass;
  }
  [StructLayout(LayoutKind.Sequential)] struct Io {
    public ulong ReadOps, WriteOps, OtherOps, ReadBytes, WriteBytes, OtherBytes;
  }
  [StructLayout(LayoutKind.Sequential)] struct Extended {
    public Basic BasicLimits; public Io IoInfo;
    public UIntPtr ProcessMemoryLimit, JobMemoryLimit, PeakProcessMemoryUsed, PeakJobMemoryUsed;
  }
  [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
  static extern IntPtr CreateJobObject(IntPtr attributes, string name);
  [DllImport("kernel32.dll", SetLastError = true)]
  static extern bool SetInformationJobObject(IntPtr job, int infoClass, IntPtr info, uint length);
  [DllImport("kernel32.dll", SetLastError = true)]
  static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
  [DllImport("kernel32.dll")]
  static extern IntPtr GetCurrentProcess();
  public static bool KillChildrenOnExit() {
    IntPtr job = CreateJobObject(IntPtr.Zero, null);
    if (job == IntPtr.Zero) return false;
    var info = new Extended();
    info.BasicLimits.LimitFlags = 0x2000; // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
    int length = Marshal.SizeOf(typeof(Extended));
    IntPtr ptr = Marshal.AllocHGlobal(length);
    try {
      Marshal.StructureToPtr(info, ptr, false);
      return SetInformationJobObject(job, 9, ptr, (uint)length) // JobObjectExtendedLimitInformation
        && AssignProcessToJobObject(job, GetCurrentProcess());
    } finally {
      Marshal.FreeHGlobal(ptr);
    }
  }
}
'@
  if (-not [BiltEmulatorJob]::KillChildrenOnExit()) {
    Write-Warning 'Could not tie the bridge and dev server to this process; stop them by hand if it is killed.'
  }
}

$sessionMode = if ($localOnly) { 'local' } else { 'terminal' }
$workDir = Join-Path ([IO.Path]::GetTempPath()) ("bilt-browser-emulator." + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $workDir | Out-Null
$config = Join-Path $workDir 'config.json'
$bridgeLog = Join-Path $workDir 'bridge.log'

$bridge = $null
$web = $null
$adbSerial = $null
$adbPort = $null
$status = 0

try {
  $origins = @("http://127.0.0.1:$webPort", "http://localhost:$webPort")
  $bridgeConfig = [ordered]@{ port = $bridgePort; allowedOrigins = $origins }
  if (-not $localOnly) {
    $hostName = ($terminal -split ':')[0]
    $tPort = 8443
    if ($terminal -match ':(\d+)$') { $tPort = [int]$Matches[1] }
    if ($useAdb) {
      # The device whose serial is the address, or its wifi-adb serial ("<ip>:5555");
      # else the single attached device. USB devices take a moment to enumerate after
      # a cold server start, so an empty listing is retried for a few seconds.
      Invoke-Native { & $adb start-server 2>&1 | Out-Null }
      if ($LASTEXITCODE -ne 0) { Fail "--adb: '$adb start-server' failed ($LASTEXITCODE)." }
      $serials = @()
      for ($try = 0; $try -lt 6 -and $serials.Count -eq 0; $try++) {
        $serials = @(Invoke-Native { & $adb devices 2>$null } | Select-Object -Skip 1 |
            ForEach-Object { $f = -split $_; if ($f.Count -ge 2 -and $f[1] -eq 'device') { $f[0] } })
        if ($serials.Count -eq 0) { Start-Sleep -Milliseconds 500 }
      }
      $selectorIp = $terminal -replace ':[^:]*$', ''
      $adbSerial = $serials | Where-Object { $_ -eq $terminal } | Select-Object -First 1
      if (-not $adbSerial -and $terminal) {
        $adbSerial = $serials | Where-Object { ($_ -replace ':[^:]*$', '') -eq $selectorIp } | Select-Object -First 1
      }
      if (-not $adbSerial -and $serials.Count -eq 1) { $adbSerial = $serials[0] }
      if (-not $adbSerial) {
        if ($serials.Count -eq 0) {
          $hint = if ($terminal) { " or run 'adb connect $terminal'" } else { '' }
          Fail "--adb: no adb device attached (checked with $adb); plug the terminal in$hint."
        }
        Fail ("--adb: several adb devices attached and none matches '$terminal': $($serials -join ' ')`n" +
          'Pick one with --terminal <serial or wifi-adb ip>.')
      }
      $forwarded = ((Invoke-Native { & $adb -s $adbSerial forward tcp:0 tcp:8443 2>$null }) | Select-Object -Last 1 | Out-String).Trim()
      if ($forwarded -notmatch '^\d+$') { Fail "--adb: adb forward did not return a port: $forwarded" }
      $adbPort = $forwarded
      Write-Host "Forwarding 127.0.0.1:$adbPort to port 8443 of $adbSerial over adb."
      $hostName = '127.0.0.1'
      $tPort = [int]$adbPort
    }
    $bridgeConfig.terminal = [ordered]@{ host = $hostName; port = $tPort; encryption = $false; trustAll = $true }
  }
  [IO.File]::WriteAllText($config, ($bridgeConfig | ConvertTo-Json -Depth 4))

  Write-Host "Starting the Terminal Bridge on 127.0.0.1:$bridgePort (log: $bridgeLog)..."
  $env:JAVA_OPTS = "$env:JAVA_OPTS -Djava.awt.headless=true".Trim()
  $bridge = Start-Shell "`"$bridgeLauncher`" --config `"$config`" > `"$bridgeLog`" 2>&1" $repoRoot

  $health = "http://127.0.0.1:$bridgePort/health"
  $ready = $null
  for ($s = 0; $s -lt 60 -and -not $ready; $s++) {
    try { $ready = Invoke-WebRequest -Uri $health -UseBasicParsing -TimeoutSec 2 } catch { }
    if ($ready) { break }
    if ($bridge.HasExited) {
      [Console]::Error.WriteLine('The bridge exited during startup:')
      Get-Content $bridgeLog -Tail 20 | ForEach-Object { [Console]::Error.WriteLine($_) }
      exit 1
    }
    Start-Sleep -Seconds 1
  }
  if (-not $ready) {
    [Console]::Error.WriteLine("The bridge did not answer $health within 60 s:")
    Get-Content $bridgeLog -Tail 20 | ForEach-Object { [Console]::Error.WriteLine($_) }
    exit 1
  }
  Write-Host "Bridge ready: $($ready.Content)"

  $url = "http://127.0.0.1:$webPort"
  Write-Host "Browser emulator: $url (Ctrl-C stops the emulator and the bridge)"
  # The session mode becomes the page's launch setting (src/settings.ts), so it opens
  # the kind of session the bridge config above can serve.
  $env:BRIDGE_URL = "http://127.0.0.1:$bridgePort"
  $env:VITE_BILT_BRIDGE_PORT = "$bridgePort"
  $env:VITE_BILT_SESSION_MODE = $sessionMode
  $web = Start-Shell "$pnpm --filter @biltorg/pos-emulator dev --host 127.0.0.1 --port $webPort --strictPort" (Join-Path $repoRoot 'js')

  $opened = -not $openBrowser
  $started = Get-Date
  while (-not $web.HasExited -and -not $bridge.HasExited) {
    # Give Vite a moment to bind before the browser asks for the page.
    if (-not $opened -and ((Get-Date) - $started).TotalSeconds -ge 2) {
      $opened = $true
      try {
        if ($onWindows) { Start-Process $url } elseif (Get-Command open -ErrorAction SilentlyContinue) { & open $url } else { Invoke-Native { & xdg-open $url 2>&1 | Out-Null } }
      } catch { }
    }
    Start-Sleep -Seconds 1
  }
  if ($bridge.HasExited) {
    [Console]::Error.WriteLine('The bridge stopped; last log lines:')
    Get-Content $bridgeLog -Tail 20 | ForEach-Object { [Console]::Error.WriteLine($_) }
    $status = if ($bridge.ExitCode -eq 0) { 1 } else { $bridge.ExitCode }
  } else {
    $status = $web.ExitCode
    if ($status -ne 0) { [Console]::Error.WriteLine("The dev server exited with status $status.") }
  }
} finally {
  Stop-Tree $web
  Stop-Tree $bridge
  # A forward lives in the adb server and would outlive this script.
  if ($adbPort) { Invoke-Native { & $adb -s $adbSerial forward --remove "tcp:$adbPort" 2>&1 | Out-Null } }
  Remove-Item -Recurse -Force $workDir -ErrorAction SilentlyContinue
}
exit $status

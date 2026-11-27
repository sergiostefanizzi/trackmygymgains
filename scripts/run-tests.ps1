<#
.SYNOPSIS
    Runs the JVM (Robolectric) tests and, optionally, the instrumented tests on an emulator.
    Instrumented tests run ONLY on an emulator, never on a physical phone: connectedDebugAndroidTest
    uninstalls the app at the end, which would wipe the user's real data. ANDROID_SERIAL is pinned to the emulator.

.PARAMETER UnitOnly
    Run only the JVM unit tests (skip the device tests).

.PARAMETER Avd
    AVD id to start when no device is connected (see `emulator -list-avds`). Default: Medium_Phone_API_37.0.

.EXAMPLE
    .\scripts\run-tests.ps1
    .\scripts\run-tests.ps1 -UnitOnly
#>
param(
    [switch]$UnitOnly,
    [string]$Avd = 'Medium_Phone_API_37.0'
)

$ErrorActionPreference = 'Continue'  # native tools: exit codes are checked explicitly

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulatorExe = Join-Path $sdkRoot 'emulator\emulator.exe'
$androidStudioJbr = 'C:\Program Files\Android\Android Studio\jbr'

# Always prefer Android Studio's JBR (same JDK as the IDE's Gradle); fall back to JAVA_HOME.
if (Test-Path (Join-Path $androidStudioJbr 'bin\java.exe')) {
    $env:JAVA_HOME = $androidStudioJbr
}
elseif (-not ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe')))) {
    Write-Host 'ERROR: no JDK found. Install Android Studio or set JAVA_HOME.' -ForegroundColor Red
    exit 1
}
Write-Host "JAVA_HOME = $env:JAVA_HOME"

$gradlew = Join-Path $repoRoot 'gradlew.bat'
$startedEmulator = $null
$emulatorProcess = $null
$exitCode = 0

function Get-ConnectedEmulators {
    # Emulators only (serial emulator-NNNN): physical devices are ignored on purpose.
    $lines = & $adb devices 2>$null
    return @($lines | Where-Object { $_ -match '^emulator-\d+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
}

function Stop-StartedEmulator {
    if ($script:startedEmulator) {
        Write-Host 'Stopping emulator started by this script...'
        if ($env:ANDROID_SERIAL) { & $adb -s $env:ANDROID_SERIAL emu kill 2>$null | Out-Null }
        else { Get-ConnectedEmulators | ForEach-Object { & $adb -s $_ emu kill 2>$null | Out-Null } }
        Start-Sleep -Seconds 5
        if ($script:emulatorProcess -and -not $script:emulatorProcess.HasExited) {
            Stop-Process -Id $script:emulatorProcess.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

try {
    Write-Host "`n=== JVM tests (testDebugUnitTest) ===" -ForegroundColor Cyan
    & $gradlew testDebugUnitTest --console=plain
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'JVM tests FAILED.' -ForegroundColor Red
        throw 'unit'
    }

    if (-not $UnitOnly) {
        & $adb start-server | Out-Null
        $devices = @(Get-ConnectedEmulators)
        if ($devices.Count -eq 0) {
            if (-not (Test-Path $emulatorExe)) {
                Write-Host "ERROR: emulator not found at $emulatorExe" -ForegroundColor Red
                throw 'emulator'
            }
            Write-Host "No emulator running. Starting emulator '$Avd' headless..." -ForegroundColor Yellow
            $script:emulatorProcess = Start-Process -FilePath $emulatorExe `
                -ArgumentList @('-avd', $Avd, '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot-save') `
                -PassThru -WindowStyle Hidden
            $script:startedEmulator = $true

            $deadline = (Get-Date).AddMinutes(8)
            $booted = $false
            while ((Get-Date) -lt $deadline) {
                Start-Sleep -Seconds 3
                $emus = @(Get-ConnectedEmulators)
                if ($emus.Count -gt 0) {
                    $env:ANDROID_SERIAL = $emus[0]
                    $boot = (& $adb -s $emus[0] shell getprop sys.boot_completed 2>$null) -join ''
                    if ($boot.Trim() -eq '1') {
                        $booted = $true
                        break
                    }
                }
            }
            if (-not $booted) {
                Write-Host 'ERROR: emulator did not finish booting within 8 minutes.' -ForegroundColor Red
                throw 'boot'
            }
            Write-Host 'Emulator booted.' -ForegroundColor Green
        }
        else {
            $env:ANDROID_SERIAL = $devices[0]
            Write-Host "Emulator already running: $($devices[0])"
        }
        if ($env:ANDROID_SERIAL -notmatch '^emulator-\d+$') {
            Write-Host 'ERROR: refusing to run instrumented tests on a non-emulator device.' -ForegroundColor Red
            throw 'serial'
        }
        Write-Host "Instrumented tests target ANDROID_SERIAL=$env:ANDROID_SERIAL (physical devices are never used)."

        Write-Host "`n=== Instrumented tests (connectedDebugAndroidTest) ===" -ForegroundColor Cyan
        & $gradlew connectedDebugAndroidTest --console=plain
        if ($LASTEXITCODE -ne 0) {
            Write-Host 'Instrumented tests FAILED.' -ForegroundColor Red
            throw 'connected'
        }
    }
    Write-Host "`nALL TESTS PASSED." -ForegroundColor Green
}
catch {
    Write-Host "Test run failed ($($_.Exception.Message))." -ForegroundColor Red
    $exitCode = 1
}
finally {
    Stop-StartedEmulator
    Write-Host "`nReports:"
    Write-Host "  JVM:           $(Join-Path $repoRoot 'app\build\reports\tests\testDebugUnitTest\index.html')"
    if (-not $UnitOnly) {
        Write-Host "  Instrumented:  $(Join-Path $repoRoot 'app\build\reports\androidTests\connected\debug\index.html')"
    }
}

exit $exitCode

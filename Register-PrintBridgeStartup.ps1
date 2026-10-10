$ErrorActionPreference = "Stop"

$taskName = "PrintBridge-AtLogOn"
$jarName = "printbridge-2.1.3.jar"
$jarPath = Join-Path $PSScriptRoot $jarName

if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    $jarPath = Join-Path $PSScriptRoot "target\$jarName"
}
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "Cannot find $jarName. Place it beside this script or in the target subfolder."
}

$javawCommand = Get-Command "javaw.exe" -ErrorAction SilentlyContinue
$javawPath = if ($null -ne $javawCommand) { $javawCommand.Source }
if ($null -eq $javawCommand) {
    $javaCommand = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        $javawPath = Join-Path (Split-Path -Parent $javaCommand.Source) "javaw.exe"
        if (-not (Test-Path -LiteralPath $javawPath -PathType Leaf)) {
            $javawPath = $null
        }
    }
}
if (-not $javawPath) {
    throw "Cannot find javaw.exe. Install Java 8 or later and add its bin folder to PATH."
}

$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$workingDirectory = Split-Path -Parent $jarPath
$arguments = "-Dfile.encoding=UTF-8 -jar `"$jarPath`""
$action = New-ScheduledTaskAction `
    -Execute $javawPath `
    -Argument $arguments `
    -WorkingDirectory $workingDirectory
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $currentUser
$principal = New-ScheduledTaskPrincipal `
    -UserId $currentUser `
    -LogonType Interactive `
    -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit ([TimeSpan]::Zero)

Register-ScheduledTask `
    -TaskName $taskName `
    -Action $action `
    -Trigger $trigger `
    -Principal $principal `
    -Settings $settings `
    -Description "Starts PrintBridge in the current user's interactive session at logon." `
    -Force | Out-Null

Write-Host "PrintBridge is registered to start when $currentUser logs on."
Write-Host "Task name: $taskName"
Write-Host "JAR: $jarPath"

$ErrorActionPreference = "Stop"

$taskName = "PrintBridge-AtLogOn"
$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue

if ($null -eq $task) {
    Write-Host "PrintBridge automatic startup is not registered."
    exit 0
}

Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
Write-Host "PrintBridge automatic startup has been removed."
Write-Host "If PrintBridge is currently running, close it separately."

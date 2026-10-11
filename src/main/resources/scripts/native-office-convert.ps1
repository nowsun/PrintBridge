# Native Office/WPS -> PDF converter for PrintBridge.
# Tries Microsoft Office ProgIDs first, then WPS Office ProgIDs.
# Keep this script ASCII-only so Windows PowerShell 5.1 reads it correctly.

param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Target,
    [Parameter(Mandatory = $true)][string]$Kind
)

$ErrorActionPreference = 'Stop'

$config = @{
    'word'  = @{ ProgIds = @('Word.Application', 'KWPS.Application');         Format = 17 }
    'excel' = @{ ProgIds = @('Excel.Application', 'KET.Application');         Format = 0  }
    'ppt'   = @{ ProgIds = @('PowerPoint.Application', 'KWPP.Application');   Format = 32 }
}

if (-not $config.ContainsKey($Kind)) {
    [Console]::Error.WriteLine("Unsupported kind: $Kind")
    exit 2
}

# Office/WPS process names that COM automation may spawn. Snapshot the ones
# already running BEFORE conversion so we can kill only those created during
# this run (a lingering process keeps the output PDF locked).
$officeProcessNames = @('et', 'wps', 'wpp', 'excel', 'winword', 'powerpnt')
$beforePids = @(Get-Process -ErrorAction SilentlyContinue |
    Where-Object { $officeProcessNames -contains $_.ProcessName } |
    Select-Object -ExpandProperty Id)

function Test-PdfComplete([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    $fi = Get-Item -LiteralPath $Path
    if ($fi.Length -lt 100) { return $false }
    $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $head = New-Object byte[] 5
        [void]$stream.Read($head, 0, 5)
        if ([System.Text.Encoding]::ASCII.GetString($head) -ne '%PDF-') { return $false }
        $tailLen = [Math]::Min(2048, [int]$fi.Length)
        $stream.Seek($fi.Length - $tailLen, [System.IO.SeekOrigin]::Begin) | Out-Null
        $tail = New-Object byte[] $tailLen
        [void]$stream.Read($tail, 0, $tailLen)
        return ([System.Text.Encoding]::ASCII.GetString($tail)).Contains('%%EOF')
    } finally {
        $stream.Close()
    }
}

function Release-ComObject([object]$comObj) {
    if ($null -eq $comObj) { return }
    try { [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($comObj) } catch {}
}

$entry = $config[$Kind]
$lastError = $null

foreach ($progId in $entry.ProgIds) {
    $app = $null
    $docObj = $null
    try {
        $app = New-Object -ComObject $progId
    } catch {
        $lastError = $_.Exception.Message
        continue
    }

    try {
        try { $app.Visible = $false } catch {}
        try { $app.DisplayAlerts = 0 } catch {}

        switch ($Kind) {
            'word' {
                $docObj = $app.Documents.Open($Source)
                try {
                    $docObj.SaveAs2($Target, $entry.Format)
                } catch {
                    $docObj.SaveAs($Target, $entry.Format)
                }
                $docObj.Close($false)
            }
            'excel' {
                $docObj = $app.Workbooks.Open($Source)
                $docObj.ExportAsFixedFormat($entry.Format, $Target)
                $docObj.Close($false)
            }
            'ppt' {
                $docObj = $app.Presentations.Open($Source, $true, $false, $false)
                $docObj.SaveAs($Target, $entry.Format)
                $docObj.Close()
            }
        }

        Release-ComObject $docObj
        $docObj = $null

        try { $app.Quit() } catch {}
        Release-ComObject $app
        $app = $null

        # Let COM finalize and release the output file handle.
        [GC]::Collect()
        [GC]::WaitForPendingFinalizers()
        Start-Sleep -Milliseconds 500

        # Kill any Office/WPS process created during this conversion; a lingering
        # process keeps the output PDF locked and prevents the caller from opening it.
        $after = @(Get-Process -ErrorAction SilentlyContinue |
            Where-Object { $officeProcessNames -contains $_.ProcessName })
        foreach ($p in $after) {
            if ($beforePids -notcontains $p.Id) {
                try { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } catch {}
            }
        }
        Start-Sleep -Milliseconds 300

        if (Test-PdfComplete $Target) {
            exit 0
        }
        [Console]::Error.WriteLine("$progId produced an incomplete/invalid PDF")
        $lastError = "$progId produced an incomplete/invalid PDF"
    } catch {
        $lastError = $_.Exception.Message
        Release-ComObject $docObj
        $docObj = $null
        try { if ($null -ne $app) { $app.Quit() } } catch {}
        Release-ComObject $app
        $app = $null
    }
}

[Console]::Error.WriteLine("Native conversion failed: $lastError")
exit 1

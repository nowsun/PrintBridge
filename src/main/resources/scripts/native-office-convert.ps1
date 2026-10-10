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

$entry = $config[$Kind]
$lastError = $null

foreach ($progId in $entry.ProgIds) {
    $app = $null
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
                $doc = $app.Documents.Open($Source)
                try {
                    $doc.SaveAs2($Target, $entry.Format)
                } catch {
                    $doc.SaveAs($Target, $entry.Format)
                }
                $doc.Close($false)
            }
            'excel' {
                $wb = $app.Workbooks.Open($Source)
                $wb.ExportAsFixedFormat($entry.Format, $Target)
                $wb.Close($false)
            }
            'ppt' {
                $pres = $app.Presentations.Open($Source, $true, $false, $false)
                $pres.SaveAs($Target, $entry.Format)
                $pres.Close()
            }
        }

        $app.Quit()
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($app) | Out-Null
        $app = $null

        if (Test-Path $Target) {
            exit 0
        }
        [Console]::Error.WriteLine("$progId did not create the output file")
        $lastError = "$progId did not create the output file"
    } catch {
        $lastError = $_.Exception.Message
        try { if ($null -ne $app) { $app.Quit() } } catch {}
        try { if ($null -ne $app) { [System.Runtime.InteropServices.Marshal]::ReleaseComObject($app) | Out-Null } } catch {}
        $app = $null
    }
}

[Console]::Error.WriteLine("Native conversion failed: $lastError")
exit 1

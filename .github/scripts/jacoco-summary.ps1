param(
    [Parameter(Mandatory = $true)]
    [string] $CurrentReport,

    [string] $BaseReport,

    [Parameter(Mandatory = $true)]
    [string] $OutputMarkdown,

    [Parameter(Mandatory = $true)]
    [string] $OutputProperties
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$invariantCulture = [System.Globalization.CultureInfo]::InvariantCulture

function Get-JacocoInstructionCoverage {
    param([Parameter(Mandatory = $true)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    [xml] $report = Get-Content -LiteralPath $Path
    $counter = $report.SelectSingleNode("/report/counter[@type='INSTRUCTION']")
    if ($null -eq $counter) {
        throw "JaCoCo report '$Path' does not contain INSTRUCTION counter."
    }

    $covered = [int] $counter.covered
    $missed = [int] $counter.missed
    $total = $covered + $missed
    $percentage = if ($total -eq 0) { 0 } else { ($covered / $total) * 100 }

    [pscustomobject]@{
        Covered = $covered
        Missed = $missed
        Total = $total
        Percentage = [math]::Round($percentage, 2)
    }
}

function Format-Percent {
    param($Coverage)

    if ($null -eq $Coverage) {
        return "n/a"
    }

    return [string]::Format($invariantCulture, "{0:N2}%", $Coverage.Percentage)
}

$current = Get-JacocoInstructionCoverage -Path $CurrentReport
if ($null -eq $current) {
    throw "Current JaCoCo report was not found at '$CurrentReport'."
}

$base = if ([string]::IsNullOrWhiteSpace($BaseReport)) { $null } else { Get-JacocoInstructionCoverage -Path $BaseReport }
$delta = if ($null -eq $base) { $null } else { [math]::Round($current.Percentage - $base.Percentage, 2) }
$deltaText = if ($null -eq $delta) {
    "n/a"
} elseif ($delta -gt 0) {
    "+" + $delta.ToString("0.00", $invariantCulture) + " pp"
} else {
    $delta.ToString("0.00", $invariantCulture) + " pp"
}
$basePercentage = if ($null -eq $base) { "" } else { $base.Percentage }
$deltaValue = if ($null -eq $delta) { "" } else { $delta }

$markdown = @"
<!-- cloud-comment-backend-coverage -->
## Backend Coverage

| Metric | Current | Base | Change |
| --- | ---: | ---: | ---: |
| JaCoCo instruction coverage | $(Format-Percent $current) | $(Format-Percent $base) | $deltaText |

Covered instructions: $($current.Covered) / $($current.Total). Missed instructions: $($current.Missed).
"@

Set-Content -LiteralPath $OutputMarkdown -Value $markdown -Encoding UTF8
Set-Content -LiteralPath $OutputProperties -Value @(
    "coverage=$($current.Percentage)"
    "coverage_text=$(Format-Percent $current)"
    "base_coverage=$basePercentage"
    "delta=$deltaValue"
    "delta_text=$deltaText"
) -Encoding UTF8

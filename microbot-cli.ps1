#!/usr/bin/env pwsh
# PowerShell wrapper for microbot-cli bash script.
# Usage: ./microbot-cli.ps1 <args>  (or add an alias: Set-Alias mcli ./microbot-cli.ps1)

$bash = "C:\Program Files\Git\bin\bash.exe"
$script = Join-Path $PSScriptRoot "microbot-cli"

& $bash $script @args

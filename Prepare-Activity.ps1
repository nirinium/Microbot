#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Prepare for an OSRS activity: verify BiS gear, equip, and set up inventory.

.DESCRIPTION
    Loads activity data from activities/<name>.json, compares your bank and
    equipped items against the Best In Slot list, equips the best available gear,
    sets up your inventory with switches and supplies, then sorts to the target layout.

.PARAMETER Activity
    Activity name or alias (e.g. "ToA", "toa", "Tombs"). Case-insensitive.

.PARAMETER DryRun
    Show the full plan and analysis without executing any actions.

.PARAMETER SkipEquipment
    Skip the equipment update phase (assume gear is already correct).

.PARAMETER SkipInventory
    Skip the inventory setup phase (only run sort if needed).

.PARAMETER SkipSort
    Skip the final inventory sort phase.

.EXAMPLE
    .\Prepare-Activity.ps1 -Activity ToA
    .\Prepare-Activity.ps1 -Activity ToA -DryRun
    .\Prepare-Activity.ps1 -Activity ToA -SkipEquipment
#>
param(
    [Parameter(Mandatory = $true)][string]$Activity,
    [switch]$DryRun,
    [switch]$SkipEquipment,
    [switch]$SkipInventory,
    [switch]$SkipSort
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─── Auth setup ──────────────────────────────────────────────────────────────
$tokenFile = "$env:USERPROFILE\.runelite\.agent-token"
if (-not (Test-Path $tokenFile)) { Write-Error "Agent token not found at $tokenFile"; exit 1 }
$token   = (Get-Content $tokenFile -Raw).Trim()
$headers = @{ "X-Agent-Token" = $token }
$base    = "http://127.0.0.1:8081"

# ─── Helpers ─────────────────────────────────────────────────────────────────
function Invoke-Get([string]$path) {
    $resp = Invoke-WebRequest -Uri "$base$path" -Headers $headers -ErrorAction Stop
    return $resp.Content | ConvertFrom-Json
}

function Invoke-Post([string]$path, [hashtable]$body) {
    $json = $body | ConvertTo-Json -Compress
    $resp = Invoke-WebRequest -Uri "$base$path" -Method POST -Body $json `
        -ContentType "application/json" -Headers $headers -ErrorAction Stop
    return $resp.Content | ConvertFrom-Json
}

function Write-Section([string]$title) {
    Write-Host ""
    Write-Host ("─── $title " + ("─" * [Math]::Max(0, 60 - $title.Length - 5))) -ForegroundColor Cyan
}

function Write-Ok([string]$msg)   { Write-Host "  ✓ $msg" -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host "  ⚠ $msg" -ForegroundColor Yellow }
function Write-Bad([string]$msg)  { Write-Host "  ✗ $msg" -ForegroundColor Red }
function Write-Info([string]$msg) { Write-Host "  · $msg" -ForegroundColor Gray }

# ─── Activity loading ─────────────────────────────────────────────────────────
function Resolve-ActivityFile([string]$name) {
    $activitiesDir = Join-Path $PSScriptRoot "activities"
    if (-not (Test-Path $activitiesDir)) {
        Write-Error "activities/ directory not found at $activitiesDir"
        exit 1
    }
    # Try exact filename match first
    $direct = Join-Path $activitiesDir "$name.json"
    if (Test-Path $direct) { return $direct }

    # Search all JSON files for matching alias or name
    $lower = $name.ToLower()
    foreach ($file in (Get-ChildItem $activitiesDir -Filter "*.json")) {
        $data = Get-Content $file.FullName | ConvertFrom-Json
        if ($data.name -and $data.name.ToLower() -eq $lower) { return $file.FullName }
        if ($data.aliases) {
            foreach ($alias in $data.aliases) {
                if ($alias.ToLower() -eq $lower) { return $file.FullName }
            }
        }
    }
    Write-Error "No activity found matching '$name'. Available: $(
        (Get-ChildItem $activitiesDir -Filter '*.json').BaseName -join ', ')"
    exit 1
}

function Get-ActivityData([string]$name) {
    $file = Resolve-ActivityFile $name
    $data = Get-Content $file | ConvertFrom-Json
    Write-Info "Loaded: $($data.name)  ($file)"
    return $data
}

# ─── Bank helpers ─────────────────────────────────────────────────────────────
function Open-Bank {
    $status = Invoke-Get "/bank"
    if (-not $status.open) {
        Write-Info "Opening bank..."
        $r = Invoke-Post "/bank/open" @{}
        if (-not $r.opened) { Write-Error "Failed to open bank — are you near one?"; exit 1 }
        Start-Sleep -Milliseconds 800
        $status = Invoke-Get "/bank"
    }
    return $status
}

function Close-Bank {
    Invoke-Post "/bank/close" @{} | Out-Null
    Start-Sleep -Milliseconds 400
}

# ─── BiS analysis ─────────────────────────────────────────────────────────────
# Returns array of objects per equipment slot:
#  { Slot, BiSChain, Equipped, BestInBank, Status, NewItem, Action }
function Get-BisAnalysis($activity, $equipped, $bankItems) {
    $results = @()
    $equippedBySlot = @{}
    foreach ($item in $equipped) { $equippedBySlot[$item.slot] = $item.name }

    $bankByName = @{}
    foreach ($item in $bankItems) { $bankByName[$item.name] = $item.quantity }

    foreach ($slot in $activity.equipment.PSObject.Properties.Name) {
        $slotDef  = $activity.equipment.$slot
        $chains   = $slotDef.items   # array of arrays (each sub-array is a set of equivalent items)
        $useWield = $slotDef.PSObject.Properties['wield'] -and $slotDef.wield -eq $true
        $action   = if ($useWield) { "Wield" } else { "Wear" }

        $currentName = $equippedBySlot[$slot]

        # Flatten chains: each chain is an array of equivalent names (usually 1 item)
        # Rank 0 = best
        $ranked = @()  # array of (rank, itemName)
        $rank = 0
        foreach ($chain in $chains) {
            foreach ($itemName in $chain) {
                $ranked += [pscustomobject]@{ Rank = $rank; Name = $itemName }
            }
            $rank++
        }

        # Find equipped rank
        $equippedRank = -1
        foreach ($r in $ranked) {
            if ($currentName -and $r.Name -eq $currentName) { $equippedRank = $r.Rank; break }
        }

        # Find best available in bank
        $bestBank = $null
        foreach ($r in $ranked) {
            if ($bankByName.ContainsKey($r.Name)) { $bestBank = $r; break }
        }

        # Determine status and what to do
        $status  = ""
        $newItem = $null
        if ($equippedRank -eq 0) {
            $status = "BiS"
        } elseif ($equippedRank -gt 0) {
            if ($bestBank -ne $null -and $bestBank.Rank -lt $equippedRank) {
                $status  = "Upgrade available (rank $($bestBank.Rank))"
                $newItem = $bestBank.Name
            } else {
                $status = "Using rank-$equippedRank fallback (no upgrade in bank)"
            }
        } else {
            # Not wearing anything from our BiS list
            if ($bestBank -ne $null) {
                $status  = "Not equipped — found in bank (rank $($bestBank.Rank))"
                $newItem = $bestBank.Name
            } else {
                $status = "MISSING — not equipped and not in bank"
            }
        }

        $results += [pscustomobject]@{
            Slot       = $slot
            BiSItem    = $ranked[0].Name
            Equipped   = if ($currentName) { $currentName } else { "(nothing)" }
            BestInBank = if ($bestBank) { $bestBank.Name } else { "" }
            Status     = $status
            NewItem    = $newItem
            Action     = $action
        }
    }
    return $results
}

# ─── Inventory sort ───────────────────────────────────────────────────────────
# Compute move sequence using selection-sort with swaps.
# $targetSlots = array of {slot (int), name (string)}
# $currentItems = array of {slot (int), name (string)}  (from /inventory)
function Compute-InventoryMoves($targetSlots, $currentItems) {
    # Build simulated state: slotIndex -> itemName
    $sim = @{}
    foreach ($item in $currentItems) { $sim[[int]$item.slot] = $item.name }

    # Build target map: slotIndex -> itemName
    $target = @{}
    foreach ($t in $targetSlots) { $target[[int]$t.slot] = $t.name }

    $moves  = @()   # plain PS array — avoids empty-List-returns-null pipeline quirk
    $placed = [System.Collections.Generic.HashSet[int]]::new()

    foreach ($tSlot in ($target.Keys | Sort-Object)) {
        $tItem = $target[$tSlot]

        # Already correct in simulation?
        if ($sim.ContainsKey($tSlot) -and $sim[$tSlot] -eq $tItem) {
            $placed.Add($tSlot) | Out-Null
            continue
        }

        # Find source slot (first occurrence of tItem not yet placed)
        $src = -1
        foreach ($s in ($sim.Keys | Sort-Object)) {
            if ($sim[$s] -eq $tItem -and -not $placed.Contains($s)) {
                $src = $s
                break
            }
        }

        if ($src -eq -1) {
            Write-Warn "Item '$tItem' not found in inventory for slot $tSlot — skipping"
            continue
        }

        # Record the swap move
        $moves += @{ fromSlot = $src; slot = $tSlot }

        # Simulate the swap
        $displaced = if ($sim.ContainsKey($tSlot)) { $sim[$tSlot] } else { $null }
        $sim[$tSlot] = $tItem
        if ($null -ne $displaced) { $sim[$src] = $displaced } else { $sim.Remove($src) | Out-Null }
        $placed.Add($tSlot) | Out-Null
    }

    # Return the moves array. Using Write-Output with a no-enumerate guard so PowerShell
    # doesn't collapse an empty array to $null through the pipeline.
    Write-Output -NoEnumerate $moves
}

function Invoke-InventorySort($targetSlots) {
    $maxPasses = 4
    for ($pass = 1; $pass -le $maxPasses; $pass++) {
        $inv     = Invoke-Get "/inventory"
        $current = $inv.items | ForEach-Object { [pscustomobject]@{ slot = [int]$_.slot; name = $_.name } }
        $moves   = Compute-InventoryMoves -targetSlots $targetSlots -currentItems $current
        $moves   = @($moves)   # ensure array even when empty (guards against PS pipeline null collapse)

        if ($moves.Count -eq 0) {
            if ($pass -eq 1) { Write-Ok "Inventory already sorted." }
            else              { Write-Ok "Sort complete in $pass passes." }
            return
        }

        if ($pass -eq 1) {
            Write-Info "Sorting inventory ($($moves.Count) moves)..."
        } else {
            Write-Info "Pass $pass — $($moves.Count) correction(s)..."
        }

        $step = 1
        foreach ($move in $moves) {
            $body = $move | ConvertTo-Json -Compress
            $resp = Invoke-WebRequest -Uri "$base/inventory/move" -Method POST `
                -Body $body -ContentType "application/json" -Headers $headers -ErrorAction Stop
            $data = $resp.Content | ConvertFrom-Json
            if ($data.success) {
                Write-Ok ("[{0:D2}/{1}] {2,-32} slot {3} -> {4}" -f $step, $moves.Count, $data.name, $data.fromSlot, $data.toSlot)
            } else {
                Write-Warn ("[{0:D2}/{1}] FAILED — {2}" -f $step, $moves.Count, ($data | ConvertTo-Json -Compress))
            }
            Start-Sleep -Milliseconds 300
            $step++
        }
        Start-Sleep -Milliseconds 600  # let last drag register before re-fetching
    }
    Write-Warn "Sort may be incomplete after $maxPasses passes."
}

# ─── Inventory deposit/withdraw ───────────────────────────────────────────────
function Invoke-DepositNonKeep($keepNames) {
    $inv = Invoke-Get "/inventory"
    # Group by name — bank deposit deposits ALL of a named item at once
    $toDeposit = $inv.items | Where-Object { $_.name -notin $keepNames } |
        Select-Object -ExpandProperty name | Sort-Object -Unique

    foreach ($name in $toDeposit) {
        $r = Invoke-Post "/bank/deposit" @{ name = $name }
        if ($r.success) { Write-Info "  Deposited: $name" }
        else { Write-Warn "  Deposit failed: $name" }
        Start-Sleep -Milliseconds 300
    }
}

function Invoke-WithdrawItems($itemGroups) {
    # $itemGroups = array of {name, quantity}
    foreach ($g in $itemGroups) {
        $r = Invoke-Post "/bank/withdraw" @{ name = $g.name; quantity = [int]$g.quantity }
        if ($r.success) { Write-Info "  Withdrew: $($g.quantity)x $($g.name)" }
        else { Write-Warn "  Withdraw failed (not in bank?): $($g.quantity)x $($g.name)" }
        Start-Sleep -Milliseconds 500
    }
}

# ─── Bank item resolution helpers ───────────────────────────────────────────
# Returns the actual name available in bank for a desired item.
# If the item has a dose suffix like (4), also tries (3), (2), (1) in descending order.
function Resolve-BankItem([string]$name, [hashtable]$bankByName) {
    if ($bankByName.ContainsKey($name)) { return $name }
    # Dose pattern: "item name(N)" — try lower doses highest-first
    if ($name -match '^(.+)\((\d+)\)$') {
        $base    = $Matches[1]
        $maxDose = [int]$Matches[2]
        for ($d = $maxDose - 1; $d -ge 1; $d--) {
            $dosed = "$base($d)"
            if ($bankByName.ContainsKey($dosed)) { return $dosed }
        }
    }
    return $null
}

# ─── Inventory substitution ──────────────────────────────────────────────────
# Resolves each inventory slot's fallback chain against the current bank.
# JSON slots may use either:
#   "name": "item"                 — single item, no substitution
#   "items": ["BiS", "alt", ...]   — picks the first one found in bank
# Returns array of {slot (int), name (string), type (string)}.
function Resolve-InventoryLayout($invSlots, $bankByName) {
    $resolved = @()
    foreach ($slot in $invSlots) {
        $hasChain   = $null -ne $slot.PSObject.Properties['items']
        $candidates = if ($hasChain) { @($slot.items) } else { @($slot.name) }

        # Keep items live in inventory and are never deposited — include as-is
        if ($slot.type -eq 'keep') {
            $resolved += [pscustomobject]@{ slot = [int]$slot.slot; name = $candidates[0]; type = $slot.type }
            continue
        }

        $chosen = $null
        $idx    = 0
        foreach ($candidate in $candidates) {
            $actual = Resolve-BankItem -name $candidate -bankByName $bankByName
            if ($null -ne $actual) {
                $chosen = $actual
                break
            }
            $idx++
        }
        if ($null -ne $chosen) {
            if ($hasChain -and $idx -gt 0) {
                # Different item from fallback chain (may also be a lower dose — show final name)
                Write-Host ("  Slot {0:D2}: {1} → {2}  (rank-{3} sub)" -f [int]$slot.slot, $candidates[0], $chosen, $idx) -ForegroundColor Yellow
            } elseif ($chosen -ne $candidates[0]) {
                # Same item, lower dose
                $doseNum = if ($chosen -match '\((\d+)\)$') { $Matches[1] } else { '?' }
                Write-Host ("  Slot {0:D2}: {1} → ({2} doses)" -f [int]$slot.slot, $candidates[0], $doseNum) -ForegroundColor DarkYellow
            }
        }

        if ($null -eq $chosen) {
            Write-Warn "  Slot $([int]$slot.slot) ('$($candidates[0])'): not found in bank — slot will be empty"
            continue
        }
        $resolved += [pscustomobject]@{ slot = [int]$slot.slot; name = $chosen; type = $slot.type }
    }
    Write-Output -NoEnumerate $resolved
}

function Invoke-EquipItem([string]$name, [string]$action) {
    $r = Invoke-Post "/inventory/interact" @{ name = $name; action = $action }
    if ($r.success) { Write-Ok "  Equipped: $name ($action)" }
    else {
        # Try the other action in case JSON definition is wrong
        $alt = if ($action -eq "Wear") { "Wield" } else { "Wear" }
        $r2 = Invoke-Post "/inventory/interact" @{ name = $name; action = $alt }
        if ($r2.success) { Write-Ok "  Equipped: $name ($alt)" }
        else { Write-Warn "  Failed to equip: $name" }
    }
    Start-Sleep -Milliseconds 600
}

# ─── Main ─────────────────────────────────────────────────────────────────────
$resolvedInventory = $null   # set during inventory phase, consumed by sort phase
Write-Host ""
Write-Host "══════════════════════════════════════════════════════" -ForegroundColor Magenta
Write-Host "  OSRS Activity Prep  ·  $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Magenta
Write-Host "══════════════════════════════════════════════════════" -ForegroundColor Magenta

# Verify connection
try {
    $state = Invoke-Get "/state"
    if (-not $state.loggedIn) { Write-Error "Not logged in."; exit 1 }
    Write-Ok "Connected — $($state.player.name) @ ($($state.player.position.x), $($state.player.position.y))"
} catch {
    Write-Error "Cannot reach Agent Server: $_"
    exit 1
}

# Load activity
Write-Section "Activity"
$act = Get-ActivityData $Activity
Write-Ok "Activity: $($act.name)"
if ($act.notes) { Write-Info $act.notes }

# ─── Phase 1: Equipment analysis ──────────────────────────────────────────────
if (-not $SkipEquipment) {
    Write-Section "Equipment Analysis"

    # Try the /equipment endpoint — only available after a client restart post-compile
    $equipped = $null
    try {
        $equipped = (Invoke-Get "/equipment").items
    } catch {
        Write-Warn "/equipment endpoint not available (needs client restart to activate)."
        Write-Warn "Run: .\gradlew run  or restart the RuneLite launcher."
        Write-Info "Skipping equipment phase — inventory setup will continue."
        $SkipEquipment = $true
    }
}

if (-not $SkipEquipment) {
    $bankData  = Open-Bank
    $bankItems = $bankData.items
    Close-Bank

    $analysis = Get-BisAnalysis $act $equipped $bankItems

    # Print report
    $fmtSlot   = "{0,-7}"
    $fmtBiS    = "{0,-32}"
    $fmtStatus = "{0,-38}"
    $fmtCur    = "{0}"
    Write-Host ("  " + ($fmtSlot -f "Slot") + ($fmtBiS -f "BiS Target") + ($fmtStatus -f "Status") + ($fmtCur -f "Currently Equipped")) -ForegroundColor DarkGray

    $needsGearChange = $false
    foreach ($row in $analysis) {
        $line = "  " + ($fmtSlot -f $row.Slot) + ($fmtBiS -f $row.BiSItem) + ($fmtStatus -f $row.Status) + ($fmtCur -f $row.Equipped)
        if ($row.Status -eq "BiS") {
            Write-Host $line -ForegroundColor Green
        } elseif ($row.NewItem) {
            Write-Host $line -ForegroundColor Yellow
            $needsGearChange = $true
        } elseif ($row.Status -match "MISSING") {
            Write-Host $line -ForegroundColor Red
        } else {
            Write-Host $line -ForegroundColor DarkYellow
        }
    }

    if (-not $DryRun -and $needsGearChange) {
        Write-Section "Equipment Update"
        $upgrades = $analysis | Where-Object { $_.NewItem }

        # Step 1: Open bank, deposit all non-keep, withdraw new gear
        $keepNames = ($act.inventory | Where-Object { $_.type -eq "keep" }).name
        $bankData  = Open-Bank
        Invoke-DepositNonKeep -keepNames $keepNames
        foreach ($up in $upgrades) {
            Write-Info "  Withdrawing: $($up.NewItem)"
            Invoke-Post "/bank/withdraw" @{ name = $up.NewItem; quantity = 1 } | Out-Null
            Start-Sleep -Milliseconds 500
        }
        Close-Bank

        # Step 2: Equip each new item
        foreach ($up in $upgrades) {
            Invoke-EquipItem -name $up.NewItem -action $up.Action
        }

        # Step 3: Re-open bank and deposit displaced gear
        $bankData = Open-Bank
        Invoke-DepositNonKeep -keepNames $keepNames
        # Don't close yet — inventory phase will use the open bank
        $bankAlreadyOpen = $true
    } elseif (-not $DryRun) {
        Write-Ok "All equipment is correct — no changes needed."
        $bankAlreadyOpen = $false
    } else {
        $bankAlreadyOpen = $false
    }
} else {
    $bankAlreadyOpen = $false
    Write-Info "Skipping equipment phase."
}

# ─── Phase 2: Inventory setup ─────────────────────────────────────────────────
if (-not $SkipInventory) {
    Write-Section "Inventory Setup"

    # Keep names are never substituted — they always live in inventory
    $keepNames = ($act.inventory | Where-Object { $_.type -eq 'keep' }).name

    if ($DryRun) {
        Write-Host ""
        Write-Host "  Inventory plan:" -ForegroundColor DarkGray
        foreach ($slot in ($act.inventory | Sort-Object { [int]$_.slot })) {
            $hasChain  = $null -ne $slot.PSObject.Properties['items']
            if ($hasChain) {
                $fallbackCount = @($slot.items).Count - 1
                $label = if ($fallbackCount -gt 0) { "$($slot.items[0])  (+$fallbackCount fallback)" } else { $slot.items[0] }
            } else {
                $label = $slot.name
            }
            $typeColor = if ($slot.type -eq "keep") { "DarkGray" } elseif ($slot.type -eq "supply") { "Cyan" } else { "White" }
            Write-Host ("  [{0:D2}] {1,-50} [{2}]" -f [int]$slot.slot, $label, $slot.type) -ForegroundColor $typeColor
        }
    } else {
        # Open bank if not already open from gear phase
        if (-not $bankAlreadyOpen) {
            Open-Bank | Out-Null
            Invoke-DepositNonKeep -keepNames $keepNames
        }

        # Re-fetch bank AFTER deposit — includes items just returned from inventory
        $bankItems = (Invoke-Get "/bank").items
        $bankByName = @{}
        foreach ($bi in $bankItems) { $bankByName[$bi.name] = [int]$bi.quantity }

        # Resolve substitutions: pick first available item per slot from fallback chain
        $resolvedInventory = @(Resolve-InventoryLayout -invSlots $act.inventory -bankByName $bankByName)
        $invTarget = $resolvedInventory | Where-Object { $_.type -ne 'keep' }

        # Group by name to get withdrawal quantities
        $withdrawGroups = @{}
        foreach ($item in $invTarget) {
            if ($withdrawGroups.ContainsKey($item.name)) { $withdrawGroups[$item.name]++ }
            else { $withdrawGroups[$item.name] = 1 }
        }

        $withdrawList = $withdrawGroups.GetEnumerator() |
            ForEach-Object { [pscustomobject]@{ name = $_.Key; quantity = $_.Value } } |
            Sort-Object name
        Invoke-WithdrawItems -itemGroups $withdrawList

        Close-Bank
        $bankAlreadyOpen = $false
    }
} else {
    Write-Info "Skipping inventory phase."
}

# ─── Phase 3: Sort ────────────────────────────────────────────────────────────
if (-not $SkipSort) {
    Write-Section "Inventory Sort"

    if ($DryRun) {
        Write-Host "  Target layout:" -ForegroundColor DarkGray
        foreach ($slot in ($act.inventory | Sort-Object { [int]$_.slot })) {
            $hasChain = $null -ne $slot.PSObject.Properties['items']
            $label    = if ($hasChain) { $slot.items[0] } else { $slot.name }
            Write-Host ("  [{0:D2}] {1}" -f [int]$slot.slot, $label) -ForegroundColor DarkGray
        }
    } else {
        # If inventory phase was skipped, build resolved layout from the original JSON
        # (uses first item in each chain — matches whatever the user already has in inventory)
        if ($null -eq $resolvedInventory) {
            $resolvedInventory = $act.inventory | ForEach-Object {
                $candidates = if ($_.PSObject.Properties['items']) { @($_.items) } else { @($_.name) }
                [pscustomobject]@{ slot = [int]$_.slot; name = $candidates[0]; type = $_.type }
            }
        }

        # Build target slot list (includes keep items since they stay in inventory)
        $targetSlots = $resolvedInventory | ForEach-Object {
            [pscustomobject]@{ slot = [int]$_.slot; name = $_.name }
        }
        Invoke-InventorySort -targetSlots $targetSlots

        # Verify
        Write-Host ""
        Write-Info "Final inventory:"
        $final = Invoke-Get "/inventory"
        $final.items | Sort-Object slot | ForEach-Object {
            Write-Host ("    [{0:D2}] {1}" -f [int]$_.slot, $_.name) -ForegroundColor Gray
        }
    }
} else {
    Write-Info "Skipping sort phase."
}

Write-Host ""
Write-Host "══════════════════════════════════════════════════════" -ForegroundColor Magenta
if ($DryRun) {
    Write-Host "  DRY RUN COMPLETE — no actions were taken" -ForegroundColor Yellow
} else {
    Write-Host "  DONE — ready for $($act.name)" -ForegroundColor Green
}
Write-Host "══════════════════════════════════════════════════════" -ForegroundColor Magenta
Write-Host ""

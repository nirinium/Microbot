#!/usr/bin/env pwsh
# Sort inventory to wiki ToA Masori/Expert layout.
# Requires the /inventory/move endpoint (added this session).
# Run AFTER restarting the client.
#
# Target layout:
#  0  Masori body (f)           7  Necklace of anguish (or)  15  Divine super combat(4)   22  Karambwan
#  1  Twisted bow               8  Avernic treads (max) *    16  Divine ranging(4)         23  Karambwan
#  2  Echo virtus robe top      9  Occult necklace (or)      17  Antidote++(4)             24  Karambwan
#  3  Masori chaps (f)         10  Tormented bracelet (or)   18  Antidote++(4)             25  Book of the dead
#  4  Masori mask (f)          11  Bandos godsword           19  Super restore(4)          26  Rune pouch *
#  5  Echo virtus robe bottom  12  Ava's assembler           20  Super restore(4)          27  Teleport to house *
#  6  Imbued zamorak cape      13  (empty)                   21  Karambwan
# * = already correct, not moved

$tokenFile = "$env:USERPROFILE\.runelite\.agent-token"
if (-not (Test-Path $tokenFile)) { Write-Error "Agent token not found at $tokenFile"; exit 1 }
$token = (Get-Content $tokenFile -Raw).Trim()
$headers = @{ "X-Agent-Token" = $token }
$base = "http://127.0.0.1:8081"

# 25-move sequence for current inventory state:
#   slot 0  = EMPTY (free slot — Masori mask already moved to slot 8 by earlier test)
#   slot 8  = Masori mask (f)  (must reach slot 4)
#   slot 21 = Karambwan (already correct, untouched)
#   slot 26/27 = Rune pouch / Teleport (fixed)
# Cycle A (length 21): uses slot 0 as free slot, ends with slot 8 empty
# Cycle B (length 4):  uses slot 8 (now free) as temp buffer
$moves = @(
    # --- Cycle A: slot 0 is empty, route items into it ---
    @{fromSlot=3;  slot=0},    # Masori body(f)     3->0
    @{fromSlot=6;  slot=3},    # Masori chaps(f)    6->3
    @{fromSlot=16; slot=6},    # Imbued zammy      16->6
    @{fromSlot=7;  slot=16},   # Divine ranging     7->16
    @{fromSlot=2;  slot=7},    # Necklace of ang.   2->7
    @{fromSlot=13; slot=2},    # Virtus robe top   13->2
    @{fromSlot=18; slot=13},   # Eye of ayak       18->13
    @{fromSlot=10; slot=18},   # Antidote++        10->18
    @{fromSlot=17; slot=10},   # Tormented brc     17->10
    @{fromSlot=9;  slot=17},   # Antidote++         9->17
    @{fromSlot=15; slot=9},    # Occult necklace   15->9
    @{fromSlot=5;  slot=15},   # Div super cbt      5->15
    @{fromSlot=14; slot=5},    # Virtus robe bot   14->5
    @{fromSlot=19; slot=14},   # Keris partisan    19->14
    @{fromSlot=11; slot=19},   # Super restore     11->19
    @{fromSlot=20; slot=11},   # Bandos gs         20->11
    @{fromSlot=12; slot=20},   # Super restore     12->20
    @{fromSlot=1;  slot=12},   # Ava's assembler    1->12
    @{fromSlot=4;  slot=1},    # Twisted bow        4->1
    @{fromSlot=8;  slot=4},    # Masori mask(f)     8->4  (slot 8 now empty)
    # --- Cycle B: slot 8 is empty, use as temp ---
    @{fromSlot=22; slot=8},    # Book of dead      22->temp(8)
    @{fromSlot=23; slot=22},   # Karambwan         23->22
    @{fromSlot=24; slot=23},   # Karambwan         24->23
    @{fromSlot=25; slot=24},   # Karambwan         25->24
    @{fromSlot=8;  slot=25}    # Book of dead  temp(8)->25
)

Write-Host "Sorting inventory (25 moves)..."
$step = 1
foreach ($move in $moves) {
    $body = $move | ConvertTo-Json -Compress
    try {
        $resp = Invoke-WebRequest -Uri "$base/inventory/move" -Method POST `
            -Body $body -ContentType "application/json" -Headers $headers -ErrorAction Stop
        $data = $resp.Content | ConvertFrom-Json
        if ($data.success) {
            Write-Host ("  [{0:D2}/25] OK  {1,-30} slot {2} -> {3}" -f $step, $data.name, $data.fromSlot, $data.toSlot)
        } else {
            Write-Warning ("  [{0:D2}/25] FAIL {1,-30} slot {2} -> {3}" -f $step, $data.name, $data.fromSlot, $data.toSlot)
        }
    } catch {
        Write-Error "  [$step/25] HTTP error: $_"
        exit 1
    }
    Start-Sleep -Milliseconds 300   # wait for drag to register
    $step++
}

Write-Host ""
Write-Host "Done. Verifying layout..."
$inv = (Invoke-WebRequest -Uri "$base/inventory" -Headers $headers).Content | ConvertFrom-Json
$inv.items | Format-Table @{L="Slot";E={$_.slot}}, @{L="Item";E={$_.name}} -AutoSize

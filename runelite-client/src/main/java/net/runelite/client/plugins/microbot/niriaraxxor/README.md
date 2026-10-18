# Niri Araxxor

Niri Araxxor automates travel, entry, combat, harvesting, looting, and repeated kills of Araxxor. It supports melee and ranged setups, optional defence-drain special attacks, araxyte weapon switches, configurable arena anchoring, and live fight diagnostics.

## Quick start

1. Bring an Araxyte or spider Slayer task.
2. Configure the primary weapon, optional offhand/ammo, araxyte weapon, food, potions, and teleport item.
3. Stand on the preferred arena tile and press **Capture start tile** once.
4. Enable **Auto-enter and fight** if the script should enter the tunnel and begin without manual placement.
5. Start the script at a bank, the Morytania Spider Cave entrance, the lair door, or inside the arena.

Rebuild and restart the client after changing source code:

```powershell
.\gradlew.bat :client:compileJava
.\gradlew.bat :client:run
```

## Recommended ranged setup

- Primary weapon: Twisted bow or another configured ranged weapon.
- Ammo: matching arrows or ammunition.
- Araxyte weapon: Heavy ballista with javelins, a halberd, or another suitable weapon.
- Prayers: Protect from Melee, Protect from Missiles, Protect from Magic, and Rigour.
- Supplies: extended anti-venom+, ranging potion, prayer restoration, high-healing food, and a configured emergency teleport.

When a ballista is configured, the script equips the best supported javelin available and restores the primary ranged weapon as soon as the combat XP drop confirms the araxyte hit.

## Banking and restocking

Enable **Bank between trips** and select an **Inventory setup** to automate restocking. The script checks the selected setup before travelling, walks to the nearest bank when needed, restores its equipment and inventory, and resumes after planned or low-supply teleports.

Create the setup with the Inventory Setups plugin while wearing the complete combat equipment and carrying the desired supplies, switches, ammunition, teleport, and optional thrall items. Stack comparison and fuzzy matching settings from the setup are honored by the shared loader.

If automatic banking is disabled, the script retains the manual workflow and stops after teleporting. A missing setup while banking is enabled stops the script with an explanatory status.

## Inventory setup

Inventory quantities are starting points, not hard requirements. Adjust food and prayer restoration using the dashboard's per-kill consumption data. Four kills require enough supplies for the worst fight in the trip, not merely four times the best fight.

### Example ranged inventory

| Slots | Item | Purpose |
|---:|---|---|
| 1 | Heavy ballista or configured araxyte weapon | Kills mirrorback/acidic araxytes without remaining on the switch weapon. |
| 1 | Best available javelin stack | Equipped automatically when a ballista is selected. Keep it in inventory at startup unless already equipped. |
| 0–1 | Elder maul or Dragon warhammer | Optional opening defence-drain special attack. |
| 1 | Ranging potion | Used when the ranged boost expires. |
| 1 | Extended anti-venom+ | Maintains venom protection. |
| 3–5 | Prayer potion or restore | Consumed below **Drink prayer at %**. Start with four and tune from actual trips. |
| 1 | Configured teleport item | Required for emergency and planned exits. |
| Remaining | High-healing food | Used adaptively; leave loot capacity as described below. |

With a spec weapon, ballista, javelins, one ranging potion, one anti-venom, four prayer potions, and one teleport, 18 inventory slots remain for food or loot space. Without the spec weapon, 19 remain.

### Example melee inventory

| Slots | Item | Purpose |
|---:|---|---|
| 0–1 | Main weapon | Needed in inventory only when the opening spec weapon is initially equipped. |
| 0–1 | Shield or defender | Needed in inventory when it is not initially equipped. |
| 1 | Araxyte switch weapon | Halberd, crush weapon, or another configured option for supported araxytes. |
| 0–1 | Elder maul or Dragon warhammer | Optional opening defence-drain special attack. |
| 1 | Super combat potion | Used when the melee boost expires. |
| 1 | Extended anti-venom+ | Maintains venom protection. |
| 3–5 | Prayer potion or restore | Tune the quantity using trip results. |
| 1 | Configured teleport item | Must support `Break`, `Rub`, or `Teleport`. |
| Remaining | High-healing food | Prefer food with enough healing for enrage recovery. |

### Optional thralls

Thralls require the correct spellbook, Book of the Dead where applicable, and the necessary runes. Each non-rune-pouch stack consumes another inventory slot. Reduce food quantities accordingly and confirm the configured thrall can be cast before relying on it for trip DPS.

### Equipment and ammunition behavior

- The configured primary weapon should normally be equipped at startup.
- Primary ranged ammunition may be equipped or carried as appropriate for the weapon.
- Ballista javelins must be available when the ballista switch occurs.
- When the script changes ammunition, the previously equipped ammunition needs a free inventory slot unless it can merge into an existing stack.
- Item names are substring-matched by the inventory utilities, but using the exact visible name avoids selecting an unintended variant.
- Special attacks require at least 50% energy per configured hit. Two hits require the available energy or a supported restoration workflow.

### Loot space

Do not fill all 28 slots unless existing stacks can absorb every expected drop. A full inventory can prevent non-stackable instance loot from being collected.

- Recommended: begin with two to four free slots when learning the trip.
- If inventory is full and HP is missing, the script may eat one food to create space.
- If inventory is full at maximum HP, the overlay reports **Loot blocked — inventory full** and the script will not intentionally leave recoverable instance loot behind.
- Stackable drops only need a free slot when their stack does not already exist.

### Pre-start inventory checklist

- [ ] Primary weapon and matching offhand/ammo are equipped or available.
- [ ] Configured araxyte weapon is available.
- [ ] Javelins are available when using a ballista.
- [ ] Optional spec weapon and required special energy are available.
- [ ] Combat potion matches the selected combat style.
- [ ] Extended anti-venom+ is present when enabled.
- [ ] Prayer restoration is present.
- [ ] Enough food remains for the configured kills per trip.
- [ ] Configured teleport item is present and its name matches.
- [ ] Thrall book/runes are present when thralls are enabled.
- [ ] At least two loot slots are free or can be created safely.

## Important combat settings

### Enrage: stay in melee range

For ranged setups, the script uses weapon cooldown time to return to a safe tile within Araxxor's melee distance. Cleave, acid, and ruptura safety may temporarily move the player farther away.

### Enrage: queue every attack

Re-clicks Araxxor on the earliest legal weapon-ready tick so movement does not leave the player idle. This removes avoidable idle ticks but cannot exceed the weapon's server-defined attack speed.

### Enrage: post-dodge delay

- `0`: fastest DPS; attack on the first safe tick after a cleave dodge.
- `1`: one extra safety tick if attack clicks pull the player back toward danger.
- `2`: conservative fallback for unstable pathing.

Start with `0` and increase only if the fight log shows attacks undoing successful dodges.

### Starting tile radius

The captured starting tile is a tactical anchor, not a tile the script will blindly occupy. Known acid, collision, walls, cleaves, and urgent mechanics override it. Routine correction uses short steps and happens during weapon cooldowns.

## Mechanic behavior

| Mechanic | Script response |
|---|---|
| Standard melee/ranged/magic | Tracks attack animations and projectiles, then selects the matching protection prayer. |
| Acid Ball / cannon | Primes from the acidic special cycle, detects the ranged-looking launch animation, enables run, and takes a short diagonal route out of the three-wide trajectory. Projectile-NPC detection remains a fallback. |
| Acid Drip | Deliberately walks through the under-boss/corner sequence for six ticks to control pool placement while retaining attacks where safe. |
| Acid pools | Records projectile landings and spawned pool objects, rejects contaminated destinations, and replans if a reserved tile becomes poisoned. |
| Cleave / enrage | Predicts and confirms the cleave cadence, runs out of the target strip, then resumes the primary weapon on the configured recovery tick. |
| Ruptura araxyte | Never attacks it. The script creates distance using traversable tiles and avoids routing into arena walls. |
| Mirrorback / acidic araxyte | Uses the configured araxyte weapon, then rapidly restores the primary loadout after a confirmed hit. |
| Acidic death splatter | Moves away from the death area before resuming ordinary combat. |

Emergency movement always outranks eating, equipment changes, optional anchoring, and attacks. After movement is issued, the script requires observed player displacement instead of assuming a click succeeded.

## Entry, kills, and looting

- **Travel to Araxxor** uses the web walker to reach the Morytania Spider Cave and continue toward the lair.
- **Auto-enter and fight** interacts with the nearby lair tunnel and immediately heads for the captured start tile.
- After a kill, the script harvests or destroys the corpse according to configuration.
- Instance-only loot is collected before low-supply or trip-limit teleport logic is allowed to run.
- If another kill will be attempted, the script returns to the configured starting area after looting.
- **Kills per trip** controls the planned trip limit; `0` means remain until supplies require leaving.

## Food and supply management

The script selects food based on missing hitpoints rather than always consuming the largest item. Adaptive eating raises the threshold during enrage and acid mechanics, but immediate movement is handled before eating. Emergency teleporting occurs only when the configured HP condition is met and no usable food remains; post-kill loot interlocks take priority.

After post-kill loot is secure, the next kill requires at least one recognized food item and one recognized prayer-restoration dose. If either category is empty, the script reports **Low supplies — teleporting**. This check ends the trip; it does not perform a bank restock.

## Fight dashboard

Enable **Record fight timeline**, then run the dashboard in a second terminal:

```powershell
.\gradlew.bat :client:openAraxxorDashboard
```

Open `http://127.0.0.1:8082/` if the browser does not open automatically. The dashboard shows recent attacks, movement, damage, acid exposure, prayer misses, cleave timing, food use, and the latest kill summary. See [the dashboard guide](../../../../../../../../../../docs/ARAXXOR_DASHBOARD.md) for port and no-browser options.

## Troubleshooting

### My changes are not appearing

Stop the running client, rebuild, and start it again. `:client:run` does not hot-reload compiled Java changes.

### The script is waiting at the lair

Confirm **Auto-enter and fight** is enabled. If it is disabled, the script intentionally waits for manual entry or placement.

### The script waits for the starting tile

Capture a start tile from inside the active arena instance. The saved value is a template coordinate and is resolved into each new instance.

### It says “movement not observed”

The clicked tile was occupied, unreachable, the click was swallowed, or the player did not leave the origin before the movement deadline. The script should clear that route and choose another tile. Enable fight diagnostics if it repeats.

### Acid or cleave behavior looks wrong

Enable **Record fight timeline**, reproduce one fight, and inspect the dashboard or `~/.runelite/logs/client.log`. Useful events include `BOSS_ANIMATION`, `CANNON_ARMED`, `CANNON_RUN_LOCK`, `CLEAVE_PREDICTED`, `CLEAVE_CONFIRMED`, `MOVE`, `MOVE_TIMEOUT`, `PLAYER_DAMAGE`, and `FOOD`.

### The script enters ERROR state

Copy the first exception and its complete `Caused by` section from `client.log`. Later ERROR snapshots show the result but usually not the original cause.

## Safety notes

- Keep a configured teleport item in the inventory.
- Verify the captured tile after changing arena strategy.
- Do not set an aggressive eating threshold that consumes the trip's food during harmless chip damage.
- Mechanics and collision layouts can change after game updates; use diagnostics to validate animations, projectile IDs, and object IDs after an update.
- The overlay status is the fastest way to identify which branch currently owns movement or combat.

## Developer validation

Compile the client after changes:

```powershell
.\gradlew.bat :client:compileJava
```

The script loop runs off the client thread. Game-state reads and actions must continue using the existing client-thread/queryable APIs, and waits must remain interruptible so urgent mechanics can pre-empt ordinary combat.

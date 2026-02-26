

  

Searched for regex `sleep\(|sleepUntil\(|TimeUnit|100ms|tick` (`**/niriaraxxor/AraxxorScript.java`), 20 results

Searched for regex `sleep\(\d+\)|sleepUntil\(` (`**/niriaraxxor/AraxxorScript.java`), 34 results

Summarized conversation historyNow let me read the full file to get context around each of these: 

Read [](file:///c%3A/Users/nirinium/Documents/GitHub/Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/niriaraxxor/AraxxorScript.java#1-1), lines 1 to 100

Read [](file:///c%3A/Users/nirinium/Documents/GitHub/Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/niriaraxxor/AraxxorScript.java#100-100), lines 100 to 200

Read [](file:///c%3A/Users/nirinium/Documents/GitHub/Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/niriaraxxor/AraxxorScript.java#200-200), lines 200 to 400

Read [](file:///c%3A/Users/nirinium/Documents/GitHub/Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/niriaraxxor/AraxxorScript.java#400-400), lines 400 to 600

Read [](file:///c%3A/Users/nirinium/Documents/GitHub/Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/niriaraxxor/AraxxorScript.java#600-600), lines 600 to 800

Read [](file:///c%3A/Users/nirinium/Documents/GitHub/Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/niriaraxxor/AraxxorScript.java#800-800), lines 800 to 1000

Read [](file:///c%3A/Users/nirinium/Documents/GitHub/Microbot/runelite-client/src/main/java/net/runelite/client/plugins/microbot/niriaraxxor/AraxxorScript.java#1000-1000), lines 1000 to 1199

Here's the complete breakdown of all timings and sleeps in the Araxxor combat script:

---

## Loop Timing

| Location | Value | Purpose |
|---|---|---|
| AraxxorScript.java | **100ms** fixed delay | Core loop interval — fast for responsive dodging (game tick = 600ms, so ~6 checks per tick) |

---

## `sleep()` Calls (Thread Pauses)

### Safety / Eating
| Line | Value | Context |
|---|---|---|
| AraxxorScript.java | `sleep(300)` | After eating food (half a game tick — lets eat animation register) |
| AraxxorScript.java | `sleep(300)` | Eat before looting to free inventory space |

### Movement / Repositioning
| Line | Value | Context |
|---|---|---|
| AraxxorScript.java | `sleep(100)` | After walking to south-side preferred tile in normal combat |
| AraxxorScript.java | `sleep(100)` | After `walkFastLocal` in `fleeFromRuptura()` — minimal delay, pure reactive movement |
| AraxxorScript.java | `sleep(300)` | After walking to lure position (opposite side of Araxxor from ruptura) |
| AraxxorScript.java | `sleep(200)` | Enrage phase — step under Araxxor tile |
| AraxxorScript.java | `sleep(200)` | `dodgeCleave()` — after walking 2 tiles away on "Skree!" |
| AraxxorScript.java | `sleep(300)` | `dodgeAcidCannon()` — after 3-tile perpendicular sidestep |
| AraxxorScript.java | `sleep(300)` | Acidic death — move away from 7x7 AoE (after kill) |
| AraxxorScript.java | `sleep(200)` | `returnToCenter()` — walk 2 tiles toward arena center |
| AraxxorScript.java | `sleep(200)` | `moveOffAcid()` — step 1 tile off acid pool |

### Acid Drip Phases
| Line | Value | Context |
|---|---|---|
| AraxxorScript.java | `sleep(300)` | Phase 0: step under boss |
| AraxxorScript.java | `sleep(200)` | Phase 1: step 1 tile off boss |
| AraxxorScript.java | `sleep(200)` | Phase 1: attack boss (after moving off) |

### Combat Interactions (Attacking)
| Line | Value | Context |
|---|---|---|
| AraxxorScript.java | `sleep(600)` | Normal fight — after `interact(araxxor, "attack")` (= 1 game tick) |
| AraxxorScript.java | `sleep(600)` | Mirrorback — after `interact(mirrorback, "attack")` |
| AraxxorScript.java | `sleep(600)` | Acidic — after `interact(acidic, "attack")` |
| AraxxorScript.java | `sleep(600)` | Enrage — after `interact(araxxor, "attack")` (from under) |

### Weapon Switching
| Line | Value | Context |
|---|---|---|
| AraxxorScript.java | `sleep(300)` | Mirrorback — after equipping araxyte switch weapon |
| AraxxorScript.java | `sleep(300)` | Acidic — after equipping araxyte switch weapon |
| AraxxorScript.java | `sleep(150)` | `switchToMainWeapon()` — after equipping main weapon |
| AraxxorScript.java | `sleep(150)` | `switchToMainWeapon()` — after equipping shield |

### Special Attack
| Line | Value | Context |
|---|---|---|
| AraxxorScript.java | `sleep(300)` | After equipping spec weapon |
| AraxxorScript.java | `sleep(100)` | After toggling spec orb on |
| AraxxorScript.java | `sleep(600)` | After attacking with spec (1 game tick for hit to register) |

### Potions
| Line | Value | Context |
|---|---|---|
| AraxxorScript.java | `sleep(300)` | After drinking combat potion |
| AraxxorScript.java | `sleep(300)` | After drinking anti-venom |
| AraxxorScript.java | `sleep(300)` | After drinking prayer potion |

### Post-fight / Loot
| Line | Value | Context |
|---|---|---|
| AraxxorScript.java | `sleep(1200)` | After interacting with corpse (Harvest/Destroy — long animation) |
| AraxxorScript.java | `sleep(600, 1200)` | After main loot pass (randomized 600-1200ms) |

---

## `sleepUntil()` Calls (Condition Waits)

| Line | Timeout | Interval | Condition | Idle Callback |
|---|---|---|---|---|
| AraxxorScript.java | **8000ms** | **200ms** | Ruptura dead/despawned. Re-flees at ≤2 tiles inside loop. | Eats food if low HP |
| AraxxorScript.java | **6000ms** | **600ms** | Mirrorback dead. Also dodges cleave inside condition. | Re-attacks if player stopped interacting |
| AraxxorScript.java | **6000ms** | **600ms** | Acidic dead. Also dodges cleave + acid cannon inside condition. | Re-attacks if player stopped interacting |
| AraxxorScript.java | **5000ms** | *(default)* | Left instanced region after emergency teleport. | None |

---

## Tick-Based Timing

| Location | Value | Purpose |
|---|---|---|
| AraxxorScript.java | **6 game ticks** | Acid drip duration — after 6 ticks, `acidDripActive` is set to false |

---

### Summary of timing tiers used:

- **100ms** — minimal/reactive (flee, reposition, spec toggle)
- **150ms** — weapon equip (main weapon / shield)
- **200ms** — dodge movement (cleave, return to center, move off acid, acid drip step-off)
- **300ms** — interaction acknowledgment (eat, drink potion, weapon switch, acid cannon dodge, lure position, acid drip step-under)
- **600ms** — full game tick (attack interactions, normal combat)
- **1200ms** — long animations (corpse harvest/destroy)
- **sleepUntil 200ms interval** — ruptura death wait (fast polling for re-flee)
- **sleepUntil 600ms interval** — minion death wait (one poll per tick)
# Abyssal Sire Script — Complete

## Files Created

| File | Lines | Purpose |
|------|-------|---------|
| `SireState.java` | 23 | State enum (IDLE, STUNNING, DESTROYING_VENTS, FIGHTING, DODGING, LOOTING, etc.) |
| `SireConfig.java` | 215 | Config interface with sections: Combat, Prayer, Potions, Loot, Safety |
| `SireOverlay.java` | 130 | Status overlay — phase, state, HP, prayer, vents, stun timer |
| `SirePlugin.java` | 151 | Event wiring — animation, NPC spawn/despawn detection |
| `SireScript.java` | 825 | Main fight logic — all 3 phases, spec, dodge, loot |

## Key Features

- **Phase 1:** Shadow Barrage stun + Scorching bow for vent destruction, auto re-stun when timer runs low
- **Phase 2:** Elder maul double spec at start, melee combat, prayer switch to Protect from Missiles when Sire panics (50% HP)
- **Phase 3:** Melee with explosion dodge — pre-computed destination, immediate walk from event handler (same pattern as Araxxor acid cannon)
- **Miasma dodge:** All phases, 2-tile cardinal/diagonal escape
- **Spawns/scions:** Ignored (tanked)
- **Banking:** Placeholder method for future implementation
- **Row positions:** Offsets relative to Sire spawn position (ROW1_DY=-2, ROW2_DY=-5, ROW3_DY=-8) — placeholders needing tuning in-game

## Tuning Checklist

- [ ] Row tile offsets (ROW1_DY, ROW2_DY, ROW3_DY, STUN_DY) — need exact SW room positioning
- [ ] Stun duration — currently 50 ticks with re-stun at 8 ticks remaining
- [ ] Vent targeting — currently attacks nearest lung NPC; may need specific ordering
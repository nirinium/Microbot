package net.runelite.client.plugins.microbot.nirisire;

public enum SireState {
    IDLE,

    // Banking & travel
    TELEPORTING_OUT,   // Teleporting away from boss arena (house tab, etc.)
    WALKING_TO_BANK,   // Walking to the nearest bank
    BANKING,           // Interacting with bank — restocking via Inventory Setups
    TELEPORTING_BACK,  // Using POH fairy ring / teleport to return
    WALKING_TO_SIRE,   // Walking from fairy ring to the SW room

    // Phase 1: Vent destruction
    STUNNING_SIRE,     // Casting Shadow Barrage
    WALKING_TO_VENT,   // Moving toward out-of-range respiratory system
    DESTROYING_VENTS,  // Attacking respiratory systems with Scorching bow
    TRANSITION_ATTACK, // Attacking Sire during Phase 1→2 transition

    // Phase 2: Melee combat
    SPEC_ATTACK,       // Defence drain spec (Phase 2) or damage spec (Phase 3)
    FIGHTING_MELEE,    // Main melee DPS

    // Phase 3: Endgame
    FIGHTING_PHASE3,       // Phase 3 Stage I (>35% HP, miasma attacks, tentacles active)
    FIGHTING_PHASE3_FINAL, // Phase 3 Stage II (post-explosion, finishing)
    DODGING_EXPLOSION,     // 2-tick explosion dodge

    // Shared
    DODGING_MIASMA,    // Moving off miasma pool
    EATING,
    RETREATING,        // Phase 3 low-HP retreat for safety

    // Post-fight
    LOOTING,
    DEAD
}

package net.runelite.client.plugins.microbot.niriaraxxor;

/**
 * States for the Araxxor boss fight automation script.
 */
public enum AraxxorState {
    // Pre-fight
    IDLE,
    BANKING,
    TRAVELING,
    ENTERING_LAIR,

    // Core combat
    FIGHTING,
    EATING,
    DRINKING_POTIONS,

    // Mechanic handling
    DODGING_ACID_BALL,
    DODGING_ACID_SPLATTER,
    DODGING_ACID_DRIP,
    AVOIDING_ACID_POOLS,
    KILLING_RUPTURA,
    KILLING_MIRRORBACK,
    KILLING_ACIDIC,
    DESTROYING_EGG,

    // Enrage phase
    ENRAGED_DODGE_CLEAVE,
    ENRAGED_STEP_UNDER,

    // Defence drain
    SPEC_ATTACK,

    // Post-fight
    LOOTING,
    DEAD,
    TELEPORTING_AWAY
}

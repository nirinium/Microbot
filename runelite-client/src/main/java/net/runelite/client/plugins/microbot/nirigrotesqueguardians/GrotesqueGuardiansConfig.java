package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(GrotesqueGuardiansConfig.GROUP)
@ConfigInformation(
        "Start on the Slayer Tower roof with a rock hammer, food, prayer restore, "
                + "and melee equipment. Dawn requires a halberd; Dusk uses the best eligible melee weapon. "
                + "Banking and roof travel are not included yet."
)
public interface GrotesqueGuardiansConfig extends Config {
    String GROUP = "nirigrotesqueguardians";

    @ConfigSection(name = "Combat", description = "Weapons and prayers", position = 0)
    String combatSection = "combat";

    @ConfigSection(name = "Mechanics", description = "Boss mechanic timing and movement", position = 1)
    String mechanicsSection = "mechanics";

    @ConfigSection(name = "Safety", description = "Food, prayer, and potion thresholds", position = 2)
    String safetySection = "safety";

    @ConfigSection(name = "Loot", description = "Post-kill looting", position = 3)
    String lootSection = "loot";

    @ConfigSection(name = "Advanced", description = "Interaction retry timing", position = 4, closedByDefault = true)
    String advancedSection = "advanced";

    @ConfigSection(name = "Diagnostics", description = "Live fight diagnostics", position = 5)
    String diagnosticsSection = "diagnostics";

    @ConfigItem(keyName = "gearTier", name = "Melee gear tier",
            description = "Maximum weapon bracket; automatically falls back to lower usable tiers",
            position = 0, section = combatSection)
    default GrotesqueGuardiansGearTier gearTier() {
        return GrotesqueGuardiansGearTier.BEST_AVAILABLE;
    }

    @ConfigItem(keyName = "customDawnWeapon", name = "Custom Dawn halberd",
            description = "Optional exact item name tried before the tier list; must be able to reach Dawn",
            position = 1, section = combatSection)
    default String customDawnWeapon() {
        return "";
    }

    @ConfigItem(keyName = "customDuskWeapon", name = "Custom Dusk weapon",
            description = "Optional exact item name tried before the tier list",
            position = 2, section = combatSection)
    default String customDuskWeapon() {
        return "";
    }

    @ConfigItem(keyName = "meleeOffhand", name = "Melee offhand",
            description = "Optional offhand for one-handed Dusk weapons; two-handed weapons ignore it",
            position = 3, section = combatSection)
    default String meleeOffhand() {
        return "";
    }

    @ConfigItem(keyName = "finisher", name = "Gargoyle finisher",
            description = "Required hammer; rock thrownhammers are consumed and require at least two per kill",
            position = 4, section = combatSection)
    default GrotesqueGuardiansFinisher finisher() {
        return GrotesqueGuardiansFinisher.AUTO;
    }

    @ConfigItem(keyName = "absorbSpheres", name = "Absorb healing spheres",
            description = "Walk into Dawn's healing spheres during phase three", position = 5, section = combatSection)
    default boolean absorbSpheres() {
        return true;
    }

    @ConfigItem(keyName = "sphereMaxDistance", name = "Sphere pursuit distance",
            description = "Maximum tiles to travel to absorb a healing sphere",
            position = 0, section = mechanicsSection)
    @Range(min = 3, max = 15)
    default int sphereMaxDistance() {
        return 8;
    }

    @ConfigItem(keyName = "p2DodgeDistance", name = "P2 safe distance",
            description = "Minimum distance from Dusk's area after his phase-two shockwave animation",
            position = 1, section = mechanicsSection)
    @Range(min = 2, max = 4)
    default int p2DodgeDistance() {
        return 2;
    }

    @ConfigItem(keyName = "p2EvadeTicks", name = "P2 reaction window",
            description = "Ticks after Dusk's shockwave animation during which unsafe proximity triggers a dodge",
            position = 2, section = mechanicsSection)
    @Range(min = 2, max = 6)
    default int p2EvadeTicks() {
        return 3;
    }

    @ConfigItem(keyName = "lightningAvoidTicks", name = "Lightning avoid ticks",
            description = "How long a detected lightning tile and its adjacent tiles remain unsafe",
            position = 3, section = mechanicsSection)
    @Range(min = 2, max = 10)
    default int lightningAvoidTicks() {
        return 6;
    }

    @ConfigItem(keyName = "debrisAvoidTicks", name = "Debris avoid ticks",
            description = "How long a detected falling-rock tile and its adjacent tiles remain unsafe",
            position = 4, section = mechanicsSection)
    @Range(min = 2, max = 12)
    default int debrisAvoidTicks() {
        return 8;
    }

    @ConfigItem(keyName = "useDawnSpecialAttacks", name = "Use halberd specs on Dawn",
            description = "Use Crystal or Dragon halberd special attacks on Dawn",
            position = 6, section = combatSection)
    default boolean useDawnSpecialAttacks() {
        return true;
    }

    @ConfigItem(keyName = "p2SpecialCount", name = "Dawn P1 special count",
            description = "Maximum halberd special attacks to use on Dawn during phase one",
            position = 7, section = combatSection)
    @Range(min = 0, max = 3)
    default int dawnP1SpecialCount() {
        return 1;
    }

    @ConfigItem(keyName = "p4SpecialCount", name = "Dawn P3 special count",
            description = "Maximum halberd special attacks to use on Dawn near the end of phase three",
            position = 8, section = combatSection)
    @Range(min = 0, max = 3)
    default int dawnP3SpecialCount() {
        return 2;
    }

    @ConfigItem(keyName = "customDawnSpecWeapon", name = "Custom Dawn spec halberd",
            description = "Optional exact halberd name tried before Crystal and Dragon halberds",
            position = 9, section = combatSection)
    default String customDawnSpecWeapon() {
        return "";
    }

    @ConfigItem(keyName = "useDeathCharge", name = "Use Death Charge",
            description = "Cast Death Charge when Dawn reaches the configured phase-three health threshold",
            position = 10, section = combatSection)
    default boolean useDeathCharge() {
        return true;
    }

    @ConfigItem(keyName = "deathChargeAtPercent", name = "Death Charge at Dawn HP %",
            description = "Cast Death Charge and begin the phase-three halberd spec dump at or below this health",
            position = 11, section = combatSection)
    @Range(min = 5, max = 50)
    default int deathChargeAtPercent() {
        return 25;
    }

    @ConfigItem(keyName = "useSurgePotion", name = "Use surge potion",
            description = "After the kill, restore 25% special attack energy for the next fight when off cooldown",
            position = 12, section = combatSection)
    default boolean useSurgePotion() {
        return true;
    }

    @ConfigItem(keyName = "useOffensivePrayers", name = "Offensive prayers",
            description = "Use Piety throughout the melee-only fight", position = 13, section = combatSection)
    default boolean useOffensivePrayers() {
        return true;
    }

    @ConfigItem(keyName = "useCombatPotions", name = "Use super combats",
            description = "Drink a super combat potion when Attack or Strength is no longer meaningfully boosted",
            position = 14, section = combatSection)
    default boolean useCombatPotions() {
        return true;
    }

    @ConfigItem(keyName = "combatPotionBoostFloor", name = "Re-pot boost margin",
            description = "Drink when boosted Attack or Strength falls to this many levels above its real level",
            position = 2, section = safetySection)
    @Range(min = 0, max = 10)
    default int combatPotionBoostFloor() {
        return 2;
    }

    @ConfigItem(keyName = "eatAtPercent", name = "Eat at HP %",
            description = "Eat ordinary food at or below this percentage", position = 0, section = safetySection)
    @Range(min = 25, max = 80)
    default int eatAtPercent() {
        return 55;
    }

    @ConfigItem(keyName = "drinkPrayerAtPercent", name = "Restore prayer at %",
            description = "Drink a prayer restore at or below this percentage", position = 1, section = safetySection)
    @Range(min = 10, max = 70)
    default int drinkPrayerAtPercent() {
        return 30;
    }

    @ConfigItem(keyName = "lootValue", name = "Minimum loot value",
            description = "Minimum total GE value to pick up", position = 0, section = lootSection)
    default int lootValue() {
        return 1000;
    }

    @ConfigItem(keyName = "repeatKills", name = "Repeat kills",
            description = "Ring the bell again after looting while supplies remain", position = 1, section = lootSection)
    default boolean repeatKills() {
        return true;
    }

    @ConfigItem(keyName = "attackRetryTicks", name = "Attack retry ticks",
            description = "Minimum game ticks between attack or finisher interaction attempts",
            position = 0, section = advancedSection)
    @Range(min = 1, max = 5)
    default int attackRetryTicks() {
        return 1;
    }

    @ConfigItem(keyName = "gearRetryTicks", name = "Gear retry ticks",
            description = "Minimum game ticks between equipment interaction attempts",
            position = 1, section = advancedSection)
    @Range(min = 1, max = 5)
    default int gearRetryTicks() {
        return 1;
    }

    @ConfigItem(keyName = "bellRetryTicks", name = "Bell retry ticks",
            description = "Minimum game ticks between Quick-start attempts on the Cloister Bell",
            position = 2, section = advancedSection)
    @Range(min = 1, max = 10)
    default int bellRetryTicks() {
        return 3;
    }

    @ConfigItem(keyName = "lootEmptyTicks", name = "Loot settle ticks",
            description = "Consecutive empty scans before looting is considered complete",
            position = 3, section = advancedSection)
    @Range(min = 2, max = 10)
    default int lootEmptyTicks() {
        return 4;
    }

    @ConfigItem(keyName = "autoOpenDiagnostics", name = "Auto-open diagnostics",
            description = "Open the live diagnostic window when the plugin starts",
            position = 0, section = diagnosticsSection)
    default boolean autoOpenDiagnostics() {
        return true;
    }

    @ConfigItem(keyName = "diagnosticsAlwaysOnTop", name = "Always on top",
            description = "Keep the diagnostic window above other windows",
            position = 1, section = diagnosticsSection)
    default boolean diagnosticsAlwaysOnTop() {
        return false;
    }

    @ConfigItem(keyName = "diagnosticsRefreshMs", name = "Refresh milliseconds",
            description = "How often the diagnostic window refreshes",
            position = 2, section = diagnosticsSection)
    @Range(min = 100, max = 2000)
    default int diagnosticsRefreshMs() {
        return 250;
    }

    @ConfigItem(keyName = "openDiagnostics", name = "Diagnostic window",
            description = "Open or focus the live diagnostic window",
            position = 3, section = diagnosticsSection)
    default ConfigButton openDiagnostics() {
        return new ConfigButton();
    }
}

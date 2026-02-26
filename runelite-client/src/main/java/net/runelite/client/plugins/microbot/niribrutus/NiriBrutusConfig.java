package net.runelite.client.plugins.microbot.niribrutus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(NiriBrutusConfig.GROUP)
public interface NiriBrutusConfig extends Config {

    String GROUP = "niribrutus";

    @ConfigSection(
            name = "Combat",
            description = "Combat settings",
            position = 0
    )
    String combatSection = "combat";

    @ConfigSection(
            name = "Safety",
            description = "Safety and antiban settings",
            position = 1
    )
    String safetySection = "safety";

    // ── Combat ──────────────────────────────────────────

    @ConfigItem(
            keyName = "npcName",
            name = "NPC name",
            description = "Name of the NPC to attack (default: Brutus)",
            position = 0,
            section = combatSection
    )
    default String npcName() {
        return "Brutus";
    }

    @ConfigItem(
            keyName = "useQuickPrayer",
            name = "Use quick prayers",
            description = "Toggle quick prayers on when in combat, off when out of combat",
            position = 1,
            section = combatSection
    )
    default boolean useQuickPrayer() {
        return false;
    }

    @ConfigItem(
            keyName = "useSpecialAttack",
            name = "Use special attack",
            description = "Use special attack when available",
            position = 2,
            section = combatSection
    )
    default boolean useSpecialAttack() {
        return false;
    }

    @ConfigItem(
            keyName = "specEnergyThreshold",
            name = "Spec energy %",
            description = "Minimum special attack energy to use spec (in %)",
            position = 3,
            section = combatSection
    )
    @Range(min = 10, max = 100)
    default int specEnergyThreshold() {
        return 25;
    }

    // ── Safety ──────────────────────────────────────────

    @ConfigItem(
            keyName = "eatAtHpPercent",
            name = "Eat at HP %",
            description = "Eat food when HP drops below this percentage",
            position = 0,
            section = safetySection
    )
    @Range(min = 10, max = 90)
    default int eatAtHpPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable antiban",
            description = "Enable antiban safety features (mouse jitter, play-style variation, fatigue)",
            position = 1,
            section = safetySection
    )
    default boolean enableAntiban() {
        return true;
    }
}

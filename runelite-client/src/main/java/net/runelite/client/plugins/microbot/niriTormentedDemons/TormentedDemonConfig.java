package net.runelite.client.plugins.microbot.niriTormentedDemons;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.magic.thralls.ThrallType;

@ConfigInformation(
        "<b>Niri Tormented Demon Killer</b><br/><br/>"
                + "Supports Full Auto (banking, travel, combat) and Combat Only modes.<br/><br/>"
                + "<b>Setup Instructions:</b><br/>"
                + "<ol>"
                + "  <li>Enter comma-separated item names or IDs for each combat style's gear.</li>"
                + "  <li>Only list the items that <b>change</b> between styles (weapon, offhand, etc).</li>"
                + "  <li>Select a <b>Banking Setup</b> from the Inventory Setups plugin for restocking.</li>"
                + "  <li>Enable at least 2 combat styles so the bot can switch off the demon's protection prayer.</li>"
                + "</ol>"
                + "<b>Full Auto Banking Methods:</b><br/>"
                + "&bull; <b>Ferox</b>: Ring of Dueling → Ferox pool → bank → Guthixian temple teleport scroll<br/>"
                + "&bull; <b>POH Jewellery Box</b>: Teleport to house → ornate pool → jewellery box to GE → bank → Master Scroll Book to Guthixian Temple<br/><br/>"
                + "<b>Combat Only</b>: Stand near the demons and start."
)
@ConfigGroup(TormentedDemonConfig.GROUP)
public interface TormentedDemonConfig extends Config {

    String GROUP = "niriTormentedDemon";

    // ─── General ────────────────────────────────────────────────────────────────

    @ConfigSection(
            name = "General",
            description = "Bot mode and general settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "mode",
            name = "Mode",
            description = "Full Auto handles banking, travel, and combat. Combat Only just fights.",
            section = generalSection,
            position = 0
    )
    default Mode mode() {
        return Mode.FULL_AUTO;
    }

    @ConfigItem(
            keyName = "bankingMethod",
            name = "Banking Method",
            description = "How to bank between trips (Full Auto only)",
            section = generalSection,
            position = 1
    )
    default BankingMethod bankingMethod() {
        return BankingMethod.FEROX;
    }

    @ConfigItem(
            keyName = "travelMethod",
            name = "Travel to Demons",
            description = "How to teleport back to the Guthixian Temple after banking",
            section = generalSection,
            position = 2
    )
    default TravelMethod travelMethod() {
        return TravelMethod.SCROLL;
    }

    @ConfigItem(
            keyName = "walkToDemons",
            name = "Walk to Demons",
            description = "Manually walk from the Guthixian Temple to the Tormented Demon area (use after teleporting yourself)",
            section = generalSection,
            position = 3
    )
    default ConfigButton walkToDemons() {
        return new ConfigButton();
    }

    // ─── Gear Setups ────────────────────────────────────────────────────────────

    @ConfigSection(
            name = "Gear Setups",
            description = "Enter item names or IDs for each combat style's switch gear, plus a banking preset",
            position = 1
    )
    String gearSection = "gearSetups";

    @ConfigItem(
            keyName = "useMelee",
            name = "Use Melee",
            description = "Enable melee combat style",
            section = gearSection,
            position = 0
    )
    default boolean useMelee() {
        return true;
    }

    @ConfigItem(
            keyName = "meleeGear",
            name = "Melee Gear",
            description = "Comma-separated item names or IDs to equip for melee (e.g. Emberlight,Avernic defender)",
            section = gearSection,
            position = 1
    )
    default String meleeGear() {
        return "";
    }

    @ConfigItem(
            keyName = "useRanged",
            name = "Use Ranged",
            description = "Enable ranged combat style",
            section = gearSection,
            position = 2
    )
    default boolean useRanged() {
        return true;
    }

    @ConfigItem(
            keyName = "rangedGear",
            name = "Ranged Gear",
            description = "Comma-separated item names or IDs to equip for ranged (e.g. Armadyl crossbow,Dragon bolts (e))",
            section = gearSection,
            position = 3
    )
    default String rangedGear() {
        return "";
    }

    @ConfigItem(
            keyName = "useMagic",
            name = "Use Magic",
            description = "Enable magic combat style",
            section = gearSection,
            position = 4
    )
    default boolean useMagic() {
        return false;
    }

    @ConfigItem(
            keyName = "magicGear",
            name = "Magic Gear",
            description = "Comma-separated item names or IDs to equip for magic (e.g. Purging staff,Book of the dead)",
            section = gearSection,
            position = 5
    )
    default String magicGear() {
        return "";
    }

    @ConfigItem(
            keyName = "bankingSetup",
            name = "Banking Setup",
            description = "Inventory Setups preset loaded at bank (should contain all switch gear, food, pots)",
            section = gearSection,
            position = 6
    )
    default InventorySetup bankingSetup() {
        return null;
    }

    // ─── Combat ─────────────────────────────────────────────────────────────────

    @ConfigSection(
            name = "Combat",
            description = "Prayer and combat tuning",
            position = 2
    )
    String combatSection = "combat";

    @ConfigItem(
            keyName = "enableDefensivePrayer",
            name = "Auto Protection Prayer",
            description = "Automatically switch protection prayers based on demon attack style",
            section = combatSection,
            position = 0
    )
    default boolean enableDefensivePrayer() {
        return true;
    }

    @ConfigItem(
            keyName = "enableOffensivePrayer",
            name = "Auto Offensive Prayer",
            description = "Automatically activate the best offensive prayer for your current combat style",
            section = combatSection,
            position = 1
    )
    default boolean enableOffensivePrayer() {
        return true;
    }

    @ConfigItem(
            keyName = "gearSwitchDelay",
            name = "Gear Switch Delay (ms)",
            description = "Delay between equipping each gear piece (0 for fastest switching)",
            section = combatSection,
            position = 5
    )
    @Range(min = 0, max = 300)
    default int gearSwitchDelay() {
        return 50;
    }

    @ConfigItem(
            keyName = "useThralls",
            name = "Use Thralls",
            description = "Automatically summon a thrall when not active (requires Arceuus spellbook & Book of the Dead)",
            section = combatSection,
            position = 7
    )
    default boolean useThralls() {
        return false;
    }

    @ConfigItem(
            keyName = "thrallType",
            name = "Thrall Type",
            description = "Which thrall type to summon",
            section = combatSection,
            position = 8
    )
    default ThrallType thrallType() {
        return ThrallType.MAGIC;
    }

    @ConfigItem(
            keyName = "useDeathCharge",
            name = "Use Death Charge",
            description = "Automatically cast Death Charge when not active (requires Arceuus spellbook)",
            section = combatSection,
            position = 9
    )
    default boolean useDeathCharge() {
        return false;
    }

    @ConfigItem(
            keyName = "useSaturatedHeart",
            name = "Use Saturated Heart",
            description = "Automatically use Saturated Heart when magic boost expires",
            section = combatSection,
            position = 10
    )
    default boolean useSaturatedHeart() {
        return false;
    }

    @ConfigItem(
            keyName = "useSpecWeapon",
            name = "Use Spec Weapon",
            description = "Use a special attack weapon during melee combat (e.g. Burning claws)",
            section = combatSection,
            position = 11
    )
    default boolean useSpecWeapon() {
        return false;
    }

    @ConfigItem(
            keyName = "specWeaponName",
            name = "Spec Weapon Name",
            description = "Name of the special attack weapon to equip (e.g. Burning claws, Dragon claws)",
            section = combatSection,
            position = 12
    )
    default String specWeaponName() {
        return "Burning claws";
    }

    @ConfigItem(
            keyName = "specEnergyCost",
            name = "Spec Energy Cost (%)",
            description = "Percentage of special attack energy the weapon costs per use",
            section = combatSection,
            position = 13
    )
    @Range(min = 10, max = 100)
    default int specEnergyCost() {
        return 50;
    }

    @ConfigItem(
            keyName = "specAttackCooldown",
            name = "Spec Attack Cooldown (ticks)",
            description = "Minimum ticks between spec attacks to avoid rapid gear switching (1 tick = 0.6s, recommended: 20-50)",
            section = combatSection,
            position = 14
    )
    @Range(min = 5, max = 100)
    default int specAttackCooldown() {
        return 25;
    }

    @ConfigItem(
            keyName = "useDharokPunish",
            name = "Dharok Punish",
            description = "Switch to Dharok's greataxe for a hit after your fire weapon breaks the demon's shield",
            section = combatSection,
            position = 15
    )
    default boolean useDharokPunish() {
        return false;
    }

    @ConfigItem(
            keyName = "punishWeaponName",
            name = "Punish Weapon Name",
            description = "Name of the punish weapon to equip when the demon's shield is down",
            section = combatSection,
            position = 16
    )
    default String punishWeaponName() {
        return "Dharok's greataxe";
    }

    @ConfigItem(
            keyName = "useRapidHeal",
            name = "Use Rapid Heal",
            description = "Keep the Rapid Heal prayer active during combat",
            section = combatSection,
            position = 17
    )
    default boolean useRapidHeal() {
        return false;
    }

    // ─── Fire Bomb Dodge ─────────────────────────────────────────────────────────

    @ConfigSection(
            name = "Fire Bomb Dodge",
            description = "Dodge behaviour when the demon throws fire bombs",
            position = 3
    )
    String dodgeSection = "fireBombDodge";

    @ConfigItem(
            keyName = "enableDodge",
            name = "Enable Dodge",
            description = "Automatically dodge fire bombs thrown by the demon",
            section = dodgeSection,
            position = 0
    )
    default boolean enableDodge() {
        return true;
    }

    @ConfigItem(
            keyName = "dodgeDelay",
            name = "Dodge Delay (ms)",
            description = "Delay before processing fire bomb dodge. Increase if dodging too early.",
            section = dodgeSection,
            position = 1
    )
    @Range(min = 0, max = 2000)
    default int dodgeDelay() {
        return 600;
    }

    @ConfigItem(
            keyName = "dodgeClickCount",
            name = "Dodge Click Count",
            description = "Number of walk clicks per dodge (spam click for reliability)",
            section = dodgeSection,
            position = 2
    )
    @Range(min = 1, max = 15)
    default int dodgeClickCount() {
        return 3;
    }

    @ConfigItem(
            keyName = "dodgeClickInterval",
            name = "Click Interval (ms)",
            description = "Delay between each dodge click (only relevant if click count > 1)",
            section = dodgeSection,
            position = 3
    )
    @Range(min = 50, max = 300)
    default int dodgeClickInterval() {
        return 100;
    }

    @ConfigItem(
            keyName = "dodgeSafeDistance",
            name = "Safe Distance",
            description = "Minimum Chebyshev distance from all fire bomb centers for a tile to be safe (2 = one tile gap)",
            section = dodgeSection,
            position = 4
    )
    @Range(min = 2, max = 5)
    default int dodgeSafeDistance() {
        return 2;
    }

    @ConfigItem(
            keyName = "dodgeSearchRadius",
            name = "Search Radius",
            description = "How many tiles around the player to search for a safe dodge tile",
            section = dodgeSection,
            position = 5
    )
    @Range(min = 3, max = 10)
    default int dodgeSearchRadius() {
        return 5;
    }

    @ConfigItem(
            keyName = "fireBombDuration",
            name = "Bomb Duration (ticks)",
            description = "How many game ticks a fire bomb stays dangerous (1 tick = 600ms)",
            section = dodgeSection,
            position = 6
    )
    @Range(min = 1, max = 10)
    default int fireBombDuration() {
        return 4;
    }

    // ─── Food & Potions ─────────────────────────────────────────────────────────

    @ConfigSection(
            name = "Food & Potions",
            description = "Eating, drinking, and retreat thresholds",
            position = 4
    )
    String suppliesSection = "supplies";

    @ConfigItem(
            keyName = "minEatPercent",
            name = "Eat Below HP %",
            description = "Eat food when health drops below this percentage",
            section = suppliesSection,
            position = 0
    )
    @Range(min = 10, max = 90)
    default int minEatPercent() {
        return 50;
    }

    @ConfigItem(
            keyName = "minPrayerPercent",
            name = "Drink Prayer Below %",
            description = "Drink prayer potion when prayer drops below this percentage",
            section = suppliesSection,
            position = 1
    )
    @Range(min = 5, max = 80)
    default int minPrayerPercent() {
        return 25;
    }

    @ConfigItem(
            keyName = "healthThreshold",
            name = "Retreat HP %",
            description = "Retreat to bank when health drops below this % and no food remains (Full Auto)",
            section = suppliesSection,
            position = 2
    )
    @Range(min = 10, max = 80)
    default int healthThreshold() {
        return 30;
    }

    @ConfigItem(
            keyName = "enableEmergencyTeleport",
            name = "Enable Emergency Teleport",
            description = "Use emergency teleport when HP is critical and no food remains",
            section = suppliesSection,
            position = 3
    )
    default boolean enableEmergencyTeleport() {
        return true;
    }

    @ConfigItem(
            keyName = "emergencyTeleportHp",
            name = "Emergency Teleport HP %",
            description = "Teleport out when HP drops below this % and no food/healing remains",
            section = suppliesSection,
            position = 4
    )
    @Range(min = 5, max = 50)
    default int emergencyTeleportHp() {
        return 20;
    }

    @ConfigItem(
            keyName = "teleportItem",
            name = "Emergency Teleport Item",
            description = "Item name for emergency teleport (e.g. Teleport to house, Varrock teleport, Ring of dueling)",
            section = suppliesSection,
            position = 5
    )
    default String teleportItem() {
        return "Teleport to house";
    }

    @ConfigItem(
            keyName = "combatPotionType",
            name = "Combat Potion",
            description = "Type of melee stat-boosting potion to use",
            section = suppliesSection,
            position = 6
    )
    default CombatPotionType combatPotionType() {
        return CombatPotionType.SUPER_COMBAT;
    }

    @ConfigItem(
            keyName = "rangingPotionType",
            name = "Ranging Potion",
            description = "Type of ranged stat-boosting potion to use",
            section = suppliesSection,
            position = 7
    )
    default RangingPotionType rangingPotionType() {
        return RangingPotionType.RANGING;
    }

    @ConfigItem(
            keyName = "boostedStatsThreshold",
            name = "Re-pot Below % Boost",
            description = "Drink a combat/ranging potion when boosted stats fall below this % of max boost",
            section = suppliesSection,
            position = 8
    )
    @Range(min = 1, max = 100)
    default int boostedStatsThreshold() {
        return 10;
    }

    // ─── Looting ────────────────────────────────────────────────────────────────

    @ConfigSection(
            name = "Looting",
            description = "Loot filter settings",
            position = 5
    )
    String lootingSection = "looting";

    @ConfigItem(
            keyName = "lootItems",
            name = "Loot Items",
            description = "Comma-separated item names to always loot (e.g. tormented synapse, burning claw)",
            section = lootingSection,
            position = 0
    )
    default String lootItems() {
        return "tormented synapse,burning claw,guthixian temple teleport";
    }

    @ConfigItem(
            keyName = "minLootValue",
            name = "Min Loot Value",
            description = "Minimum GE value to auto-loot items not in the name list (0 = name list only)",
            section = lootingSection,
            position = 1
    )
    default int minLootValue() {
        return 5000;
    }

    @ConfigItem(
            keyName = "scatterAshes",
            name = "Scatter Infernal Ashes",
            description = "Automatically loot and scatter Infernal ashes for prayer XP",
            section = lootingSection,
            position = 2
    )
    default boolean scatterAshes() {
        return false;
    }

    @ConfigItem(
            keyName = "lootSmoulderingFlesh",
            name = "Loot Smouldering Flesh",
            description = "Automatically loot 'Smouldering pile of flesh' for healing during combat (heals 10 HP, can overheal above max HP)",
            section = lootingSection,
            position = 3
    )
    default boolean lootSmoulderingFlesh() {
        return true;
    }

    @ConfigItem(
            keyName = "smoulderingFleshHpThreshold",
            name = "Flesh HP Threshold %",
            description = "Loot smouldering flesh when HP is at or below this percentage. Set to 100 to always pick up (overheals)",
            section = lootingSection,
            position = 4
    )
    @Range(min = 20, max = 100)
    default int smoulderingFleshHpThreshold() {
        return 90;
    }

    @ConfigItem(
            keyName = "lootSmoulderingGland",
            name = "Loot Smouldering Gland",
            description = "Automatically loot 'Smouldering gland' for prayer restoration during combat (restores 10 prayer)",
            section = lootingSection,
            position = 5
    )
    default boolean lootSmoulderingGland() {
        return true;
    }

    @ConfigItem(
            keyName = "smoulderingGlandPrayerThreshold",
            name = "Gland Prayer Threshold %",
            description = "Loot smouldering gland when prayer drops below this percentage",
            section = lootingSection,
            position = 6
    )
    @Range(min = 10, max = 90)
    default int smoulderingGlandPrayerThreshold() {
        return 50;
    }

    @ConfigItem(
            keyName = "lootSmoulderingHeart",
            name = "Loot Smouldering Heart",
            description = "Automatically loot 'Smouldering heart' during combat (boosts Attack, Strength, Ranged, Magic by 2)",
            section = lootingSection,
            position = 7
    )
    default boolean lootSmoulderingHeart() {
        return true;
    }

    // ─── Enums ──────────────────────────────────────────────────────────────────

    @Getter
    @RequiredArgsConstructor
    enum Mode {
        FULL_AUTO("Full Auto"),
        COMBAT_ONLY("Combat Only");
        private final String name;

        @Override
        public String toString() {
            return name;
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum CombatPotionType {
        NONE("None"),
        SUPER_COMBAT("Super combat"),
        DIVINE_SUPER_COMBAT("Divine super combat");
        private final String keyword;

        @Override
        public String toString() {
            return keyword;
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum RangingPotionType {
        NONE("None"),
        RANGING("Ranging potion"),
        DIVINE_RANGING("Divine ranging potion"),
        BASTION("Bastion potion");
        private final String keyword;

        @Override
        public String toString() {
            return keyword;
        }
    }

    enum CombatStyle {
        MELEE, RANGED, MAGIC
    }

    @Getter
    @RequiredArgsConstructor
    enum BankingMethod {
        FEROX("Ferox Enclave"),
        POH_JEWELLERY_BOX("POH → Jewellery Box → GE");
        private final String name;

        @Override
        public String toString() {
            return name;
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum TravelMethod {
        SCROLL("Guthixian Temple Scroll"),
        MASTER_SCROLL_BOOK("Master Scroll Book");
        private final String name;

        @Override
        public String toString() {
            return name;
        }
    }
}




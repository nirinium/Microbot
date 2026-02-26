package net.runelite.client.plugins.microbot.niriaraxxor;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(AraxxorConfig.GROUP)
public interface AraxxorConfig extends Config {

    String GROUP = "niriaraxxor";

    // ── Sections ────────────────────────────────────────
    @ConfigSection(name = "Combat", description = "Combat settings", position = 0)
    String combatSection = "combat";

    @ConfigSection(name = "Prayer", description = "Prayer settings", position = 1)
    String prayerSection = "prayer";

    @ConfigSection(name = "Potions", description = "Potion settings", position = 2)
    String potionSection = "potions";

    @ConfigSection(name = "Loot", description = "Loot settings", position = 3)
    String lootSection = "loot";

    @ConfigSection(name = "Safety", description = "Safety & teleport settings", position = 4)
    String safetySection = "safety";

    // ── Combat ──────────────────────────────────────────

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = -1
    )
    default String GUIDE() {
        return "1. Requires Slayer task (Araxyte or spider)\n"
                + "2. Start at the Morytania Spider Cave entrance or a bank\n"
                + "3. Use crush weapons (Scythe, Inquisitor's Mace, Bludgeon)\n"
                + "4. Bring extended anti-venom+, prayer pots, super/divine super combat, food\n"
                + "5. Optional: Elder maul for Defence drain special attack";
    }

    @ConfigItem(
            keyName = "useSpecialAttack",
            name = "Use spec for Defence drain",
            description = "Use Elder Maul / DWH spec at start of fight to drain Defence",
            position = 0,
            section = combatSection
    )
    default boolean useSpecialAttack() {
        return true;
    }

    @ConfigItem(
            keyName = "specWeapon",
            name = "Spec weapon name",
            description = "Name of the special attack weapon (e.g. Elder maul, Dragon warhammer)",
            position = 1,
            section = combatSection
    )
    default String specWeapon() {
        return "Elder maul";
    }

    @ConfigItem(
            keyName = "mainWeapon",
            name = "Main weapon name",
            description = "Name of main weapon to wield after spec (e.g. Inquisitor's mace)",
            position = 2,
            section = combatSection
    )
    default String mainWeapon() {
        return "Inquisitor's mace";
    }

    @ConfigItem(
            keyName = "mainShield",
            name = "Shield/offhand name",
            description = "Shield or defender to wield with main weapon (leave blank for 2H)",
            position = 3,
            section = combatSection
    )
    default String mainShield() {
        return "";
    }

    @ConfigItem(
            keyName = "araxyteSwitchWeapon",
            name = "Araxyte kill weapon",
            description = "Weapon for killing araxyte minions (Noxious halberd recommended for guaranteed max hits)",
            position = 4,
            section = combatSection
    )
    default String araxyteSwitchWeapon() {
        return "Noxious halberd";
    }

    @ConfigItem(
            keyName = "killAcidicAraxytes",
            name = "Kill acidic araxytes",
            description = "Kill acidic (green) araxytes or ignore them and tank",
            position = 5,
            section = combatSection
    )
    default boolean killAcidicAraxytes() {
        return true;
    }

    @ConfigItem(
            keyName = "lureRupturaToAraxxor",
            name = "Lure ruptura to Araxxor",
            description = "Lure red (ruptura) araxytes to explode under Araxxor for extra damage",
            position = 6,
            section = combatSection
    )
    default boolean lureRupturaToAraxxor() {
        return true;
    }

    // ── Prayer ──────────────────────────────────────────

    @ConfigItem(
            keyName = "usePiety",
            name = "Use Piety",
            description = "Activate Piety during fight (requires 70 Prayer & Defence)",
            position = 0,
            section = prayerSection
    )
    default boolean usePiety() {
        return true;
    }

    @ConfigItem(
            keyName = "protectFromMelee",
            name = "Protect from Melee",
            description = "Use Protect from Melee when in melee range (reduces damage from 38 to 5)",
            position = 1,
            section = prayerSection
    )
    default boolean protectFromMelee() {
        return true;
    }

    @ConfigItem(
            keyName = "drinkPrayerAtPercent",
            name = "Drink prayer at %",
            description = "Drink prayer potion when prayer points drop below this percentage",
            position = 2,
            section = prayerSection
    )
    @Range(min = 10, max = 80)
    default int drinkPrayerAtPercent() {
        return 30;
    }

    // ── Potions ─────────────────────────────────────────

    @ConfigItem(
            keyName = "combatPotionType",
            name = "Combat potion",
            description = "Which combat potion to drink (or None to skip)",
            position = 0,
            section = potionSection
    )
    default CombatPotionType combatPotionType() {
        return CombatPotionType.SUPER_COMBAT;
    }

    @ConfigItem(
            keyName = "useExtendedAntiVenom",
            name = "Use extended anti-venom+",
            description = "Drink extended anti-venom+ to protect from venom",
            position = 1,
            section = potionSection
    )
    default boolean useExtendedAntiVenom() {
        return true;
    }

    // ── Loot ────────────────────────────────────────────

    @ConfigItem(
            keyName = "lootPriceThreshold",
            name = "Min loot value",
            description = "Minimum item value to loot after kill",
            position = 0,
            section = lootSection
    )
    default int lootPriceThreshold() {
        return 1000;
    }

    @ConfigItem(
            keyName = "harvestCorpse",
            name = "Harvest corpse",
            description = "Harvest Araxxor's corpse (true) or destroy for doubled pet rate (false)",
            position = 1,
            section = lootSection
    )
    default boolean harvestCorpse() {
        return true;
    }

    // ── Safety ──────────────────────────────────────────

    @ConfigItem(
            keyName = "eatAtHpPercent",
            name = "Eat at HP %",
            description = "Eat food when HP drops below this percentage",
            position = 0,
            section = safetySection
    )
    @Range(min = 20, max = 90)
    default int eatAtHpPercent() {
        return 60;
    }

    @ConfigItem(
            keyName = "emergencyTeleportHp",
            name = "Emergency teleport HP %",
            description = "Teleport out when HP drops below this percentage and no food left",
            position = 1,
            section = safetySection
    )
    @Range(min = 10, max = 50)
    default int emergencyTeleportHp() {
        return 25;
    }

    @ConfigItem(
            keyName = "maxKillsPerTrip",
            name = "Kills per trip",
            description = "Leave after this many kills (0 = stay until out of supplies)",
            position = 2,
            section = safetySection
    )
    @Range(min = 0, max = 50)
    default int maxKillsPerTrip() {
        return 0;
    }

    @ConfigItem(
            keyName = "teleportItem",
            name = "Teleport item",
            description = "Item name for emergency teleport (e.g. Teleport to house, Varrock teleport)",
            position = 3,
            section = safetySection
    )
    default String teleportItem() {
        return "Teleport to house";
    }
}

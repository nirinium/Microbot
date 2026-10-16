package net.runelite.client.plugins.microbot.niriaraxxor;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.util.magic.thralls.ThrallType;

@ConfigGroup(AraxxorConfig.GROUP)
@ConfigInformation(
        "1. Requires Slayer task (Araxyte or spider)<br />"
                + "2. Start at the Morytania Spider Cave entrance or a bank<br />"
                + "3. Use crush weapons (Scythe, Inquisitor's Mace, Bludgeon) or Ranged (Twisted bow)<br />"
                + "4. Ranged: use high magic defence, Aranea boots, Rigour, and a ranging potion<br />"
                + "5. Bring extended anti-venom+, prayer potions, food, and an araxyte weapon<br />"
                + "6. Optional: one Elder maul or Dragon warhammer special attack"
)
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

    enum CombatStyle {
        MELEE,
        RANGED
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
            keyName = "specCount",
            name = "Spec hit count",
            description = "Number of spec hits at the start (one is enough to reach Araxxor's drain cap)",
            position = 2,
            section = combatSection
    )
    @Range(min = 1, max = 2)
    default int specCount() {
        return 1;
    }

    @ConfigItem(
            keyName = "useSurgePotion",
            name = "Use surge potion",
            description = "Drink a surge potion after all spec hits land for the special attack energy boost",
            position = 3,
            section = combatSection
    )
    default boolean useSurgePotion() {
        return false;
    }

    @ConfigItem(
            keyName = "combatStyle",
            name = "Combat style",
            description = "Melee (main weapon/shield) or Ranged (Twisted bow/ammo)",
            position = 4,
            section = combatSection
    )
    default CombatStyle combatStyle() {
        return CombatStyle.MELEE;
    }

    @ConfigItem(
            keyName = "mainWeapon",
            name = "Main weapon name",
            description = "Name of main weapon to wield after spec (e.g. Inquisitor's mace)",
            position = 5,
            section = combatSection
    )
    default String mainWeapon() {
        return "Inquisitor's mace";
    }

    @ConfigItem(
            keyName = "mainShield",
            name = "Shield/offhand name",
            description = "Shield or defender to wield with main weapon (leave blank for 2H)",
            position = 6,
            section = combatSection
    )
    default String mainShield() {
        return "";
    }

    @ConfigItem(
            keyName = "rangedWeapon",
            name = "Ranged weapon name",
            description = "Name of ranged weapon to wield (e.g. Twisted bow)",
            position = 7,
            section = combatSection
    )
    default String rangedWeapon() {
        return "Twisted bow";
    }

    @ConfigItem(
            keyName = "rangedAmmo",
            name = "Ranged ammo name",
            description = "Name of ammo to wield with ranged weapon (e.g. Dragon arrow)",
            position = 8,
            section = combatSection
    )
    default String rangedAmmo() {
        return "Dragon arrow";
    }

    @ConfigItem(
            keyName = "araxyteSwitchWeapon",
            name = "Araxyte kill weapon",
            description = "Noxious halberd, crush weapon, or ballista (best available javelin is equipped automatically)",
            position = 9,
            section = combatSection
    )
    default String araxyteSwitchWeapon() {
        return "Noxious halberd";
    }

    @ConfigItem(
            keyName = "prioritizeAraxytes",
            name = "Prioritize araxytes",
            description = "Immediately focus spawned araxytes; attacks ruptura from a safe distance instead of waiting for its pathing",
            position = 10,
            section = combatSection
    )
    default boolean prioritizeAraxytes() {
        return true;
    }

    @ConfigItem(
            keyName = "killAcidicAraxytes",
            name = "Kill acidic araxytes",
            description = "Kill acidic (green) araxytes or ignore them and tank",
            position = 11,
            section = combatSection
    )
    default boolean killAcidicAraxytes() {
        return true;
    }

    @ConfigItem(
            keyName = "lureRupturaToAraxxor",
            name = "Lure ruptura to Araxxor",
            description = "Lure red (ruptura) araxytes to explode under Araxxor for extra damage",
            position = 12,
            section = combatSection
    )
    default boolean lureRupturaToAraxxor() {
        return true;
    }

    @ConfigItem(
            keyName = "useThralls",
            name = "Use Thralls",
            description = "Summon a thrall using the Arceuus spellbook (requires Book of the Dead and runes)",
            position = 13,
            section = combatSection
    )
    default boolean useThralls() {
        return false;
    }

    @ConfigItem(
            keyName = "thrallType",
            name = "Thrall type",
            description = "Which type of thrall to summon (Magic = ghost, Ranged = skeleton, Melee = zombie)",
            position = 14,
            section = combatSection
    )
    default ThrallType thrallType() {
        return ThrallType.MELEE;
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
            keyName = "useRigour",
            name = "Use Rigour",
            description = "Activate Rigour during fight when using Ranged (requires 74 Prayer & Defence)",
            position = 2,
            section = prayerSection
    )
    default boolean useRigour() {
        return true;
    }

    @ConfigItem(
            keyName = "protectFromMissiles",
            name = "Protect from Missiles",
            description = "Use Protect from Missiles while outside Araxxor's melee or cleave range",
            position = 3,
            section = prayerSection
    )
    default boolean protectFromMissiles() {
        return true;
    }

    @ConfigItem(
            keyName = "drinkPrayerAtPercent",
            name = "Drink prayer at %",
            description = "Drink prayer potion when prayer points drop below this percentage",
            position = 4,
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
            description = "Automatic uses super combat for Melee and ranging potion for Ranged",
            position = 0,
            section = potionSection
    )
    default CombatPotionType combatPotionType() {
        return CombatPotionType.AUTO;
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

    @ConfigItem(keyName = "enterLair", name = "Enter nearby lair",
            description = "Enter a nearby Araxxor tunnel when supplied; does not travel or bank",
            position = 4, section = safetySection)
    default boolean enterLair() {
        return true;
    }

    @ConfigItem(
            keyName = "eatAtHpPercent",
            name = "Eat at HP %",
            description = "Eat food when HP drops below this percentage",
            position = 0,
            section = safetySection
    )
    @Range(min = 20, max = 90)
    default int eatAtHpPercent() {
        return 50;
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

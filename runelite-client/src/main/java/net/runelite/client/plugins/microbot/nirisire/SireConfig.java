package net.runelite.client.plugins.microbot.nirisire;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.niriaraxxor.CombatPotionType;

@ConfigGroup(SireConfig.GROUP)
public interface SireConfig extends Config {

    String GROUP = "nirisire";

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

    @ConfigSection(name = "Banking", description = "Banking & walk-back settings", position = 5)
    String bankingSection = "banking";

    @ConfigSection(name = "Tuning", description = "Performance tuning & debug settings", position = 6)
    String tuningSection = "tuning";

    @ConfigSection(name = "Explosion Dodge", description = "Phase 3 explosion dodge tuning — adjust in real-time to debug dodge failures", position = 7, closedByDefault = false)
    String explosionDodgeSection = "explosionDodge";

    // ── Combat ──────────────────────────────────────────

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = -1
    )
    default String GUIDE() {
        return "1. Requires Slayer task (Abyssal demon or boss task)\n"
                + "2. Start in the Sire's arena (SW room preferred)\n"
                + "3. Ancient spellbook required for Shadow Barrage stun\n"
                + "4. Bring Scorching bow + any arrows for Phase 1 vents (1-shots them)\n"
                + "5. Bring melee weapon (Emberlight/Fang recommended) for Phase 2-3\n"
                + "6. Optional: Defence drain spec (DWH/Elder maul) at Phase 2 start\n"
                + "7. Optional: Damage spec (Burning claws/Dragon claws) for Phase 3\n"
                + "8. Bring prayer pots, combat pots, anti-poison, food";
    }

    @ConfigItem(
            keyName = "mainWeapon",
            name = "Main weapon name",
            description = "Melee weapon for Phase 2-3 (e.g. Abyssal bludgeon, Inquisitor's mace)",
            position = 0,
            section = combatSection
    )
    default String mainWeapon() {
        return "Abyssal bludgeon";
    }

    @ConfigItem(
            keyName = "mainShield",
            name = "Shield/offhand name",
            description = "Shield or defender to wield with main weapon (leave blank for 2H)",
            position = 1,
            section = combatSection
    )
    default String mainShield() {
        return "";
    }

    @ConfigItem(
            keyName = "useSpecialAttack",
            name = "Use Defence drain spec (P2)",
            description = "Use Elder Maul / DWH spec at start of Phase 2 to drain Defence",
            position = 2,
            section = combatSection
    )
    default boolean useSpecialAttack() {
        return true;
    }

    @ConfigItem(
            keyName = "specWeapon",
            name = "Defence spec weapon",
            description = "Name of the defence drain spec weapon (e.g. Elder maul, Dragon warhammer)",
            position = 3,
            section = combatSection
    )
    default String specWeapon() {
        return "Elder maul";
    }

    @ConfigItem(
            keyName = "specCount",
            name = "Defence spec count",
            description = "Number of defence drain spec hits to perform at the start of Phase 2",
            position = 4,
            section = combatSection
    )
    @Range(min = 1, max = 2)
    default int specCount() {
        return 2;
    }

    @ConfigItem(
            keyName = "useDamageSpec",
            name = "Use damage spec (P3)",
            description = "Use Burning claws / Dragon claws spec during Phase 3 for fast finishing",
            position = 5,
            section = combatSection
    )
    default boolean useDamageSpec() {
        return true;
    }

    @ConfigItem(
            keyName = "damageSpecWeapon",
            name = "Damage spec weapon",
            description = "Name of the damage spec weapon for Phase 3 (e.g. Burning claws, Dragon claws, Voidwaker)",
            position = 6,
            section = combatSection
    )
    default String damageSpecWeapon() {
        return "Burning claws";
    }

    @ConfigItem(
            keyName = "damageSpecCost",
            name = "Damage spec cost (%)",
            description = "Special attack energy cost per hit for the damage spec weapon",
            position = 7,
            section = combatSection
    )
    @Range(min = 25, max = 100)
    default int damageSpecCost() {
        return 50;
    }

    @ConfigItem(
            keyName = "scorchingBow",
            name = "Scorching bow name",
            description = "Scorching bow 1-shots all respiratory vents; works with full melee gear",
            position = 8,
            section = combatSection
    )
    default String scorchingBow() {
        return "Scorching bow";
    }

    // ── Prayer ──────────────────────────────────────────

    @ConfigItem(
            keyName = "protectFromMelee",
            name = "Protect from Melee (P2)",
            description = "Use Protect from Melee during Phase 2 melee combat",
            position = 0,
            section = prayerSection
    )
    default boolean protectFromMelee() {
        return true;
    }

    @ConfigItem(
            keyName = "protectFromMissiles",
            name = "Protect from Missiles (P3)",
            description = "Use Protect from Missiles during Phase 3 and when Sire panics (50% HP)",
            position = 1,
            section = prayerSection
    )
    default boolean protectFromMissiles() {
        return true;
    }

    @ConfigItem(
            keyName = "usePiety",
            name = "Use Piety",
            description = "Activate Piety during melee phases (requires 70 Prayer & Defence)",
            position = 2,
            section = prayerSection
    )
    default boolean usePiety() {
        return true;
    }

    @ConfigItem(
            keyName = "drinkPrayerAtPercent",
            name = "Drink prayer at %",
            description = "Drink prayer potion when prayer points drop below this percentage",
            position = 3,
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
            keyName = "useAntiPoison",
            name = "Use anti-poison",
            description = "Drink anti-poison/anti-venom to protect from miasma poison",
            position = 1,
            section = potionSection
    )
    default boolean useAntiPoison() {
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
            keyName = "phase3RetreatHp",
            name = "Phase 3 retreat HP",
            description = "In Phase 3, retreat south briefly when HP drops below this value (scions hurt)",
            position = 2,
            section = safetySection
    )
    @Range(min = 20, max = 70)
    default int phase3RetreatHp() {
        return 40;
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

    // ── Banking ─────────────────────────────────────────

    @ConfigItem(
            keyName = "enableBanking",
            name = "Enable banking",
            description = "Automatically teleport out, bank, and walk back when supplies are low",
            position = 0,
            section = bankingSection
    )
    default boolean enableBanking() {
        return false;
    }

    @ConfigItem(
            keyName = "inventorySetup",
            name = "Inventory Setup",
            description = "Select the Inventory Setups preset to load at the bank. Create one in the Inventory Setups plugin first.",
            position = 1,
            section = bankingSection
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigItem(
            keyName = "minFood",
            name = "Min food to continue",
            description = "Bank when food count drops below this number (after looting)",
            position = 2,
            section = bankingSection
    )
    @Range(min = 0, max = 20)
    default int minFood() {
        return 3;
    }

    @ConfigItem(
            keyName = "minPrayerDoses",
            name = "Min prayer doses",
            description = "Bank when total prayer potion doses drop below this number",
            position = 3,
            section = bankingSection
    )
    @Range(min = 0, max = 16)
    default int minPrayerDoses() {
        return 2;
    }

    @ConfigItem(
            keyName = "usePohPool",
            name = "Use POH pool",
            description = "Use Ornate rejuvenation pool in POH to restore HP/prayer/stats before returning",
            position = 4,
            section = bankingSection
    )
    default boolean usePohPool() {
        return true;
    }

    @ConfigItem(
            keyName = "usePohFairyRing",
            name = "Use POH fairy ring",
            description = "Use POH fairy ring to travel back (requires Spirit tree or Fairy ring in POH). If disabled, uses nearest world fairy ring.",
            position = 5,
            section = bankingSection
    )
    default boolean usePohFairyRing() {
        return true;
    }

    // ── Tuning ──────────────────────────────────────────

    @ConfigItem(
            keyName = "sameTickEatPot",
            name = "Same-tick eat + pot",
            description = "Eat food and drink a potion in the same game tick (saves ~600ms per combo)",
            position = 0,
            section = tuningSection
    )
    default boolean sameTickEatPot() {
        return true;
    }

    @ConfigItem(
            keyName = "clampP3Position",
            name = "Clamp P3 position",
            description = "Force player back onto Row 2/3 if they drift off during Phase 3 (prevents tentacle damage)",
            position = 1,
            section = tuningSection
    )
    default boolean clampP3Position() {
        return true;
    }

    @ConfigItem(
            keyName = "ventAttackTimeoutMs",
            name = "Vent attack timeout (ms)",
            description = "Max time to wait for vent death after attacking. Lower = faster vent-to-vent transitions but more aggressive.",
            position = 2,
            section = tuningSection
    )
    @Range(min = 500, max = 10000)
    default int ventAttackTimeoutMs() {
        return 2000;
    }

    @ConfigItem(
            keyName = "ventPollIntervalMs",
            name = "Vent poll interval (ms)",
            description = "How often to check for vent death during attack. Lower = faster detection but higher CPU.",
            position = 3,
            section = tuningSection
    )
    @Range(min = 10, max = 500)
    default int ventPollIntervalMs() {
        return 50;
    }

    @ConfigItem(
            keyName = "ventInnerLoop",
            name = "Vent inner loop",
            description = "Attack all 4 vents in a tight inner loop without returning to main loop (fastest). Disable if vents get stuck.",
            position = 4,
            section = tuningSection
    )
    default boolean ventInnerLoop() {
        return true;
    }

    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug logging",
            description = "Enable verbose debug logging for troubleshooting (check RuneLite log)",
            position = 5,
            section = tuningSection
    )
    default boolean debugLogging() {
        return false;
    }

    // ── Explosion Dodge ─────────────────────────────────────────

    @ConfigItem(
            keyName = "dodgeStaggeredClicks",
            name = "Staggered walk clicks",
            description = "Number of scheduled walk commands to fire across the dodge window (from animation detection). More clicks = more chances to land after teleport.",
            position = 0,
            section = explosionDodgeSection
    )
    @Range(min = 1, max = 20)
    default int dodgeStaggeredClicks() {
        return 7;
    }

    @ConfigItem(
            keyName = "dodgeStaggeredIntervalMs",
            name = "Staggered interval (ms)",
            description = "Delay between each staggered walk command. Lower = denser spam. Total dodge window = clicks × interval.",
            position = 1,
            section = explosionDodgeSection
    )
    @Range(min = 50, max = 600)
    default int dodgeStaggeredIntervalMs() {
        return 150;
    }

    @ConfigItem(
            keyName = "dodgeImmediateSpamClicks",
            name = "Immediate spam clicks",
            description = "Number of tight back-to-back walk commands in the main loop when explosion is detected. These fire with minimal delay.",
            position = 2,
            section = explosionDodgeSection
    )
    @Range(min = 1, max = 20)
    default int dodgeImmediateSpamClicks() {
        return 5;
    }

    @ConfigItem(
            keyName = "dodgeImmediateSpamDelayMs",
            name = "Immediate spam delay (ms)",
            description = "Sleep between each immediate spam click. Lower = faster but may overwhelm the client.",
            position = 3,
            section = explosionDodgeSection
    )
    @Range(min = 0, max = 200)
    default int dodgeImmediateSpamDelayMs() {
        return 50;
    }

    @ConfigItem(
            keyName = "dodgeTimeoutMs",
            name = "Dodge timeout (ms)",
            description = "Max time to wait for the player to reach safety after explosion teleport. Explosion fires at ~1200ms; add buffer for safety.",
            position = 4,
            section = explosionDodgeSection
    )
    @Range(min = 600, max = 3000)
    default int dodgeTimeoutMs() {
        return 1800;
    }

    @ConfigItem(
            keyName = "explosionWarnThreshold",
            name = "Explosion warn threshold",
            description = "After this many P3 regular attacks, flag explosion as imminent and pre-enable run. Sire typically does 4 attacks before exploding.",
            position = 5,
            section = explosionDodgeSection
    )
    @Range(min = 1, max = 6)
    default int explosionWarnThreshold() {
        return 3;
    }

    @ConfigItem(
            keyName = "dodgeDebugOverlay",
            name = "Dodge debug overlay",
            description = "Show real-time dodge diagnostics on the overlay: click counts, target tile, timing, and method used.",
            position = 6,
            section = explosionDodgeSection
    )
    default boolean dodgeDebugOverlay() {
        return true;
    }
}

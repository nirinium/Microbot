package net.runelite.client.plugins.microbot.niriTormentedDemons;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.niriTormentedDemons.TormentedDemonConfig.CombatStyle;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tormented Demon automation script.
 * <p>
 * Implements the OSRS Wiki strategy:
 * <ul>
 *   <li>Switches between 2-3 combat styles via comma-separated gear lists</li>
 *   <li>Reacts to overhead prayer changes for gear switching</li>
 *   <li>Defensive prayer auto-switching driven by Plugin animation events</li>
 *   <li>Fire bomb dodging driven by Plugin graphics events</li>
 *   <li>Potions, food, looting, and banking (Full Auto mode)</li>
 * </ul>
 */
@Slf4j
public class TormentedDemonScript extends Script {

    // ─── Constants ──────────────────────────────────────────────────────────────

    private static final String TD_NAME = "Tormented Demon";
    private static final int FEROX_POOL_ID = 39651;
    private static final WorldPoint FEROX_AREA = new WorldPoint(3150, 3634, 0);

    /**
     * Items that are ALWAYS looted regardless of config or mode.
     */
    private static final List<String> ALWAYS_LOOT = List.of(
            "Tormented synapse",
            "Burning claw",
            "Guthixian temple teleport"
    );

    // Travel object IDs
    private static final int FIRST_STAIRS_ID = 53623;
    private static final int SECOND_STAIRS_ID = 53624;
    private static final int CLIMB_THROUGH_ID = 54082;
    private static final WorldPoint TEMPLE_ENTRANCE = new WorldPoint(4062, 4558, 0);
    private static final WorldPoint TD_FIGHT_AREA = new WorldPoint(4073, 4432, 0);

    // ─── State ──────────────────────────────────────────────────────────────────

    public enum BotState {
        BANKING, TRAVELLING, FIGHTING
    }

    private enum BankStep {
        TRAVEL_TO_FEROX, RESTORE, OPEN_BANK, LOAD_SETUP
    }

    private enum TravelStep {
        TELEPORT, CLIMB_FIRST, CLIMB_SECOND, CLIMB_THROUGH, WALK_TO_DEMONS
    }

    @Getter
    public static volatile BotState botState = BotState.BANKING;
    @Getter
    public static volatile int killCount = 0;
    @Getter
    public static volatile String statusText = "Initializing...";
    @Getter
    @Setter
    public static volatile CombatStyle activeCombatStyle = null;

    private Rs2NpcModel currentTarget;
    private HeadIcon lastKnownHeadIcon;
    private BankStep bankStep = BankStep.TRAVEL_TO_FEROX;
    private TravelStep travelStep = TravelStep.TELEPORT;
    private boolean hasLooted = false;
    private String lastLogMessage = "";

    // Spec & punish tracking
    private volatile long shieldBrokenTick = 0;
    private long lastPunishTick = 0;
    private long lastSpecTick = 0;
    private static final int SHIELD_DOWN_DURATION_TICKS = 25; // ~15 seconds after shield break animation
    private static final int PUNISH_COOLDOWN_TICKS = 8; // minimum ticks between punish swaps

    // ─── Entry Point ────────────────────────────────────────────────────────────

    public boolean run(TormentedDemonConfig config) {
        // Validate config: need at least 2 combat styles enabled
        int styleCount = 0;
        if (config.useMelee()) styleCount++;
        if (config.useRanged()) styleCount++;
        if (config.useMagic()) styleCount++;
        if (styleCount < 2) {
            Microbot.showMessage("Enable at least 2 combat styles for Tormented Demons!");
            return false;
        }

        killCount = 0;
        activeCombatStyle = null;
        Microbot.enableAutoRunOn = false;

        if (config.mode() == TormentedDemonConfig.Mode.COMBAT_ONLY) {
            botState = BotState.FIGHTING;
        } else {
            botState = BotState.BANKING;
        }

        bankStep = BankStep.RESTORE;
        travelStep = TravelStep.TELEPORT;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                switch (botState) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case TRAVELLING:
                        handleTravel(config);
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                }
            } catch (Exception ex) {
                log.error("Tormented Demon script error", ex);
                logOnce("Error: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    // ─── Banking ────────────────────────────────────────────────────────────────

    private void handleBanking(TormentedDemonConfig config) {
        InventorySetup bankSetup = config.bankingSetup();
        if (bankSetup == null) {
            logOnce("No banking setup selected! Configure a banking inventory setup.");
            shutdown();
            return;
        }

        switch (bankStep) {
            case TRAVEL_TO_FEROX:
                statusText = "Travelling to Ferox...";
                if (playerLocation().distanceTo(FEROX_AREA) <= 20) {
                    bankStep = BankStep.RESTORE;
                    break;
                }
                // Try to teleport using Ring of Dueling
                teleportToFerox();
                if (playerLocation().distanceTo(FEROX_AREA) <= 20) {
                    bankStep = BankStep.RESTORE;
                } else {
                    logOnce("Cannot reach Ferox Enclave — no Ring of Dueling?");
                }
                break;

            case RESTORE:
                statusText = "Restoring at Ferox pool...";
                // If we're too far from Ferox, go back to travel step
                if (playerLocation().distanceTo(FEROX_AREA) > 30) {
                    bankStep = BankStep.TRAVEL_TO_FEROX;
                    break;
                }
                int hp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
                int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
                int pray = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
                int maxPray = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);

                if (hp < maxHp || pray < maxPray) {
                    if (Rs2GameObject.interact(FEROX_POOL_ID, "Drink")) {
                        sleepUntil(() -> {
                            int curHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
                            int curPray = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
                            return curHp >= maxHp && curPray >= maxPray;
                        }, 5000);
                    } else {
                        // Pool not found — try walking closer to Ferox
                        Rs2Walker.walkTo(FEROX_AREA, 4);
                        sleepUntil(() -> playerLocation().distanceTo(FEROX_AREA) <= 6, 5000);
                        break;
                    }
                }
                bankStep = BankStep.OPEN_BANK;
                break;

            case OPEN_BANK:
                statusText = "Opening bank...";
                if (Rs2Bank.isOpen()) {
                    Rs2Bank.depositAll();
                    sleep(300, 500);
                    bankStep = BankStep.LOAD_SETUP;
                } else {
                    if (!Rs2Bank.isNearBank(15)) {
                        // Walk to the nearest bank if we're not close enough
                        Rs2Bank.walkToBank();
                        sleep(600, 1000);
                    } else {
                        Rs2Bank.openBank();
                        sleepUntil(Rs2Bank::isOpen, 5000);
                    }
                }
                break;

            case LOAD_SETUP:
                statusText = "Loading gear & inventory...";
                if (!Rs2Bank.isOpen()) {
                    // Bank closed unexpectedly — reopen it
                    bankStep = BankStep.OPEN_BANK;
                    break;
                }
                Rs2InventorySetup setup = new Rs2InventorySetup(bankSetup, mainScheduledFuture);
                boolean equipOk = setup.loadEquipment();
                boolean invOk = setup.loadInventory();

                if (equipOk && invOk) {
                    Rs2Bank.closeBank();
                    sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
                    bankStep = BankStep.TRAVEL_TO_FEROX;
                    botState = BotState.TRAVELLING;
                    logOnce("Banking complete, heading to demons.");
                } else {
                    logOnce("Failed to load setup — missing items? Shutting down.");
                    shutdown();
                }
                break;
        }
    }

    // ─── Travel ─────────────────────────────────────────────────────────────────

    private void handleTravel(TormentedDemonConfig config) {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleep(300);
            return;
        }

        switch (travelStep) {
            case TELEPORT:
                statusText = "Teleporting to Guthixian Temple...";
                if (Rs2Inventory.interact("Guthixian temple teleport", "Teleport")) {
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    sleep(1200, 1800);
                    travelStep = TravelStep.CLIMB_FIRST;
                } else {
                    logOnce("No Guthixian temple teleport found!");
                }
                break;

            case CLIMB_FIRST:
                statusText = "Climbing first stairs...";
                if (Rs2GameObject.interact(FIRST_STAIRS_ID, "Climb-up")) {
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    sleep(600, 1000);
                    travelStep = TravelStep.CLIMB_SECOND;
                }
                break;

            case CLIMB_SECOND:
                statusText = "Climbing second stairs...";
                if (Rs2GameObject.interact(SECOND_STAIRS_ID, "Climb-up")) {
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    sleep(600, 1000);
                    travelStep = TravelStep.CLIMB_THROUGH;
                }
                break;

            case CLIMB_THROUGH:
                statusText = "Climbing through...";
                if (Rs2GameObject.interact(CLIMB_THROUGH_ID, "Climb-through")) {
                    sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                    sleep(600, 1000);
                    travelStep = TravelStep.WALK_TO_DEMONS;
                }
                break;

            case WALK_TO_DEMONS:
                statusText = "Walking to Tormented Demons...";
                if (Rs2Walker.walkTo(TD_FIGHT_AREA, 4)) {
                    sleepUntil(() -> playerLocation().distanceTo(TD_FIGHT_AREA) <= 6, 10000);
                    travelStep = TravelStep.TELEPORT;
                    botState = BotState.FIGHTING;
                    logOnce("Arrived at demons.");
                }
                break;
        }
    }

    // ─── Fighting ───────────────────────────────────────────────────────────────

    private void handleFighting(TormentedDemonConfig config) {
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) return;

        // ── Eat & drink ──
        Rs2Player.eatAt(config.minEatPercent());
        Rs2Player.drinkPrayerPotionAt(config.minPrayerPercent());

        // ── Loot smouldering drops for emergency healing/prayer ──
        lootSmoulderingDropsIfNeeded(config);

        // ── Emergency teleport check (highest priority) ──
        if (config.enableEmergencyTeleport()) {
            int hpPercent = getHpPercent();
            boolean hasHealing = hasHealing();
            if (!hasHealing && hpPercent <= config.emergencyTeleportHp()) {
                statusText = "CRITICAL HP - Emergency teleporting!";
                // Try to loot quickly if possible before teleporting
                if (currentTarget != null && currentTarget.isDead() && !hasLooted) {
                    attemptLooting(config);
                    hasLooted = true;
                    killCount++;
                }
                emergencyTeleport(config);
                return;
            }
        }

        // ── Retreat check (Full Auto) ──
        if (config.mode() == TormentedDemonConfig.Mode.FULL_AUTO && shouldRetreat(config)) {
            statusText = "Retreating to bank...";
            disableAllPrayers();
            currentTarget = null;
            teleportToFerox();
            bankStep = BankStep.RESTORE;
            botState = BotState.BANKING;
            return;
        }

        // ── Handle dead target → loot ──
        if (currentTarget != null && currentTarget.isDead()) {
            statusText = "Target dying...";
            disableAllPrayers();
            sleepUntil(() -> {
                // Wait for death animation to finish
                Rs2NpcModel refreshed = Rs2Npc.getNpc(currentTarget.getIndex());
                return refreshed == null || refreshed.isDead();
            }, 4000);
            sleep(600, 1000);

            if (!hasLooted) {
                attemptLooting(config);
                hasLooted = true;
                killCount++;
            }
            currentTarget = null;
            lastKnownHeadIcon = null;
            return;
        }

        // ── Acquire target ──
        if (currentTarget == null || currentTarget.isDead()) {
            statusText = "Finding target...";
            hasLooted = false;
            currentTarget = findBestTarget(config);
            if (currentTarget == null) {
                statusText = "No targets available.";
                return;
            }
            lastKnownHeadIcon = currentTarget.getHeadIcon();
            if (lastKnownHeadIcon == null) {
                logOnce("Could not read demon's overhead prayer.");
                currentTarget = null;
                return;
            }
            switchToStyleAgainst(config, lastKnownHeadIcon);
        }

        // ── Check for overhead prayer changes → gear switch ──
        HeadIcon currentIcon = currentTarget.getHeadIcon();
        if (currentIcon != null && currentIcon != lastKnownHeadIcon) {
            statusText = "Switching gear...";
            lastKnownHeadIcon = currentIcon;
            if (!Rs2Inventory.isOpen()) {
                Rs2Inventory.open();
                sleepUntil(Rs2Inventory::isOpen, 800);
            }
            switchToStyleAgainst(config, currentIcon);
            sleep(100, 200);
        }

        // ── Attack if not already interacting ──
        if (currentTarget != null && !currentTarget.isDead()) {
            statusText = "Fighting " + TD_NAME;

            Rs2NpcModel interacting = null;
            if (Rs2Player.getInteracting() instanceof Rs2NpcModel) {
                interacting = (Rs2NpcModel) Rs2Player.getInteracting();
            }

            boolean needsAttack = interacting == null || interacting.getIndex() != currentTarget.getIndex();
            if (needsAttack) {
                if (Rs2Npc.interact(currentTarget, "attack")) {
                    sleepUntil(() -> {
                        Object target = Rs2Player.getInteracting();
                        return target instanceof Rs2NpcModel
                                && ((Rs2NpcModel) target).getIndex() == currentTarget.getIndex();
                    }, 3000);
                } else {
                    logOnce("Attack failed on " + currentTarget.getName());
                    currentTarget = null;
                    return;
                }
            }
        }

        // ── Thralls (cast before weapon swaps to avoid early returns) ──
        if (config.useThralls()) {
            int thrallActive = Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.ARCEUUS_RESURRECTION_ACTIVE);
            int thrallCooldown = Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.ARCEUUS_RESURRECTION_COOLDOWN);
            
            Microbot.log("Thrall check - Active: " + thrallActive + ", Cooldown: " + thrallCooldown + ", Config enabled: " + config.useThralls());
            
            if (thrallActive == 0 && thrallCooldown == 0) {
                Rs2Thrall bestThrall = Rs2Thrall.getBestThrall(config.thrallType());
                Microbot.log("Best thrall found: " + (bestThrall != null ? bestThrall.getName() : "null") + ", Type: " + config.thrallType());
                
                if (bestThrall != null) {
                    boolean hasRequirements = bestThrall.hasRequirements();
                    boolean hasRunes = Rs2Magic.hasRequiredRunes(bestThrall);
                    Microbot.log("Thrall casting check - Requirements: " + hasRequirements + ", Runes: " + hasRunes);
                    
                    if (hasRequirements && hasRunes) {
                        statusText = "Summoning thrall...";
                        Microbot.log("Attempting to cast thrall: " + bestThrall.getName());
                        if (Rs2Magic.cast(bestThrall)) {
                            Microbot.log("Successfully cast thrall: " + bestThrall.getName());
                            sleep(600, 800);
                        } else {
                            Microbot.log("Failed to cast thrall: " + bestThrall.getName());
                        }
                    }
                }
            }
        }

        // ── Spec / Punish weapons (melee only) ──
        if (activeCombatStyle == CombatStyle.MELEE && currentTarget != null && !currentTarget.isDead()) {
            // Spec with Burning Claws (or configured spec weapon)
            if (config.useSpecWeapon()) {
                int specEnergy = Rs2Combat.getSpecEnergy();
                int costRaw = config.specEnergyCost() * 10;
                long currentTick = Microbot.getClient().getTickCount();
                long ticksSinceLastSpec = currentTick - lastSpecTick;
                
                // Check if we have enough spec energy AND cooldown has passed
                if (specEnergy >= costRaw && ticksSinceLastSpec >= config.specAttackCooldown()) {
                    performSpecAttack(config);
                    lastSpecTick = currentTick;
                    return;
                }
            }
            // Dharok's punish when shield is down
            if (config.useDharokPunish() && isDemonShieldDown()) {
                long currentTick = Microbot.getClient().getTickCount();
                if (currentTick - lastPunishTick >= PUNISH_COOLDOWN_TICKS) {
                    performDharokPunish(config);
                    lastPunishTick = currentTick;
                    return;
                }
            }
        }

        // ── Offensive prayer ──
        if (config.enableOffensivePrayer() && activeCombatStyle != null) {
            activateOffensivePrayer(activeCombatStyle);
        }

        // ── Rapid Restore ──
        if (config.useRapidHeal() && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RAPID_HEAL)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.RAPID_HEAL, true);
        }

        // ── Death Charge ──
        if (config.useDeathCharge() && !isDeathChargeActive()) {
            if (Rs2Magic.canCast(Rs2Spells.DEATH_CHARGE)) {
                if (Rs2Magic.cast(Rs2Spells.DEATH_CHARGE)) {
                    logOnce("Cast Death Charge");
                    sleep(600, 800);
                }
            }
        }

        // ── Saturated Heart ──
        if (config.useSaturatedHeart()) {
            useSaturatedHeartIfReady();
        }

        // ── Potions ──
        evaluatePotions(config);
    }

    // ─── Gear Switching ─────────────────────────────────────────────────────────

    /**
     * Given the demon's current overhead prayer, pick the best available style
     * that isn't blocked and equip the corresponding gear setup.
     */
    private void switchToStyleAgainst(TormentedDemonConfig config, HeadIcon demonPrayer) {
        List<CombatStyle> available = getAvailableStyles(config, demonPrayer);
        if (available.isEmpty()) {
            logOnce("No combat style available against " + demonPrayer + "!");
            return;
        }

        // If we're already using an effective style, don't switch
        if (activeCombatStyle != null && available.contains(activeCombatStyle)) {
            return;
        }

        // Pick first available style (priority: melee > ranged > magic)
        CombatStyle chosen = available.get(0);
        String gearList = getGearStringForStyle(config, chosen);
        if (gearList == null || gearList.trim().isEmpty()) {
            logOnce("No gear configured for " + chosen + "!");
            return;
        }

        equipGearFromString(gearList, config.gearSwitchDelay());
        activeCombatStyle = chosen;

        // Immediately switch offensive prayer to match the new style
        if (config.enableOffensivePrayer()) {
            activateOffensivePrayer(chosen);
        }

        logOnce("Switched to " + chosen + " (demon praying " + demonPrayer + ")");
    }

    /**
     * Returns the list of combat styles that are enabled AND not blocked by the demon's prayer.
     */
    private List<CombatStyle> getAvailableStyles(TormentedDemonConfig config, HeadIcon demonPrayer) {
        List<CombatStyle> styles = new ArrayList<>();

        if (config.useMelee() && !config.meleeGear().trim().isEmpty() && demonPrayer != HeadIcon.MELEE) {
            styles.add(CombatStyle.MELEE);
        }
        if (config.useRanged() && !config.rangedGear().trim().isEmpty() && demonPrayer != HeadIcon.RANGED) {
            styles.add(CombatStyle.RANGED);
        }
        if (config.useMagic() && !config.magicGear().trim().isEmpty() && demonPrayer != HeadIcon.MAGIC) {
            styles.add(CombatStyle.MAGIC);
        }
        return styles;
    }

    /**
     * Returns the comma-separated gear string for the given combat style.
     */
    private String getGearStringForStyle(TormentedDemonConfig config, CombatStyle style) {
        switch (style) {
            case MELEE:
                return config.meleeGear();
            case RANGED:
                return config.rangedGear();
            case MAGIC:
                return config.magicGear();
            default:
                return null;
        }
    }

    /**
     * Parse a comma-separated gear string (item names or IDs) and equip each piece.
     * Items already worn are skipped. Supports both names and numeric IDs.
     * Uses config-driven delay between equips for speed tuning.
     */
    private void equipGearFromString(String gearCsv) {
        equipGearFromString(gearCsv, 50); // default fallback
    }

    private void equipGearFromString(String gearCsv, int delayMs) {
        if (gearCsv == null || gearCsv.trim().isEmpty()) return;

        List<String> items = Arrays.stream(gearCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        for (String entry : items) {
            // Try to parse as numeric item ID first
            try {
                int itemId = Integer.parseInt(entry);
                if (Rs2Equipment.isWearing(itemId)) continue;
                if (Rs2Inventory.hasItem(itemId)) {
                    Rs2Inventory.wield(itemId);
                    if (delayMs > 0) sleep(delayMs, delayMs + 50);
                }
                continue;
            } catch (NumberFormatException ignored) {
                // Not a number — treat as item name
            }

            // Treat as item name
            if (Rs2Equipment.isWearing(entry)) continue;
            if (Rs2Inventory.hasItem(entry)) {
                Rs2Inventory.wield(entry);
                if (delayMs > 0) sleep(delayMs, delayMs + 50);
            }
        }
    }

    // ─── Offensive Prayer ───────────────────────────────────────────────────────

    private Rs2PrayerEnum currentOffensivePrayer = null;

    private void activateOffensivePrayer(CombatStyle style) {
        Rs2PrayerEnum desired;
        switch (style) {
            case MELEE:
                desired = Rs2Prayer.getBestMeleePrayer();
                break;
            case RANGED:
                desired = Rs2Prayer.getBestRangePrayer();
                break;
            case MAGIC:
                desired = Rs2Prayer.getBestMagePrayer();
                break;
            default:
                return;
        }

        if (desired != null && desired != currentOffensivePrayer) {
            if (currentOffensivePrayer != null) {
                Rs2Prayer.toggle(currentOffensivePrayer, false);
            }
            Rs2Prayer.toggle(desired, true);
            currentOffensivePrayer = desired;
        }
    }

    // ─── Target Selection ───────────────────────────────────────────────────────

    /**
     * Find the best tormented demon to attack:
     * prefer ones already targeting us, then ones not in combat, then closest.
     * Always filters out NPCs that are being fought by another player.
     */
    private Rs2NpcModel findBestTarget(TormentedDemonConfig config) {
        Player local = Microbot.getClient().getLocalPlayer();
        if (local == null) return null;

        // First: demons already targeting us (always valid)
        Rs2NpcModel engaging = Rs2Npc.getAttackableNpcs(TD_NAME)
                .filter(npc -> npc.getInteracting() == local)
                .filter(npc -> npc.getHeadIcon() != null)
                .filter(npc -> !getAvailableStyles(config, npc.getHeadIcon()).isEmpty())
                .findFirst()
                .orElse(null);

        if (engaging != null) return engaging;

        // Second: idle demons not involved with any other player
        return Rs2Npc.getAttackableNpcs(TD_NAME)
                .filter(npc -> !isBeingFoughtByOtherPlayer(npc, local))
                .filter(npc -> npc.getHeadIcon() != null)
                .filter(npc -> !getAvailableStyles(config, npc.getHeadIcon()).isEmpty())
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks both directions to determine if another player is engaged with an NPC:
     * 1. The NPC is targeting another player (npc → player)
     * 2. Another player is targeting the NPC (player → npc)
     */
    private boolean isBeingFoughtByOtherPlayer(Rs2NpcModel npc, Player local) {
        // Check direction 1: NPC is interacting with another player
        Actor npcTarget = npc.getInteracting();
        if (npcTarget instanceof Player && npcTarget != local) {
            return true;
        }

        // Check direction 2: any other player is interacting with this NPC
        int npcIndex = npc.getIndex();
        boolean otherPlayerTargeting = Rs2Player.getPlayers(player -> {
            Actor playerTarget = player.getInteracting();
            if (playerTarget == null) return false;
            if (!(playerTarget instanceof NPC)) return false;
            return ((NPC) playerTarget).getIndex() == npcIndex;
        }).findAny().isPresent();

        return otherPlayerTargeting;
    }

    // ─── Potions ────────────────────────────────────────────────────────────────

    private void evaluatePotions(TormentedDemonConfig config) {
        int threshold = config.boostedStatsThreshold();

        // Combat potion (melee)
        if (config.combatPotionType() != TormentedDemonConfig.CombatPotionType.NONE) {
            if (!isCombatPotActive(config.combatPotionType(), threshold)) {
                drinkPotion(config.combatPotionType().getKeyword());
            }
        }

        // Ranging potion
        if (config.rangingPotionType() != TormentedDemonConfig.RangingPotionType.NONE) {
            if (!isRangePotActive(config.rangingPotionType(), threshold)) {
                drinkPotion(config.rangingPotionType().getKeyword());
            }
        }
    }

    private boolean isCombatPotActive(TormentedDemonConfig.CombatPotionType type, int threshold) {
        switch (type) {
            case SUPER_COMBAT:
                return Rs2Player.hasAttackActive(threshold) && Rs2Player.hasStrengthActive(threshold);
            case DIVINE_SUPER_COMBAT:
                return Rs2Player.hasDivineCombatActive();
            default:
                return true;
        }
    }

    private boolean isRangePotActive(TormentedDemonConfig.RangingPotionType type, int threshold) {
        switch (type) {
            case RANGING:
                return Rs2Player.hasRangingPotionActive(threshold);
            case DIVINE_RANGING:
                return Rs2Player.hasDivineRangedActive();
            case BASTION:
                return Rs2Player.hasDivineBastionActive();
            default:
                return true;
        }
    }

    private void drinkPotion(String keyword) {
        Rs2Inventory.getPotions().stream()
                .filter(p -> p.getName().toLowerCase().contains(keyword.toLowerCase()))
                .findFirst()
                .ifPresent(p -> {
                    Rs2Inventory.interact(p, "Drink");
                    logOnce("Drinking " + p.getName());
                });
    }

    // ─── Looting ────────────────────────────────────────────────────────────────

    /**
     * Opportunistically loot smouldering drops during combat for emergency healing/prayer.
     * These drops appear on the ground while fighting and provide instant restoration:
     * - Smouldering pile of flesh: heals 10 HP
     * - Smouldering gland: restores 10 prayer points
     */
    private void lootSmoulderingDropsIfNeeded(TormentedDemonConfig config) {
        int hpPercent = getHpPercent();
        int prayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        int maxPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        int prayerPercent = maxPrayer > 0 ? (prayer * 100) / maxPrayer : 100;

        // Loot smouldering flesh if HP is low
        if (config.lootSmoulderingFlesh() && hpPercent <= config.smoulderingFleshHpThreshold()) {
            if (Rs2GroundItem.exists("Smouldering pile of flesh", 10)) {
                statusText = "Emergency loot: flesh for healing";
                if (Rs2GroundItem.loot("Smouldering pile of flesh", 10)) {
                    sleep(300, 600);
                }
            }
        }

        // Loot smouldering gland if prayer is low
        if (config.lootSmoulderingGland() && prayerPercent <= config.smoulderingGlandPrayerThreshold()) {
            if (Rs2GroundItem.exists("Smouldering gland", 10)) {
                statusText = "Emergency loot: gland for prayer";
                if (Rs2GroundItem.loot("Smouldering gland", 10)) {
                    sleep(300, 600);
                }
            }
        }
    }

    private void attemptLooting(TormentedDemonConfig config) {
        statusText = "Looting...";

        // Always loot priority items first, regardless of config
        if (!Rs2Inventory.isFull()) {
            LootingParameters priorityParams = new LootingParameters(
                    10, 1, 1, 0, false, true,
                    ALWAYS_LOOT.toArray(new String[0])
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(priorityParams)) {
                sleep(600, 1000);
            }
        }

        // Then loot user-configured items
        List<String> names = parseLootNames(config.lootItems());
        // Merge in always-loot items (avoid duplicates)
        for (String alwaysItem : ALWAYS_LOOT) {
            if (names.stream().noneMatch(n -> n.equalsIgnoreCase(alwaysItem))) {
                names.add(alwaysItem);
            }
        }

        if (!names.isEmpty()) {
            LootingParameters params = new LootingParameters(
                    10, 1, 1, config.minLootValue(), false, true,
                    names.toArray(new String[0])
            );
            Rs2GroundItem.lootItemsBasedOnNames(params);
            sleep(600, 1000);
        }

        // Also loot items above min value threshold
        if (config.minLootValue() > 0) {
            LootingParameters valueParams = new LootingParameters(
                    10, 1, 1, config.minLootValue(), false, true
            );
            Rs2GroundItem.lootItemsBasedOnNames(valueParams);
            sleep(300, 600);
        }

        if (config.scatterAshes()) {
            lootAndScatterAshes();
        }
    }

    private void lootAndScatterAshes() {
        String ashesName = "Infernal ashes";
        if (!Rs2Inventory.isFull()) {
            LootingParameters ashParams = new LootingParameters(
                    10, 1, 1, 0, false, true, ashesName
            );
            if (Rs2GroundItem.lootItemsBasedOnNames(ashParams)) {
                sleepUntil(() -> Rs2Inventory.contains(ashesName), 2000);
                if (Rs2Inventory.contains(ashesName)) {
                    Rs2Inventory.interact(ashesName, "Scatter");
                    sleep(600, 800);
                }
            }
        }
    }

    private List<String> parseLootNames(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ─── Retreat ────────────────────────────────────────────────────────────────

    private boolean shouldRetreat(TormentedDemonConfig config) {
        int hp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        int hpPercent = (int) ((hp / (double) maxHp) * 100);
        boolean noFood = Rs2Inventory.getInventoryFood().isEmpty();
        boolean noPots = Rs2Inventory.items()
                .noneMatch(item -> item != null && item.getName() != null
                        && item.getName().toLowerCase().contains("prayer"));

        int prayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        return (noFood && hpPercent <= config.healthThreshold())
                || (noPots && prayer < 10);
    }

    private void teleportToFerox() {
        int[] duelingRings = {
                ItemID.RING_OF_DUELING_1, ItemID.RING_OF_DUELING_2,
                ItemID.RING_OF_DUELING_3, ItemID.RING_OF_DUELING_4,
                ItemID.RING_OF_DUELING_5, ItemID.RING_OF_DUELING_6,
                ItemID.RING_OF_DUELING_7, ItemID.RING_OF_DUELING_8
        };

        // Try from equipment first
        if (Rs2Equipment.isWearing("Ring of dueling")) {
            Rs2Equipment.interact(
                    JewelleryLocationEnum.FEROX_ENCLAVE.getTooltip(),
                    JewelleryLocationEnum.FEROX_ENCLAVE.getDestination()
            );
            logOnce("Teleporting to Ferox via equipped ring.");
            sleepUntil(() -> playerLocation().distanceTo(FEROX_AREA) <= 10, 8000);
            return;
        }

        // Try from inventory
        for (int ringId : duelingRings) {
            if (Rs2Inventory.hasItem(ringId)) {
                Rs2Inventory.interact(ringId, "Wear");
                sleep(600, 800);
                Rs2Equipment.interact(
                        JewelleryLocationEnum.FEROX_ENCLAVE.getTooltip(),
                        JewelleryLocationEnum.FEROX_ENCLAVE.getDestination()
                );
                logOnce("Teleporting to Ferox via inventory ring.");
                sleepUntil(() -> playerLocation().distanceTo(FEROX_AREA) <= 10, 8000);
                return;
            }
        }

        logOnce("No Ring of Dueling found for retreat!");
    }

    // ─── Spec Weapon ────────────────────────────────────────────────────────────

    /**
     * Called by the Plugin when the demon plays its shield-break animation (11399).
     * This indicates the fire shield has been broken by a fire weapon hit.
     */
    public void onShieldBroken() {
        shieldBrokenTick = Microbot.getClient().getTickCount();
        logOnce("Demon shield broken! Punish window open.");
    }

    /**
     * Checks whether the demon's fire shield is currently down.
     * Tracked via the NPC's shield-break animation (11399) played by the demon,
     * rather than by timing player attacks.
     * Shield stays down for ~25 ticks (~15 seconds) after the animation.
     */
    private boolean isDemonShieldDown() {
        if (shieldBrokenTick == 0) return false;
        long currentTick = Microbot.getClient().getTickCount();
        long elapsed = currentTick - shieldBrokenTick;
        return elapsed >= 0 && elapsed <= SHIELD_DOWN_DURATION_TICKS;
    }

    /**
     * Equip the configured spec weapon, enable special attack, attack the current target,
     * then switch back to normal melee gear.
     */
    private void performSpecAttack(TormentedDemonConfig config) {
        String specWeapon = config.specWeaponName();
        if (specWeapon == null || specWeapon.trim().isEmpty()) return;
        if (!Rs2Inventory.hasItem(specWeapon) && !Rs2Equipment.isWearing(specWeapon)) {
            logOnce("Spec weapon '" + specWeapon + "' not found in inventory!");
            return;
        }

        statusText = "Spec: " + specWeapon;

        // Equip spec weapon
        if (!Rs2Equipment.isWearing(specWeapon)) {
            Rs2Inventory.wield(specWeapon);
            int delay = config.gearSwitchDelay();
            if (delay > 0) sleep(delay, delay + 50);
        }

        // Enable special attack
        Rs2Combat.setSpecState(true, config.specEnergyCost() * 10);
        sleep(100, 200);

        // Attack target with spec
        if (currentTarget != null && !currentTarget.isDead()) {
            Rs2Npc.interact(currentTarget, "attack");
            sleepUntil(() -> {
                Player p = Microbot.getClient().getLocalPlayer();
                return p != null && p.getAnimation() != -1;
            }, 2000);
            sleep(600, 800);
        }

        // Switch back to normal melee gear
        equipGearFromString(config.meleeGear(), config.gearSwitchDelay());
        logOnce("Spec attack with " + specWeapon);
    }

    /**
     * Equip the configured punish weapon (Dharok's greataxe), attack the current target
     * for a single hit, then switch back to normal melee gear.
     * Should only be called while the demon's fire shield is down.
     */
    private void performDharokPunish(TormentedDemonConfig config) {
        String punishWeapon = config.punishWeaponName();
        if (punishWeapon == null || punishWeapon.trim().isEmpty()) return;
        if (!Rs2Inventory.hasItem(punishWeapon) && !Rs2Equipment.isWearing(punishWeapon)) return;

        statusText = "Punish: " + punishWeapon;

        // Equip punish weapon
        if (!Rs2Equipment.isWearing(punishWeapon)) {
            Rs2Inventory.wield(punishWeapon);
            int delay = config.gearSwitchDelay();
            if (delay > 0) sleep(delay, delay + 50);
        }

        // Attack target
        if (currentTarget != null && !currentTarget.isDead()) {
            Rs2Npc.interact(currentTarget, "attack");
            sleepUntil(() -> {
                Player p = Microbot.getClient().getLocalPlayer();
                return p != null && p.getAnimation() != -1;
            }, 2000);
            sleep(600, 800);
        }

        // Switch back to normal melee gear
        equipGearFromString(config.meleeGear(), config.gearSwitchDelay());
        logOnce("Punish hit with " + punishWeapon);
    }

    // ─── Death Charge ────────────────────────────────────────────────────────────

    /**
     * Returns whether Death Charge is currently active or on cooldown.
     */
    private boolean isDeathChargeActive() {
        return Microbot.getVarbitValue(VarbitID.ARCEUUS_DEATH_CHARGE_ACTIVE) == 1
                || Microbot.getVarbitValue(VarbitID.ARCEUUS_DEATH_CHARGE_COOLDOWN) >= 1;
    }

    // ─── Saturated Heart ────────────────────────────────────────────────────────

    /**
     * Uses the Saturated Heart if it is in the inventory and its cooldown has expired.
     * The heart boosts Magic by 4 + 10% of the player's level.
     */
    private void useSaturatedHeartIfReady() {
        // Check if the heart's cooldown timer varbit is 0 (ready to use)
        if (Microbot.getVarbitValue(VarbitID.SATURATED_HEART_TIME) > 0) return;

        if (Rs2Inventory.hasItem(ItemID.SATURATED_HEART)) {
            Rs2Inventory.interact(ItemID.SATURATED_HEART, "Invigorate");
            logOnce("Used Saturated Heart");
            sleep(600, 800);
        }
    }

    // ─── Utility ────────────────────────────────────────────────────────────────

    void disableAllPrayers() {
        Rs2Prayer.disableAllPrayers();
        currentOffensivePrayer = null;
    }

    /**
     * Get current target (for plugin event handlers).
     */
    public Rs2NpcModel getCurrentTarget() {
        return currentTarget;
    }

    private WorldPoint playerLocation() {
        Player p = Microbot.getClient().getLocalPlayer();
        return p != null ? p.getWorldLocation() : new WorldPoint(0, 0, 0);
    }

    void logOnce(String message) {
        if (!message.equals(lastLogMessage)) {
            Microbot.log(message);
            lastLogMessage = message;
        }
    }

    // ─── Emergency Teleport ─────────────────────────────────────────────────────

    private void emergencyTeleport(TormentedDemonConfig config) {
        statusText = "EMERGENCY TELEPORT!";
        disableAllPrayers();
        currentTarget = null;

        String teleItem = config.teleportItem();
        if (!teleItem.isEmpty() && Rs2Inventory.hasItem(teleItem)) {
            // Try common teleport actions in order
            if (!Rs2Inventory.interact(teleItem, "break")) {
                if (!Rs2Inventory.interact(teleItem, "rub")) {
                    if (!Rs2Inventory.interact(teleItem, "teleport")) {
                        Rs2Inventory.interact(teleItem);
                    }
                }
            }
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Microbot.getClient().isInInstancedRegion(), 5000);
            // After teleport, stop the script or return to banking
            if (config.mode() == TormentedDemonConfig.Mode.FULL_AUTO) {
                botState = BotState.BANKING;
                bankStep = BankStep.RESTORE;
            }
        } else {
            logOnce("Emergency teleport item not found: " + teleItem);
        }
    }

    private int getHpPercent() {
        int current = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int max = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        return max > 0 ? (current * 100) / max : 100;
    }

    private boolean hasHealing() {
        // Check if player has food or brews
        if (!Rs2Inventory.getInventoryFood().isEmpty()) {
            return true;
        }
        // Check for Saradomin brews or other healing potions
        return Rs2Inventory.contains("Saradomin brew");
    }

    @Override
    public void shutdown() {
        super.shutdown();
        disableAllPrayers();
        botState = BotState.BANKING;
        currentTarget = null;
        lastKnownHeadIcon = null;
        activeCombatStyle = null;
        killCount = 0;
        hasLooted = false;
        bankStep = BankStep.TRAVEL_TO_FEROX;
        travelStep = TravelStep.TELEPORT;
        shieldBrokenTick = 0;
        lastPunishTick = 0;
        lastSpecTick = 0;
        statusText = "Stopped.";
    }
}

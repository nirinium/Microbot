package net.runelite.client.plugins.microbot.niriaraxxor;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2LootEngine;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.magic.thralls.ThrallType;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.Microbot.log;

/**
 * Araxxor boss fight automation script.
 * <p>
 * Handles all phases: normal combat, egg/minion mechanics (acidic, mirrorback, ruptura),
 * special attacks (acid ball, acid splatter, acid drip), and enrage phase (cleave dodge).
 * <p>
 * Key mechanics:
 * - Araxxor attacks melee in range; otherwise magic or ranged (whichever player is weaker to)
 * - Melee max 38 → 5 with Protect from Melee; ranged max 38 → 18 with Protect from Missiles
 * - Magic max 21, drains 9% prayer; ranged drains 9% defence
 * - 9 eggs hatch in sequence: first after 3 attacks, then every 6 attacks
 * - Special attack determined by south-east egg colour; same spec used all fight
 * - Enrage at ≤255 HP: faster attacks, cleave replaces melee, dodge on "Skree!"
 */
@Slf4j
public class AraxxorScript extends Script {

    // ── Constants ───────────────────────────────────────
    // NPC IDs
    private static final int ARAXXOR_ID = NpcID.ARAXXOR;
    private static final int ARAXXOR_DEAD_ID = NpcID.ARAXXOR_DEAD;
    private static final int RUPTURA_ID = NpcID.ARAXXOR_MINION_EXPLODE;        // 13673
    private static final int MIRRORBACK_ID = NpcID.ARAXXOR_MINION_MIRRORBACK;  // 13671
    private static final int ACIDIC_ID = NpcID.ARAXXOR_MINION_VENOM;           // 13675
    private static final int EGG_MIRRORBACK_ID = NpcID.ARAXXOR_MINION_EGG_MIRRORBACK; // 13670
    private static final int EGG_EXPLODE_ID = NpcID.ARAXXOR_MINION_EGG_EXPLODE;       // 13672
    private static final int EGG_VENOM_ID = NpcID.ARAXXOR_MINION_EGG_VENOM;           // 13674

    // Acid cannon projectile NPC ID — used for active polling fallback
    private static final int ACID_CANNON_PROJ_NPC = NpcID.ARAXXOR_ACID_CANNON_PROJECTILE; // 13676

    // Projectile / Spotanim IDs
    private static final int RANGED_PROJ = SpotanimID.ARAXXOR_RANGED_PROJECTILE;  // 1621
    private static final int MAGIC_PROJ = SpotanimID.ARAXXOR_MAGIC_PROJECTILE;    // 1622
    private static final int ACID_POOL_PROJ = SpotanimID.ARAXXOR_POOLS_PROJ;      // 2924
    private static final int ACID_POOL_SPLASH = SpotanimID.ARAXXOR_POOLS_SPLASH;  // 2923
    private static final int VENOM_DRIP_GFX = SpotanimID.ARAXXOR_VENOM_DRIP;     // 1626

    // Animation IDs
    private static final int ANIM_MELEE = AnimationID.NPC_ARAXXOR_01_ATTACK_MELEE_01;          // 11480
    private static final int ANIM_RANGED = AnimationID.NPC_ARAXXOR_01_ATTACK_RANGED_01;        // 11476
    private static final int ANIM_MAGIC = AnimationID.NPC_ARAXXOR_01_ATTACK_MAGIC_01;          // 11479
    private static final int ANIM_ACID_LEAK = AnimationID.NPC_ARAXXOR_01_ATTACK_ACID_LEAK_01;  // 11477
    private static final int ANIM_ACID_SPRAY = AnimationID.NPC_ARAXXOR_01_ATTACK_ACID_SPRAY_01;// 11478
    private static final int ANIM_ACID_CANNON = AnimationID.NPC_ARAXXOR_01_ACID_CANNON_01;     // 11493
    private static final int ANIM_ENRAGE_MELEE = AnimationID.NPC_ARAXXOR_01_ATTACK_MELEE_ENRAGED_01; // 11487
    private static final int ANIM_ENRAGE_TRANSITION = AnimationID.NPC_ARAXXOR_01_ENRAGE_TRANSITION_01; // 11488
    private static final int ANIM_DEATH = AnimationID.NPC_ARAXXOR_01_DEATH_01;                 // 11481

    // Object IDs
    private static final int ACID_POOL_OBJ = ObjectID1.ARAXXOR_ACIDPOOL;          // 54148
    private static final int VENOM_PUDDLE_1 = ObjectID1.ARAXXOR_VENOM_PUDDLE01;   // 54255
    private static final int VENOM_PUDDLE_2 = ObjectID1.ARAXXOR_VENOM_PUDDLE02;   // 54256
    private static final int VENOM_PUDDLE_3 = ObjectID1.ARAXXOR_VENOM_PUDDLE03;   // 54257

    // Loot targets
    private static final Set<String> ARAXXOR_UNIQUE_DROPS = new HashSet<>(Arrays.asList(
            "noxious point", "noxious blade", "noxious pommel",
            "araxyte fang", "araxyte venom sack", "jar of venom",
            "spider cave teleport"));
    private static final Set<String> ARAXXOR_SUPPLY_DROPS = new HashSet<>(Arrays.asList(
            "shark", "prayer potion", "super combat potion"));

    // ── State ───────────────────────────────────────────
    @Getter
    @Setter
    private volatile AraxxorState state = AraxxorState.IDLE;
    @Getter
    private volatile String status = "Starting...";
    @Getter
    private volatile int killCount = 0;
    @Getter
    private volatile boolean enraged = false;

    // Acid pools tracked via plugin events
    @Getter
    private final Set<WorldPoint> acidPools = new HashSet<>();

    // Attack counter for egg hatching awareness
    @Getter
    @Setter
    private volatile int araxxorAttackCount = 0;

    // Spec tracking
    private volatile int specHitsCompleted = 0;

    // Acid drip tracking
    @Getter
    @Setter
    private volatile boolean acidDripActive = false;
    @Setter
    private volatile long acidDripStartTick = 0;
    // Alternating phase for acid drip movement: 0 = step under boss, 1 = step off + attack
    private volatile int acidDripPhase = 0;

    // Enrage cleave tracking
    @Getter
    @Setter
    private volatile boolean cleaveIncoming = false;

    // Acid cannon (big acid ball) tracking
    @Getter
    @Setter
    private volatile boolean acidCannonIncoming = false;
    private volatile WorldPoint acidCannonSourceTile = null;
    // Pre-computed dodge destination — set by event handler for immediate reaction
    private volatile WorldPoint precomputedAcidDodge = null;

    // Pre-computed cleave dodge destination — set on same game tick as detection
    private volatile WorldPoint precomputedCleaveDodge = null;
    // Tick when cleave was detected — used to measure reaction time
    private volatile int cleaveDetectedTick = 0;
    // Tick when last cleave dodge walk completed — used to prevent stepping back under too soon
    private volatile int lastCleaveDodgeTick = 0;

    // Cleave AoE tracking — the 3 danger tiles from the most recent cleave
    private volatile Set<WorldPoint> cleaveDangerTiles = new HashSet<>();
    // Normalized attack direction (Araxxor→Player) at the moment the cleave fired
    private volatile int cleaveAttackDirX = 0;
    private volatile int cleaveAttackDirY = 0;
    // Last known non-zero attack direction — used as fallback when player is on boss tile (0,0)
    private volatile int lastKnownAtkDirX = 0;
    private volatile int lastKnownAtkDirY = 0;

    // Arena center — set when we first find Araxxor, used to bias all movement toward center
    private volatile WorldPoint arenaCenter = null;
    // Approximate arena radius (Araxxor lair is roughly 14-16 tiles across)
    private static final int ARENA_RADIUS = 7;
    // Minimum distance from walls we want to maintain
    private static final int WALL_BUFFER = 2;

    private AraxxorConfig config;

    // ── Public API ──────────────────────────────────────

    public boolean run(AraxxorConfig config) {
        this.config = config;
        state = AraxxorState.IDLE;
        status = "Starting...";
        killCount = 0;
        enraged = false;
        specHitsCompleted = 0;
        araxxorAttackCount = 0;
        acidPools.clear();
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                loop();
            } catch (Exception ex) {
                Microbot.logStackTrace("AraxxorScript", ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);  // 100ms tick for responsive dodging

        return true;
    }

    /**
     * Called from plugin when Araxxor's animation changes — used to detect attacks & enrage.
     */
    public void onAraxxorAnimation(int animationId) {
        if (animationId == ANIM_ENRAGE_TRANSITION) {
            enraged = true;
            log("Araxxor entering enrage phase!");
            return;
        }

        // Count standard attacks for egg hatch tracking
        if (animationId == ANIM_MELEE || animationId == ANIM_RANGED || animationId == ANIM_MAGIC) {
            araxxorAttackCount++;
        }

        // Detect enrage cleave
        if (animationId == ANIM_ENRAGE_MELEE) {
            cleaveIncoming = true;
            computeCleaveTiles();
            // Issue dodge walk on the SAME game tick for fastest possible reaction
            precomputeAndDodgeCleave();
        }

        // Detect acid special attacks
        if (animationId == ANIM_ACID_LEAK) {
            acidDripActive = true;
            acidDripStartTick = Microbot.getClient().getTickCount();
            acidDripPhase = 0; // start with step-under
            log("Acid drip detected — step under boss!");
        }

        if (animationId == ANIM_ACID_SPRAY) {
            log("Acid splatter detected — move away!");
        }

        if (animationId == ANIM_ACID_CANNON) {
            log("Acid cannon launched — dodge NOW!");
            acidCannonIncoming = true;
            // Track where Araxxor was standing when it fired
            Rs2NpcModel boss = Rs2Npc.getNpc(ARAXXOR_ID);
            if (boss != null) {
                acidCannonSourceTile = boss.getWorldLocation();
            }
            // Pre-compute and initiate dodge on the same game tick
            precomputeAndDodgeAcidCannon();
        }

        // Detect death animation — reliable reset even if onNpcDespawned misses isDead()
        if (animationId == ANIM_DEATH) {
            log("Araxxor death animation detected — resetting state");
            onAraxxorDeath();
        }
    }

    /**
     * Called from plugin when acid cannon projectile NPC spawns — backup detection.
     * Ensures we flag the dodge even if animation detection was missed.
     */
    public void onAcidCannonDetected() {
        if (!acidCannonIncoming) {
            log("Acid cannon detected via projectile NPC spawn!");
            acidCannonIncoming = true;
            Rs2NpcModel boss = Rs2Npc.getNpc(ARAXXOR_ID);
            if (boss != null) {
                acidCannonSourceTile = boss.getWorldLocation();
            }
            // Pre-compute and dodge immediately if animation path didn't already
            if (precomputedAcidDodge == null) {
                precomputeAndDodgeAcidCannon();
            }
        }
    }

    /**
     * Called from plugin on NPC death for kill tracking.
     * Guarded against double-calls (death animation + despawn can both trigger this).
     */
    public void onAraxxorDeath() {
        // Guard: only count the kill once per fight (state transitions to LOOTING on first call)
        if (state == AraxxorState.LOOTING) {
            return; // Already processed this death
        }
        killCount++;
        resetFightState();
        state = AraxxorState.LOOTING;
        log("Araxxor killed! Total: " + killCount);
    }

    @Override
    public void shutdown() {
        try {
            togglePrayers(false);
        } catch (Exception ignored) {
        }
        state = AraxxorState.IDLE;
        status = "Stopped";
        acidPools.clear();
        super.shutdown();
    }

    // ── Tick-Aligned Interruptible Sleep ────────────────

    /**
     * Sleep up to 1 game tick (600ms) but wake early if an urgent dodge event fires.
     * Polls every 100ms for cleave or acid cannon flags set by plugin event handlers.
     * <p>
     * This replaces raw {@code sleep(600)} in all combat-phase code so the script
     * can react to "Skree!" cleaves and acid cannon within ~100ms instead of being
     * blind for a full tick.
     *
     * @return true if interrupted by a dodge event (caller should return to main loop)
     */
    private boolean tickSleep() {
        return sleepUntil(() -> (enraged && cleaveIncoming) || acidCannonIncoming, 600);
    }

    // ── Main Loop ───────────────────────────────────────

    private void loop() {
        Client client = Microbot.getClient();
        Player player = client.getLocalPlayer();
        if (player == null) return;

        // ── 0. Safety: eat & emergency teleport checks ──
        int hpPercent = getHpPercent();
        boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();

        if (!hasFood && hpPercent <= config.emergencyTeleportHp()) {
            status = "Emergency teleport!";
            emergencyTeleport();
            return;
        }
        if (hpPercent <= config.eatAtHpPercent() && hasFood) {
            status = "Eating...";
            Rs2Player.eatAt(config.eatAtHpPercent());
            if (tickSleep()) return; // interrupted by dodge event
        }

        // ── 1. HIGHEST PRIORITY: flee ruptura before it reaches us ──
        // Ruptura walks toward the player and explodes once on the same tile for 1 tick.
        // The explosion hits for up to 80 damage at melee range. At 3+ tiles it's only ~7.
        // We flee at distance ≤ 2 (one tile BEFORE it can reach us) to guarantee we're
        // gone before it arrives on our tile and triggers the explosion.
        Rs2NpcModel rupturaUrgent = findNearestNpc(RUPTURA_ID);
        if (rupturaUrgent != null && !rupturaUrgent.isDead()) {
            WorldPoint ruptLoc = rupturaUrgent.getWorldLocation();
            int ruptDist = player.getWorldLocation().distanceTo(ruptLoc);
            if (ruptDist <= 3) {
                // Getting close! Run 5 tiles away NOW before it reaches our tile
                status = "FLEE RUPTURA! (" + ruptDist + " tiles)";
                state = AraxxorState.KILLING_RUPTURA;
                fleeFromRuptura(player, ruptLoc);
                return;
            }
        }

        // ── 2. Priority: dodge enrage cleave immediately ──
        if (enraged && cleaveIncoming) {
            status = "Dodging cleave!";
            dodgeCleave(player);
            cleaveIncoming = false;
            clearCleaveTiles();
            precomputedCleaveDodge = null;
            return;
        }

        // ── 3. Priority: dodge acid cannon (big acid ball) ──
        // Active poll: check if the acid cannon projectile NPC (13676) is alive in case
        // event-based detection (animation / NPC spawn) was missed entirely.
        if (!acidCannonIncoming) {
            Rs2NpcModel cannonNpc = findNearestNpc(ACID_CANNON_PROJ_NPC);
            if (cannonNpc != null && !cannonNpc.isDead()) {
                log("Acid cannon detected via active polling!");
                acidCannonIncoming = true;
                Rs2NpcModel boss = findNearestNpc(ARAXXOR_ID);
                if (boss != null) {
                    acidCannonSourceTile = boss.getWorldLocation();
                }
            }
        }
        if (acidCannonIncoming) {
            status = "Dodging acid cannon!";
            state = AraxxorState.DODGING_ACID_BALL;
            if (precomputedAcidDodge != null) {
                // Walk already initiated by event handler — just confirm and clear
                log("Acid cannon dodge already in progress → " + precomputedAcidDodge);
                precomputedAcidDodge = null;
            } else {
                // Fallback: event handler missed, compute and dodge now
                dodgeAcidCannon(player);
            }
            acidCannonIncoming = false;
            return;
        }

        // ── 4. Priority: handle acid drip (keep moving) ──
        if (acidDripActive) {
            long ticksSinceDrip = client.getTickCount() - acidDripStartTick;
            if (ticksSinceDrip > 6) {
                acidDripActive = false;
            } else {
                status = "Acid drip — moving!";
                handleAcidDrip(player);
                return;
            }
        }

        // ── 5. Avoid standing on acid pools ──
        if (isOnAcidPool(player.getWorldLocation())) {
            status = "Moving off acid!";
            moveOffAcid(player);
            return;
        }

        // Wall proximity is handled passively by pickBestTile()'s scoreTile() —
        // all movement destinations are penalised for being near walls.
        // We only force a return-to-center after explosive events (ruptura flee,
        // acid cannon dodge) that may have pushed us far out, not every loop tick.

        // ── 5. Find Araxxor ──
        Rs2NpcModel araxxor = findNearestNpc(ARAXXOR_ID);

        // No boss found — could be dead/looting/idle
        if (araxxor == null) {
            handleNoBoss();
            return;
        }

        // Set arena center on first sighting of Araxxor (its spawn ≈ arena center)
        if (arenaCenter == null) {
            arenaCenter = araxxor.getWorldLocation();
            log("Arena center set to " + arenaCenter);
        }

        // ── 6. Handle minions (priority order: ruptura > mirrorback > acidic) ──
        if (handleMinions(player, araxxor)) {
            return; // Minion handling took priority
        }

        // ── 7. Potions ──
        drinkPotions();

        // ── 8. Prayer management ──
        managePrayers();

        // ── 8.5. Thrall summoning ──
        if (config.useThralls()) {
            summonThrall();
        }

        // ── 9. Special attack for Defence drain ──
        if (config.useSpecialAttack() && specHitsCompleted < config.specCount()) {
            if (performSpecialAttack(araxxor)) {
                return;
            }
        }

        // ── 10. Enrage phase: step-under technique ──
        if (enraged) {
            status = "Enraged — step-under fighting!";
            handleEnrageFight(player, araxxor);
            return;
        }

        // ── 11. Normal fight ──
        status = "Fighting Araxxor";
        state = AraxxorState.FIGHTING;

        WorldPoint playerLoc = player.getWorldLocation();

        // Only reposition if we're standing on acid — otherwise stay put and fight.
        // All dodge/flee destinations are already center-biased and acid-aware via pickBestTile.
        if (isOnAcidPool(playerLoc)) {
            moveOffAcid(player);
            return;
        }

        // Engage Araxxor if not already in combat
        if (!Rs2Combat.inCombat() || !player.isInteracting()) {
            Rs2Npc.interact(araxxor, "attack");
            tickSleep();
        }

        // Drink prayer potion if needed
        drinkPrayerPotion();
    }

    // ── Enrage Cleave Safety ────────────────────────────

    /**
     * Check for and dodge an incoming enrage cleave attack.
     * This is called from every minion handler so the player is never caught
     * standing still during a "Skree!" cleave while dealing with adds.
     *
     * @return true if a cleave was dodged (caller should re-evaluate state)
     */
    private boolean checkAndDodgeCleave(Player player) {
        if (enraged && cleaveIncoming) {
            status = "Dodging cleave (during adds)!";
            dodgeCleave(player);
            cleaveIncoming = false;
            clearCleaveTiles();
            precomputedCleaveDodge = null;
            return true;
        }
        return false;
    }

    // ── Minion Handling ─────────────────────────────────

    /**
     * Handle araxyte minions in priority order.
     * Every minion handler also checks for enrage cleave so the player always
     * dodges "Skree!" even while dealing with adds.
     *
     * @return true if a minion action was taken and we should skip the rest of the loop
     */
    private boolean handleMinions(Player player, Rs2NpcModel araxxor) {
        // Cleave & acid cannon checks before starting any minion work
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

        // ── Ruptura (red) — most dangerous, explodes for up to 80 damage ──
        Rs2NpcModel ruptura = findNearestNpc(RUPTURA_ID);
        if (ruptura != null && !ruptura.isDead()) {
            return handleRuptura(player, araxxor, ruptura);
        }

        // ── Mirrorback (white) — reflects damage ──
        Rs2NpcModel mirrorback = findNearestNpc(MIRRORBACK_ID);
        if (mirrorback != null && !mirrorback.isDead()) {
            return handleMirrorback(player, mirrorback);
        }

        // ── Acidic (green) — ranged attacks, explodes on death ──
        if (config.killAcidicAraxytes()) {
            Rs2NpcModel acidic = findNearestNpc(ACIDIC_ID);
            if (acidic != null && !acidic.isDead()) {
                return handleAcidic(player, acidic);
            }
        }

        return false;
    }

    /**
     * Handle ruptura araxyte — wait for it to approach, then dodge well before it reaches us.
     * <p>
     * Ruptura walks toward the player and explodes once on the same tile.
     * It only needs 1 game tick on our tile to trigger the explosion.
     * At 4+ tiles the explosion only deals ~7 damage.
     * <p>
     * Strategy: keep attacking Araxxor while the ruptura approaches. The moment it
     * reaches distance ≤ 3, run 5 tiles away and keep re-fleeing every 100ms if it closes.
     * This gives multiple game ticks of margin so we're safely distant when it detonates.
     * <p>
     * Lure mode: position so the ruptura paths through/under Araxxor, dealing
     * 64-80 damage to the boss when it explodes.
     */
    private boolean handleRuptura(Player player, Rs2NpcModel araxxor, Rs2NpcModel ruptura) {
        state = AraxxorState.KILLING_RUPTURA;
        WorldPoint rupturaLoc = ruptura.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();
        int distToRuptura = playerLoc.distanceTo(rupturaLoc);

        // ── Close enough to worry? RUN 5 tiles NOW (flee at ≤3 for extra margin) ──
        if (distToRuptura <= 3) {
            status = "FLEE RUPTURA! Close! (" + distToRuptura + " tiles)";
            fleeFromRuptura(player, rupturaLoc);

            // Stay 4+ tiles away and wait for the ruptura to die/despawn before re-engaging.
            // Poll every 100ms (faster than before) to catch closing distance sooner.
            sleepUntil(() -> {
                Player p = Microbot.getClient().getLocalPlayer();
                if (p == null) return true;

                // Keep dodging cleave/cannon while waiting
                checkAndDodgeCleave(p);
                checkAndDodgeAcidCannon(p);

                Rs2NpcModel rupt = findNearestNpc(RUPTURA_ID);
                if (rupt == null || rupt.isDead()) {
                    return true; // It's dead, safe to proceed
                }

                // If it's getting close again, flee at ≤3 tiles (before it can reach us)
                WorldPoint rLoc = rupt.getWorldLocation();
                if (p.getWorldLocation().distanceTo(rLoc) <= 3) {
                    fleeFromRuptura(p, rLoc);
                }

                return false;
            }, () -> {
                // Idle callback: eat if needed while waiting
                Rs2Player.eatAt(config.eatAtHpPercent());
            }, 10000, 100);

            // After ruptura dies, verify we're at a safe distance before returning
            status = "Ruptura dead — checking position";
            if (isTooCloseToWall(player.getWorldLocation())) {
                returnToCenter(player);
            }
            return true;
        }

        // ── Dodge enrage cleave / acid cannon even while waiting for ruptura ──
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

        // ── Still approaching — keep fighting Araxxor ──
        if (config.lureRupturaToAraxxor()) {
            // Position so ruptura paths through Araxxor to reach us.
            // Stay on the opposite side of Araxxor from the ruptura.
            WorldPoint araxxorLoc = araxxor.getWorldLocation();
            int dx = araxxorLoc.getX() - rupturaLoc.getX();
            int dy = araxxorLoc.getY() - rupturaLoc.getY();
            int normX = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
            int normY = dy == 0 ? 0 : (dy > 0 ? 1 : -1);
            // Stand 1 tile past Araxxor on the far side from ruptura
            WorldPoint lureTile = new WorldPoint(
                    araxxorLoc.getX() + normX,
                    araxxorLoc.getY() + normY,
                    playerLoc.getPlane()
            );

            if (playerLoc.distanceTo(lureTile) > 1) {
                status = "Luring ruptura — positioning (" + distToRuptura + " tiles)";
                Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), lureTile));
                tickSleep();
            } else {
                status = "Ruptura approaching (" + distToRuptura + " tiles)";
                if (!player.isInteracting()) {
                    Rs2Npc.interact(araxxor, "attack");
                }
            }
        } else {
            // Non-lure: just keep fighting, we'll dodge when it's adjacent
            status = "Ruptura approaching (" + distToRuptura + " tiles)";
            if (!player.isInteracting()) {
                Rs2Npc.interact(araxxor, "attack");
            }
        }

        Rs2Player.eatAt(config.eatAtHpPercent());
        return true;
    }

    /**
     * Run 5 tiles away from a ruptura araxyte in the opposite direction.
     * Increased distance from 3 to 5 to provide a larger safety margin —
     * the ruptura moves 1 tile/tick, so even with polling variance we stay safe.
     * Avoids acid pools on both the destination and the path.
     */
    private void fleeFromRuptura(Player player, WorldPoint rupturaLoc) {
        WorldPoint playerLoc = player.getWorldLocation();
        int dx = playerLoc.getX() - rupturaLoc.getX();
        int dy = playerLoc.getY() - rupturaLoc.getY();

        // If on the same tile, pick a random direction
        if (dx == 0 && dy == 0) {
            dx = Rs2Random.between(0, 2) == 0 ? 1 : -1;
            dy = Rs2Random.between(0, 2) == 0 ? 1 : -1;
        }

        int normX = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
        int normY = dy == 0 ? 0 : (dy > 0 ? 1 : -1);

        // Generate candidates at 5 tiles (primary), 4 tiles (fallback), plus perpendiculars
        WorldPoint[] candidates = {
                // Primary: 5 tiles directly away
                new WorldPoint(playerLoc.getX() + normX * 5, playerLoc.getY() + normY * 5, playerLoc.getPlane()),
                // Perpendicular escapes at 5 tiles
                new WorldPoint(playerLoc.getX() + normY * 5, playerLoc.getY() - normX * 5, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() - normY * 5, playerLoc.getY() + normX * 5, playerLoc.getPlane()),
                // Fallback: 4 tiles directly away
                new WorldPoint(playerLoc.getX() + normX * 4, playerLoc.getY() + normY * 4, playerLoc.getPlane()),
                // Perpendicular at 4 tiles
                new WorldPoint(playerLoc.getX() + normY * 4, playerLoc.getY() - normX * 4, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() - normY * 4, playerLoc.getY() + normX * 4, playerLoc.getPlane()),
        };

        WorldPoint best = pickBestTile(candidates);
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
        tickSleep();
    }

    /**
     * Handle mirrorback araxyte — redirects 20% of Araxxor damage to itself,
     * recoils 50% of that to player. Must kill with ranged/magic/halberd (no melee recoil).
     * Noxious halberd 1-hits them.
     * <p>
     * Critical: with a halberd we must maintain the configured safe distance (default 2 tiles)
     * between us and the mirrorback to avoid reflected damage. At distance 1 (adjacent),
     * the mirrorback reflects heavy damage back to the player. At distance 2, the halberd's
     * extended reach lets us hit safely without reflection.
     * <p>
     * We verify true tile distance before attacking and re-verify inside the wait loop
     * after any dodge repositions us.
     */
    private boolean handleMirrorback(Player player, Rs2NpcModel mirrorback) {
        state = AraxxorState.KILLING_MIRRORBACK;
        status = "Killing mirrorback araxyte!";

        // Dodge cleave before committing to weapon switch
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

        int safeDist = config.mirrorbackSafeDistance();

        // Check if we're at the correct distance from the mirrorback.
        // For halberd (safeDist=2): need exactly 2 cardinal tiles (1 tile gap).
        // For melee  (safeDist=1): need exactly 1 cardinal tile (adjacent).
        WorldPoint mbLoc = mirrorback.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();
        int relX = Math.abs(playerLoc.getX() - mbLoc.getX());
        int relY = Math.abs(playerLoc.getY() - mbLoc.getY());
        // Cardinal distance: one axis equals safeDist, the other is 0
        boolean atCorrectDistance = (relX == safeDist && relY == 0) || (relX == 0 && relY == safeDist);

        if (!atCorrectDistance) {
            // Walk to a cardinal tile at the safe distance
            WorldPoint[] targetTiles = {
                    new WorldPoint(mbLoc.getX(), mbLoc.getY() - safeDist, mbLoc.getPlane()),  // south
                    new WorldPoint(mbLoc.getX() + safeDist, mbLoc.getY(), mbLoc.getPlane()),  // east
                    new WorldPoint(mbLoc.getX() - safeDist, mbLoc.getY(), mbLoc.getPlane()),  // west
                    new WorldPoint(mbLoc.getX(), mbLoc.getY() + safeDist, mbLoc.getPlane()),  // north
            };
            WorldPoint best = pickBestTile(targetTiles);
            Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
            if (tickSleep()) return true; // interrupted by dodge
            // Wait until we actually arrive at the correct distance
            final int sd = safeDist;
            sleepUntil(() -> {
                Player p = Microbot.getClient().getLocalPlayer();
                Rs2NpcModel mb = findNearestNpc(MIRRORBACK_ID);
                if (p == null || mb == null || mb.isDead()) return true;
                int rx = Math.abs(p.getWorldLocation().getX() - mb.getWorldLocation().getX());
                int ry = Math.abs(p.getWorldLocation().getY() - mb.getWorldLocation().getY());
                return (rx == sd && ry == 0) || (rx == 0 && ry == sd);
            }, 1800);
            return true; // let loop re-verify position
        }

        // Switch to araxyte weapon (noxious halberd recommended)
        String araxWeapon = config.araxyteSwitchWeapon();
        if (!araxWeapon.isEmpty()) {
            if (!Rs2Equipment.isWearing(araxWeapon)) {
                if (!Rs2Inventory.wield(araxWeapon)) {
                    log("WARNING: " + araxWeapon + " not in inventory — cannot equip for mirrorback");
                    return true;
                }
                if (tickSleep()) { switchToMainWeapon(); return true; }
                // Verify the weapon actually equipped
                sleepUntil(() -> Rs2Equipment.isWearing(config.araxyteSwitchWeapon()), 1200);
                if (!Rs2Equipment.isWearing(araxWeapon)) {
                    log("WARNING: Failed to equip " + araxWeapon + " for mirrorback!");
                    switchToMainWeapon();
                    return true; // retry next loop
                }
            }
        }

        // Dodge cleave before attacking
        if (checkAndDodgeCleave(player)) {
            switchToMainWeapon();
            return true;
        }
        if (checkAndDodgeAcidCannon(player)) {
            switchToMainWeapon();
            return true;
        }

        Rs2Npc.interact(mirrorback, "attack");
        tickSleep();

        // Wait until the mirrorback is actually dead before switching back.
        // Check for cleave dodges while we wait so we don't eat a "Skree!".
        // Also re-verify safe distance and reposition if a dodge moved us.
        sleepUntil(() -> {
            Player p = Microbot.getClient().getLocalPlayer();
            if (p != null) {
                checkAndDodgeCleave(p);
                checkAndDodgeAcidCannon(p);
            }
            Rs2NpcModel mb = findNearestNpc(MIRRORBACK_ID);
            return mb == null || mb.isDead();
        }, () -> {
            // Keep re-attacking if we stopped interacting (e.g. after a dodge)
            Rs2NpcModel mb = findNearestNpc(MIRRORBACK_ID);
            if (mb != null && !mb.isDead()) {
                Player p = Microbot.getClient().getLocalPlayer();
                if (p != null) {
                    // Re-verify safe distance; reposition if needed
                    WorldPoint pLoc = p.getWorldLocation();
                    WorldPoint mLoc = mb.getWorldLocation();
                    int rx = Math.abs(pLoc.getX() - mLoc.getX());
                    int ry = Math.abs(pLoc.getY() - mLoc.getY());
                    int sd = config.mirrorbackSafeDistance();
                    boolean ok = (rx == sd && ry == 0) || (rx == 0 && ry == sd);
                    if (!ok) {
                        // Not at correct distance — walk to nearest cardinal tile at safe distance
                        WorldPoint[] adj = {
                                new WorldPoint(mLoc.getX(), mLoc.getY() - sd, mLoc.getPlane()),
                                new WorldPoint(mLoc.getX() + sd, mLoc.getY(), mLoc.getPlane()),
                                new WorldPoint(mLoc.getX() - sd, mLoc.getY(), mLoc.getPlane()),
                                new WorldPoint(mLoc.getX(), mLoc.getY() + sd, mLoc.getPlane()),
                        };
                        WorldPoint best = pickBestTile(adj);
                        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
                    } else if (!p.isInteracting()) {
                        Rs2Npc.interact(mb, "attack");
                    }
                }
            }
        }, 6000, 600);

        // Dodge cleave one more time before switching
        checkAndDodgeCleave(player);

        // Now safe to switch back to main weapon
        switchToMainWeapon();
        return true;
    }

    /**
     * Handle acidic araxyte — ranged attack up to 15 damage.
     * Explodes on death splattering acid in 7x7 AoE — move away after killing.
     * <p>
     * Also checks for enrage cleave between actions so we never eat a "Skree!" while
     * weapon-switching or waiting for the attack/explosion.
     */
    private boolean handleAcidic(Player player, Rs2NpcModel acidic) {
        state = AraxxorState.KILLING_ACIDIC;
        status = "Killing acidic araxyte!";

        // Dodge cleave / acid cannon before committing to weapon switch
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

        String araxWeapon = config.araxyteSwitchWeapon();
        if (!araxWeapon.isEmpty()) {
            if (!Rs2Equipment.isWearing(araxWeapon)) {
                if (!Rs2Inventory.wield(araxWeapon)) {
                    log("WARNING: " + araxWeapon + " not in inventory — cannot equip for acidic");
                    return true;
                }
                if (tickSleep()) { switchToMainWeapon(); return true; }
                // Verify the weapon actually equipped
                sleepUntil(() -> Rs2Equipment.isWearing(config.araxyteSwitchWeapon()), 1200);
                if (!Rs2Equipment.isWearing(araxWeapon)) {
                    log("WARNING: Failed to equip " + araxWeapon + " for acidic!");
                    switchToMainWeapon();
                    return true; // retry next loop
                }
            }
        }

        // Dodge cleave / acid cannon before attacking
        if (checkAndDodgeCleave(player)) {
            switchToMainWeapon();
            return true;
        }
        if (checkAndDodgeAcidCannon(player)) {
            switchToMainWeapon();
            return true;
        }

        Rs2Npc.interact(acidic, "attack");
        tickSleep();

        // Wait until the acidic is dead before switching weapon or moving away.
        // Dodge cleave/cannon while waiting.
        sleepUntil(() -> {
            Player p = Microbot.getClient().getLocalPlayer();
            if (p != null) {
                checkAndDodgeCleave(p);
                checkAndDodgeAcidCannon(p);
            }
            Rs2NpcModel ac = findNearestNpc(ACIDIC_ID);
            return ac == null || ac.isDead();
        }, () -> {
            // Keep re-attacking if we stopped interacting (e.g. after a dodge)
            Rs2NpcModel ac = findNearestNpc(ACIDIC_ID);
            if (ac != null && !ac.isDead()) {
                Player p = Microbot.getClient().getLocalPlayer();
                if (p != null && !p.isInteracting()) {
                        Rs2Npc.interact(ac, "attack");
                    }
            }
        }, 6000, 600);

        // Acidic is dead — move away from the 7x7 acid AoE explosion
        WorldPoint acidLoc = acidic.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();
        if (playerLoc.distanceTo(acidLoc) < 5) {
            moveAwayFrom(playerLoc, acidLoc, 4);
            tickSleep();
        }

        // Now safe to switch back to main weapon
        switchToMainWeapon();
        return true;
    }

    // ── Enrage Phase ────────────────────────────────────

    /** Maximum acid tiles tolerated in a 5x5 area around Araxxor before we lure it elsewhere. */
    private static final int ACID_LURE_THRESHOLD = 3;

    /**
     * Handle enrage phase using step-under technique.
     * After ≤255 HP: attack speed 6→4 ticks, cleave replaces melee (1x3 area + acid).
     * Step-under makes cleave damage Araxxor itself for 8-12 damage.
     * <p>
     * Before stepping under, we check the acid density around Araxxor's tile.
     * If too many acid pools surround the boss, we lure it toward a clean spot
     * by walking there (Araxxor follows the player in melee range). Once the
     * area is clean, we resume the step-under → dodge → attack cycle.
     */
    private void handleEnrageFight(Player player, Rs2NpcModel araxxor) {
        state = AraxxorState.ENRAGED_STEP_UNDER;

        WorldPoint araxxorLoc = araxxor.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();
        int currentTick = Microbot.getClient().getTickCount();

        // ── Dodge cooldown: don't step back under too soon after dodging ──
        // After a cleave dodge, we must wait at least 2 game ticks before stepping
        // under Araxxor again. Otherwise the step-under walk can OVERRIDE the dodge
        // walk within the same game tick, leaving us on the cleave AoE.
        boolean recentlyDodged = (currentTick - lastCleaveDodgeTick) < 2;

        // ── Check acid density around Araxxor ──
        int acidNearBoss = countAcidInArea(araxxorLoc, 2); // 5x5 area (±2 tiles)

        if (acidNearBoss >= ACID_LURE_THRESHOLD) {
            // Too much acid — lure Araxxor to a cleaner spot
            WorldPoint cleanSpot = findCleanSpot(playerLoc);
            if (cleanSpot != null && playerLoc.distanceTo(cleanSpot) > 1) {
                status = "Luring Araxxor to clean area (" + acidNearBoss + " acid tiles)";
                Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), cleanSpot));
                if (tickSleep()) return; // dodge takes priority
                return; // let main loop re-evaluate; Araxxor will follow us
            }
        }

        // ── Ensure we're dealing damage — attack FIRST, then reposition ──
        // This prevents the dodge loop: dodgeCleave → handleEnrageFight → walk under
        // (interrupted by next cleave) → dodgeCleave → ... never attacking.
        // By queuing attack before the walk, damage is dealt even if we're interrupted.
        if (!player.isInteracting()) {
            Rs2Npc.interact(araxxor, "attack");
        }

        // ── Step under Araxxor for cleave self-damage ──
        // SKIP stepping under if we recently dodged — wait for the cleave to fully
        // resolve before walking back in. Just attack from current position (the game
        // will auto-path us to melee range, usually 1 tile adjacent — NOT on top).
        if (recentlyDodged) {
            status = "Enraged — dodge cooldown, attacking from range";
            tickSleep();
            Rs2Player.eatAt(config.eatAtHpPercent());
            drinkPrayerPotion();
            return;
        }

        int dist = playerLoc.distanceTo(araxxorLoc);
        if (dist > 1) {
            // Only reposition if we're more than 1 tile away (melee range is fine)
            if (isOnAcidPool(araxxorLoc)) {
                WorldPoint[] adjacent = {
                        new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() - 1, araxxorLoc.getPlane()),
                        new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() + 1, araxxorLoc.getPlane()),
                        new WorldPoint(araxxorLoc.getX() - 1, araxxorLoc.getY(), araxxorLoc.getPlane()),
                        new WorldPoint(araxxorLoc.getX() + 1, araxxorLoc.getY(), araxxorLoc.getPlane()),
                };
                WorldPoint best = pickBestTile(adjacent);
                Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
            } else {
                Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), araxxorLoc));
            }
            if (tickSleep()) return; // dodge takes priority, but attack is already queued
        }

        tickSleep();

        // Eat if needed during enrage
        Rs2Player.eatAt(config.eatAtHpPercent());
        drinkPrayerPotion();
    }

    /**
     * Count acid pool tiles in a square area centered on the given point.
     * @param center the center tile
     * @param radius half-width of the square (e.g. 2 = 5x5 area)
     * @return number of acid tiles in the area
     */
    private int countAcidInArea(WorldPoint center, int radius) {
        int count = 0;
        for (WorldPoint pool : acidPools) {
            if (Math.abs(pool.getX() - center.getX()) <= radius
                    && Math.abs(pool.getY() - center.getY()) <= radius
                    && pool.getPlane() == center.getPlane()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Find the cleanest reachable spot in the arena to lure Araxxor to.
     * Scans candidate tiles in a grid around the player, scoring by acid density
     * and distance to arena center. Returns the best tile, or null if nowhere is better.
     */
    private WorldPoint findCleanSpot(WorldPoint playerLoc) {
        WorldPoint bestSpot = null;
        int bestScore = Integer.MAX_VALUE;

        // Scan in a grid around the player (±5 tiles, step 2 for performance)
        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dy = -5; dy <= 5; dy += 2) {
                WorldPoint candidate = new WorldPoint(
                        playerLoc.getX() + dx, playerLoc.getY() + dy, playerLoc.getPlane());

                // Skip tiles that are themselves acid or too close to walls
                if (isOnAcidPool(candidate)) continue;
                if (isTooCloseToWall(candidate)) continue;

                int acidCount = countAcidInArea(candidate, 2); // 5x5 around candidate
                int centerDist = arenaCenter != null ? candidate.distanceTo(arenaCenter) : 0;
                int pathAcid = countAcidOnPath(playerLoc, candidate);

                // Score: heavily weight acid density, then path safety, then center proximity
                int score = acidCount * 100 + pathAcid * 200 + centerDist * 5;

                if (score < bestScore) {
                    bestScore = score;
                    bestSpot = candidate;
                }
            }
        }

        return bestSpot;
    }

    /**
     * Dodge the cleave attack during enrage.
     * The cleave targets a 1x3 area where the player was standing, oriented
     * PERPENDICULAR to the Araxxor→Player direction.
     * <p>
     * If a pre-computed dodge was already issued by the event handler (same-tick reaction),
     * we just spam-click it for reliability and counter-attack. Otherwise we compute and
     * walk as a fallback.
     * <p>
     * To avoid the cleave, we move PARALLEL to the attack direction (toward or
     * away from Araxxor along its attack line). This guarantees we leave the
     * 3-tile perpendicular strip immediately without crossing through it.
     * <p>
     * Candidates are scored via pickBestTile() which penalizes cleave tiles
     * on both the destination and the path, preventing path-through-damage.
     */
    private void dodgeCleave(Player player) {
        state = AraxxorState.ENRAGED_DODGE_CLEAVE;
        WorldPoint loc = player.getWorldLocation();

        WorldPoint dodgeDest;
        if (precomputedCleaveDodge != null) {
            // Pre-computed dodge already issued on the detection tick — use it
            dodgeDest = precomputedCleaveDodge;
            log("Cleave dodge: using pre-computed destination → " + dodgeDest);
        } else {
            // Fallback: compute now (slightly delayed but still functional)
            dodgeDest = computeCleaveDodgeDestination(loc);
            log("Cleave dodge: computed fallback destination → " + dodgeDest);
        }

        // Spam-click the dodge destination for reliable movement (3 clicks, 50ms apart)
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), dodgeDest));
        sleep(50, 80);
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), dodgeDest));
        sleep(50, 80);
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), dodgeDest));

        // Record dodge tick — prevents handleEnrageFight from stepping back under too soon
        lastCleaveDodgeTick = Microbot.getClient().getTickCount();

        // Wait a FULL game tick (600ms) for the dodge movement to register and the
        // cleave damage to resolve. Without this, the main loop can issue a step-under
        // walk that OVERRIDES the dodge walk within the same game tick.
        sleep(600, 700);

        // Counter-attack from current (safe) position — the game will auto-path to melee range
        Rs2NpcModel bossTarget = findNearestNpc(ARAXXOR_ID);
        if (bossTarget != null) {
            Rs2Npc.interact(bossTarget, "attack");
        }
    }

    /**
     * Pre-compute cleave dodge and initiate walk IMMEDIATELY.
     * Called from event handlers (onAraxxorAnimation, onOverheadTextChanged) to
     * react on the SAME game tick the cleave is detected, bypassing the main loop delay.
     * <p>
     * This mirrors the acid cannon's precomputeAndDodgeAcidCannon() pattern.
     */
    public void precomputeAndDodgeCleave() {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) return;

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint dodge = computeCleaveDodgeDestination(playerLoc);
        precomputedCleaveDodge = dodge;
        cleaveDetectedTick = Microbot.getClient().getTickCount();

        // Issue walk IMMEDIATELY — queues on the current game tick
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), dodge));
        log("Pre-computed cleave dodge → " + dodge + " (immediate walk on tick " + cleaveDetectedTick + ")");
    }

    /**
     * Compute the best dodge destination for a cleave attack.
     * Pure computation: generates candidates parallel to the attack direction
     * and scores them via pickBestTile().
     *
     * @param playerLoc player position at time of cleave detection
     * @return the best dodge tile
     */
    private WorldPoint computeCleaveDodgeDestination(WorldPoint playerLoc) {
        // Determine dodge distance based on diagonal/corner position
        Rs2NpcModel araxxor = findNearestNpc(ARAXXOR_ID);
        boolean onCorner = false;
        if (araxxor != null) {
            WorldPoint bossLoc = araxxor.getWorldLocation();
            int relX = playerLoc.getX() - bossLoc.getX();
            int relY = playerLoc.getY() - bossLoc.getY();
            onCorner = (relX != 0 && relY != 0);
        }

        int dodgeDist = onCorner ? 3 : 2;

        // Use stored attack direction if available; otherwise fall back to boss-relative
        int atkX = cleaveAttackDirX;
        int atkY = cleaveAttackDirY;
        if (atkX == 0 && atkY == 0) {
            // Try last known direction (from when we were NOT on boss tile)
            atkX = lastKnownAtkDirX;
            atkY = lastKnownAtkDirY;
        }
        if (atkX == 0 && atkY == 0 && araxxor != null) {
            WorldPoint bossLoc = araxxor.getWorldLocation();
            atkX = Integer.signum(playerLoc.getX() - bossLoc.getX());
            atkY = Integer.signum(playerLoc.getY() - bossLoc.getY());
        }

        java.util.List<WorldPoint> candidates = new java.util.ArrayList<>();

        if (atkX != 0 || atkY != 0) {
            // PRIMARY: move parallel to attack direction (away from OR toward boss)
            candidates.add(new WorldPoint(playerLoc.getX() + atkX * dodgeDist, playerLoc.getY() + atkY * dodgeDist, playerLoc.getPlane()));
            candidates.add(new WorldPoint(playerLoc.getX() - atkX * dodgeDist, playerLoc.getY() - atkY * dodgeDist, playerLoc.getPlane()));
            // Slightly shorter parallel fallbacks
            candidates.add(new WorldPoint(playerLoc.getX() + atkX * 2, playerLoc.getY() + atkY * 2, playerLoc.getPlane()));
            candidates.add(new WorldPoint(playerLoc.getX() - atkX * 2, playerLoc.getY() - atkY * 2, playerLoc.getPlane()));
        }

        // SECONDARY: diagonal escapes
        candidates.add(new WorldPoint(playerLoc.getX() + 2, playerLoc.getY() + 2, playerLoc.getPlane()));
        candidates.add(new WorldPoint(playerLoc.getX() - 2, playerLoc.getY() - 2, playerLoc.getPlane()));
        candidates.add(new WorldPoint(playerLoc.getX() + 2, playerLoc.getY() - 2, playerLoc.getPlane()));
        candidates.add(new WorldPoint(playerLoc.getX() - 2, playerLoc.getY() + 2, playerLoc.getPlane()));

        // FALLBACK: cardinal directions
        candidates.add(new WorldPoint(playerLoc.getX() + dodgeDist, playerLoc.getY(), playerLoc.getPlane()));
        candidates.add(new WorldPoint(playerLoc.getX() - dodgeDist, playerLoc.getY(), playerLoc.getPlane()));
        candidates.add(new WorldPoint(playerLoc.getX(), playerLoc.getY() + dodgeDist, playerLoc.getPlane()));
        candidates.add(new WorldPoint(playerLoc.getX(), playerLoc.getY() - dodgeDist, playerLoc.getPlane()));

        return pickBestTile(candidates.toArray(new WorldPoint[0]));
    }

    // ── Acid Special Attack Handling ────────────────────

    /**
     * Compute the best dodge destination for an acid cannon blast.
     * Pure computation — no side effects, no walking, no sleeping.
     * <p>
     * Determines the perpendicular escape direction using 2D cross product to
     * pick the side the player is already on, preventing path-through-blast routing.
     *
     * @param playerLoc  player position at time of detection
     * @param sourceTile Araxxor's position when it fired (blast origin)
     * @return the best dodge destination tile
     */
    private WorldPoint computeAcidCannonDodge(WorldPoint playerLoc, WorldPoint sourceTile) {
        // Direction vector from boss to player (the blast line)
        int blastDx = 0;
        int blastDy = 0;
        if (sourceTile != null) {
            blastDx = playerLoc.getX() - sourceTile.getX();
            blastDy = playerLoc.getY() - sourceTile.getY();
        }

        // Normalize blast direction to unit-ish vector
        int normBx = Integer.signum(blastDx);
        int normBy = Integer.signum(blastDy);

        // Compute TRUE perpendicular: rotate (normBx, normBy) by 90°
        int perpX1 = normBy;
        int perpY1 = -normBx;
        int perpX2 = -normBy;
        int perpY2 = normBx;

        // If blast direction is zero (boss on our tile or unknown), default to east/west
        if (perpX1 == 0 && perpY1 == 0) {
            perpX1 = 1;  perpY1 = 0;
            perpX2 = -1; perpY2 = 0;
        }

        // Determine which side of the blast line the player is on via 2D cross product
        int safePerpX;
        int safePerpY;
        if (sourceTile != null) {
            int toPlayerX = playerLoc.getX() - sourceTile.getX();
            int toPlayerY = playerLoc.getY() - sourceTile.getY();
            int cross = normBx * toPlayerY - normBy * toPlayerX;

            if (cross > 0) {
                safePerpX = perpX1;
                safePerpY = perpY1;
            } else if (cross < 0) {
                safePerpX = perpX2;
                safePerpY = perpY2;
            } else {
                // Exactly on the blast line — pick whichever perp side is closer to center
                if (arenaCenter != null) {
                    WorldPoint side1 = new WorldPoint(playerLoc.getX() + perpX1, playerLoc.getY() + perpY1, playerLoc.getPlane());
                    WorldPoint side2 = new WorldPoint(playerLoc.getX() + perpX2, playerLoc.getY() + perpY2, playerLoc.getPlane());
                    safePerpX = side1.distanceTo(arenaCenter) <= side2.distanceTo(arenaCenter) ? perpX1 : perpX2;
                    safePerpY = side1.distanceTo(arenaCenter) <= side2.distanceTo(arenaCenter) ? perpY1 : perpY2;
                } else {
                    safePerpX = perpX1;
                    safePerpY = perpY1;
                }
            }
        } else {
            safePerpX = perpX1;
            safePerpY = perpY1;
        }

        // Only offer candidates on the SAFE side (away from blast)
        WorldPoint[] safeCandidates = {
                new WorldPoint(playerLoc.getX() + safePerpX * 4, playerLoc.getY() + safePerpY * 4, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() + safePerpX * 3, playerLoc.getY() + safePerpY * 3, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() + safePerpX * 2, playerLoc.getY() + safePerpY * 2, playerLoc.getPlane()),
        };

        // Cardinal fallbacks — only those on the safe side of the blast line
        java.util.List<WorldPoint> fallbacks = new java.util.ArrayList<>();
        int[][] cardinals = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] c : cardinals) {
            int dotSafe = c[0] * safePerpX + c[1] * safePerpY;
            int dotBlast = c[0] * normBx + c[1] * normBy;
            if (dotSafe > 0 && Math.abs(dotBlast) <= 1) {
                fallbacks.add(new WorldPoint(
                        playerLoc.getX() + c[0], playerLoc.getY() + c[1], playerLoc.getPlane()));
            }
        }

        // Merge: safe perpendicular candidates first, then safe cardinals
        WorldPoint[] allCandidates = new WorldPoint[safeCandidates.length + fallbacks.size()];
        System.arraycopy(safeCandidates, 0, allCandidates, 0, safeCandidates.length);
        for (int i = 0; i < fallbacks.size(); i++) {
            allCandidates[safeCandidates.length + i] = fallbacks.get(i);
        }

        if (allCandidates.length == 0) {
            allCandidates = new WorldPoint[]{
                    new WorldPoint(playerLoc.getX() + safePerpX * 3, playerLoc.getY() + safePerpY * 3, playerLoc.getPlane()),
            };
        }

        return pickBestTile(allCandidates);
    }

    /**
     * Pre-compute acid cannon dodge and initiate walk immediately.
     * Called from event handlers (onAraxxorAnimation, onAcidCannonDetected) to
     * react on the SAME game tick the cannon is detected, bypassing the main loop delay.
     */
    private void precomputeAndDodgeAcidCannon() {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null || acidCannonSourceTile == null) return;

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint dodge = computeAcidCannonDodge(playerLoc, acidCannonSourceTile);
        precomputedAcidDodge = dodge;

        // Initiate walk IMMEDIATELY — this queues on the current game tick
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), dodge));
        log("Pre-computed acid dodge → " + dodge + " (immediate walk issued)");
    }

    /**
     * Fallback acid cannon dodge used by the main loop if pre-computation missed.
     * Uses the same computation but runs on the script thread.
     */
    private void dodgeAcidCannon(Player player) {
        state = AraxxorState.DODGING_ACID_BALL;
        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint best = computeAcidCannonDodge(playerLoc, acidCannonSourceTile);

        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
        tickSleep(); // interruptible — can react to cleave while dodging
        log("Dodged acid cannon (fallback) → " + best);
    }

    /**
     * Check for and dodge an incoming acid cannon attack.
     * Called from minion handlers so the player dodges even while dealing with adds.
     *
     * @return true if an acid cannon was dodged (caller should re-evaluate state)
     */
    private boolean checkAndDodgeAcidCannon(Player player) {
        if (acidCannonIncoming) {
            status = "Dodging acid cannon (during adds)!";
            dodgeAcidCannon(player);
            acidCannonIncoming = false;
            return true;
        }
        return false;
    }

    /**
     * Handle acid drip: player is dripping venom for 6 ticks and must keep moving.
     * <p>
     * Optimal technique (from wiki): stand under boss right after the attack lands,
     * move 1 tile off, attack the boss, then repeat. This avoids all acid pools
     * while maintaining DPS uptime and staying in melee range.
     * <p>
     * We alternate between two phases each loop tick:
     *   Phase 0: Walk onto Araxxor's tile (step under)
     *   Phase 1: Move 1 tile adjacent, then attack
     */
    private void handleAcidDrip(Player player) {
        Rs2NpcModel araxxor = findNearestNpc(ARAXXOR_ID);
        if (araxxor == null) {
            acidDripActive = false;
            return;
        }

        WorldPoint araxxorLoc = araxxor.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();

        if (acidDripPhase == 0) {
            // Phase 0: step under boss — puddle drops at our previous location
            if (playerLoc.distanceTo(araxxorLoc) > 0) {
                Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), araxxorLoc));
            }
            tickSleep();
            acidDripPhase = 1;
        } else {
            // Phase 1: step 1 tile off boss + attack in same tick (tick manipulation)
            // Both walk and attack queue for the next server tick
            WorldPoint[] candidates = {
                    new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() - 1, araxxorLoc.getPlane()),  // south (preferred)
                    new WorldPoint(araxxorLoc.getX() + 1, araxxorLoc.getY(), araxxorLoc.getPlane()),  // east
                    new WorldPoint(araxxorLoc.getX() - 1, araxxorLoc.getY(), araxxorLoc.getPlane()),  // west
                    new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() + 1, araxxorLoc.getPlane()),  // north
            };

            WorldPoint stepOff = pickBestTile(candidates);
            Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), stepOff));
            Rs2Npc.interact(araxxor, "attack"); // Walk + attack queue for same tick
            tickSleep();
            acidDripPhase = 0;
        }
    }

    // ── Defence Drain Special Attack ────────────────────

    /**
     * Perform special attack with Elder Maul / DWH to drain Araxxor's Defence.
     * Araxxor has 135 Defence, drainable up to 45 levels.
     * Elder maul drains 45 in one hit; DWH drains 40.
     * Supports dual-spec: performs up to {@code config.specCount()} spec hits per fight.
     * After all spec hits land, optionally drinks a surge potion before switching back.
     */
    private boolean performSpecialAttack(Rs2NpcModel araxxor) {
        int specEnergy = Rs2Combat.getSpecEnergy() / 10; // 0–100 (Rs2Combat returns 0–1000)
        String specWeapon = config.specWeapon().toLowerCase();
        int specPerHit = specWeapon.contains("dragon warhammer") ? 50 : 50; // both 50%
        int hitsRemaining = config.specCount() - specHitsCompleted;

        // Need enough energy for at least one spec hit
        if (specEnergy < specPerHit || hitsRemaining <= 0) return false;

        // Need the weapon available
        boolean specWeaponEquipped = Rs2Equipment.isWearing(config.specWeapon());
        if (!specWeaponEquipped && !Rs2Inventory.hasItem(config.specWeapon())) return false;

        state = AraxxorState.SPEC_ATTACK;

        // Equip spec weapon if not already worn
        if (!specWeaponEquipped) {
            status = "Equipping " + config.specWeapon();
            Rs2Inventory.wield(config.specWeapon());
            if (tickSleep()) { switchToMainWeapon(); return true; }
            // Wait for the weapon to actually equip
            sleepUntil(() -> Rs2Equipment.isWearing(config.specWeapon()), 1200);
        }

        // Perform spec hits (one per call, loop returns to main loop so dodges are checked)
        status = "Spec " + (specHitsCompleted + 1) + "/" + config.specCount() + ": " + config.specWeapon();

        // Switch to combat tab so the spec orb widget is visible, then toggle spec on
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 1200);
        Rs2Combat.setSpecState(true, specPerHit * 10); // raw 0–1000 scale
        if (tickSleep()) { switchToMainWeapon(); return true; }

        // Attack araxxor with spec enabled
        Rs2Npc.interact(araxxor, "attack");
        tickSleep();

        // Wait for the hit to register (player animation plays)
        sleepUntil(Rs2Player::isAnimating, 1800);
        tickSleep(); // Let the hit land

        specHitsCompleted++;
        log("Spec hit " + specHitsCompleted + "/" + config.specCount() + " landed");

        // If we still have hits remaining AND enough energy, stay on spec weapon for next loop iteration
        int energyAfter = Rs2Combat.getSpecEnergy() / 10;
        if (specHitsCompleted < config.specCount() && energyAfter >= specPerHit) {
            // Don't switch back yet — main loop will call us again next iteration
            return true;
        }

        // All spec hits done (or out of energy).
        // Surge potion hook: drink after all specs land, before switching back
        if (config.useSurgePotion() && specHitsCompleted >= config.specCount()) {
            drinkSurgePotion();
        }

        // Switch back to main weapon
        switchToMainWeapon();
        return true;
    }

    /**
     * Drink a surge potion to restore special attack energy.
     * Placeholder — will be expanded with specific potion variants.
     */
    private void drinkSurgePotion() {
        status = "Drinking surge potion";
        // Try common surge potion variants
        if (Rs2Inventory.interact("Super restore", "drink")
                || Rs2Inventory.interact("Surge potion", "drink")) {
            tickSleep();
            log("Surge potion consumed");
        } else {
            log("No surge potion found in inventory");
        }
    }

    // ── Potion Management ───────────────────────────────

    private void drinkPotions() {
        CombatPotionType potionType = config.combatPotionType();
        if (potionType != CombatPotionType.NONE) {
            boolean hasCombatBoost = Rs2Player.getBoostedSkillLevel(Skill.STRENGTH)
                    > Rs2Player.getRealSkillLevel(Skill.STRENGTH);
            if (!hasCombatBoost) {
                Rs2Inventory.interact(potionType.getInventoryName(), "drink");
                tickSleep();
            }
        }

        if (config.useExtendedAntiVenom() && !Rs2Player.hasAntiVenomActive()) {
            if (Rs2Inventory.interact("extended anti-venom+", "drink")
                    || Rs2Inventory.interact("anti-venom+", "drink")
                    || Rs2Inventory.interact("anti-venom", "drink")) {
                tickSleep();
            }
        }
    }

    private void drinkPrayerPotion() {
        int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);
        int thresholdPoints = Math.max(1, (maxPrayer * config.drinkPrayerAtPercent()) / 100);
        Rs2Player.drinkPrayerPotionAt(thresholdPoints);
    }

    // ── Prayer Management ───────────────────────────────

    private void managePrayers() {
        if (config.protectFromMelee()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        }
        if (config.usePiety()) {
            if (Rs2Player.getRealSkillLevel(Skill.PRAYER) >= 70
                    && Rs2Player.getRealSkillLevel(Skill.DEFENCE) >= 70) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
            }
        }
    }

    private void togglePrayers(boolean on) {
        if (Rs2Prayer.isOutOfPrayer()) return;
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, on);
        if (config.usePiety()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, on);
        }
    }

    // ── Thrall Summoning ────────────────────────────────

    /**
     * Summon a thrall if one is not currently active.
     * Uses the best available thrall of the configured type (Greater > Superior > Lesser).
     * Thralls last ~100 seconds and auto-attack the player's target.
     */
    private void summonThrall() {
        // Don't re-summon if one is already active or on cooldown
        if (Rs2Thrall.isActive()) return;

        ThrallType type = config.thrallType();
        Rs2Thrall bestThrall = Rs2Thrall.getBestThrall(type);
        if (bestThrall != null) {
            status = "Summoning thrall";
            Rs2Thrall.cast(bestThrall);
            tickSleep();
            log("Summoned " + bestThrall.getName());
        }
    }

    // ── Cleave AoE Tracking ──────────────────────────────

    /**
     * Compute and store the 3 danger tiles for the current cleave attack.
     * Called when cleaveIncoming is set (from animation or overhead text detection).
     * <p>
     * The cleave is a 1x3 area centered on the player's position at the moment
     * the attack fires, oriented PERPENDICULAR to the Araxxor→Player direction.
     * <p>
     * Example: if Araxxor is north of player, the attack direction is N→S,
     * perpendicular is E-W, so the 3 tiles form a horizontal strip:
     *   (playerX-1, playerY), (playerX, playerY), (playerX+1, playerY)
     */
    public void computeCleaveTiles() {
        Player player = Microbot.getClient().getLocalPlayer();
        Rs2NpcModel araxxor = findNearestNpc(ARAXXOR_ID);
        if (player == null || araxxor == null) return;

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint bossLoc = araxxor.getWorldLocation();

        // Attack direction: Araxxor → Player (normalized to signs)
        int atkDirX = Integer.signum(playerLoc.getX() - bossLoc.getX());
        int atkDirY = Integer.signum(playerLoc.getY() - bossLoc.getY());

        // When player is on the same tile as the boss (step-under), atkDir is (0,0).
        // Fall back to the last known direction from when we were NOT on the boss tile.
        if (atkDirX == 0 && atkDirY == 0) {
            if (lastKnownAtkDirX != 0 || lastKnownAtkDirY != 0) {
                atkDirX = lastKnownAtkDirX;
                atkDirY = lastKnownAtkDirY;
                log("Using last known atk dir for cleave: " + atkDirX + "," + atkDirY);
            }
        } else {
            // Store this valid direction for future fallback
            lastKnownAtkDirX = atkDirX;
            lastKnownAtkDirY = atkDirY;
        }

        // Store attack direction for dodge candidate generation
        cleaveAttackDirX = atkDirX;
        cleaveAttackDirY = atkDirY;

        // Perpendicular to attack direction (the cleave strip runs along this axis)
        int perpX = -atkDirY;
        int perpY = atkDirX;

        // If attack direction is zero (on same tile, no last-known), default perpendicular to E-W
        if (perpX == 0 && perpY == 0) {
            perpX = 1;
            perpY = 0;
        }

        // The 3 cleave tiles: center + 1 tile each side along perpendicular
        Set<WorldPoint> tiles = new HashSet<>();
        tiles.add(playerLoc);
        tiles.add(new WorldPoint(playerLoc.getX() + perpX, playerLoc.getY() + perpY, playerLoc.getPlane()));
        tiles.add(new WorldPoint(playerLoc.getX() - perpX, playerLoc.getY() - perpY, playerLoc.getPlane()));
        cleaveDangerTiles = tiles;
        log("Cleave tiles computed: " + tiles + " (atk dir: " + atkDirX + "," + atkDirY + ")");
    }

    /**
     * Count how many cleave danger tiles lie on the straight-line path from start to end.
     * Same Bresenham logic as countAcidOnPath() but checks cleaveDangerTiles.
     */
    private int countCleaveTilesOnPath(WorldPoint start, WorldPoint end) {
        Set<WorldPoint> danger = cleaveDangerTiles;
        if (danger.isEmpty()) return 0;

        int count = 0;
        int x0 = start.getX(), y0 = start.getY();
        int x1 = end.getX(), y1 = end.getY();
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (danger.contains(new WorldPoint(x0, y0, start.getPlane()))) {
                count++;
            }
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
        return count;
    }

    /**
     * Clear cleave tracking state. Called after a cleave is dodged or fight resets.
     */
    private void clearCleaveTiles() {
        cleaveDangerTiles = new HashSet<>();
        cleaveAttackDirX = 0;
        cleaveAttackDirY = 0;
        precomputedCleaveDodge = null;
    }

    // ── Movement Helpers ────────────────────────────────

    /**
     * Score a candidate tile. Lower score = better.
     * Prefers tiles that are: not on acid, not on cleave, path doesn't cross
     * acid or cleave tiles, closer to arena center, away from walls.
     */
    private int scoreTile(WorldPoint candidate) {
        int score = 0;
        // Massive penalty if the destination itself is acid
        if (isOnAcidPool(candidate)) {
            score += 1000;
        }
        // Massive penalty if the destination is a cleave danger tile
        if (!cleaveDangerTiles.isEmpty() && cleaveDangerTiles.contains(candidate)) {
            score += 1000;
        }
        // Large penalty if the straight-line path to this tile crosses any acid pool or cleave tile
        Player player = Microbot.getClient().getLocalPlayer();
        if (player != null) {
            WorldPoint playerLoc = player.getWorldLocation();
            int acidOnPath = countAcidOnPath(playerLoc, candidate);
            score += acidOnPath * 500;
            int cleaveOnPath = countCleaveTilesOnPath(playerLoc, candidate);
            score += cleaveOnPath * 500;
        }
        if (arenaCenter != null) {
            score += candidate.distanceTo(arenaCenter); // prefer tiles closer to center
            // Heavy penalty for being within WALL_BUFFER of estimated arena edge
            if (isTooCloseToWall(candidate)) {
                score += 500;
            }
        }
        return score;
    }

    /**
     * Count how many acid pool tiles lie on the straight-line path from start to end.
     * Uses Bresenham-style line walking to check every tile along the path.
     */
    private int countAcidOnPath(WorldPoint start, WorldPoint end) {
        int count = 0;
        int x0 = start.getX();
        int y0 = start.getY();
        int x1 = end.getX();
        int y1 = end.getY();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            WorldPoint check = new WorldPoint(x0, y0, start.getPlane());
            if (acidPools.contains(check)) {
                count++;
            }
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
        return count;
    }

    /**
     * Check if the straight-line path from start to end crosses any acid pool.
     */
    private boolean pathCrossesAcid(WorldPoint start, WorldPoint end) {
        return countAcidOnPath(start, end) > 0;
    }

    /**
     * Pick the best tile from a list of candidates, preferring non-acid tiles
     * with a clean path, closer to arena center. Always returns a tile (never null).
     */
    private WorldPoint pickBestTile(WorldPoint[] candidates) {
        return Arrays.stream(candidates)
                .min(Comparator.comparingInt(this::scoreTile))
                .orElse(candidates[0]);
    }

    private boolean isOnAcidPool(WorldPoint tile) {
        return acidPools.contains(tile);
    }

    /**
     * Check whether a tile is within WALL_BUFFER (2) tiles of the estimated arena edge.
     * The arena is modelled as a circle of ARENA_RADIUS around arenaCenter.
     * A tile is "too close to wall" if its distance from center is > (ARENA_RADIUS - WALL_BUFFER).
     */
    private boolean isTooCloseToWall(WorldPoint tile) {
        if (arenaCenter == null) return false;
        int dist = tile.distanceTo(arenaCenter);
        return dist > (ARENA_RADIUS - WALL_BUFFER);
    }

    /**
     * Walk toward the arena center, dodging acid pools on the way.
     * Moves 2 tiles toward center per call to avoid overshooting.
     */
    private void returnToCenter(Player player) {
        if (arenaCenter == null) return;
        WorldPoint playerLoc = player.getWorldLocation();

        int dx = arenaCenter.getX() - playerLoc.getX();
        int dy = arenaCenter.getY() - playerLoc.getY();
        int normX = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
        int normY = dy == 0 ? 0 : (dy > 0 ? 1 : -1);

        // Generate candidates: direct toward center + slight perpendicular offsets
        WorldPoint[] candidates = {
                new WorldPoint(playerLoc.getX() + normX * 2, playerLoc.getY() + normY * 2, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() + normX * 2 + normY, playerLoc.getY() + normY * 2 - normX, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() + normX * 2 - normY, playerLoc.getY() + normY * 2 + normX, playerLoc.getPlane()),
                // Straight to center if close
                arenaCenter,
        };

        WorldPoint best = pickBestTile(candidates);
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
        tickSleep();
    }

    private void moveOffAcid(Player player) {
        WorldPoint loc = player.getWorldLocation();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        WorldPoint[] candidates = new WorldPoint[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            candidates[i] = new WorldPoint(loc.getX() + offsets[i][0], loc.getY() + offsets[i][1], loc.getPlane());
        }
        WorldPoint best = pickBestTile(candidates);
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
        tickSleep();
    }

    private void moveAwayFrom(WorldPoint from, WorldPoint danger, int distance) {
        int dx = from.getX() - danger.getX();
        int dy = from.getY() - danger.getY();
        int normX = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
        int normY = dy == 0 ? 0 : (dy > 0 ? 1 : -1);

        // Generate candidates: primary direction + perpendicular alternatives
        WorldPoint[] candidates = {
                new WorldPoint(from.getX() + normX * distance, from.getY() + normY * distance, from.getPlane()),
                new WorldPoint(from.getX() + normY * distance, from.getY() - normX * distance, from.getPlane()),
                new WorldPoint(from.getX() - normY * distance, from.getY() + normX * distance, from.getPlane()),
        };
        WorldPoint best = pickBestTile(candidates);
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
    }

    // ── Weapon Switching ────────────────────────────────

    private void switchToMainWeapon() {
        String mainWeapon = config.mainWeapon();
        String shield = config.mainShield();
        // Queue both equips — weapon and shield can process in the same game tick
        if (!mainWeapon.isEmpty()) {
            Rs2Inventory.wield(mainWeapon);
        }
        if (!shield.isEmpty()) {
            Rs2Inventory.wield(shield);
        }
        if (!mainWeapon.isEmpty() || !shield.isEmpty()) {
            tickSleep();
        }
    }

    // ── Post-fight Handling ─────────────────────────────

    private void handleNoBoss() {
        // Check if boss corpse exists first — must interact with it before items appear on ground.
        // onAraxxorDeath() sets state=LOOTING immediately, but ground items only spawn AFTER
        // the corpse is harvested/destroyed. So corpse interaction must come before any loot attempt.
        Rs2NpcModel deadAraxxor = findNearestNpc(ARAXXOR_DEAD_ID);
        if (deadAraxxor != null) {
            state = AraxxorState.LOOTING;
            status = config.harvestCorpse() ? "Harvesting corpse..." : "Destroying corpse...";

            String action = config.harvestCorpse() ? "Harvest" : "Destroy";
            Rs2Npc.interact(deadAraxxor, action);
            sleep(1200);
            return;
        }

        // Corpse is gone — if in LOOTING state, pick up the ground items now
        if (state == AraxxorState.LOOTING) {
            status = "Looting...";
            lootItems();
            return;
        }

        // If we've killed enough, teleport out
        if (config.maxKillsPerTrip() > 0 && killCount >= config.maxKillsPerTrip()) {
            status = "Trip complete — teleporting";
            emergencyTeleport();
            return;
        }

        // Check supplies before next fight
        boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
        boolean hasPrayerPots = Rs2Inventory.hasItem(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new));

        if (!hasFood || !hasPrayerPots) {
            status = "Low supplies — teleporting";
            emergencyTeleport();
            return;
        }

        // Reset fight state for new encounter — clears enraged/acid/cleave flags
        // that may have persisted from the previous kill
        resetFightState();

        // Drink potions and prepare for next fight
        status = "Waiting for Araxxor to spawn...";
        state = AraxxorState.IDLE;
        drinkPotions();
        managePrayers();
    }

    /**
     * Reset all per-fight state flags. Called when transitioning to idle between kills
     * to ensure stale enraged/acid/cleave flags from a previous fight don't carry over.
     */
    private void resetFightState() {
        enraged = false;
        specHitsCompleted = 0;
        araxxorAttackCount = 0;
        acidDripActive = false;
        acidDripPhase = 0;
        cleaveIncoming = false;
        clearCleaveTiles();
        acidCannonIncoming = false;
        acidCannonSourceTile = null;
        precomputedAcidDodge = null;
        precomputedCleaveDodge = null;
        cleaveDetectedTick = 0;
        lastCleaveDodgeTick = 0;
        lastKnownAtkDirX = 0;
        lastKnownAtkDirY = 0;
        arenaCenter = null;
        acidPools.clear();
    }

    private void lootItems() {
        togglePrayers(false);

        LootingParameters params = new LootingParameters(
                config.lootPriceThreshold(),
                Integer.MAX_VALUE,
                20,
                1,
                0,
                false,
                false);
        params.setEatFoodForSpace(true);

        Rs2LootEngine.with(params)
                .withLootAction(Rs2GroundItem::coreLoot)
                .addByValue()
                .addCustom("araxxor-uniques",
                        gi -> gi.getName() != null && ARAXXOR_UNIQUE_DROPS.contains(gi.getName().toLowerCase()),
                        null)
                .addCustom("araxxor-supplies",
                        gi -> gi.getName() != null && ARAXXOR_SUPPLY_DROPS.contains(gi.getName().toLowerCase()),
                        null)
                .loot();

        state = AraxxorState.IDLE;
        status = "Loot complete";
    }

    // ── Utility ─────────────────────────────────────────

    private void emergencyTeleport() {
        state = AraxxorState.TELEPORTING_AWAY;
        togglePrayers(false);

        String teleItem = config.teleportItem();
        if (!teleItem.isEmpty() && Rs2Inventory.hasItem(teleItem)) {
            // Try common teleport actions in order
            if (!Rs2Inventory.interact(teleItem, "break")) {
                if (!Rs2Inventory.interact(teleItem, "rub")) {
                    Rs2Inventory.interact(teleItem, "teleport");
                }
            }
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Microbot.getClient().isInInstancedRegion(), 5000);
        }
    }

    private int getHpPercent() {
        int current = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
        int max = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
        return max > 0 ? (current * 100) / max : 100;
    }

    /**
     * Look up the nearest NPC with the given ID, executed on the client thread.
     * <p>
     * The new {@code Rs2NpcCache.query()} calls {@code worldView.npcs()} at construction
     * time. When invoked from the script (scheduler) thread this is not guaranteed safe,
     * so we marshal the entire query onto the client thread — matching the behaviour of
     * the old {@code Rs2Npc.getNpcs()} which wrapped everything in
     * {@code runOnClientThreadOptional()}.
     */
    private Rs2NpcModel findNearestNpc(int npcId) {
        return Rs2Npc.getNpc(npcId);
    }
}

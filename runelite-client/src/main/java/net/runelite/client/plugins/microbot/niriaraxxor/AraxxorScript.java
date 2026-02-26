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
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
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
    private volatile boolean specUsed = false;

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

    // Arena center — set when we first find Araxxor, used to bias all movement toward center
    private volatile WorldPoint arenaCenter = null;
    // Approximate arena radius (Araxxor lair is roughly 14-16 tiles across)
    private static final int ARENA_RADIUS = 7;
    // Minimum distance from walls we want to maintain
    private static final int WALL_BUFFER = 3;

    private AraxxorConfig config;

    // ── Public API ──────────────────────────────────────

    public boolean run(AraxxorConfig config) {
        this.config = config;
        state = AraxxorState.IDLE;
        killCount = 0;
        enraged = false;
        specUsed = false;
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
            sleep(300);
        }

        // ── 1. HIGHEST PRIORITY: flee ruptura before it reaches us ──
        // Ruptura walks toward the player and explodes once on the same tile for 1 tick.
        // The explosion hits for up to 80 damage at melee range. At 3+ tiles it's only ~7.
        // We flee at distance ≤ 2 (one tile BEFORE it can reach us) to guarantee we're
        // gone before it arrives on our tile and triggers the explosion.
        Rs2NpcModel rupturaUrgent = Rs2Npc.getNpc(RUPTURA_ID);
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
            return;
        }

        // ── 3. Priority: dodge acid cannon (big acid ball) ──
        // Active poll: check if the acid cannon projectile NPC (13676) is alive in case
        // event-based detection (animation / NPC spawn) was missed entirely.
        if (!acidCannonIncoming) {
            Rs2NpcModel cannonNpc = Rs2Npc.getNpc(ACID_CANNON_PROJ_NPC);
            if (cannonNpc != null && !cannonNpc.isDead()) {
                log("Acid cannon detected via active polling!");
                acidCannonIncoming = true;
                Rs2NpcModel boss = Rs2Npc.getNpc(ARAXXOR_ID);
                if (boss != null) {
                    acidCannonSourceTile = boss.getWorldLocation();
                }
            }
        }
        if (acidCannonIncoming) {
            status = "Dodging acid cannon!";
            dodgeAcidCannon(player);
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

        // ── 5b. Wall proximity check — return toward center if too close to walls ──
        if (isTooCloseToWall(player.getWorldLocation())) {
            status = "Too close to wall — returning to center!";
            returnToCenter(player);
            return;
        }

        // ── 5. Find Araxxor ──
        Rs2NpcModel araxxor = Rs2Npc.getNpc(ARAXXOR_ID);

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

        // ── 9. Special attack for Defence drain ──
        if (config.useSpecialAttack() && !specUsed) {
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

        // Prefer south-side positioning to keep consistent dodge directions.
        // Pick the safest adjacent tile — avoids acid on both destination and path.
        WorldPoint araxxorLoc = araxxor.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();

        // Generate 4 cardinal adjacent tiles around Araxxor, prefer south
        WorldPoint[] adjacentTiles = {
                new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() - 1, araxxorLoc.getPlane()),  // south (preferred)
                new WorldPoint(araxxorLoc.getX() - 1, araxxorLoc.getY(), araxxorLoc.getPlane()),  // west
                new WorldPoint(araxxorLoc.getX() + 1, araxxorLoc.getY(), araxxorLoc.getPlane()),  // east
                new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() + 1, araxxorLoc.getPlane()),  // north
        };
        WorldPoint preferredTile = pickBestTile(adjacentTiles);

        // If we're not on a safe tile, not in combat yet — reposition
        if (!player.isInteracting()
                && playerLoc.distanceTo(preferredTile) > 0
                && playerLoc.distanceTo(araxxorLoc) <= 2
                && !pathCrossesAcid(playerLoc, preferredTile)) {
            Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), preferredTile));
            sleep(100);
        }

        // If we're currently standing on acid, move off immediately before attacking
        if (isOnAcidPool(playerLoc)) {
            moveOffAcid(player);
            return;
        }

        if (!Rs2Combat.inCombat() || !player.isInteracting()) {
            Rs2Npc.interact(araxxor, "attack");
            sleep(600);
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
        Rs2NpcModel ruptura = Rs2Npc.getNpc(RUPTURA_ID);
        if (ruptura != null && !ruptura.isDead()) {
            return handleRuptura(player, araxxor, ruptura);
        }

        // ── Mirrorback (white) — reflects damage ──
        Rs2NpcModel mirrorback = Rs2Npc.getNpc(MIRRORBACK_ID);
        if (mirrorback != null && !mirrorback.isDead()) {
            return handleMirrorback(player, mirrorback);
        }

        // ── Acidic (green) — ranged attacks, explodes on death ──
        if (config.killAcidicAraxytes()) {
            Rs2NpcModel acidic = Rs2Npc.getNpc(ACIDIC_ID);
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

                Rs2NpcModel rupt = Rs2Npc.getNpc(RUPTURA_ID);
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
                sleep(300);
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
        sleep(300); // Wait longer to ensure movement completes before the loop re-checks
    }

    /**
     * Handle mirrorback araxyte — redirects 20% of Araxxor damage to itself,
     * recoils 50% of that to player. Must kill with ranged/magic/halberd (no melee recoil).
     * Noxious halberd 1-hits them.
     * <p>
     * Also checks for enrage cleave between actions so we never eat a "Skree!" while
     * weapon-switching or waiting for the attack animation.
     */
    private boolean handleMirrorback(Player player, Rs2NpcModel mirrorback) {
        state = AraxxorState.KILLING_MIRRORBACK;
        status = "Killing mirrorback araxyte!";

        // Dodge cleave before committing to weapon switch
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

        // Ensure we're 1 tile away from mirrorback (not on top of it) before attacking
        WorldPoint mbLoc = mirrorback.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();
        int distToMb = playerLoc.distanceTo(mbLoc);
        if (distToMb == 0 || distToMb > 1) {
            // Walk to an adjacent tile (1 tile away)
            WorldPoint[] adjacentTiles = {
                    new WorldPoint(mbLoc.getX(), mbLoc.getY() - 1, mbLoc.getPlane()),  // south
                    new WorldPoint(mbLoc.getX() + 1, mbLoc.getY(), mbLoc.getPlane()),  // east
                    new WorldPoint(mbLoc.getX() - 1, mbLoc.getY(), mbLoc.getPlane()),  // west
                    new WorldPoint(mbLoc.getX(), mbLoc.getY() + 1, mbLoc.getPlane()),  // north
            };
            WorldPoint best = pickBestTile(adjacentTiles);
            Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
            sleep(300);
        }

        // Switch to araxyte weapon (noxious halberd recommended)
        String araxWeapon = config.araxyteSwitchWeapon();
        if (!araxWeapon.isEmpty()) {
            Rs2Inventory.wield(araxWeapon);
            sleep(300);
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
        sleep(600);

        // Wait until the mirrorback is actually dead before switching back.
        // Check for cleave dodges while we wait so we don't eat a "Skree!".
        sleepUntil(() -> {
            checkAndDodgeCleave(Microbot.getClient().getLocalPlayer());
            Rs2NpcModel mb = Rs2Npc.getNpc(MIRRORBACK_ID);
            return mb == null || mb.isDead();
        }, () -> {
            // Keep re-attacking if we stopped interacting (e.g. after a dodge)
            Rs2NpcModel mb = Rs2Npc.getNpc(MIRRORBACK_ID);
            if (mb != null && !mb.isDead()) {
                Player p = Microbot.getClient().getLocalPlayer();
                if (p != null && !p.isInteracting()) {
                    Rs2Npc.interact(mb, "attack");
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
            Rs2Inventory.wield(araxWeapon);
            sleep(300);
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
        sleep(600);

        // Wait until the acidic is dead before switching weapon or moving away.
        // Dodge cleave/cannon while waiting.
        sleepUntil(() -> {
            Player p = Microbot.getClient().getLocalPlayer();
            if (p != null) {
                checkAndDodgeCleave(p);
                checkAndDodgeAcidCannon(p);
            }
            Rs2NpcModel ac = Rs2Npc.getNpc(ACIDIC_ID);
            return ac == null || ac.isDead();
        }, () -> {
            // Keep re-attacking if we stopped interacting (e.g. after a dodge)
            Rs2NpcModel ac = Rs2Npc.getNpc(ACIDIC_ID);
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
            sleep(300);
        }

        // Now safe to switch back to main weapon
        switchToMainWeapon();
        return true;
    }

    // ── Enrage Phase ────────────────────────────────────

    /**
     * Handle enrage phase using step-under technique.
     * After ≤255 HP: attack speed 6→4 ticks, cleave replaces melee (1x3 area + acid).
     * Step-under makes cleave damage Araxxor itself for 8-12 damage.
     * <p>
     * Technique: move 2 tiles into Araxxor → when "Skree!" dodge → attack → repeat.
     * If under Araxxor, cleave only leaves 1 center acid tile.
     */
    private void handleEnrageFight(Player player, Rs2NpcModel araxxor) {
        state = AraxxorState.ENRAGED_STEP_UNDER;

        WorldPoint araxxorLoc = araxxor.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();
        int dist = playerLoc.distanceTo(araxxorLoc);

        // Step under Araxxor
        if (dist > 0) {
            Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), araxxorLoc));
            sleep(200);
        }

        // Attack from under
        Rs2Npc.interact(araxxor, "attack");
        sleep(600);

        // Eat if needed during enrage
        Rs2Player.eatAt(config.eatAtHpPercent());
        drinkPrayerPotion();
    }

    /**
     * Dodge the cleave attack during enrage.
     * Move 2 tiles cardinally or 1 tile diagonally away when "Skree!" is detected.
     * The cleave targets a 1x3 area where the player was standing.
     */
    private void dodgeCleave(Player player) {
        state = AraxxorState.ENRAGED_DODGE_CLEAVE;
        WorldPoint loc = player.getWorldLocation();

        // Find a tile 2 tiles away that isn't acid, biased toward arena center
        WorldPoint[] candidates = {
                new WorldPoint(loc.getX() + 2, loc.getY(), loc.getPlane()),
                new WorldPoint(loc.getX() - 2, loc.getY(), loc.getPlane()),
                new WorldPoint(loc.getX(), loc.getY() + 2, loc.getPlane()),
                new WorldPoint(loc.getX(), loc.getY() - 2, loc.getPlane()),
                new WorldPoint(loc.getX() + 1, loc.getY() + 1, loc.getPlane()),
                new WorldPoint(loc.getX() - 1, loc.getY() - 1, loc.getPlane()),
                new WorldPoint(loc.getX() + 1, loc.getY() - 1, loc.getPlane()),
                new WorldPoint(loc.getX() - 1, loc.getY() + 1, loc.getPlane()),
        };

        WorldPoint best = pickBestTile(candidates);
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
        sleep(200);
    }

    // ── Acid Special Attack Handling ────────────────────

    /**
     * Dodge the acid cannon (big acid ball) attack.
     * Araxxor fires a large acid projectile in a line toward the player's position.
     * The ball travels where the player was standing and splashes a large area.
     * We move 8 tiles perpendicular to the boss→player line to clear the blast zone.
     * Fallback candidates at 7 and 10 tiles ensure we dodge even around acid/walls.
     */
    private void dodgeAcidCannon(Player player) {
        state = AraxxorState.DODGING_ACID_BALL;
        WorldPoint playerLoc = player.getWorldLocation();

        // Determine sidestep direction — perpendicular to boss→player line
        int dx = 0;
        int dy = 0;
        if (acidCannonSourceTile != null) {
            dx = playerLoc.getX() - acidCannonSourceTile.getX();
            dy = playerLoc.getY() - acidCannonSourceTile.getY();
        }

        // Perpendicular vectors: rotate 90° → (dy, -dx) and (-dy, dx)
        // Normalize to unit direction
        int perpX1 = dy == 0 ? 0 : (dy > 0 ? 1 : -1);
        int perpY1 = dx == 0 ? 0 : (dx > 0 ? -1 : 1);

        // If boss→player is perfectly diagonal or dx/dy are both 0, use fallback
        if (perpX1 == 0 && perpY1 == 0) {
            perpX1 = 1;
            perpY1 = 0;
        }

        // Move 8 tiles perpendicular (7-10 range for safety), with fallbacks
        WorldPoint[] candidates = {
                // Primary: 8 tiles perpendicular both directions
                new WorldPoint(playerLoc.getX() + perpX1 * 8, playerLoc.getY() + perpY1 * 8, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() - perpX1 * 8, playerLoc.getY() - perpY1 * 8, playerLoc.getPlane()),
                // Fallback: 10 tiles if 8 lands on acid/wall
                new WorldPoint(playerLoc.getX() + perpX1 * 10, playerLoc.getY() + perpY1 * 10, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() - perpX1 * 10, playerLoc.getY() - perpY1 * 10, playerLoc.getPlane()),
                // Fallback: 7 tiles if further options are blocked
                new WorldPoint(playerLoc.getX() + perpX1 * 7, playerLoc.getY() + perpY1 * 7, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() - perpX1 * 7, playerLoc.getY() - perpY1 * 7, playerLoc.getPlane()),
        };

        WorldPoint best = pickBestTile(candidates);
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), best));
        sleep(300);
        log("Dodged acid cannon 8+ tiles → " + best);
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
        Rs2NpcModel araxxor = Rs2Npc.getNpc(ARAXXOR_ID);
        if (araxxor == null) {
            acidDripActive = false;
            return;
        }

        WorldPoint araxxorLoc = araxxor.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();

        if (acidDripPhase == 0) {
            // Phase 0: step under the boss
            if (playerLoc.distanceTo(araxxorLoc) > 0) {
                Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), araxxorLoc));
            }
            sleep(300);
            acidDripPhase = 1;
        } else {
            // Phase 1: step 1 tile off boss (staying adjacent = melee range), then attack
            // Pick the best adjacent tile: not on acid, close to arena center
            WorldPoint[] candidates = {
                    new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() - 1, araxxorLoc.getPlane()),  // south (preferred)
                    new WorldPoint(araxxorLoc.getX() + 1, araxxorLoc.getY(), araxxorLoc.getPlane()),  // east
                    new WorldPoint(araxxorLoc.getX() - 1, araxxorLoc.getY(), araxxorLoc.getPlane()),  // west
                    new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() + 1, araxxorLoc.getPlane()),  // north
            };

            WorldPoint stepOff = pickBestTile(candidates);
            Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), stepOff));
            sleep(200);

            // Attack the boss while standing adjacent
            Rs2Npc.interact(araxxor, "attack");
            sleep(200);
            acidDripPhase = 0;
        }
    }

    // ── Defence Drain Special Attack ────────────────────

    /**
     * Perform special attack with Elder Maul / DWH to drain Araxxor's Defence.
     * Araxxor has 135 Defence, drainable up to 45 levels.
     * Elder maul drains 45 in one hit; DWH drains 40.
     */
    private boolean performSpecialAttack(Rs2NpcModel araxxor) {
        int specEnergy = Microbot.getClient().getVarpValue(300) / 10;
        String specWeapon = config.specWeapon().toLowerCase();

        int requiredSpec = specWeapon.contains("elder maul") ? 50 : 50;

        if (specEnergy >= requiredSpec && Rs2Inventory.hasItem(config.specWeapon())) {
            state = AraxxorState.SPEC_ATTACK;
            status = "Spec: " + config.specWeapon();

            Rs2Inventory.wield(config.specWeapon());
            sleep(300);
            Rs2Combat.setSpecState(true, 300);
            sleep(100);
            Rs2Npc.interact(araxxor, "attack");
            sleep(600);

            specUsed = true;

            // Switch back to main weapon
            switchToMainWeapon();
            return true;
        }
        return false;
    }

    // ── Potion Management ───────────────────────────────

    private void drinkPotions() {
        CombatPotionType potionType = config.combatPotionType();
        if (potionType != CombatPotionType.NONE) {
            boolean hasCombatBoost = Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH)
                    > Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
            if (!hasCombatBoost) {
                Rs2Inventory.interact(potionType.getInventoryName(), "drink");
                sleep(300);
            }
        }

        if (config.useExtendedAntiVenom() && !Rs2Player.hasAntiVenomActive()) {
            if (Rs2Inventory.interact("extended anti-venom", "drink")
                    || Rs2Inventory.interact("anti-venom", "drink")) {
                sleep(300);
            }
        }
    }

    private void drinkPrayerPotion() {
        int prayerPercent = (Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100)
                / Math.max(1, Microbot.getClient().getRealSkillLevel(Skill.PRAYER));
        if (prayerPercent < config.drinkPrayerAtPercent()) {
            Rs2Inventory.interact(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new), "drink");
            sleep(300);
        }
    }

    // ── Prayer Management ───────────────────────────────

    private void managePrayers() {
        if (config.protectFromMelee()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        }
        if (config.usePiety()) {
            if (Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 70
                    && Microbot.getClient().getRealSkillLevel(Skill.DEFENCE) >= 70) {
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

    // ── Movement Helpers ────────────────────────────────

    /**
     * Score a candidate tile. Lower score = better.
     * Prefers tiles that are: not on acid, path doesn't cross acid,
     * closer to arena center, away from walls.
     */
    private int scoreTile(WorldPoint candidate) {
        int score = 0;
        // Massive penalty if the destination itself is acid
        if (isOnAcidPool(candidate)) {
            score += 1000;
        }
        // Large penalty if the straight-line path to this tile crosses any acid pool
        Player player = Microbot.getClient().getLocalPlayer();
        if (player != null) {
            int acidOnPath = countAcidOnPath(player.getWorldLocation(), candidate);
            score += acidOnPath * 500; // each acid tile on the path is strongly penalized
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
     * Check whether a tile is within WALL_BUFFER (3) tiles of the estimated arena edge.
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
        sleep(200);
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
        sleep(200);
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
        if (!mainWeapon.isEmpty()) {
            Rs2Inventory.wield(mainWeapon);
            sleep(150);
        }
        String shield = config.mainShield();
        if (!shield.isEmpty()) {
            Rs2Inventory.wield(shield);
            sleep(150);
        }
    }

    // ── Post-fight Handling ─────────────────────────────

    private void handleNoBoss() {
        // Check if we're in the looting state
        if (state == AraxxorState.LOOTING) {
            status = "Looting...";
            lootItems();
            return;
        }

        // Check if boss corpse exists for harvesting/destroying
        Rs2NpcModel deadAraxxor = Rs2Npc.getNpc(ARAXXOR_DEAD_ID);
        if (deadAraxxor != null) {
            state = AraxxorState.LOOTING;
            status = config.harvestCorpse() ? "Harvesting corpse..." : "Destroying corpse...";

            String action = config.harvestCorpse() ? "Harvest" : "Destroy";
            Rs2Npc.interact(deadAraxxor, action);
            sleep(1200);
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
        specUsed = false;
        araxxorAttackCount = 0;
        acidDripActive = false;
        acidDripPhase = 0;
        cleaveIncoming = false;
        acidCannonIncoming = false;
        acidCannonSourceTile = null;
        arenaCenter = null;
        acidPools.clear();
    }

    private void lootItems() {
        togglePrayers(false);

        // Check inventory space — eat food to make room
        if (Rs2Inventory.isFull()) {
            boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
            if (hasFood) {
                Rs2Player.eatAt(100);
                sleep(300);
            }
        }

        LootingParameters params = new LootingParameters(
                config.lootPriceThreshold(),
                Integer.MAX_VALUE,
                20,
                1,
                0,
                false,
                false
        );

        // Loot valuable items
        Rs2GroundItem.lootItemBasedOnValue(params);

        // Always loot unique drops
        Rs2GroundItem.loot("Noxious point", 20);
        Rs2GroundItem.loot("Noxious blade", 20);
        Rs2GroundItem.loot("Noxious pommel", 20);
        Rs2GroundItem.loot("Araxyte fang", 20);
        Rs2GroundItem.loot("Araxyte venom sack", 20);
        Rs2GroundItem.loot("Jar of venom", 20);
        Rs2GroundItem.loot("Spider cave teleport", 20);

        sleep(600, 1200);

        // Check if done looting
        if (!Rs2GroundItem.isItemBasedOnValueOnGround(config.lootPriceThreshold(), 20)) {
            // Pick up supply drops (Araxxor drops food/prayer pots)
            Rs2GroundItem.loot("Shark", 20);
            Rs2GroundItem.loot("Prayer potion", 20);
            Rs2GroundItem.loot("Super combat potion", 20);
            sleep(600);

            state = AraxxorState.IDLE;
            status = "Loot complete";
        }
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
        Client client = Microbot.getClient();
        int current = client.getBoostedSkillLevel(Skill.HITPOINTS);
        int max = client.getRealSkillLevel(Skill.HITPOINTS);
        return max > 0 ? (current * 100) / max : 100;
    }
}

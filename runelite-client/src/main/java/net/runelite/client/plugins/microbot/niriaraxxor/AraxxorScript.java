package net.runelite.client.plugins.microbot.niriaraxxor;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.magic.thralls.ThrallType;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
public class AraxxorScript extends StateMachineScript<AraxxorScript.EncounterPhase> {

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

    private static final String[] BALLISTA_JAVELINS = {
            "Dragon javelin", "Amethyst javelin", "Rune javelin", "Adamant javelin",
            "Mithril javelin", "Steel javelin", "Iron javelin", "Bronze javelin"
    };

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

    // Acid pools tracked via plugin events — mutated from the client thread (event handlers)
    // and read from the script thread, so must be a concurrency-safe set.
    @Getter
    private final Set<WorldPoint> acidPools = ConcurrentHashMap.newKeySet();

    // Attack counter for egg hatching awareness
    @Getter
    @Setter
    private volatile int araxxorAttackCount = 0;
    private volatile int lastRangedRepositionAttack = -1;

    // Spec tracking
    private volatile int specHitsCompleted = 0;
    // Acid drip tracking
    @Getter
    @Setter
    private volatile boolean acidDripActive = false;
    @Setter
    private volatile long acidDripStartTick = 0;
    // Acid drip sequence: 0 = step under boss, 1 = step off, 2 = attack after movement lands
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

    // Cleave AoE tracking — the 3 danger tiles from the most recent cleave
    private volatile Set<WorldPoint> cleaveDangerTiles = new HashSet<>();
    // Normalized attack direction (Araxxor→Player) at the moment the cleave fired
    private volatile int cleaveAttackDirX = 0;
    private volatile int cleaveAttackDirY = 0;

    // Arena center — set when we first find Araxxor, used to bias all movement toward center
    private volatile WorldPoint arenaCenter = null;
    // Approximate arena radius (Araxxor lair is roughly 14-16 tiles across)
    private static final int ARENA_RADIUS = 7;
    // Minimum distance from walls we want to maintain
    private static final int WALL_BUFFER = 2;

    private AraxxorConfig config;

    enum EncounterPhase { WAITING, ACTIVE, POST_FIGHT, ERROR }
    private volatile boolean postFight;
    private volatile boolean deathProcessed;
    private boolean bossAvailable;
    private WorldPoint movementDestination;
    private WorldPoint movementOrigin;
    private long movementDeadline;
    private boolean escapingAcid;
    private long corpseRetryAt;
    private long entryRetryAt;
    private long lootQuietSince;
    private int pendingLootId = -1;
    private WorldPoint pendingLootTile;
    private int pendingLootQuantity;
    private long lootRetryAt;

    @Override
    protected EncounterPhase initialState() { return EncounterPhase.WAITING; }

    @Override
    protected List<Transition<EncounterPhase>> defineTransitions() {
        List<Transition<EncounterPhase>> transitions = new ArrayList<>();
        for (EncounterPhase from : EncounterPhase.values()) {
            if (from != EncounterPhase.ERROR) {
                transitions.add(Transition.from(from).when(() -> state == AraxxorState.ERROR, "execution failed")
                        .because("Stop after execution failure").goTo(EncounterPhase.ERROR));
            }
            if (from != EncounterPhase.POST_FIGHT) {
                transitions.add(Transition.from(from).when(() -> postFight, "postFight")
                        .because("Death or corpse observed").goTo(EncounterPhase.POST_FIGHT));
            }
            if (from != EncounterPhase.ACTIVE) {
                transitions.add(Transition.from(from).when(() -> !postFight && bossAvailable, "live boss observed")
                        .because("Encounter available").goTo(EncounterPhase.ACTIVE));
            }
            if (from != EncounterPhase.WAITING) {
                transitions.add(Transition.from(from).when(() -> !postFight && !bossAvailable, "no encounter pending")
                        .because("Ready for next encounter").goTo(EncounterPhase.WAITING));
            }
        }
        return transitions;
    }

    @Override
    protected void onState(EncounterPhase phase) {
        if (phase != EncounterPhase.ERROR) loop();
    }

    @Override
    protected EncounterPhase onError(EncounterPhase phase, Exception error) {
        throw new IllegalStateException("Araxxor encounter action failed", error);
    }

    private <T> T readClient(Supplier<T> read) {
        return Microbot.getClientThread().runOnClientThreadOptional(read::get)
                .orElseThrow(() -> new IllegalStateException("Client snapshot unavailable"));
    }

    @Getter
    private static final class PlayerSnapshot {
        private final WorldPoint worldLocation;
        private PlayerSnapshot(WorldPoint worldLocation) { this.worldLocation = worldLocation; }
    }

    private PlayerSnapshot readPlayer() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player == null ? null : new PlayerSnapshot(player.getWorldLocation());
        }).orElse(null);
    }

    private boolean isTarget(Rs2NpcModel target) {
        return target != null && readClient(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null && player.getInteracting() == target.getNpc();
        });
    }

    private boolean movementPending() {
        if (movementDestination == null) return false;
        PlayerSnapshot player = readPlayer();
        WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
        boolean reachedDestination = playerLocation != null && movementDestination.equals(playerLocation);
        boolean movedAdjacent = playerLocation != null
                && movementOrigin != null
                && !movementOrigin.equals(playerLocation)
                && movementDestination.distanceTo(playerLocation) <= 1;
        if (reachedDestination || movedAdjacent) {
            movementDestination = null;
            movementOrigin = null;
            movementDeadline = 0;
            escapingAcid = false;
            return false;
        }
        if (System.currentTimeMillis() >= movementDeadline) {
            // A blocked/occupied destination is recoverable. Clear it so the next
            // loop recomputes the mechanic and chooses a fresh safe tile. Return
            // true for this iteration so a timed-out dodge never permits an attack.
            movementDestination = null;
            movementOrigin = null;
            movementDeadline = 0;
            escapingAcid = false;
            status = "Movement not observed — choosing another tile";
            return true;
        }
        return true;
    }

    private void moveTo(WorldPoint destination) {
        if (movementPending()) return;
        LocalPoint local = readClient(() -> LocalPoint.fromWorld(Microbot.getClient(), destination));
        PlayerSnapshot player = readPlayer();
        movementOrigin = player == null ? null : player.getWorldLocation();
        movementDestination = destination;
        movementDeadline = System.currentTimeMillis() + 4800;
        escapingAcid = false;
        Rs2Walker.walkFastLocal(local);
    }

    // ── Public API ──────────────────────────────────────

    public synchronized boolean run(AraxxorConfig config) {
        if (isRunning()) {
            return true;
        }

        this.config = config;
        resetFightState();
        deathProcessed = false;
        postFight = false;
        pendingLootId = -1;
        state = AraxxorState.IDLE;
        status = "Waiting for Araxxor";
        killCount = 0;
        enraged = false;
        specHitsCompleted = 0;
        araxxorAttackCount = 0;
        acidPools.clear();
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    status = "Waiting for login";
                    return;
                }
                Rs2NpcModel observedBoss = getNpc(ARAXXOR_ID);
                bossAvailable = observedBoss != null && !isDead(observedBoss);
                if (!step()) {
                    status = "Paused by Microbot";
                    return;
                }
            } catch (Exception | AssertionError ex) {
                log.error("Araxxor execution stopped", ex);
                super.shutdown();
                state = AraxxorState.ERROR;
                status = "Stopped after " + ex.getClass().getSimpleName();
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
        }

        // Detect acid special attacks
        if (animationId == ANIM_ACID_LEAK) {
            acidDripActive = true;
            acidDripStartTick = readClient(() -> Microbot.getClient().getTickCount());
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
            Rs2NpcModel boss = getNpc(ARAXXOR_ID);
            if (boss != null) {
                acidCannonSourceTile = boss.getWorldLocation();
            }
            // Pre-compute the destination; movement is issued by the script thread.
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
            Rs2NpcModel boss = getNpc(ARAXXOR_ID);
            if (boss != null) {
                acidCannonSourceTile = boss.getWorldLocation();
            }
            // Pre-compute the destination if animation detection missed it.
            if (precomputedAcidDodge == null) {
                precomputeAndDodgeAcidCannon();
            }
        }
    }

    /**
     * Called from plugin on NPC death for kill tracking.
     * Guarded against double-calls (death animation + despawn can both trigger this).
     */
    public synchronized void onAraxxorDeath() {
        // Guard: only count the kill once per fight (state transitions to LOOTING on first call)
        if (deathProcessed) {
            return; // Already processed this death
        }
        deathProcessed = true;
        postFight = true;
        lootQuietSince = System.currentTimeMillis();
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

        PlayerSnapshot player = readPlayer();
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
        Rs2NpcModel rupturaUrgent = getNpc(RUPTURA_ID);
        if (rupturaUrgent != null && !isDead(rupturaUrgent)) {
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
            return;
        }

        // ── 3. Priority: dodge acid cannon (big acid ball) ──
        // Active poll: check if the acid cannon projectile NPC (13676) is alive in case
        // event-based detection (animation / NPC spawn) was missed entirely.
        if (!acidCannonIncoming) {
            Rs2NpcModel cannonNpc = getNpc(ACID_CANNON_PROJ_NPC);
            if (cannonNpc != null && !isDead(cannonNpc)) {
                log("Acid cannon detected via active polling!");
                acidCannonIncoming = true;
                Rs2NpcModel boss = getNpc(ARAXXOR_ID);
                if (boss != null) {
                    acidCannonSourceTile = boss.getWorldLocation();
                }
            }
        }
        if (acidCannonIncoming) {
            status = "Dodging acid cannon!";
            state = AraxxorState.DODGING_ACID_BALL;
            dodgeAcidCannon(player);
            acidCannonIncoming = false;
            return;
        }

        // Acid can spawn underneath us while an older walk is still pending. It
        // must supersede that route instead of waiting for its timeout.
        if (isOnAcidPool(player.getWorldLocation()) && !escapingAcid) {
            movementDestination = null;
            movementOrigin = null;
            movementDeadline = 0;
            status = "Moving off acid!";
            moveOffAcid(player);
            return;
        }

        if (movementPending()) return;

        // ── 4. Priority: handle acid drip (keep moving) ──
        if (acidDripActive) {
            long ticksSinceDrip = readClient(() -> Microbot.getClient().getTickCount()) - acidDripStartTick;
            if (ticksSinceDrip > 6) {
                acidDripActive = false;
            } else {
                status = "Acid drip — moving!";
                handleAcidDrip(player);
                return;
            }
        }

        // Wall proximity is handled passively by pickBestTile()'s scoreTile() —
        // all movement destinations are penalised for being near walls.
        // We only force a return-to-center after explosive events (ruptura flee,
        // acid cannon dodge) that may have pushed us far out, not every loop tick.

        // ── 5. Find Araxxor ──
        Rs2NpcModel araxxor = getNpc(ARAXXOR_ID);

        // No boss found — could be dead/looting/idle
        if (araxxor == null || isDead(araxxor)) {
            handleNoBoss();
            return;
        }

        if (!isDead(araxxor)) deathProcessed = false;

        // Protection must be active as soon as a live Araxxor is observed. Do this
        // before gear, potion, minion, or attack actions can consume the next tick.
        managePrayers(player, araxxor);

        // Set arena center on first sighting of Araxxor (its spawn ≈ arena center)
        if (arenaCenter == null) {
            arenaCenter = araxxor.getWorldLocation();
            log("Arena center set to " + arenaCenter);
        }

        // ── 6. Handle minions (priority order: ruptura > mirrorback > acidic) ──
        if (handleMinions(player, araxxor)) {
            return; // Minion handling took priority
        }

        // After the first successful spec, keep the spec weapon equipped and fire
        // the next configured spec immediately on the next safe loop iteration.
        if (config.useSpecialAttack()
                && specHitsCompleted > 0
                && specHitsCompleted < config.specCount()
                && Rs2Equipment.isWearing(config.specWeapon())) {
            if (performSpecialAttack(player, araxxor)) return;
        }

        switchToMainWeapon();

        // ── 7. Potions ──
        drinkPotions();

        // ── 8. Prayer management ──
        managePrayers(player, araxxor);

        // ── 8.5. Thrall summoning ──
        if (config.useThralls()) {
            summonThrall();
        }

        // ── 9. Special attack for Defence drain ──
        if (config.useSpecialAttack() && specHitsCompleted < config.specCount()) {
            if (performSpecialAttack(player, araxxor)) {
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

        if (config.combatStyle() == AraxxorConfig.CombatStyle.RANGED
                && shouldForceRangedAttack()
                && maintainRangedDistance(player, araxxor, 2)) {
            status = "Creating space for ranged attack";
            return;
        }

        // Engage Araxxor if not already in combat
        if (!isTarget(araxxor)) {
            attackNpc(araxxor);
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
    private boolean checkAndDodgeCleave(PlayerSnapshot player) {
        if (enraged && cleaveIncoming) {
            status = "Dodging cleave (during adds)!";
            dodgeCleave(player);
            cleaveIncoming = false;
            clearCleaveTiles();
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
    private boolean handleMinions(PlayerSnapshot player, Rs2NpcModel araxxor) {
        // Cleave & acid cannon checks before starting any minion work
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

        // ── Ruptura (red) — most dangerous, explodes for up to 80 damage ──
        Rs2NpcModel ruptura = getNpc(RUPTURA_ID);
        if (ruptura != null && !isDead(ruptura)) {
            return handleRuptura(player, araxxor, ruptura);
        }

        // ── Mirrorback (white) — reflects damage ──
        Rs2NpcModel mirrorback = getNpc(MIRRORBACK_ID);
        if (mirrorback != null && !isDead(mirrorback)) {
            return handleMirrorback(player, araxxor, mirrorback);
        }

        // ── Acidic (green) — ranged attacks, explodes on death ──
        if (config.killAcidicAraxytes()) {
            Rs2NpcModel acidic = getNpc(ACIDIC_ID);
            if (acidic != null && !isDead(acidic)) {
                return handleAcidic(player, araxxor, acidic);
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
    private boolean handleRuptura(PlayerSnapshot player, Rs2NpcModel araxxor, Rs2NpcModel ruptura) {
        state = AraxxorState.KILLING_RUPTURA;
        managePrayers(player, araxxor);
        WorldPoint rupturaLoc = ruptura.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();
        int distToRuptura = playerLoc.distanceTo(rupturaLoc);

        // ── Close enough to worry? RUN 5 tiles NOW (flee at ≤3 for extra margin) ──
        if (distToRuptura <= 3) {
            status = "FLEE RUPTURA! Close! (" + distToRuptura + " tiles)";
            fleeFromRuptura(player, rupturaLoc);

            return true;
        }

        if (config.prioritizeAraxytes()) {
            status = "Prioritizing ruptura araxyte (" + distToRuptura + " tiles)";
            if (!switchToAraxyteWeapon()) return true;
            managePrayers(player, araxxor);
            attackNpc(ruptura, true);
            tickSleep();
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
                moveTo(lureTile);
                tickSleep();
            } else {
                status = "Ruptura approaching (" + distToRuptura + " tiles)";
                if (!isTarget(araxxor)) {
                    attackNpc(araxxor);
                }
            }
        } else {
            // Non-lure: just keep fighting, we'll dodge when it's adjacent
            status = "Ruptura approaching (" + distToRuptura + " tiles)";
            if (!isTarget(araxxor)) {
                attackNpc(araxxor);
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
    private void fleeFromRuptura(PlayerSnapshot player, WorldPoint rupturaLoc) {
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
        moveTo(best);
        tickSleep();
    }

    /**
     * Handle mirrorback araxyte — redirects 20% of Araxxor damage to itself,
     * recoils 50% of that to player. A noxious halberd, crush weapon, or heavy
     * ranged weapon can guarantee a max hit when its corresponding style is highest.
     */
    private boolean handleMirrorback(PlayerSnapshot player, Rs2NpcModel araxxor, Rs2NpcModel mirrorback) {
        state = AraxxorState.KILLING_MIRRORBACK;
        status = "Killing mirrorback araxyte!";

        // Dodge cleave before committing to weapon switch
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

        if (!switchToAraxyteWeapon()) {
            return true;
        }
        managePrayers(player, araxxor);

        // Dodge cleave before attacking
        if (checkAndDodgeCleave(player)) {
            switchToMainWeapon();
            return true;
        }
        if (checkAndDodgeAcidCannon(player)) {
            switchToMainWeapon();
            return true;
        }

        attackNpc(mirrorback);
        tickSleep();

        return true;
    }

    /**
     * Handle acidic araxyte — ranged attack up to 15 damage.
     * Explodes on death splattering acid in 7x7 AoE — move away after killing.
     * <p>
     * Also checks for enrage cleave between actions so we never eat a "Skree!" while
     * weapon-switching or waiting for the attack/explosion.
     */
    private boolean handleAcidic(PlayerSnapshot player, Rs2NpcModel araxxor, Rs2NpcModel acidic) {
        state = AraxxorState.KILLING_ACIDIC;
        status = "Killing acidic araxyte!";

        // Dodge cleave / acid cannon before committing to weapon switch
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

        if (!switchToAraxyteWeapon()) {
            return true;
        }
        managePrayers(player, araxxor);

        // Dodge cleave / acid cannon before attacking
        if (checkAndDodgeCleave(player)) {
            switchToMainWeapon();
            return true;
        }
        if (checkAndDodgeAcidCannon(player)) {
            switchToMainWeapon();
            return true;
        }

        attackNpc(acidic);
        tickSleep();

        return true;
    }

    private boolean switchToAraxyteWeapon() {
        String araxWeapon = config.araxyteSwitchWeapon().trim();
        if (araxWeapon.isEmpty()) return true;

        if (!Rs2Equipment.isWearing(araxWeapon)) {
            status = "Equipping " + araxWeapon;
            if (!Rs2Inventory.wield(araxWeapon)) {
                log("WARNING: " + araxWeapon + " not in inventory — cannot equip for araxyte");
                return false;
            }
            if (tickSleep()) {
                switchToMainWeapon();
                return false;
            }
            if (!sleepUntil(() -> Rs2Equipment.isWearing(araxWeapon), 1200)) {
                log("WARNING: Failed to equip " + araxWeapon + " for araxyte!");
                switchToMainWeapon();
                return false;
            }
        }

        if (!isBallista(araxWeapon)) return true;

        String javelin = Arrays.stream(BALLISTA_JAVELINS)
                .filter(Rs2Inventory::hasItem)
                .findFirst()
                .orElse(null);
        if (javelin == null) {
            if (isJavelinEquipped()) return true;
            status = "No javelins available for " + araxWeapon;
            log("WARNING: No javelins in inventory or ammo slot for " + araxWeapon);
            return false;
        }

        if (!Rs2Equipment.isWearing(javelin)) {
            status = "Equipping " + javelin;
            if (!Rs2Inventory.wield(javelin)) {
                log("WARNING: Failed to click " + javelin + " for " + araxWeapon);
                return false;
            }
            if (tickSleep()) {
                switchToMainWeapon();
                return false;
            }
            if (!sleepUntil(() -> Rs2Equipment.isWearing(javelin), 1200)) {
                log("WARNING: Failed to equip " + javelin + " for " + araxWeapon);
                return false;
            }
        }
        return true;
    }

    private boolean isBallista(String weaponName) {
        return weaponName.toLowerCase(Locale.ROOT).contains("ballista");
    }

    private boolean isJavelinEquipped() {
        return Rs2Equipment.isWearing(item -> item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("javelin"));
    }

    private boolean shouldForceRangedAttack() {
        int attackCount = araxxorAttackCount;
        if (attackCount <= 0 || attackCount % 2 != 0 || attackCount == lastRangedRepositionAttack) {
            return false;
        }
        lastRangedRepositionAttack = attackCount;
        return true;
    }

    /**
     * Move far enough from Araxxor to force a ranged or magic attack.
     * Ranged setups should have higher magic defence so this resolves to ranged,
     * while Aranea boots can negate that attack once every eight ticks.
     *
     * @return true when a movement command was issued
     */
    private boolean maintainRangedDistance(PlayerSnapshot player, Rs2NpcModel araxxor, int minimumDistance) {
        WorldPoint playerLoc = player.getWorldLocation();
        WorldArea bossArea = readClient(araxxor::getWorldArea);
        int currentDistance = bossArea.distanceTo(playerLoc);
        if (currentDistance >= minimumDistance) {
            return false;
        }

        int bossCenterX = bossArea.getX() + bossArea.getWidth() / 2;
        int bossCenterY = bossArea.getY() + bossArea.getHeight() / 2;
        int awayX = Integer.signum(playerLoc.getX() - bossCenterX);
        int awayY = Integer.signum(playerLoc.getY() - bossCenterY);
        if (awayX == 0 && awayY == 0) {
            awayY = -1;
        }

        int moveDistance = minimumDistance - currentDistance + 1;
        WorldPoint[] candidates = {
                new WorldPoint(
                        playerLoc.getX() + awayX * moveDistance,
                        playerLoc.getY() + awayY * moveDistance,
                        playerLoc.getPlane()),
                new WorldPoint(
                        playerLoc.getX() + awayX * moveDistance + awayY,
                        playerLoc.getY() + awayY * moveDistance - awayX,
                        playerLoc.getPlane()),
                new WorldPoint(
                        playerLoc.getX() + awayX * moveDistance - awayY,
                        playerLoc.getY() + awayY * moveDistance + awayX,
                        playerLoc.getPlane()),
        };

        WorldPoint best = pickBestTile(candidates);
        moveTo(best);
        tickSleep();
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
    private void handleEnrageFight(PlayerSnapshot player, Rs2NpcModel araxxor) {
        if (config.combatStyle() == AraxxorConfig.CombatStyle.RANGED) {
            handleEnrageFightRanged(player, araxxor);
            return;
        }

        state = AraxxorState.ENRAGED_STEP_UNDER;

        WorldPoint araxxorLoc = araxxor.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();

        // ── Check acid density around Araxxor ──
        int acidNearBoss = countAcidInArea(araxxorLoc, 2); // 5x5 area (±2 tiles)

        if (acidNearBoss >= ACID_LURE_THRESHOLD) {
            // Too much acid — lure Araxxor to a cleaner spot
            WorldPoint cleanSpot = findCleanSpot(playerLoc);
            if (cleanSpot != null && playerLoc.distanceTo(cleanSpot) > 1) {
                status = "Luring Araxxor to clean area (" + acidNearBoss + " acid tiles)";
                moveTo(cleanSpot);
                if (tickSleep()) return; // dodge takes priority
                return; // let main loop re-evaluate; Araxxor will follow us
            }
        }

        // ── Ensure we're dealing damage — attack FIRST, then reposition ──
        // This prevents the dodge loop: dodgeCleave → handleEnrageFight → walk under
        // (interrupted by next cleave) → dodgeCleave → ... never attacking.
        // By queuing attack before the walk, damage is dealt even if we're interrupted.
        if (!isTarget(araxxor)) {
            attackNpc(araxxor);
        }

        // ── Step under Araxxor for cleave self-damage ──
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
                moveTo(best);
            } else {
                moveTo(araxxorLoc);
            }
            if (tickSleep()) return; // dodge takes priority, but attack is already queued
        }

        tickSleep();

        // Eat if needed during enrage
        Rs2Player.eatAt(config.eatAtHpPercent());
        drinkPrayerPotion();
    }

    /**
     * Ranged variant of {@link #handleEnrageFight(Player, Rs2NpcModel)}.
     * The melee step-under trick only benefits players standing adjacent to Araxxor,
     * so a ranged player simply keeps attacking from range; the main loop's cleave
     * dodge check (higher priority) already handles repositioning away from danger.
     */
    private void handleEnrageFightRanged(PlayerSnapshot player, Rs2NpcModel araxxor) {
        state = AraxxorState.FIGHTING;

        if (maintainRangedDistance(player, araxxor, 3)) {
            status = "Staying outside cleave range";
            return;
        }

        if (!isTarget(araxxor)) {
            attackNpc(araxxor);
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
     * To avoid the cleave, we move PARALLEL to the attack direction (toward or
     * away from Araxxor along its attack line). This guarantees we leave the
     * 3-tile perpendicular strip immediately without crossing through it.
     * <p>
     * Candidates are scored via pickBestTile() which now penalizes cleave tiles
     * on both the destination and the path, preventing path-through-damage.
     */
    private void dodgeCleave(PlayerSnapshot player) {
        state = AraxxorState.ENRAGED_DODGE_CLEAVE;
        WorldPoint loc = player.getWorldLocation();

        // Determine dodge distance based on diagonal/corner position
        Rs2NpcModel araxxor = getNpc(ARAXXOR_ID);
        boolean onCorner = false;
        if (araxxor != null) {
            WorldPoint bossLoc = araxxor.getWorldLocation();
            int relX = loc.getX() - bossLoc.getX();
            int relY = loc.getY() - bossLoc.getY();
            onCorner = (relX != 0 && relY != 0);
        }

        int dodgeDist = onCorner ? 3 : 2;

        // Use stored attack direction if available; otherwise fall back to boss-relative calculation
        int atkX = cleaveAttackDirX;
        int atkY = cleaveAttackDirY;
        if (atkX == 0 && atkY == 0 && araxxor != null) {
            WorldPoint bossLoc = araxxor.getWorldLocation();
            atkX = Integer.signum(loc.getX() - bossLoc.getX());
            atkY = Integer.signum(loc.getY() - bossLoc.getY());
        }

        java.util.List<WorldPoint> candidates = new java.util.ArrayList<>();

        if (atkX != 0 || atkY != 0) {
            // PRIMARY: move parallel to attack direction (away from OR toward boss)
            // These tiles are guaranteed to be off the perpendicular cleave strip
            candidates.add(new WorldPoint(loc.getX() + atkX * dodgeDist, loc.getY() + atkY * dodgeDist, loc.getPlane()));
            candidates.add(new WorldPoint(loc.getX() - atkX * dodgeDist, loc.getY() - atkY * dodgeDist, loc.getPlane()));
            // Slightly shorter parallel fallbacks
            candidates.add(new WorldPoint(loc.getX() + atkX * 2, loc.getY() + atkY * 2, loc.getPlane()));
            candidates.add(new WorldPoint(loc.getX() - atkX * 2, loc.getY() - atkY * 2, loc.getPlane()));
        }

        // SECONDARY: diagonal escapes (likely clear of the strip at sufficient distance)
        candidates.add(new WorldPoint(loc.getX() + 2, loc.getY() + 2, loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX() - 2, loc.getY() - 2, loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX() + 2, loc.getY() - 2, loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX() - 2, loc.getY() + 2, loc.getPlane()));

        // FALLBACK: cardinal directions at dodge distance (scored — cleave tiles penalized)
        candidates.add(new WorldPoint(loc.getX() + dodgeDist, loc.getY(), loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX() - dodgeDist, loc.getY(), loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX(), loc.getY() + dodgeDist, loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX(), loc.getY() - dodgeDist, loc.getPlane()));

        // pickBestTile scores all candidates — cleave tiles on path/destination get +1000/+500 penalty
        WorldPoint best = pickBestTile(candidates.toArray(new WorldPoint[0]));
        moveTo(best);
        tickSleep();

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
     * Pre-compute the acid cannon dodge destination.
     * Called from event handlers (onAraxxorAnimation, onAcidCannonDetected) to
     * reduce the delay before the script loop issues the movement.
     */
    private void precomputeAndDodgeAcidCannon() {
        PlayerSnapshot player = readPlayer();
        if (player == null || acidCannonSourceTile == null) return;

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint dodge = computeAcidCannonDodge(playerLoc, acidCannonSourceTile);
        precomputedAcidDodge = dodge;

        // The script loop performs the walk so this event handler never blocks the client thread.
        log("Pre-computed acid dodge → " + dodge);
    }

    /**
     * Fallback acid cannon dodge used by the main loop if pre-computation missed.
     * Uses the same computation but runs on the script thread.
     */
    private void dodgeAcidCannon(PlayerSnapshot player) {
        state = AraxxorState.DODGING_ACID_BALL;
        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint best = precomputedAcidDodge != null
                ? precomputedAcidDodge
                : computeAcidCannonDodge(playerLoc, acidCannonSourceTile);
        precomputedAcidDodge = null;

        moveTo(best);
        tickSleep(); // interruptible — can react to cleave while dodging
        log("Dodged acid cannon (fallback) → " + best);
    }

    /**
     * Check for and dodge an incoming acid cannon attack.
     * Called from minion handlers so the player dodges even while dealing with adds.
     *
     * @return true if an acid cannon was dodged (caller should re-evaluate state)
     */
    private boolean checkAndDodgeAcidCannon(PlayerSnapshot player) {
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
     * We advance through three phases across loop iterations:
     *   Phase 0: Walk onto Araxxor's tile (step under)
     *   Phase 1: Move 1 tile adjacent
     *   Phase 2: Attack after the movement is observed
     */
    private void handleAcidDrip(PlayerSnapshot player) {
        Rs2NpcModel araxxor = getNpc(ARAXXOR_ID);
        if (araxxor == null || isDead(araxxor)) {
            acidDripActive = false;
            return;
        }

        if (config.combatStyle() == AraxxorConfig.CombatStyle.RANGED) {
            handleAcidDripRanged(player, araxxor);
            return;
        }

        WorldPoint araxxorLoc = araxxor.getWorldLocation();
        WorldPoint playerLoc = player.getWorldLocation();

        if (acidDripPhase == 0) {
            // Phase 0: step under boss — puddle drops at our previous location
            if (playerLoc.distanceTo(araxxorLoc) > 0) {
                moveTo(araxxorLoc);
                return;
            }
            acidDripPhase = 1;
        } else if (acidDripPhase == 1) {
            // Phase 1: step one tile off the boss. attackNpc deliberately refuses to
            // act while movement is pending, so the attack is issued in phase 2.
            WorldPoint[] candidates = {
                    new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() - 1, araxxorLoc.getPlane()),  // south (preferred)
                    new WorldPoint(araxxorLoc.getX() + 1, araxxorLoc.getY(), araxxorLoc.getPlane()),  // east
                    new WorldPoint(araxxorLoc.getX() - 1, araxxorLoc.getY(), araxxorLoc.getPlane()),  // west
                    new WorldPoint(araxxorLoc.getX(), araxxorLoc.getY() + 1, araxxorLoc.getPlane()),  // north
            };

            WorldPoint stepOff = pickBestTile(candidates);
            moveTo(stepOff);
            acidDripPhase = 2;
        } else {
            // The main loop's movementPending guard ensures the step-off was observed.
            attackNpc(araxxor);
            acidDripPhase = 0;
        }
        tickSleep();
    }

    /**
     * Ranged variant of {@link #handleAcidDrip(Player)}.
     * A ranged player isn't standing adjacent to Araxxor, so the melee step-under/step-off
     * choreography (which only exists to avoid the puddle dropped at the player's own feet)
     * doesn't apply. Instead, just keep attacking and step off the current tile if it is
     * (or is about to become) an acid pool.
     */
    private void handleAcidDripRanged(PlayerSnapshot player, Rs2NpcModel araxxor) {
        WorldPoint playerLoc = player.getWorldLocation();

        if (isOnAcidPool(playerLoc)) {
            WorldPoint[] candidates = {
                    new WorldPoint(playerLoc.getX(), playerLoc.getY() - 1, playerLoc.getPlane()),
                    new WorldPoint(playerLoc.getX() + 1, playerLoc.getY(), playerLoc.getPlane()),
                    new WorldPoint(playerLoc.getX() - 1, playerLoc.getY(), playerLoc.getPlane()),
                    new WorldPoint(playerLoc.getX(), playerLoc.getY() + 1, playerLoc.getPlane()),
            };
            WorldPoint best = pickBestTile(candidates);
            moveTo(best);
        }

        if (!isTarget(araxxor)) {
            attackNpc(araxxor);
        }
        tickSleep();
    }

    // ── Defence Drain Special Attack ────────────────────

    /**
     * Perform special attack with Elder Maul / DWH to drain Araxxor's Defence.
     * Araxxor has 135 Defence, drainable up to 45 levels.
     * Elder maul drains 45 in one hit; DWH drains 40.
     * Supports dual-spec: performs up to {@code config.specCount()} spec hits per fight.
     * After all spec hits land, optionally drinks a surge potion before switching back.
     */
    private boolean performSpecialAttack(PlayerSnapshot player, Rs2NpcModel araxxor) {
        int specEnergy = readClient(() -> Microbot.getClient().getVarpValue(300)) / 10; // 0–100
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
        managePrayers(player, araxxor);

        // Perform spec hits (one per call, loop returns to main loop so dodges are checked)
        status = "Spec " + (specHitsCompleted + 1) + "/" + config.specCount() + ": " + config.specWeapon();

        // Switch to combat tab so the spec orb widget is visible, then toggle spec on
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 1200);
        Rs2Combat.setSpecState(true, specPerHit * 10); // raw 0–1000 scale
        if (tickSleep()) { switchToMainWeapon(); return true; }

        // Attack araxxor with spec enabled
        attackNpc(araxxor, true);
        tickSleep();

        // Wait for the hit to register (player animation plays)
        if (!sleepUntil(() -> readClient(() -> Microbot.getClient().getVarpValue(300)) / 10 < specEnergy, 1800)) {
            status = "Special attack not observed — retrying";
            return true;
        }
        tickSleep(); // Let the hit land

        specHitsCompleted++;
        log("Spec hit " + specHitsCompleted + "/" + config.specCount() + " landed");

        // If we still have hits remaining AND enough energy, stay on spec weapon for next loop iteration
        int energyAfter = readClient(() -> Microbot.getClient().getVarpValue(300)) / 10;
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
        if (potionType == CombatPotionType.AUTO) {
            potionType = config.combatStyle() == AraxxorConfig.CombatStyle.RANGED
                    ? CombatPotionType.RANGING
                    : CombatPotionType.SUPER_COMBAT;
        }
        if (potionType != CombatPotionType.NONE) {
            Skill boostedSkill = potionType.getBoostedSkill();
            boolean hasCombatBoost = readClient(() -> Microbot.getClient().getBoostedSkillLevel(boostedSkill))
                    > readClient(() -> Microbot.getClient().getRealSkillLevel(boostedSkill));
            if (!hasCombatBoost) {
                Rs2Inventory.interact(potionType.getInventoryName(), "drink");
                tickSleep();
            }
        }

        if (config.useExtendedAntiVenom() && !Rs2Player.hasAntiVenomActive()) {
            if (Rs2Inventory.interact("extended anti-venom", "drink")
                    || Rs2Inventory.interact("anti-venom", "drink")) {
                tickSleep();
            }
        }
    }

    private void drinkPrayerPotion() {
        int prayerPercent = (readClient(() -> Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER)) * 100)
                / Math.max(1, readClient(() -> Microbot.getClient().getRealSkillLevel(Skill.PRAYER)));
        if (prayerPercent < config.drinkPrayerAtPercent()) {
            Rs2Inventory.interact(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new), "drink");
            tickSleep();
        }
    }

    // ── Prayer Management ───────────────────────────────

    private void managePrayers(PlayerSnapshot player, Rs2NpcModel araxxor) {
        int meleeRange = enraged ? 2 : 1;
        WorldArea araxxorArea = readClient(araxxor::getWorldArea);
        boolean inMeleeRange = araxxorArea != null
                && araxxorArea.distanceTo(player.getWorldLocation()) <= meleeRange;
        if (inMeleeRange && config.protectFromMelee()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        } else if (config.protectFromMissiles()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        }

        int prayerLevel = readClient(() -> Microbot.getClient().getRealSkillLevel(Skill.PRAYER));
        int defenceLevel = readClient(() -> Microbot.getClient().getRealSkillLevel(Skill.DEFENCE));
        if (isRangedWeaponEquipped()) {
            if (config.useRigour() && prayerLevel >= Rs2PrayerEnum.RIGOUR.getLevel() && defenceLevel >= 70) {
                Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, true);
            }
        } else if (config.usePiety()
                && prayerLevel >= Rs2PrayerEnum.PIETY.getLevel()
                && defenceLevel >= 70) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        }
    }

    private boolean isRangedWeaponEquipped() {
        String rangedWeapon = config.rangedWeapon().trim();
        if (!rangedWeapon.isEmpty() && Rs2Equipment.isWearing(rangedWeapon)) return true;

        String araxWeapon = config.araxyteSwitchWeapon().trim();
        if (!araxWeapon.isEmpty() && isBallista(araxWeapon) && Rs2Equipment.isWearing(araxWeapon)) return true;

        String mainWeapon = config.mainWeapon().trim();
        if (!mainWeapon.isEmpty() && Rs2Equipment.isWearing(mainWeapon)) return false;
        String specWeapon = config.specWeapon().trim();
        if (!specWeapon.isEmpty() && Rs2Equipment.isWearing(specWeapon)) return false;
        if (!araxWeapon.isEmpty() && !isBallista(araxWeapon) && Rs2Equipment.isWearing(araxWeapon)) return false;
        return config.combatStyle() == AraxxorConfig.CombatStyle.RANGED;
    }

    private void togglePrayers(boolean on) {
        if (Rs2Prayer.isOutOfPrayer()) return;
        if (!on) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
            Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, false);
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, false);
            return;
        }

        if (isRangedWeaponEquipped()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
            if (config.useRigour()) Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, true);
        } else {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            if (config.usePiety()) Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
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
        PlayerSnapshot player = readPlayer();
        Rs2NpcModel araxxor = getNpc(ARAXXOR_ID);
        if (player == null || araxxor == null) return;

        WorldPoint playerLoc = player.getWorldLocation();
        WorldPoint bossLoc = araxxor.getWorldLocation();

        // Attack direction: Araxxor → Player (normalized to signs)
        int atkDirX = Integer.signum(playerLoc.getX() - bossLoc.getX());
        int atkDirY = Integer.signum(playerLoc.getY() - bossLoc.getY());

        // Store attack direction for dodge candidate generation
        cleaveAttackDirX = atkDirX;
        cleaveAttackDirY = atkDirY;

        // Perpendicular to attack direction (the cleave strip runs along this axis)
        int perpX = -atkDirY;
        int perpY = atkDirX;

        // If attack direction is zero (on same tile), default perpendicular to E-W
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
        PlayerSnapshot player = readPlayer();
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
     * Pick the best tile from a list of candidates, preferring non-acid tiles
     * with a clean path, closer to arena center. Always returns a tile (never null).
     */
    private WorldPoint pickBestTile(WorldPoint[] candidates) {
        return Arrays.stream(candidates)
                .min(Comparator.comparingInt(this::scoreTile))
                .orElse(candidates[0]);
    }

    private boolean isOnAcidPool(WorldPoint tile) {
        if (acidPools.contains(tile)) return true;
        return Microbot.getRs2TileObjectCache().query()
                .where(object -> (object.getId() == ACID_POOL_OBJ
                        || object.getId() == VENOM_PUDDLE_1
                        || object.getId() == VENOM_PUDDLE_2
                        || object.getId() == VENOM_PUDDLE_3)
                        && tile.equals(object.getWorldLocation()))
                .firstOnClientThread() != null;
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
    private void returnToCenter(PlayerSnapshot player) {
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
        moveTo(best);
        tickSleep();
    }

    private void moveOffAcid(PlayerSnapshot player) {
        WorldPoint loc = player.getWorldLocation();
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        WorldPoint[] candidates = new WorldPoint[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            candidates[i] = new WorldPoint(loc.getX() + offsets[i][0], loc.getY() + offsets[i][1], loc.getPlane());
        }
        WorldPoint best = pickBestTile(candidates);
        moveTo(best);
        escapingAcid = movementDestination != null;
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
        moveTo(best);
    }

    // ── Weapon Switching ────────────────────────────────

    private void switchToMainWeapon() {
        boolean ranged = config.combatStyle() == AraxxorConfig.CombatStyle.RANGED;
        String weapon = ranged ? config.rangedWeapon() : config.mainWeapon();
        String offhand = ranged ? config.rangedAmmo() : config.mainShield();
        // Queue both equips — weapon and offhand/ammo can process in the same game tick
        if (!weapon.isEmpty() && !Rs2Equipment.isWearing(weapon)) {
            Rs2Inventory.wield(weapon);
        }
        if (!offhand.isEmpty() && !Rs2Equipment.isWearing(offhand)) {
            Rs2Inventory.wield(offhand);
        }
        if ((!weapon.isEmpty() && !Rs2Equipment.isWearing(weapon)) || (!offhand.isEmpty() && !Rs2Equipment.isWearing(offhand))) {
            tickSleep();
        }
    }

    // ── Post-fight Handling ─────────────────────────────

    private void handleNoBoss() {
        long now = System.currentTimeMillis();
        Rs2NpcModel corpse = getNpc(ARAXXOR_DEAD_ID);
        if (corpse != null) {
            // Death animation/despawn events can be missed while loading an instance.
            // Observing the corpse is sufficient evidence to count the kill once.
            if (!deathProcessed) {
                onAraxxorDeath();
            }
            postFight = true;
            state = AraxxorState.HARVESTING;
            lootQuietSince = now;
            if (now < corpseRetryAt) return;
            // Re-query each attempt; a click is not evidence of harvesting.
            status = config.harvestCorpse() ? "Harvesting corpse" : "Destroying corpse";
            corpse.click(config.harvestCorpse() ? "Harvest" : "Destroy");
            corpseRetryAt = now + 3000;
            return;
        }
        if (postFight) {
            state = AraxxorState.LOOTING;
            lootItems();
            return;
        }
        if (config.maxKillsPerTrip() > 0 && killCount >= config.maxKillsPerTrip()) {
            emergencyTeleport();
            return;
        }
        if (Rs2Inventory.getInventoryFood().isEmpty()
                || !Rs2Inventory.hasItem(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new))) {
            status = "Low supplies — teleporting";
            emergencyTeleport();
            return;
        }
        state = AraxxorState.IDLE;
        status = "Waiting for Araxxor";
        if (config.enterLair() && now >= entryRetryAt) {
            var entrance = Microbot.getRs2TileObjectCache().query()
                    .where(object -> object.getId() == ObjectID1.ARAXXOR_CAVE_OUTER_TUNNEL_OP
                            || object.getId() == ObjectID1.ARAXXOR_CAVE_OUTER_TUNNEL_OP_2
                            || object.getId() == ObjectID1.ARAXXOR_CAVE_OUTER_TUNNEL_OP_3
                            || object.getId() == ObjectID1.ARAXXOR_CAVE_OUTER_TUNNEL_OP_4
                            || object.getId() == ObjectID1.ARAXXOR_BOSS_TUNNEL_MULTI)
                    .within(10).nearestOnClientThread();
            if (entrance != null) {
                String action = readClient(() -> {
                    ObjectComposition composition = entrance.getObjectComposition();
                    if (composition == null) return "";
                    if (composition.getImpostorIds() != null && composition.getImpostor() != null) {
                        composition = composition.getImpostor();
                    }
                    if (composition == null || composition.getActions() == null) return "";
                    return Arrays.stream(composition.getActions())
                            .filter(a -> a != null && (a.toLowerCase(Locale.ROOT).contains("enter")
                                    || a.equalsIgnoreCase("Pass")))
                            .findFirst().orElse("");
                });
                if (!action.isEmpty()) {
                    state = AraxxorState.ENTERING_LAIR;
                    status = "Entering lair — awaiting instance";
                    if (!entrance.click(action)) {
                        status = "Lair door click failed — retrying";
                    }
                    entryRetryAt = now + 5000;
                    return;
                }
                status = "Lair door found, but no enter action";
                entryRetryAt = now + 2000;
                return;
            }
            status = "No Araxxor lair door within 10 tiles";
            entryRetryAt = now + 2000;
        }
        togglePrayers(false);
    }
    /**
     * Reset all per-fight state flags. Called when transitioning to idle between kills
     * to ensure stale enraged/acid/cleave flags from a previous fight don't carry over.
     */
    private void resetFightState() {
        enraged = false;
        specHitsCompleted = 0;
        araxxorAttackCount = 0;
        lastRangedRepositionAttack = -1;
        acidDripActive = false;
        acidDripPhase = 0;
        cleaveIncoming = false;
        clearCleaveTiles();
        acidCannonIncoming = false;
        acidCannonSourceTile = null;
        precomputedAcidDodge = null;
        arenaCenter = null;
        acidPools.clear();
        movementDestination = null;
        movementOrigin = null;
        movementDeadline = 0;
        escapingAcid = false;
        corpseRetryAt = 0;
        pendingLootId = -1;
        pendingLootTile = null;
        pendingLootQuantity = 0;
        lootRetryAt = 0;
    }

    private boolean wantedLoot(Rs2TileItemModel item) {
        String name = item.getName();
        return item.isLootAble() && (item.getTotalValue() >= config.lootPriceThreshold()
                || Arrays.asList("Noxious point", "Noxious blade", "Noxious pommel", "Araxyte fang",
                "Araxyte venom sack", "Jar of venom", "Spider cave teleport", "Shark").contains(name)
                || name != null && (name.startsWith("Prayer potion(") || name.startsWith("Super combat potion(")));
    }

    private void lootItems() {
        long now = System.currentTimeMillis();
        if (now < lootRetryAt) return;
        if (pendingLootId != -1) {
            Rs2TileItemModel remaining = Microbot.getRs2TileItemCache().query()
                    .withId(pendingLootId)
                    .where(item -> pendingLootTile.equals(item.getWorldLocation()))
                    .firstOnClientThread();
            if (remaining != null && remaining.getQuantity() >= pendingLootQuantity) {
                status = "Pickup not observed — retrying";
            }
            pendingLootId = -1;
        }
        Rs2TileItemModel item = Microbot.getRs2TileItemCache().query()
                .where(this::wantedLoot).within(20).nearestOnClientThread();
        if (item == null) {
            // Allow delayed corpse drops to reach the cache before ending this kill.
            if (now - lootQuietSince >= 2400) {
                postFight = false;
                resetFightState();
                state = AraxxorState.IDLE;
                status = "Loot complete";
            }
            return;
        }
        lootQuietSince = now;
        if (Rs2Inventory.isFull() && !(item.isStackable() && Rs2Inventory.hasItem(item.getId()))) {
            if (!Rs2Inventory.getInventoryFood().isEmpty() && getHpPercent() < 100) {
                Rs2Player.eatAt(100);
                lootRetryAt = now + 1800;
                status = "Making loot space — awaiting inventory";
            } else {
                status = "Loot blocked — inventory full";
            }
            return;
        }
        // Exactly one loot action per iteration. Identity includes tile and quantity.
        pendingLootId = item.getId();
        pendingLootTile = item.getWorldLocation();
        pendingLootQuantity = item.getQuantity();
        item.pickup();
        lootRetryAt = now + 2400;
        status = "Awaiting pickup";
    }
    // ── Utility ─────────────────────────────────────────
    /**
     * Find the nearest NPC by ID using the Queryable API.
     *
     * IMPORTANT: Never use the legacy {@code Rs2Npc.getNpc(...)} helper directly here —
     * this script runs on a background thread and must go through the cache/query layer
     * which safely bridges client-thread access.
     */
    private Rs2NpcModel getNpc(int id) {
        return Microbot.getRs2NpcCache().query()
                .withId(id)
                .where(npc -> npc.getNpc() != null)
                // ID-specific encounter lookups do not need a distance sort. Using
                // nearest here also reads the player cache, which can briefly contain
                // a null actor while login or an instance transition is completing.
                .firstOnClientThread();
    }

    private boolean isDead(Rs2NpcModel npc) {
        return npc == null || readClient(npc::isDead);
    }
    private boolean attackNpc(Rs2NpcModel target) {
        return attackNpc(target, false);
    }

    private boolean attackNpc(Rs2NpcModel target, boolean forceClick) {
        if (target == null || movementPending() || (!forceClick && isTarget(target))) {
            return false;
        }

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().isWidgetSelected()) {
                Microbot.getClient().setWidgetSelected(false);
            }
            return true;
        });

        Rs2NpcModel current = Microbot.getRs2NpcCache().query()
                .withId(target.getId()).where(npc -> npc.getNpc() == target.getNpc()).firstOnClientThread();
        boolean clicked = current != null && !isDead(current) && current.click("Attack");
        if (!clicked) {
            status = "Unable to attack " + target.getName();
        }
        return clicked;
    }

    private void emergencyTeleport() {
        state = AraxxorState.TELEPORTING_AWAY;
        togglePrayers(false);

        String teleItem = config.teleportItem();
        if (teleItem.isEmpty() || !Rs2Inventory.hasItem(teleItem)) {
            failSafe("Emergency teleport item unavailable");
            return;
        }

        // Try common teleport actions in order and require an observed interaction.
        boolean interacted = Rs2Inventory.interact(teleItem, "break")
                || Rs2Inventory.interact(teleItem, "rub")
                || Rs2Inventory.interact(teleItem, "teleport");
        if (!interacted) {
            failSafe("Emergency teleport interaction failed");
            return;
        }

        Rs2Player.waitForAnimation();
        if (sleepUntil(() -> !readClient(() -> Microbot.getClient().isInInstancedRegion()), 5000)) {
            shutdown();
        } else {
            failSafe("Emergency teleport not observed");
        }
    }

    private void failSafe(String message) {
        status = message + " — stopped";
        state = AraxxorState.ERROR;
        super.shutdown();
    }

    private int getHpPercent() {

        int current = readClient(() -> Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS));
        int max = readClient(() -> Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS));
        return max > 0 ? (current * 100) / max : 100;
    }
}

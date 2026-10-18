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
import net.runelite.api.gameval.ObjectID;
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
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.magic.thralls.ThrallType;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
    /** Keep collision-map work small enough that a script iteration cannot stall the client thread. */
    private static final int MAX_TRAVERSABILITY_CANDIDATES = 24;
    private static final int MAX_TRAVERSABILITY_STEPS = 16;
    private static final WorldPoint SPIDER_CAVE_ENTRANCE = new WorldPoint(3658, 3409, 0);
    private static final WorldPoint ARAXXOR_LAIR_APPROACH = new WorldPoint(3658, 9816, 0);

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
    private enum IncomingAttackStyle { MELEE, RANGED, MAGIC }
    private volatile IncomingAttackStyle incomingAttackStyle = IncomingAttackStyle.RANGED;

    // Acid pools tracked via plugin events — mutated from the client thread (event handlers)
    // and read from the script thread, so must be a concurrency-safe set.
    @Getter
    private final Set<WorldPoint> acidPools = ConcurrentHashMap.newKeySet();

    // Attack counter for egg hatching awareness
    @Getter
    @Setter
    private volatile int araxxorAttackCount = 0;
    private int lastRangedMovementTick = -1000;
    private volatile boolean ballistaHitPending;
    private volatile boolean primaryWeaponRestoreRequested;
    private volatile int ballistaHitDeadlineTick = -1;

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
    private int acidDripCornerIndex = -1;
    private int acidDripCycles;
    private boolean acidDripRunStateCaptured;
    private boolean restoreRunAfterAcidDrip;

    // Enrage cleave tracking
    @Getter
    private volatile boolean cleaveIncoming = false;
    private volatile int cleaveDetectedTick = -1000;
    private int enrageRecoveryThroughTick = -1000;
    private volatile int nextPredictedCleaveTick = -1;
    private volatile int lastPredictedCleaveImpactTick = -1;

    // Acid cannon (big acid ball) tracking
    @Getter
    @Setter
    private volatile boolean acidCannonIncoming = false;
    private volatile WorldPoint acidCannonSourceTile = null;
    private volatile WorldPoint acidCannonOriginTile = null;
    private WorldPoint acidCannonDodgeDestination;
    private volatile long acidCannonHandledUntil = 0;
    private volatile boolean acidCannonExpected;
    private volatile boolean acidCannonPrimed;
    private volatile boolean acidCannonPredictionActive;
    private volatile int predictedAcidCannonTick = -1;
    private int acidCannonPredictionHoldUntilTick = -1;
    private boolean acidCannonRunWasEnabled;
    // Cleave AoE tracking — the 3 danger tiles from the most recent cleave
    private volatile Set<WorldPoint> cleaveDangerTiles = new HashSet<>();
    private volatile WorldPoint cleaveOriginTile;
    private WorldPoint cleaveDodgeDestination;
    // Normalized attack direction (Araxxor→Player) at the moment the cleave fired
    private volatile int cleaveAttackDirX = 0;
    private volatile int cleaveAttackDirY = 0;

    // Arena center — set when we first find Araxxor, used to bias all movement toward center
    private volatile WorldPoint arenaCenter = null;
    private WorldPoint resolvedStartingTile;
    private boolean startingTileReady;
    private int lastAnchorReturnTick = -1000;
    // Approximate arena radius (Araxxor lair is roughly 14-16 tiles across)
    private static final int ARENA_RADIUS = 7;
    // Minimum distance from walls we want to maintain
    private static final int WALL_BUFFER = 2;

    private AraxxorConfig config;

    enum EncounterPhase { WAITING, ACTIVE, POST_FIGHT, ERROR }
    private volatile boolean postFight;
    private volatile boolean deathProcessed;
    private boolean bossAvailable;
    private volatile Rs2NpcModel loopBoss;
    private WorldPoint movementDestination;
    private WorldPoint movementOrigin;
    private long movementIssuedAt;
    private long movementDeadline;
    private WorldPoint movementReassertDestination;
    private long movementReassertAt;
    private volatile WorldPoint reservedMovementTile;
    private volatile int reservedMovementUntilTick;
    private int routineMovementBlockedUntilTick;
    private volatile int nextAttackReadyTick;
    private final Map<WorldPoint, Long> blockedMovementTiles = new ConcurrentHashMap<>();
    private boolean escapingAcid;
    private WorldPoint acidicDeathLocation;
    private boolean acidicKillPending;
    private long enrageAttackResumeAt;
    private long corpseRetryAt;
    private long entryRetryAt;
    private long lootQuietSince;
    private long lootCompletionNotBefore;
    private boolean lootCollectionComplete = true;
    private int pendingLootId = -1;
    private WorldPoint pendingLootTile;
    private int pendingLootQuantity;
    private long lootRetryAt;
    private volatile int diagnosticTick;
    private volatile PlayerSnapshot tickPlayer;
    private volatile int tickHitpoints;
    private volatile int tickMaxHitpoints = 1;
    private volatile int tickPrayer;
    private volatile int tickMaxPrayer = 1;
    private volatile int tickPrayerLevel;
    private volatile int tickDefenceLevel;
    private volatile int tickSpecEnergy;
    private volatile int tickWeaponAttackSpeed = 4;
    private long nextCannonFallbackPollAt;
    private long nextPotionCheckAt;
    private long nextThrallCheckAt;
    private long nextPrayerPotionCheckAt;
    private int nextEatTick;
    private long nextEquipmentActionAt;
    private int lastPrayerManageTick = -1;
    private volatile boolean prayerDirty = true;
    private enum SpecPhase { IDLE, EQUIPPING, OPENING_TAB, ATTACKING, WAITING_HIT }
    private SpecPhase specPhase = SpecPhase.IDLE;
    private int specEnergyBeforeHit;
    private long specPhaseDeadline;
    private String[] prayerPotionVariants = new String[0];
    private volatile String lastDiagnosticSnapshot = "";
    private volatile String lastDiagnosticEvent = "none";
    private volatile int lastProjectileTraceTick = -1;
    private volatile int lastProjectileTraceId = -1;
    private volatile long lastGameTickAt;
    private volatile boolean watchdogArmed;
    private ScheduledExecutorService watchdogExecutor;
    private ScheduledFuture<?> watchdogFuture;
    private long fightStartedAt;
    private int fightDamageTaken;
    private int fightFoodConsumed;
    private int fightMovementCommands;
    private int fightAttacks;
    private int fightAcidExposureTicks;
    private int fightPrayerMissTicks;
    private int fightAttackReadyMovementTicks;
    private int lastMetricsTick = -1;
    private int lastMovementLossTick = -1;
    @Getter
    private volatile String lastKillSummary = "No completed kill";

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
        PlayerSnapshot snapshot = tickPlayer;
        if (snapshot != null) return snapshot;
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
        long now = System.currentTimeMillis();
        PlayerSnapshot player = readPlayer();
        WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
        if (isOnAcidPool(movementDestination)) {
            traceEvent("MOVE_RESERVATION_INVALIDATED", "acid=" + movementDestination);
            clearMovementState();
            routineMovementBlockedUntilTick = diagnosticTick + 2;
            return false;
        }
        boolean reachedDestination = playerLocation != null && movementDestination.equals(playerLocation);
        boolean movedAdjacent = playerLocation != null
                && movementOrigin != null
                && !movementOrigin.equals(playerLocation)
                && movementDestination.distanceTo(playerLocation) <= 1;
        if (reachedDestination || movedAdjacent) {
            clearMovementState();
            routineMovementBlockedUntilTick = diagnosticTick + 2;
            escapingAcid = false;
            return false;
        }
        // A local walk should begin by the next game tick. If it has not, the
        // destination is usually obstructed or the click was swallowed. Replan
        // quickly instead of freezing all combat actions for several seconds.
        boolean awaitingReassert = movementReassertDestination != null;
        boolean neverStarted = playerLocation != null
                && movementOrigin != null
                && movementOrigin.equals(playerLocation)
                && now - movementIssuedAt >= 900;
        if (!awaitingReassert && (neverStarted || now >= movementDeadline)) {
            // A blocked/occupied destination is recoverable. Clear it so the next
            // loop recomputes the mechanic and chooses a fresh safe tile. Return
            // true for this iteration so a timed-out dodge never permits an attack.
            WorldPoint failedDestination = movementDestination;
            blockedMovementTiles.put(failedDestination, now + 3000);
            clearMovementState();
            routineMovementBlockedUntilTick = diagnosticTick + 2;
            escapingAcid = false;
            status = "Movement not observed — choosing another tile";
            traceEvent("MOVE_TIMEOUT", "destination=" + failedDestination
                    + " neverStarted=" + neverStarted);
            return true;
        }
        return true;
    }

    private void clearMovementState() {
        movementDestination = null;
        movementOrigin = null;
        movementIssuedAt = 0;
        movementDeadline = 0;
        movementReassertDestination = null;
        movementReassertAt = 0;
        reservedMovementTile = null;
        reservedMovementUntilTick = 0;
    }

    private boolean routineMovementAllowed() {
        return diagnosticTick >= routineMovementBlockedUntilTick && movementDestination == null;
    }

    private void moveTo(WorldPoint destination) {
        moveTo(destination, true);
    }

    private void moveTo(WorldPoint destination, boolean allowGenericReplan) {
        moveTo(destination, allowGenericReplan, false);
    }

    private void moveTo(WorldPoint destination, boolean allowGenericReplan, boolean forceWalk) {
        if (movementPending()) return;
        if (forceWalk && Rs2Player.isRunEnabled()) {
            Rs2Player.toggleRunEnergy(false);
            traceEvent("WALK_MODE", "disabling run for forced-walk mechanic");
            return;
        }
        PlayerSnapshot player = readPlayer();
        if (player == null || destination == null) {
            traceEvent("MOVE_REJECTED", "missing player or destination reason=" + status);
            return;
        }
        // Keep the origin immutable for this movement transaction. Event
        // callbacks (notably death/reset and late acid despawns) may clear the
        // published movement state while client-thread validation is in flight.
        WorldPoint origin = player.getWorldLocation();

        // Individual mechanic solvers must never be able to send us to known
        // acid or collapse an emergency movement into a no-op on an acid tile.
        if (isOnAcidPool(destination)) {
            WorldPoint replacement = allowGenericReplan ? findEmergencySafeTile(origin) : null;
            traceEvent("MOVE_REPLAN", "unsafe=" + destination + " replacement=" + replacement
                    + " reason=" + status);
            destination = replacement;
        }
        if (destination == null || destination.equals(origin)) {
            traceEvent("MOVE_REJECTED", "from=" + origin + " to=" + destination
                    + " reason=" + status);
            return;
        }

        if (!getTraversableCandidates(origin, new WorldPoint[]{destination})[0]) {
            WorldPoint replacement = allowGenericReplan ? findEmergencySafeTile(origin) : null;
            traceEvent("MOVE_REPLAN", "untraversable=" + destination + " replacement=" + replacement
                    + " reason=" + status);
            destination = replacement;
            if (destination == null) {
                return;
            }
        }

        WorldPoint resolvedDestination = destination;
        LocalPoint local = readClient(() -> LocalPoint.fromWorld(Microbot.getClient(), resolvedDestination));
        if (local == null) {
            traceEvent("MOVE_REJECTED", "destination not in scene: " + destination + " reason=" + status);
            return;
        }
        // Ensure the forced WALK entry cannot inherit a selected spell/item.
        // walkFastLocal invokes MenuAction.WALK ("Walk here") directly, so an
        // NPC occupying the same canvas area cannot turn this into Attack.
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().isWidgetSelected()) {
                Microbot.getClient().setWidgetSelected(false);
            }
            return true;
        });
        movementOrigin = origin;
        movementDestination = destination;
        reservedMovementTile = destination;
        reservedMovementUntilTick = diagnosticTick + 4;
        movementReassertDestination = null;
        movementReassertAt = 0;
        movementIssuedAt = System.currentTimeMillis();
        int distance = origin.distanceTo(destination);
        movementDeadline = movementIssuedAt + Math.min(3000, Math.max(1400, 800 + distance * 300L));
        escapingAcid = false;
        traceEvent("MOVE", "from=" + origin + " to=" + destination
                + " reason=" + status + " forcedWalk=" + forceWalk
                + " acidAtDestination=" + isOnAcidPool(destination));
        fightMovementCommands++;
        Rs2Walker.walkFastLocal(local);
    }

    /**
     * Preserve ranged attack cadence during a multi-tile escape. The player must
     * first leave the origin tile; then an attack is issued and the safe walk is
     * reasserted on the following script iteration so the attack click cannot pull
     * the player back toward Araxxor.
     */
    private void maintainRangedDpsWhileMoving(PlayerSnapshot player) {
        WorldPoint destination = movementDestination;
        if (destination == null || player == null) return;

        // A click on Araxxor can cancel or bend the route selected for a cleave.
        // Enrage movement is intentionally single-purpose: finish the dodge,
        // optionally eat, and only then resume attacking.
        if (enraged || cleaveIncoming || state == AraxxorState.ENRAGED_DODGE_CLEAVE) {
            movementReassertDestination = null;
            movementReassertAt = 0;
            return;
        }

        long now = System.currentTimeMillis();
        if (movementReassertDestination != null) {
            if (now < movementReassertAt) return;
            WorldPoint reassert = movementReassertDestination;
            movementReassertDestination = null;
            movementReassertAt = 0;
            if (isOnAcidPool(reassert)) {
                reassert = findEmergencySafeTile(player.getWorldLocation());
            }
            if (reassert == null) return;
            WorldPoint resolvedReassert = reassert;
            LocalPoint local = readClient(() -> LocalPoint.fromWorld(Microbot.getClient(), resolvedReassert));
            if (local != null) {
                movementDestination = reassert;
                movementDeadline = now + Math.max(1200, player.getWorldLocation().distanceTo(reassert) * 300L + 800);
                Rs2Walker.walkFastLocal(local);
                traceEvent("MOVE_REASSERT", "to=" + reassert + " after moving attack");
            }
            return;
        }

        if (config.combatStyle() != AraxxorConfig.CombatStyle.RANGED
                || specPhase != SpecPhase.IDLE
                || diagnosticTick < nextAttackReadyTick
                || movementOrigin == null
                || movementOrigin.equals(player.getWorldLocation())
                || state == AraxxorState.KILLING_MIRRORBACK
                || state == AraxxorState.KILLING_ACIDIC
                || isOnAcidPool(player.getWorldLocation())) {
            return;
        }

        Rs2NpcModel boss = loopBoss;
        if (boss == null || isDead(boss)
                || player.getWorldLocation().distanceTo(boss.getWorldLocation()) > 8) {
            return;
        }

        if (clickAttack(boss, true)) {
            movementReassertDestination = destination;
            movementReassertAt = now + 60;
            traceEvent("MOVING_ATTACK", "boss=" + boss.getId() + " reassert=" + destination);
        }
    }

    private WorldPoint findEmergencySafeTile(WorldPoint playerLocation) {
        return findEmergencySafeTile(playerLocation, 1);
    }

    private WorldPoint findEmergencySafeTile(WorldPoint playerLocation, int minimumDistance) {
        java.util.List<WorldPoint> candidates = new ArrayList<>();
        for (int distance = minimumDistance; distance <= 6; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                candidates.add(new WorldPoint(playerLocation.getX() + dx,
                        playerLocation.getY() - distance, playerLocation.getPlane()));
                candidates.add(new WorldPoint(playerLocation.getX() + dx,
                        playerLocation.getY() + distance, playerLocation.getPlane()));
            }
            for (int dy = -distance + 1; dy < distance; dy++) {
                candidates.add(new WorldPoint(playerLocation.getX() - distance,
                        playerLocation.getY() + dy, playerLocation.getPlane()));
                candidates.add(new WorldPoint(playerLocation.getX() + distance,
                        playerLocation.getY() + dy, playerLocation.getPlane()));
            }
        }
        return pickBestImmediateTile(playerLocation, candidates.toArray(new WorldPoint[0]));
    }

    private WorldPoint pickBestImmediateTile(WorldPoint playerLocation, WorldPoint[] candidates) {
        long now = System.currentTimeMillis();
        blockedMovementTiles.entrySet().removeIf(entry -> entry.getValue() <= now);
        Set<WorldPoint> occupiedTiles = getEncounterNpcTiles();
        // Acid/path scoring is ordinary background-thread work. Rank candidates
        // before touching the collision map so only a small, bounded shortlist is
        // ever evaluated on RuneLite's latency-sensitive client thread.
        java.util.List<WorldPoint> shortlist = new ArrayList<>();
        for (WorldPoint tile : candidates) {
            if (tile == null || tile.equals(playerLocation) || tile.getPlane() != playerLocation.getPlane()
                    || isOnAcidPool(tile) || cleaveDangerTiles.contains(tile)
                    || occupiedTiles.contains(tile) || blockedMovementTiles.containsKey(tile)) {
                continue;
            }
            shortlist.add(tile);
        }
        shortlist.sort(java.util.Comparator.comparingInt(tile ->
                scoreTile(tile, playerLocation)
                        + countAcidInArea(tile, 1) * 250
                        + playerLocation.distanceTo(tile) * 20));
        if (shortlist.size() > MAX_TRAVERSABILITY_CANDIDATES) {
            shortlist = new ArrayList<>(shortlist.subList(0, MAX_TRAVERSABILITY_CANDIDATES));
        }
        WorldPoint[] rankedCandidates = shortlist.toArray(new WorldPoint[0]);
        boolean[] traversable = getTraversableCandidates(playerLocation, rankedCandidates);
        WorldPoint best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < rankedCandidates.length; i++) {
            WorldPoint tile = rankedCandidates[i];
            if (!traversable[i]) continue;
            int score = scoreTile(tile, playerLocation)
                    + countAcidInArea(tile, 1) * 250
                    + playerLocation.distanceTo(tile) * 20;
            if (score < bestScore) {
                best = tile;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean[] getTraversableCandidates(WorldPoint start, WorldPoint[] candidates) {
        return readClient(() -> {
            boolean[] traversable = new boolean[candidates.length];
            var worldView = Microbot.getClient().findWorldViewFromWorldPoint(start);
            if (worldView == null || LocalPoint.fromWorld(worldView, start) == null) return traversable;

            for (int i = 0; i < candidates.length; i++) {
                WorldPoint destination = candidates[i];
                if (destination == null || destination.getPlane() != start.getPlane()
                        || LocalPoint.fromWorld(worldView, destination) == null) {
                    continue;
                }

                int x = start.getX();
                int y = start.getY();
                boolean valid = true;
                int steps = 0;
                while (x != destination.getX() || y != destination.getY()) {
                    if (++steps > MAX_TRAVERSABILITY_STEPS) {
                        valid = false;
                        break;
                    }
                    int dx = Integer.signum(destination.getX() - x);
                    int dy = Integer.signum(destination.getY() - y);
                    WorldArea stepArea = new WorldArea(x, y, 1, 1, start.getPlane());
                    if (!stepArea.canTravelInDirection(worldView, dx, dy)) {
                        valid = false;
                        break;
                    }
                    x += dx;
                    y += dy;
                }
                traversable[i] = valid;
            }
            return traversable;
        });
    }

    private Set<WorldPoint> getEncounterNpcTiles() {
        Set<WorldPoint> occupied = new HashSet<>();
        List<Rs2NpcModel> encounterNpcs = Microbot.getRs2NpcCache().query()
                .withIds(ARAXXOR_ID, RUPTURA_ID, MIRRORBACK_ID, ACIDIC_ID)
                .toList();
        for (Rs2NpcModel npc : encounterNpcs) {
            if (npc == null || npc.getNpc() == null) continue;
            List<WorldPoint> areaTiles = readClient(() -> npc.getNpc().getWorldArea() == null
                    ? java.util.Collections.emptyList()
                    : npc.getNpc().getWorldArea().toWorldPointList());
            if (areaTiles != null) occupied.addAll(areaTiles);
        }
        return occupied;
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
        lootCollectionComplete = true;
        lootCompletionNotBefore = 0;
        state = AraxxorState.IDLE;
        status = "Waiting for Araxxor";
        killCount = 0;
        enraged = false;
        specHitsCompleted = 0;
        araxxorAttackCount = 0;
        acidPools.clear();
        seedAcidPools();
        prayerPotionVariants = Rs2Potion.getPrayerPotionsVariants().toArray(new String[0]);
        Microbot.enableAutoRunOn = false;
        lastGameTickAt = System.currentTimeMillis();
        watchdogExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AraxxorWatchdog");
            thread.setDaemon(true);
            return thread;
        });
        watchdogFuture = watchdogExecutor.scheduleAtFixedRate(() -> {
            long staleFor = System.currentTimeMillis() - lastGameTickAt;
            if (isRunning() && watchdogArmed && staleFor > 6_000) {
                status = "Client tick stalled — script worker cancelled";
                state = AraxxorState.ERROR;
                if (mainScheduledFuture != null) mainScheduledFuture.cancel(true);
                log.warn("Araxxor watchdog cancelled worker after {} ms without a game tick", staleFor);
            }
        }, 2, 2, TimeUnit.SECONDS);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    watchdogArmed = false;
                    status = "Waiting for login";
                    return;
                }
                watchdogArmed = true;
                Rs2NpcModel observedBoss = getNpc(ARAXXOR_ID);
                loopBoss = observedBoss;
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
        }, 0, 75, TimeUnit.MILLISECONDS);  // single-writer loop; events publish urgent flags

        return true;
    }

    /**
     * Called from plugin when Araxxor's animation changes — used to detect attacks & enrage.
     */
    public void onAraxxorAnimation(int animationId) {
        traceEvent("BOSS_ANIMATION", "id=" + animationId);
        boolean attackStyleChanged = false;
        if (animationId == ANIM_MELEE || animationId == ANIM_ENRAGE_MELEE) {
            incomingAttackStyle = IncomingAttackStyle.MELEE;
            attackStyleChanged = true;
        } else if (animationId == ANIM_RANGED) {
            incomingAttackStyle = IncomingAttackStyle.RANGED;
            attackStyleChanged = true;
            // Recorded fights show Acid Ball using the ordinary ranged-looking
            // animation (11476); the nominal 11493 animation is not emitted.
            // Once the acidic special is primed, this is the earliest reliable
            // launch signal and precedes damage by roughly three game ticks.
            if (acidCannonPrimed && System.currentTimeMillis() >= acidCannonHandledUntil) {
                acidCannonPrimed = false;
                armAcidCannon(false, "primed-ranged-animation");
            }
        } else if (animationId == ANIM_MAGIC) {
            incomingAttackStyle = IncomingAttackStyle.MAGIC;
            attackStyleChanged = true;
        }
        if (attackStyleChanged) prayerDirty = true;
        if (animationId == ANIM_ENRAGE_TRANSITION) {
            enraged = true;
            log("Araxxor entering enrage phase!");
            return;
        }

        // Count standard attacks for egg hatch tracking
        if (animationId == ANIM_MELEE || animationId == ANIM_RANGED || animationId == ANIM_MAGIC) {
            araxxorAttackCount++;
            if (acidCannonExpected && araxxorAttackCount % 6 == 0) {
                // The special follows the sixth standard attack, but its delay is
                // not fixed. Wait for the subsequent 11476 launch animation rather
                // than guessing +4 ticks and completing the dodge too early.
                acidCannonPrimed = true;
                acidCannonExpected = false;
                traceEvent("CANNON_PREDICTED", "standardAttacks=" + araxxorAttackCount
                        + " waitingForRangedLaunch=true");
            }
        }

        // Detect enrage cleave
        if (animationId == ANIM_ENRAGE_MELEE) {
            onCleaveDetected();
        }

        // Detect acid special attacks
        if (animationId == ANIM_ACID_LEAK) {
            if (!acidDripActive) acidDripRunStateCaptured = false;
            acidDripActive = true;
            acidDripStartTick = diagnosticTick;
            acidDripPhase = 0; // start with step-under
            acidDripCornerIndex = -1;
            acidDripCycles = 0;
            log("Acid drip detected — step under boss!");
        }

        if (animationId == ANIM_ACID_SPRAY) {
            log("Acid splatter detected — move away!");
        }

        if (animationId == ANIM_ACID_CANNON) {
            if (System.currentTimeMillis() < acidCannonHandledUntil) return;
            log("Acid cannon launched — dodge NOW!");
            armAcidCannon(false, "boss-animation");
        }

        // Detect death animation — reliable reset even if onNpcDespawned misses isDead()
        if (animationId == ANIM_DEATH) {
            log("Araxxor death animation detected — resetting state");
            onAraxxorDeath();
        }
    }

    public void onAraxxorProjectile(int projectileId) {
        if (projectileId == RANGED_PROJ) {
            incomingAttackStyle = IncomingAttackStyle.RANGED;
            prayerDirty = true;
        } else if (projectileId == MAGIC_PROJ) {
            incomingAttackStyle = IncomingAttackStyle.MAGIC;
            prayerDirty = true;
        }
        if (projectileId == RANGED_PROJ || projectileId == MAGIC_PROJ
                || projectileId == ACID_POOL_PROJ || projectileId == ACID_POOL_SPLASH) {
            if (diagnosticTick != lastProjectileTraceTick || projectileId != lastProjectileTraceId) {
                lastProjectileTraceTick = diagnosticTick;
                lastProjectileTraceId = projectileId;
                traceEvent("PROJECTILE", "id=" + projectileId + " inferredStyle=" + incomingAttackStyle);
            }
        }
    }

    public void onAcidDripLanded(int tick) {
        if (!acidDripActive) {
            acidDripRunStateCaptured = false;
            acidDripPhase = 0;
            acidDripCornerIndex = -1;
            acidDripCycles = 0;
        }
        acidDripActive = true;
        acidDripStartTick = tick;
        traceEvent("ACID_DRIP_LANDED", "durationThroughTick=" + (tick + 6));
    }

    public void onAcidTileSpawned(WorldPoint tile) {
        if (tile == null) return;
        acidPools.add(tile);
        if (tile.equals(reservedMovementTile) && diagnosticTick <= reservedMovementUntilTick) {
            // Publish invalidation only; the script thread owns route cleanup.
            movementDeadline = 0;
            traceEvent("MOVE_RESERVATION_POISONED", "tile=" + tile);
        }
    }

    public void onDiagnosticTick(int tick, WorldPoint playerTile, int hp, int maxHp, String target,
                                 int weaponId, int ammoId, String activePrayers) {
        if (!diagnosticsEnabled()) return;
        String snapshot = "phase=" + getCurrentState() + " state=" + state + " status=" + status
                + " hp=" + hp + "/" + maxHp + " tile=" + playerTile + " target=" + target
                + " expectedPrayer=" + incomingAttackStyle + " activePrayers=" + activePrayers
                + " weaponId=" + weaponId + " ammoId=" + ammoId + " enraged=" + enraged
                + " acidTiles=" + acidPools.size() + " move=" + movementDestination;
        if (!snapshot.equals(lastDiagnosticSnapshot) || tick % 5 == 0) {
            log.info("ARAXXOR_TRACE tick={} SNAPSHOT {}", tick, snapshot);
            lastDiagnosticSnapshot = snapshot;
        }
    }

    public void onGameTickSnapshot(int tick, WorldPoint playerTile, int hp, int maxHp,
                                   int prayer, int maxPrayer, int prayerLevel, int defenceLevel,
                                   int specEnergy, int weaponAttackSpeed,
                                   boolean protectMelee, boolean protectRange, boolean protectMagic) {
        lastGameTickAt = System.currentTimeMillis();
        watchdogArmed = true;
        diagnosticTick = tick;
        tickPlayer = playerTile == null ? null : new PlayerSnapshot(playerTile);
        tickHitpoints = hp;
        tickMaxHitpoints = Math.max(1, maxHp);
        tickPrayer = prayer;
        tickMaxPrayer = Math.max(1, maxPrayer);
        tickPrayerLevel = prayerLevel;
        tickDefenceLevel = defenceLevel;
        tickSpecEnergy = specEnergy;
        tickWeaponAttackSpeed = Math.max(1, weaponAttackSpeed);
        if (bossAvailable && fightStartedAt != 0 && tick != lastMetricsTick) {
            lastMetricsTick = tick;
            if (playerTile != null && isOnAcidPool(playerTile)) fightAcidExposureTicks++;
            boolean correctProtection = incomingAttackStyle == IncomingAttackStyle.MELEE && protectMelee
                    || incomingAttackStyle == IncomingAttackStyle.RANGED && protectRange
                    || incomingAttackStyle == IncomingAttackStyle.MAGIC && protectMagic;
            if (!correctProtection) fightPrayerMissTicks++;
        }
    }

    public void onPlayerAttackAnimation(int attackTick, int weaponAttackSpeed) {
        fightAttacks++;
        nextAttackReadyTick = Math.max(nextAttackReadyTick,
                attackTick + Math.max(1, weaponAttackSpeed));
        traceEvent("PLAYER_ATTACK", "speed=" + weaponAttackSpeed + " readyTick=" + nextAttackReadyTick);
    }

    public boolean isFightDiagnosticsEnabled() {
        return diagnosticsEnabled();
    }

    public void onPlayerDamage(int amount) {
        if (amount > 0) fightDamageTaken += amount;
        traceEvent("PLAYER_DAMAGE", "amount=" + amount + " preceding=" + lastDiagnosticEvent);
    }

    public void traceEvent(String event, String detail) {
        if (!diagnosticsEnabled()) return;
        lastDiagnosticEvent = event + " " + detail;
        log.info("ARAXXOR_TRACE tick={} {} {}", diagnosticTick, event, detail);
    }

    private boolean diagnosticsEnabled() {
        return config != null && config.recordFightTimeline();
    }

    /**
     * Called from plugin when acid cannon projectile NPC spawns — backup detection.
     * Ensures we flag the dodge even if animation detection was missed.
     */
    public void onAcidCannonDetected() {
        if ((!acidCannonIncoming || acidCannonPredictionActive)
                && System.currentTimeMillis() >= acidCannonHandledUntil) {
            log("Acid cannon detected via projectile NPC spawn!");
            armAcidCannon(false, "projectile-npc");
        }
    }

    public void onAraxyteSpawned(int npcId) {
        if (npcId == ACIDIC_ID) {
            acidCannonExpected = true;
            traceEvent("CANNON_PATTERN", "acidic araxyte identifies next special");
        }
    }

    /** Client-thread XP events only publish the switch request; inventory work remains on the script thread. */
    public void onCombatExperienceDrop(Skill skill) {
        if (!ballistaHitPending || diagnosticTick > ballistaHitDeadlineTick) return;
        if (skill != Skill.RANGED && skill != Skill.HITPOINTS) return;
        ballistaHitPending = false;
        primaryWeaponRestoreRequested = true;
        traceEvent("BALLISTA_XP_DROP", "skill=" + skill + " restorePrimary=true");
    }

    private void armAcidCannon(boolean predicted, String source) {
        PlayerSnapshot player = readPlayer();
        if (!acidCannonIncoming) {
            acidCannonRunWasEnabled = Rs2Player.isRunEnabled();
        }
        acidCannonIncoming = true;
        acidCannonPredictionActive = predicted;
        // Keep running beyond the first lateral clearance. For an exact trigger
        // three ticks covers the ball's passage; prediction gets one extra tick
        // to span uncertainty in the special-attack cadence.
        acidCannonPredictionHoldUntilTick = diagnosticTick + (predicted ? 4 : 3);
        acidCannonDodgeDestination = null;
        acidCannonOriginTile = player == null ? null : player.getWorldLocation();
        Rs2NpcModel boss = loopBoss;
        acidCannonSourceTile = boss == null ? null : boss.getWorldLocation();
        traceEvent(predicted ? "CANNON_PRE_DODGE" : "CANNON_ARMED",
                "source=" + source + " origin=" + acidCannonOriginTile
                        + " boss=" + acidCannonSourceTile);
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
        long now = System.currentTimeMillis();
        lootQuietSince = now;
        // A death/despawn event can precede the corpse drop reaching the ground
        // item cache. No trip-ending teleport is permitted until loot handling
        // explicitly confirms a sustained quiet period after corpse processing.
        lootCollectionComplete = false;
        lootCompletionNotBefore = now + 10_000;
        killCount++;
        long durationSeconds = fightStartedAt == 0 ? 0 : Math.max(0, (now - fightStartedAt) / 1000);
        lastKillSummary = "Kill " + killCount + ": " + durationSeconds + "s, dmg " + fightDamageTaken
                + ", food " + fightFoodConsumed + ", moves " + fightMovementCommands
                + ", attacks " + fightAttacks + ", acid " + fightAcidExposureTicks
                + "t, prayer misses " + fightPrayerMissTicks
                + ", attack-ready movement " + fightAttackReadyMovementTicks + "t";
        log.info("Araxxor metrics: {}", lastKillSummary);
        resetFightState();
        state = AraxxorState.LOOTING;
        log("Araxxor killed! Total: " + killCount);
    }

    @Override
    public void shutdown() {
        // Cancel/interrupt the worker first. The stop control must never wait for
        // another combat iteration before the script becomes inactive.
        super.shutdown();
        if (watchdogFuture != null) watchdogFuture.cancel(true);
        if (watchdogExecutor != null) watchdogExecutor.shutdownNow();
        watchdogFuture = null;
        watchdogExecutor = null;
        watchdogArmed = false;
        try {
            togglePrayers(false);
        } catch (Exception ignored) {
        }
        state = AraxxorState.IDLE;
        status = "Stopped";
        acidPools.clear();
        blockedMovementTiles.clear();
        tickPlayer = null;
        loopBoss = null;
        Rs2Walker.setTarget(null, "araxxor:shutdown");
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
        return sleepUntil(() -> (enraged && cleaveIncoming) || acidCannonIncoming,
                () -> { }, 600, 25);
    }

    // ── Main Loop ───────────────────────────────────────

    private void loop() {

        PlayerSnapshot player = readPlayer();
        if (player == null) return;
        long now = System.currentTimeMillis();
        Rs2NpcModel araxxor = loopBoss;

        if (!acidCannonIncoming && predictedAcidCannonTick >= 0
                && diagnosticTick >= predictedAcidCannonTick) {
            predictedAcidCannonTick = -1;
            armAcidCannon(true, "six-attack-pattern");
        }

        // Live traces show enrage cleaves on a strict four-tick cadence. Prepare
        // run one tick before the warning window, then pre-arm movement one tick
        // before impact so the server sees us off the origin tile in time.
        if (enraged && nextPredictedCleaveTick > 0
                && diagnosticTick >= nextPredictedCleaveTick - 2
                && !Rs2Player.isRunEnabled()) {
            status = "Preparing run for predicted cleave";
            Rs2Player.toggleRunEnergy(true);
            return;
        }
        if (enraged && !cleaveIncoming && nextPredictedCleaveTick > 0
                && diagnosticTick >= nextPredictedCleaveTick - 1) {
            armPredictedCleave();
        }

        // The arena becomes observable before the normal WAITING -> ACTIVE path
        // finishes. Give entry placement first ownership of the loop so food,
        // equipment, adds, and attack clicks cannot delay the initial run from
        // the doorway to the configured tile.
        if (!startingTileReady && araxxor != null && !isDead(araxxor)) {
            state = AraxxorState.ENTERING_LAIR;
            status = "Inside lair — moving to configured starting tile";
            if (config.protectFromMelee()) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            }
            if (handleStartingTile(player)) return;
        }

        // Dodge flags are raised by client-thread events. Process them before
        // eating, gear changes, minions, or any other action can consume a tick.
        if (enraged && cleaveIncoming) {
            status = "Dodging cleave!";
            if (dodgeCleave(player)) {
                cleaveIncoming = false;
                clearCleaveTiles();
            }
            return;
        }
        if (acidCannonIncoming) {
            status = "Dodging acid cannon!";
            state = AraxxorState.DODGING_ACID_BALL;
            if (dodgeAcidCannon(player)) {
                acidCannonIncoming = false;
            }
            return;
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

        // ── 2. Backup poll: detect acid cannon if the event was missed ──
        // Active poll: check if the acid cannon projectile NPC (13676) is alive in case
        // event-based detection (animation / NPC spawn) was missed entirely.
        if (!acidCannonIncoming && now >= acidCannonHandledUntil && now >= nextCannonFallbackPollAt) {
            nextCannonFallbackPollAt = now + 300;
            Rs2NpcModel cannonNpc = getNpc(ACID_CANNON_PROJ_NPC);
            if (cannonNpc != null && !isDead(cannonNpc)) {
                log("Acid cannon detected via active polling!");
                acidCannonIncoming = true;
                acidCannonDodgeDestination = null;
                acidCannonOriginTile = player.getWorldLocation();
                Rs2NpcModel boss = loopBoss;
                if (boss != null) {
                    acidCannonSourceTile = boss.getWorldLocation();
                }
            }
        }
        if (acidCannonIncoming) {
            status = "Dodging acid cannon!";
            state = AraxxorState.DODGING_ACID_BALL;
            if (dodgeAcidCannon(player)) acidCannonIncoming = false;
            return;
        }

        // Acid Drip owns movement for its full six-tick window. Generic acid
        // escape must not redirect the deliberate under-boss/corner sequence.
        if (acidDripActive) {
            long ticksSinceDrip = diagnosticTick - acidDripStartTick;
            if (ticksSinceDrip > 6) {
                finishAcidDrip();
            } else {
                status = "Acid drip — orbiting Araxxor!";
                handleAcidDrip(player);
                return;
            }
        }

        // Acid can spawn underneath us while an older walk is still pending. It
        // must supersede that route instead of waiting for its timeout.
        if (isOnAcidPool(player.getWorldLocation()) && !escapingAcid) {
            movementDestination = null;
            movementOrigin = null;
            movementIssuedAt = 0;
            movementDeadline = 0;
            movementReassertDestination = null;
            movementReassertAt = 0;
            status = "Moving off acid!";
            moveOffAcid(player);
            return;
        }

        // A confirmed Ballista hit should restore the primary loadout before
        // food, routine movement, or another target interaction. Urgent hazard
        // movement above still wins so an equipment click cannot cost a dodge.
        if (handlePrimaryWeaponRestore()) return;

        // Eat only after immediate movement hazards have been handled. Eating
        // before a ruptura flee or acid escape costs the exact tick needed to live.
        if (handleFoodSafety()) return;

        if (movementPending()) {
            if (diagnosticTick >= nextAttackReadyTick && diagnosticTick != lastMovementLossTick) {
                lastMovementLossTick = diagnosticTick;
                fightAttackReadyMovementTicks++;
            }
            maintainRangedDpsWhileMoving(player);
            return;
        }

        // Wall proximity is handled passively by pickBestTile()'s scoreTile() —
        // all movement destinations are penalised for being near walls.
        // We only force a return-to-center after explosive events (ruptura flee,
        // acid cannon dodge) that may have pushed us far out, not every loop tick.

        // ── 5. Find Araxxor ──
        // No boss found — could be dead/looting/idle
        if (araxxor == null || isDead(araxxor)) {
            handleNoBoss();
            return;
        }

        if (!isDead(araxxor)) deathProcessed = false;

        // Set arena center on first sighting of Araxxor (its spawn ≈ arena center)
        if (arenaCenter == null) {
            arenaCenter = araxxor.getWorldLocation();
            log("Arena center set to " + arenaCenter);
        }

        // The instance spawn is already inside Araxxor's melee reach. Until the
        // configured start tile is reached, entry movement owns the loop and must
        // keep Protect from Melee active; projectile inference must not switch it.
        if (!startingTileReady) {
            if (config.protectFromMelee()) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            }
            if (handleStartingTile(player)) return;
        }
        if (fightStartedAt == 0) fightStartedAt = System.currentTimeMillis();

        // Protection must be active before gear, potion, minion, or attack actions.
        managePrayers(player, araxxor);

        // ── 6. Handle minions (priority order: ruptura > mirrorback > acidic) ──
        if (handleMinions(player, araxxor, rupturaUrgent)) {
            return; // Minion handling took priority
        }

        // Finish the spec phase before restoring the main weapon. Previously the
        // normal switch ran between EQUIPPING and OPENING_TAB, undoing the spec equip.
        if (config.useSpecialAttack() && specHitsCompleted < config.specCount()) {
            if (performSpecialAttack(player, araxxor)) return;
        }

        switchToMainWeapon();

        // ── 7. Potions ──
        if (now >= nextPotionCheckAt) {
            nextPotionCheckAt = now + 600;
            drinkPotions();
        }

        // ── 8. Prayer management ──
        managePrayers(player, araxxor);

        // ── 8.5. Thrall summoning ──
        if (config.useThralls() && now >= nextThrallCheckAt) {
            nextThrallCheckAt = now + 1200;
            summonThrall();
        }

        if (returnToStartingArea(player, araxxor)) return;

        // ── 9. Enrage phase: step-under technique ──
        if (enraged) {
            status = config.combatStyle() == AraxxorConfig.CombatStyle.RANGED
                    ? "Enraged — ranged kiting"
                    : "Enraged — melee step-under fighting";
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
        if (!isTarget(araxxor)) {
            attackNpc(araxxor);
            tickSleep();
        }

        // Drink prayer potion if needed
        drinkPrayerPotion();
    }

    private boolean handleStartingTile(PlayerSnapshot player) {
        String configured = config.startingTile().trim();
        if (configured.isEmpty()) {
            startingTileReady = true;
            return false;
        }

        if (resolvedStartingTile == null) {
            String[] parts = configured.split(",");
            if (parts.length != 3) {
                traceEvent("START_TILE_INVALID", "value=" + configured);
                startingTileReady = true;
                return false;
            }
            try {
                WorldPoint template = new WorldPoint(Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
                java.util.Collection<WorldPoint> occurrences = readClient(() ->
                        WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), template));
                WorldPoint playerLocation = player.getWorldLocation();
                for (WorldPoint occurrence : occurrences) {
                    if (resolvedStartingTile == null
                            || occurrence.distanceTo(playerLocation) < resolvedStartingTile.distanceTo(playerLocation)) {
                        resolvedStartingTile = occurrence;
                    }
                }
            } catch (NumberFormatException ex) {
                traceEvent("START_TILE_INVALID", "value=" + configured);
                startingTileReady = true;
                return false;
            }
        }

        if (resolvedStartingTile == null) {
            traceEvent("START_TILE_MISSING", "template=" + configured);
            startingTileReady = true;
            return false;
        }
        if (player.getWorldLocation().equals(resolvedStartingTile)) {
            startingTileReady = true;
            status = "Starting tile reached";
            traceEvent("START_TILE_READY", "tile=" + resolvedStartingTile);
            return false;
        }

        status = "Moving to configured starting tile";
        moveTo(resolvedStartingTile);
        return true;
    }

    private boolean returnToStartingArea(PlayerSnapshot player, Rs2NpcModel araxxor) {
        if (resolvedStartingTile == null || !startingTileReady
                || acidDripActive || acidCannonIncoming || cleaveIncoming
                || specPhase != SpecPhase.IDLE
                || diagnosticTick - lastAnchorReturnTick < 6
                || !routineMovementAllowed()) {
            return false;
        }

        WorldPoint playerLocation = player.getWorldLocation();
        int radius = config.startingTileRadius();
        if (playerLocation.distanceTo(resolvedStartingTile) <= radius) return false;

        // Reposition inside the weapon cooldown so anchoring does not replace an
        // available attack. The normal attack branch runs first when ready.
        if (diagnosticTick >= nextAttackReadyTick) return false;

        WorldArea bossArea = readClient(araxxor::getWorldArea);
        if (bossArea == null) return false;
        int maxBossDistance = config.combatStyle() == AraxxorConfig.CombatStyle.RANGED ? 10 : 2;
        int minBossDistance = config.combatStyle() == AraxxorConfig.CombatStyle.RANGED ? 2 : 0;

        // Correct drift with a short local step instead of clicking 8-12 tiles
        // across the arena toward the anchor. This is less conspicuous, easier to
        // interrupt for mechanics, and cannot drag the player across new hazards.
        int towardX = Integer.signum(resolvedStartingTile.getX() - playerLocation.getX());
        int towardY = Integer.signum(resolvedStartingTile.getY() - playerLocation.getY());
        int[][] offsets = {
                {towardX, towardY}, {towardX * 2, towardY * 2},
                {towardX * 2 + towardY, towardY * 2 - towardX},
                {towardX * 2 - towardY, towardY * 2 + towardX},
                {towardX + towardY, towardY - towardX},
                {towardX - towardY, towardY + towardX}
        };
        java.util.List<WorldPoint> candidates = new ArrayList<>();
        int currentAnchorDistance = playerLocation.distanceTo(resolvedStartingTile);
        for (int[] offset : offsets) {
            if (offset[0] == 0 && offset[1] == 0) continue;
            WorldPoint tile = new WorldPoint(playerLocation.getX() + offset[0],
                    playerLocation.getY() + offset[1], playerLocation.getPlane());
            int bossDistance = bossArea.distanceTo(tile);
            if (tile.distanceTo(resolvedStartingTile) < currentAnchorDistance
                    && bossDistance >= minBossDistance && bossDistance <= maxBossDistance
                    && !isOnAcidPool(tile)
                    && countAcidInArea(tile, 1) == 0
                    && countAcidOnPath(playerLocation, tile) == 0) {
                candidates.add(tile);
            }
        }
        if (candidates.isEmpty()) return false;

        WorldPoint best = pickBestImmediateTile(playerLocation, candidates.toArray(new WorldPoint[0]));
        if (best == null) return false;
        status = "Returning to starting area";
        moveTo(best);
        if (movementDestination != null) {
            lastAnchorReturnTick = diagnosticTick;
            traceEvent("ANCHOR_RETURN", "from=" + playerLocation + " to=" + best
                    + " anchor=" + resolvedStartingTile + " radius=" + radius);
            return true;
        }
        return false;
    }

    private boolean handleFoodSafety() {
        int hpPercent = getHpPercent();
        // Persisted profiles from early builds commonly contain 72-90%. Cap the
        // routine threshold so those stale values cannot consume a trip's food.
        int eatThreshold = Math.min(config.eatAtHpPercent(), 55);
        if (config.adaptiveEating()) {
            if (enraged) eatThreshold = Math.max(eatThreshold, 60);
            if (acidDripActive || state == AraxxorState.DODGING_ACID_BALL
                    || state == AraxxorState.DODGING_ACID_SPLATTER) {
                eatThreshold = Math.max(eatThreshold, 65);
            }
        }
        eatThreshold = Math.min(90, eatThreshold);

        if (hpPercent > Math.max(eatThreshold, config.emergencyTeleportHp())) return false;
        List<Rs2ItemModel> foods = Rs2Inventory.getInventoryFood();
        boolean hasFood = !foods.isEmpty();
        if (!hasFood && hpPercent <= config.emergencyTeleportHp()) {
            status = "Emergency teleport!";
            emergencyTeleport();
            return true;
        }
        if (!hasFood || hpPercent > eatThreshold || diagnosticTick < nextEatTick) return false;

        if (eatEfficiently(foods, eatThreshold)) {
            fightFoodConsumed++;
            status = "Eating safely (" + hpPercent + "% ≤ " + eatThreshold + "%)";
            nextEatTick = diagnosticTick + 3;
            nextAttackReadyTick = Math.max(nextAttackReadyTick, nextEatTick);
            traceEvent("FOOD", "hp=" + tickHitpoints + "/" + tickMaxHitpoints
                    + " threshold=" + eatThreshold + " nextEatTick=" + nextEatTick);
            return true;
        }
        return false;
    }

    private boolean eatEfficiently(List<Rs2ItemModel> foods, int thresholdPercent) {
        int targetHp = (tickMaxHitpoints * thresholdPercent + 99) / 100;
        int healingNeeded = Math.max(1, targetHp - tickHitpoints);
        Rs2ItemModel smallestSufficient = null;
        int smallestHeal = Integer.MAX_VALUE;
        Rs2ItemModel strongest = null;
        int strongestHeal = 0;

        for (Rs2ItemModel item : foods) {
            int heal = 0;
            for (Rs2Food food : Rs2Food.values()) {
                if (food.getId() == item.getId()) {
                    heal = food.getHeal();
                    break;
                }
            }
            if (heal > strongestHeal) {
                strongest = item;
                strongestHeal = heal;
            }
            if (heal >= healingNeeded && heal < smallestHeal) {
                smallestSufficient = item;
                smallestHeal = heal;
            }
        }

        Rs2ItemModel selected = smallestSufficient != null ? smallestSufficient : strongest;
        if (selected == null) return Rs2Player.eatAt(thresholdPercent);
        traceEvent("FOOD_SELECT", "item=" + selected.getName() + " heal="
                + (smallestSufficient != null ? smallestHeal : strongestHeal)
                + " needed=" + healingNeeded);
        return Rs2Inventory.interact(selected, "eat");
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
            if (dodgeCleave(player)) {
                cleaveIncoming = false;
                clearCleaveTiles();
            }
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
    private boolean handleMinions(PlayerSnapshot player, Rs2NpcModel araxxor, Rs2NpcModel ruptura) {
        // Cleave & acid cannon checks before starting any minion work
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

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
            if (acidicKillPending && acidicDeathLocation != null) {
                acidicKillPending = false;
                status = "Escaping acidic death splatter";
                moveAwayFrom(player.getWorldLocation(), acidicDeathLocation, 4);
                return true;
            }
        }

        // ── Ruptura (red) — movement/luring only; NEVER attack it ──
        // Immediate close-range fleeing is already handled before every other
        // mechanic in loop(). At safe distance, attackable adds take priority.
        if (ruptura != null && !isDead(ruptura)) {
            return handleRuptura(player, araxxor, ruptura);
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

        // ── Dodge enrage cleave / acid cannon even while waiting for ruptura ──
        if (checkAndDodgeCleave(player)) return true;
        if (checkAndDodgeAcidCannon(player)) return true;

        // Never lure or attack Ruptura. Hold position and preserve DPS until it
        // enters the actual emergency radius, then perform one short escape.
        status = "Ruptura approaching (" + distToRuptura + " tiles)";
        if (!isTarget(araxxor)) {
            attackNpc(araxxor);
        }

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
        // Ruptura owns movement. An old anchor/ranged/acid route must never cause
        // moveTo() to reject this emergency. Keep an existing escape only if its
        // destination still produces adequate separation from the moving minion.
        if (movementDestination != null) {
            if (movementDestination.distanceTo(rupturaLoc) >= 5 && movementPending()) return;
            clearMovementState();
        }

        java.util.List<WorldPoint> candidates = new ArrayList<>();
        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };
        for (int distance = 7; distance >= 3; distance--) {
            for (int[] direction : directions) {
                WorldPoint candidate = new WorldPoint(
                        playerLoc.getX() + direction[0] * distance,
                        playerLoc.getY() + direction[1] * distance,
                        playerLoc.getPlane());
                if (!isOnAcidPool(candidate) && countAcidOnPath(playerLoc, candidate) == 0) {
                    candidates.add(candidate);
                }
            }
        }

        // Unlike ordinary movement, distance from Ruptura is the dominant score.
        // This naturally turns a blocked straight retreat into a long sidestep
        // along the wall instead of selecting a nearby anchor-biased tile.
        candidates.sort((a, b) -> Integer.compare(
                rupturaEscapeScore(b, rupturaLoc), rupturaEscapeScore(a, rupturaLoc)));
        if (candidates.size() > MAX_TRAVERSABILITY_CANDIDATES) {
            candidates = new ArrayList<>(candidates.subList(0, MAX_TRAVERSABILITY_CANDIDATES));
        }
        WorldPoint[] ranked = candidates.toArray(new WorldPoint[0]);
        boolean[] traversable = getTraversableCandidates(playerLoc, ranked);
        WorldPoint best = null;
        for (int i = 0; i < ranked.length; i++) {
            if (traversable[i]) {
                best = ranked[i];
                break;
            }
        }
        if (best == null) best = findEmergencySafeTile(playerLoc, 3);
        traceEvent("RUPTURA_ESCAPE", "from=" + playerLoc + " threat=" + rupturaLoc
                + " destination=" + best + " separation="
                + (best == null ? -1 : best.distanceTo(rupturaLoc)));
        moveTo(best);
    }

    private int rupturaEscapeScore(WorldPoint candidate, WorldPoint rupturaLoc) {
        int score = candidate.distanceTo(rupturaLoc) * 1000;
        score -= countAcidInArea(candidate, 1) * 400;
        if (isTooCloseToWall(candidate)) score -= 250;
        if (arenaCenter != null) score -= candidate.distanceTo(arenaCenter) * 5;
        return score;
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

        String araxWeapon = config.araxyteSwitchWeapon().toLowerCase(Locale.ROOT);
        if (araxWeapon.contains("halberd") && maintainRangedDistance(player, mirrorback, 2)) {
            status = "Creating safe halberd reach for mirrorback";
            return true;
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

        if (attackNpc(mirrorback)) armBallistaHitSwitch(mirrorback.getId());
        tickSleep();

        return true;
    }

    /**
     * Handle acidic araxyte — ranged attack up to 7 damage.
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

        acidicDeathLocation = acidic.getWorldLocation();
        acidicKillPending = true;
        if (attackNpc(acidic)) armBallistaHitSwitch(acidic.getId());
        tickSleep();

        return true;
    }

    private boolean switchToAraxyteWeapon() {
        String araxWeapon = config.araxyteSwitchWeapon().trim();
        if (araxWeapon.isEmpty()) return true;

        if (!Rs2Equipment.isWearing(araxWeapon)) {
            status = "Equipping " + araxWeapon;
            long now = System.currentTimeMillis();
            if (now < nextEquipmentActionAt) return false;
            if (!Rs2Inventory.wield(araxWeapon)) {
                log("WARNING: " + araxWeapon + " not in inventory — cannot equip for araxyte");
                nextEquipmentActionAt = now + 1200;
                return false;
            }
            nextEquipmentActionAt = now + 600;
            prayerDirty = true;
            return false;
        }

        if (!isBallista(araxWeapon)) return true;

        String javelin = null;
        for (String candidate : BALLISTA_JAVELINS) {
            if (Rs2Inventory.hasItem(candidate)) {
                javelin = candidate;
                break;
            }
        }
        if (javelin == null) {
            if (isJavelinEquipped()) return true;
            status = "No javelins available for " + araxWeapon;
            log("WARNING: No javelins in inventory or ammo slot for " + araxWeapon);
            return false;
        }

        if (!Rs2Equipment.isWearing(javelin)) {
            status = "Equipping " + javelin;
            long now = System.currentTimeMillis();
            if (now < nextEquipmentActionAt) return false;
            if (!Rs2Inventory.wield(javelin)) {
                log("WARNING: Failed to click " + javelin + " for " + araxWeapon);
                nextEquipmentActionAt = now + 1200;
                return false;
            }
            nextEquipmentActionAt = now + 600;
            prayerDirty = true;
            return false;
        }
        return true;
    }

    private boolean isBallista(String weaponName) {
        return weaponName.toLowerCase(Locale.ROOT).contains("ballista");
    }

    private void armBallistaHitSwitch(int targetId) {
        String araxyteWeapon = config.araxyteSwitchWeapon().trim();
        if (!isBallista(araxyteWeapon) || !Rs2Equipment.isWearing(araxyteWeapon)) return;
        ballistaHitPending = true;
        primaryWeaponRestoreRequested = false;
        // Ballistae are six-tick weapons. Allow extra latency for a distant
        // projectile while preventing an unrelated later XP drop from switching.
        ballistaHitDeadlineTick = diagnosticTick + 10;
        traceEvent("BALLISTA_SHOT", "targetId=" + targetId
                + " deadlineTick=" + ballistaHitDeadlineTick);
    }

    private boolean handlePrimaryWeaponRestore() {
        if (ballistaHitPending && diagnosticTick > ballistaHitDeadlineTick) {
            ballistaHitPending = false;
            traceEvent("BALLISTA_XP_TIMEOUT", "deadlineTick=" + ballistaHitDeadlineTick);
        }
        if (!primaryWeaponRestoreRequested) return false;

        status = "XP drop confirmed — restoring primary weapon";
        switchToMainWeapon();
        boolean ranged = config.combatStyle() == AraxxorConfig.CombatStyle.RANGED;
        String weapon = ranged ? config.rangedWeapon().trim() : config.mainWeapon().trim();
        String offhand = ranged ? config.rangedAmmo().trim() : config.mainShield().trim();
        boolean weaponReady = weapon.isEmpty() || Rs2Equipment.isWearing(weapon);
        boolean offhandReady = offhand.isEmpty() || Rs2Equipment.isWearing(offhand);
        if (weaponReady && offhandReady) {
            primaryWeaponRestoreRequested = false;
            traceEvent("PRIMARY_RESTORED", "weapon=" + weapon + " offhand=" + offhand);
        }
        return true;
    }

    private boolean isJavelinEquipped() {
        return Rs2Equipment.isWearing(item -> item.getName() != null
                && item.getName().toLowerCase(Locale.ROOT).contains("javelin"));
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
        if (!routineMovementAllowed() || diagnosticTick - lastRangedMovementTick < 4) return false;

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
        boolean issued = movementDestination != null;
        if (issued) {
            lastRangedMovementTick = diagnosticTick;
            tickSleep();
        }
        return issued;
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

        // A backward cleave dodge can be undone by attacking too soon because the
        // attack click drags the player back over the newly-created acid. Keep this
        // timestamp-based and non-blocking so emergency mechanics still pre-empt it.
        if (System.currentTimeMillis() < enrageAttackResumeAt) {
            status = "Holding after cleave dodge";
            return;
        }

        // Never spend an available attack tick on optional positioning. Attack
        // first; all routine enrage movement is confined to weapon cooldown.
        if (diagnosticTick >= nextAttackReadyTick) {
            if (!isTarget(araxxor)) attackNpc(araxxor);
            tickSleep();
            return;
        }
        if (!routineMovementAllowed()) return;

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

        drinkPrayerPotion();
    }

    /**
     * Ranged enrage prioritises the primary weapon and queues each legal attack.
     * Optional positioning happens only inside the weapon cooldown and moves back
     * toward melee distance; hazard handlers above this method may still move out.
     */
    private void handleEnrageFightRanged(PlayerSnapshot player, Rs2NpcModel araxxor) {
        state = AraxxorState.FIGHTING;

        if (cleaveIncoming || diagnosticTick <= enrageRecoveryThroughTick
                || System.currentTimeMillis() < enrageAttackResumeAt) {
            status = "Cleave recovery — move/eat before attack";
            return;
        }

        String primaryWeapon = config.rangedWeapon().trim();
        if (!primaryWeapon.isEmpty() && !Rs2Equipment.isWearing(primaryWeapon)
                && Rs2Inventory.hasItem(primaryWeapon)) {
            status = "Restoring primary ranged weapon for enrage";
            switchToMainWeapon();
            return;
        }

        if (diagnosticTick >= nextAttackReadyTick) {
            boolean forceClick = config.forceEnrageAttackClicks();
            if (forceClick || !isTarget(araxxor)) {
                attackNpc(araxxor, forceClick);
            }
            tickSleep();
            return;
        }

        if (config.stayInMeleeRangeDuringEnrage() && moveIntoEnrageMeleeRange(player, araxxor)) {
            return;
        }

        if (!isTarget(araxxor)) {
            attackNpc(araxxor);
        }

        tickSleep();

        drinkPrayerPotion();
    }

    /**
     * Correct only outward drift, using a one-tile perimeter around Araxxor.
     * The regular movement validator rejects blocked destinations and acid paths.
     */
    private boolean moveIntoEnrageMeleeRange(PlayerSnapshot player, Rs2NpcModel araxxor) {
        if (!routineMovementAllowed() || diagnosticTick - lastRangedMovementTick < 2) return false;
        WorldPoint playerLoc = player.getWorldLocation();
        WorldArea bossArea = readClient(araxxor::getWorldArea);
        if (bossArea == null || bossArea.distanceTo(playerLoc) <= 1) return false;

        List<WorldPoint> candidates = new ArrayList<>();
        int minX = bossArea.getX() - 1;
        int maxX = bossArea.getX() + bossArea.getWidth();
        int minY = bossArea.getY() - 1;
        int maxY = bossArea.getY() + bossArea.getHeight();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                WorldPoint tile = new WorldPoint(x, y, playerLoc.getPlane());
                if (bossArea.distanceTo(tile) == 1
                        && !isOnAcidPool(tile)
                        && countAcidInArea(tile, 1) == 0
                        && countAcidOnPath(playerLoc, tile) == 0) {
                    candidates.add(tile);
                }
            }
        }
        if (candidates.isEmpty()) return false;

        WorldPoint best = pickBestImmediateTile(playerLoc, candidates.toArray(new WorldPoint[0]));
        if (best == null) return false;
        status = "Returning to Araxxor melee distance during cooldown";
        moveTo(best, false);
        if (movementDestination == null) return false;
        lastRangedMovementTick = diagnosticTick;
        traceEvent("ENRAGE_MELEE_RETURN", "from=" + playerLoc + " to=" + best
                + " readyTick=" + nextAttackReadyTick);
        return true;
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
    private boolean dodgeCleave(PlayerSnapshot player) {
        state = AraxxorState.ENRAGED_DODGE_CLEAVE;
        WorldPoint loc = player.getWorldLocation();
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        if (cleaveOriginTile == null) cleaveOriginTile = loc;

        if (!Rs2Player.isRunEnabled()) {
            Rs2Player.toggleRunEnergy(true);
            status = "Cleave dodge — enabling run";
            return false;
        }

        if (diagnosticTick > cleaveDetectedTick
                && cleaveOriginTile.distanceTo(loc) >= 2
                && !cleaveDangerTiles.contains(loc) && !isOnAcidPool(loc)) {
            int recoveryTicks = config.enragePostDodgeDelayTicks();
            enrageAttackResumeAt = System.currentTimeMillis() + recoveryTicks * 600L;
            enrageRecoveryThroughTick = diagnosticTick + recoveryTicks;
            clearMovementState();
            traceEvent("CLEAVE_DODGED", "origin=" + cleaveOriginTile + " safe=" + loc);
            return true;
        }

        if (movementDestination != null && cleaveDodgeDestination != null) {
            movementPending();
            PlayerSnapshot refreshed = readPlayer();
            if (refreshed != null) {
                loc = refreshed.getWorldLocation();
                if (diagnosticTick > cleaveDetectedTick
                        && cleaveOriginTile.distanceTo(loc) >= 2
                        && !cleaveDangerTiles.contains(loc) && !isOnAcidPool(loc)) {
                    int recoveryTicks = config.enragePostDodgeDelayTicks();
                    enrageAttackResumeAt = System.currentTimeMillis() + recoveryTicks * 600L;
                    enrageRecoveryThroughTick = diagnosticTick + recoveryTicks;
                    clearMovementState();
                    traceEvent("CLEAVE_DODGED", "origin=" + cleaveOriginTile + " safe=" + loc);
                    return true;
                }
            }
            if (movementDestination != null) return false;
        }

        Rs2NpcModel araxxor = loopBoss;

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
            int parallelDistance = 2;
            candidates.add(new WorldPoint(loc.getX() + atkX * parallelDistance,
                    loc.getY() + atkY * parallelDistance, loc.getPlane()));
            candidates.add(new WorldPoint(loc.getX() - atkX * parallelDistance,
                    loc.getY() - atkY * parallelDistance, loc.getPlane()));
        }

        // Use a full two-tile run step in every direction. Although a one-tile
        // diagonal is theoretically sufficient, live traces showed those clicks
        // landing too close/late and taking 28–31 damage.
        candidates.add(new WorldPoint(loc.getX() + 2, loc.getY() + 2, loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX() - 2, loc.getY() - 2, loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX() + 2, loc.getY() - 2, loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX() - 2, loc.getY() + 2, loc.getPlane()));

        candidates.add(new WorldPoint(loc.getX() + 2, loc.getY(), loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX() - 2, loc.getY(), loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX(), loc.getY() + 2, loc.getPlane()));
        candidates.add(new WorldPoint(loc.getX(), loc.getY() - 2, loc.getPlane()));

        // pickBestTile scores all candidates — cleave tiles on path/destination get +1000/+500 penalty
        WorldPoint best = pickBestImmediateTile(loc, candidates.toArray(new WorldPoint[0]));
        if (best == null) best = findEmergencySafeTile(loc);
        if (best == null) {
            traceEvent("CLEAVE_DODGE_FAILED", "no local escape from=" + loc);
            return false;
        }
        movementDestination = null;
        movementOrigin = null;
        movementIssuedAt = 0;
        movementDeadline = 0;
        moveTo(best);
        cleaveDodgeDestination = movementDestination;
        return false;
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

        // Lead with diagonal escapes: three/four tiles across the blast line and
        // one tile away from Araxxor. This clears the three-wide lane quickly
        // without running straight across the arena. Pure perpendicular routes
        // remain fallbacks when collision blocks the diagonal.
        WorldPoint[] safeCandidates = {
                new WorldPoint(playerLoc.getX() + safePerpX * 4 + normBx,
                        playerLoc.getY() + safePerpY * 4 + normBy, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() + safePerpX * 3 + normBx,
                        playerLoc.getY() + safePerpY * 3 + normBy, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() - safePerpX * 4 + normBx,
                        playerLoc.getY() - safePerpY * 4 + normBy, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() - safePerpX * 3 + normBx,
                        playerLoc.getY() - safePerpY * 3 + normBy, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() + safePerpX * 4, playerLoc.getY() + safePerpY * 4, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() + safePerpX * 3, playerLoc.getY() + safePerpY * 3, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() - safePerpX * 4, playerLoc.getY() - safePerpY * 4, playerLoc.getPlane()),
                new WorldPoint(playerLoc.getX() - safePerpX * 3, playerLoc.getY() - safePerpY * 3, playerLoc.getPlane()),
        };

        // Cardinal fallbacks — only those on the safe side of the blast line
        java.util.List<WorldPoint> fallbacks = new java.util.ArrayList<>();
        int[][] cardinals = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
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

        WorldPoint dodge = pickBestImmediateTile(playerLoc, allCandidates);
        return dodge;
    }

    private WorldPoint findEmergencyCannonTile(WorldPoint playerLoc) {
        java.util.List<WorldPoint> candidates = new ArrayList<>();
        for (int radius = 2; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                WorldPoint south = new WorldPoint(playerLoc.getX() + dx,
                        playerLoc.getY() - radius, playerLoc.getPlane());
                WorldPoint north = new WorldPoint(playerLoc.getX() + dx,
                        playerLoc.getY() + radius, playerLoc.getPlane());
                if (hasClearedAcidCannonLine(south)) candidates.add(south);
                if (hasClearedAcidCannonLine(north)) candidates.add(north);
            }
            for (int dy = -radius + 1; dy < radius; dy++) {
                WorldPoint west = new WorldPoint(playerLoc.getX() - radius,
                        playerLoc.getY() + dy, playerLoc.getPlane());
                WorldPoint east = new WorldPoint(playerLoc.getX() + radius,
                        playerLoc.getY() + dy, playerLoc.getPlane());
                if (hasClearedAcidCannonLine(west)) candidates.add(west);
                if (hasClearedAcidCannonLine(east)) candidates.add(east);
            }
        }
        return candidates.isEmpty() ? null
                : pickBestImmediateTile(playerLoc, candidates.toArray(new WorldPoint[0]));
    }

    /** Compute and issue the cannon dodge on the script thread. */
    private boolean dodgeAcidCannon(PlayerSnapshot player) {
        state = AraxxorState.DODGING_ACID_BALL;
        WorldPoint playerLoc = player.getWorldLocation();
        if (acidCannonOriginTile == null) acidCannonOriginTile = playerLoc;

        // Acid Ball needs immediate lateral clearance. Force run for the dodge;
        // Acid Drip is the separate mechanic that must deliberately walk.
        if (!Rs2Player.isRunEnabled()) {
            Rs2Player.toggleRunEnergy(true);
            status = "Acid cannon — enabling run";
            // Toggling run and issuing the walk in the same client cycle avoids
            // sacrificing the only reaction tick after an event-based trigger.
            traceEvent("CANNON_RUN_LOCK", "enabled=true continuingWithDodge=true");
        }

        if (hasClearedAcidCannonLine(playerLoc)
                && diagnosticTick <= acidCannonPredictionHoldUntilTick) {
            // Do not stop on the first safe tile before the ball is launched: it
            // would simply retarget that tile. Finish the current leg, then chain
            // another perpendicular leg until the predicted launch window closes.
            movementPending();
            if (movementDestination != null) return false;
            acidCannonOriginTile = playerLoc;
            acidCannonDodgeDestination = null;
        } else if (hasClearedAcidCannonLine(playerLoc)) {
            // A running player can clear the danger line halfway through the
            // route. Do not restore their previous walk state until the complete
            // diagonal leg has landed, or the remaining dodge becomes a walk.
            if (movementPending()) return false;
            acidCannonHandledUntil = System.currentTimeMillis() + 6000;
            acidCannonPredictionActive = false;
            // Never restore walk here. Acid Drip owns deliberate walking; Acid
            // Ball/Cannon must leave run enabled after the entire escape leg.
            acidCannonRunWasEnabled = false;
            acidCannonOriginTile = null;
            acidCannonDodgeDestination = null;
            acidCannonSourceTile = null;
            traceEvent("CANNON_DODGED", "observed safe tile=" + playerLoc);
            return true;
        }

        // A click is not a completed dodge. Keep the mechanic armed until the
        // player is observed far enough perpendicular to the original blast line.
        if (movementDestination != null && acidCannonDodgeDestination != null) {
            movementPending();
            PlayerSnapshot refreshed = readPlayer();
            if (refreshed != null && hasClearedAcidCannonLine(refreshed.getWorldLocation())) {
                if (diagnosticTick <= acidCannonPredictionHoldUntilTick) {
                    if (movementDestination != null) return false;
                    acidCannonOriginTile = refreshed.getWorldLocation();
                    acidCannonDodgeDestination = null;
                    playerLoc = refreshed.getWorldLocation();
                } else {
                if (movementDestination != null) return false;
                acidCannonHandledUntil = System.currentTimeMillis() + 6000;
                acidCannonPredictionActive = false;
                acidCannonRunWasEnabled = false;
                acidCannonOriginTile = null;
                acidCannonDodgeDestination = null;
                acidCannonSourceTile = null;
                traceEvent("CANNON_DODGED", "observed safe tile=" + refreshed.getWorldLocation());
                return true;
                }
            }
            if (movementDestination != null) return false;
            playerLoc = refreshed == null ? playerLoc : refreshed.getWorldLocation();
        }

        WorldPoint best = computeAcidCannonDodge(playerLoc, acidCannonSourceTile);

        if (best == null || best.equals(playerLoc)) {
            // The cannon trajectory is 3 tiles wide, so a one-tile fallback
            // is not sufficient when starting on its centre line.
            best = findEmergencyCannonTile(playerLoc);
        }
        if (best == null) {
            traceEvent("CANNON_DODGE_FAILED", "no local escape from=" + playerLoc);
            return false;
        }

        // Cannon movement supersedes every older walk. Without clearing the old
        // destination, moveTo() refuses the dodge while that route is pending.
        movementDestination = null;
        movementOrigin = null;
        movementIssuedAt = 0;
        movementDeadline = 0;
        movementReassertDestination = null;
        movementReassertAt = 0;
        escapingAcid = false;
        moveTo(best, false);
        if (movementDestination == null) {
            traceEvent("CANNON_DODGE_FAILED", "movement not issued from=" + playerLoc + " to=" + best);
            return false;
        }
        acidCannonDodgeDestination = movementDestination;
        log("Issued acid cannon dodge → " + best);
        return false;
    }

    private boolean hasClearedAcidCannonLine(WorldPoint current) {
        WorldPoint origin = acidCannonOriginTile;
        WorldPoint source = acidCannonSourceTile;
        if (current == null || origin == null || isOnAcidPool(current)) return false;
        if (source == null) return current.distanceTo(origin) >= 3;

        int blastX = Integer.signum(origin.getX() - source.getX());
        int blastY = Integer.signum(origin.getY() - source.getY());
        if (blastX == 0 && blastY == 0) return current.distanceTo(origin) >= 3;
        int moveX = current.getX() - origin.getX();
        int moveY = current.getY() - origin.getY();
        int perpendicularProgress = Math.abs(moveX * blastY - moveY * blastX);
        int requiredProgress = blastX != 0 && blastY != 0 ? 4 : 2;
        return perpendicularProgress >= requiredProgress;
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
            if (dodgeAcidCannon(player)) acidCannonIncoming = false;
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
        Rs2NpcModel araxxor = loopBoss;
        if (araxxor == null || isDead(araxxor)) {
            finishAcidDrip();
            return;
        }

        if (!acidDripRunStateCaptured) {
            restoreRunAfterAcidDrip = Rs2Player.isRunEnabled();
            acidDripRunStateCaptured = true;
        }
        if (Rs2Player.isRunEnabled()) {
            Rs2Player.toggleRunEnergy(false);
            status = "Acid drip — switching to walk";
            return;
        }

        // Continue resolving the previous route, but food is still allowed while
        // walking. No other combat or positioning branch may take ownership.
        if (movementPending()) {
            handleFoodSafety();
            return;
        }

        WorldPoint playerLoc = player.getWorldLocation();
        WorldArea bossArea = readClient(araxxor::getWorldArea);
        if (bossArea == null) return;

        WorldPoint[] underCorners = {
                new WorldPoint(bossArea.getX(), bossArea.getY(), bossArea.getPlane()),
                new WorldPoint(bossArea.getX() + bossArea.getWidth() - 1, bossArea.getY(), bossArea.getPlane()),
                new WorldPoint(bossArea.getX() + bossArea.getWidth() - 1,
                        bossArea.getY() + bossArea.getHeight() - 1, bossArea.getPlane()),
                new WorldPoint(bossArea.getX(), bossArea.getY() + bossArea.getHeight() - 1, bossArea.getPlane())
        };
        WorldPoint[] outsideCorners = {
                new WorldPoint(bossArea.getX() - 1, bossArea.getY() - 1, bossArea.getPlane()),
                new WorldPoint(bossArea.getX() + bossArea.getWidth(), bossArea.getY() - 1, bossArea.getPlane()),
                new WorldPoint(bossArea.getX() + bossArea.getWidth(),
                        bossArea.getY() + bossArea.getHeight(), bossArea.getPlane()),
                new WorldPoint(bossArea.getX() - 1, bossArea.getY() + bossArea.getHeight(), bossArea.getPlane())
        };

        if (acidDripCornerIndex < 0) {
            acidDripCornerIndex = nearestSafeAcidDripCorner(playerLoc, underCorners, outsideCorners);
        }

        if (acidDripPhase == 0) {
            status = "Acid drip — stepping under corner " + acidDripCornerIndex;
            moveTo(underCorners[acidDripCornerIndex], true, true);
            acidDripPhase = 1;
        } else if (acidDripPhase == 1) {
            // Step diagonally outside the same corner while remaining in melee
            // range. Projectile targets are already present in acidPools, so a
            // contaminated corner is skipped before the click is issued.
            if (isOnAcidPool(outsideCorners[acidDripCornerIndex])) {
                acidDripCornerIndex = (acidDripCornerIndex + 1) & 3;
            }
            status = "Acid drip — stepping outside corner " + acidDripCornerIndex;
            moveTo(outsideCorners[acidDripCornerIndex], true, true);
            acidDripPhase = 2;
        } else {
            // Attack from the safe perimeter, then immediately walk through the
            // next corner. This reproduces the attack-and-90-degree-turn rhythm
            // without relying on Control-click state or allowing attack pathing
            // to leave the player stationary on the dripping tile.
            clickAttack(araxxor, true);
            acidDripCycles++;
            acidDripCornerIndex = (acidDripCornerIndex + 1) & 3;
            if (isOnAcidPool(underCorners[acidDripCornerIndex])) {
                acidDripCornerIndex = (acidDripCornerIndex + 1) & 3;
            }
            status = "Acid drip — attacking and rotating (" + acidDripCycles + ")";
            moveTo(underCorners[acidDripCornerIndex], true, true);
            acidDripPhase = 1;
        }
    }

    private void finishAcidDrip() {
        if (acidDripRunStateCaptured && restoreRunAfterAcidDrip && !Rs2Player.isRunEnabled()) {
            Rs2Player.toggleRunEnergy(true);
        }
        acidDripActive = false;
        acidDripPhase = 0;
        acidDripCornerIndex = -1;
        acidDripCycles = 0;
        acidDripRunStateCaptured = false;
        restoreRunAfterAcidDrip = false;
    }

    private int nearestSafeAcidDripCorner(WorldPoint playerLoc, WorldPoint[] underCorners,
                                          WorldPoint[] outsideCorners) {
        boolean[] traversable = getTraversableCandidates(playerLoc, underCorners);
        int best = 0;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < underCorners.length; i++) {
            if (!traversable[i] || isOnAcidPool(underCorners[i]) || isOnAcidPool(outsideCorners[i])) continue;
            int score = playerLoc.distanceTo(underCorners[i]) * 20
                    + countAcidInArea(outsideCorners[i], 1) * 250;
            if (score < bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
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
        int specEnergy = tickSpecEnergy;
        String specWeapon = config.specWeapon().toLowerCase();
        int specPerHit = specWeapon.contains("dragon warhammer") ? 50 : 50; // both 50%
        if (specPhase == SpecPhase.WAITING_HIT && specEnergy < specEnergyBeforeHit) {
            return finishObservedSpecialAttack(specEnergy, specPerHit);
        }
        int hitsRemaining = config.specCount() - specHitsCompleted;

        // Need enough energy for at least one spec hit
        if (specEnergy < specPerHit || hitsRemaining <= 0) {
            specPhase = SpecPhase.IDLE;
            return false;
        }

        // Need the weapon available
        boolean specWeaponEquipped = Rs2Equipment.isWearing(config.specWeapon());
        if (!specWeaponEquipped && !Rs2Inventory.hasItem(config.specWeapon())) {
            specPhase = SpecPhase.IDLE;
            return false;
        }

        state = AraxxorState.SPEC_ATTACK;
        long now = System.currentTimeMillis();

        // A minion switch can interrupt any phase. Re-enter through EQUIPPING
        // instead of continuing OPENING_TAB/ATTACKING with the wrong weapon.
        if (!specWeaponEquipped && specPhase != SpecPhase.IDLE && specPhase != SpecPhase.EQUIPPING) {
            specPhase = SpecPhase.IDLE;
        }

        if (specPhase == SpecPhase.IDLE && !specWeaponEquipped) {
            status = "Equipping " + config.specWeapon();
            if (now >= nextEquipmentActionAt && Rs2Inventory.wield(config.specWeapon())) {
                nextEquipmentActionAt = now + 600;
                specPhase = SpecPhase.EQUIPPING;
                specPhaseDeadline = now + 1800;
                prayerDirty = true;
            }
            return true;
        }
        if (specPhase == SpecPhase.EQUIPPING) {
            if (specWeaponEquipped) {
                specPhase = SpecPhase.OPENING_TAB;
            } else if (now >= specPhaseDeadline) {
                specPhase = SpecPhase.IDLE;
            }
            return true;
        }

        managePrayers(player, araxxor);
        status = "Spec " + (specHitsCompleted + 1) + "/" + config.specCount() + ": " + config.specWeapon();

        if (specPhase == SpecPhase.IDLE || specPhase == SpecPhase.OPENING_TAB) {
            if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
                Rs2Tab.switchTo(InterfaceTab.COMBAT);
                specPhase = SpecPhase.OPENING_TAB;
                return true;
            }
            Rs2Combat.setSpecState(true, specPerHit * 10);
            if (clickAttack(araxxor, true)) {
                specEnergyBeforeHit = specEnergy;
                specPhase = SpecPhase.WAITING_HIT;
                specPhaseDeadline = now + 1800;
            } else {
                specPhase = SpecPhase.ATTACKING;
                specPhaseDeadline = now + 150;
            }
            return true;
        }

        if (specPhase == SpecPhase.ATTACKING) {
            if (now < specPhaseDeadline) return true;
            if (attackNpc(araxxor, true)) {
                specEnergyBeforeHit = specEnergy;
                specPhase = SpecPhase.WAITING_HIT;
                specPhaseDeadline = now + 1800;
            } else {
                specPhaseDeadline = now + 300;
            }
            return true;
        }

        if (specPhase != SpecPhase.WAITING_HIT) return true;
        if (specEnergy >= specEnergyBeforeHit) {
            if (now >= specPhaseDeadline) {
                status = "Special attack not observed — retrying";
                specPhase = SpecPhase.OPENING_TAB;
            }
            return true;
        }

        return finishObservedSpecialAttack(specEnergy, specPerHit);
    }

    private boolean finishObservedSpecialAttack(int energyAfter, int specPerHit) {
        specHitsCompleted++;
        specPhase = SpecPhase.IDLE;
        log("Spec hit " + specHitsCompleted + "/" + config.specCount() + " landed");

        if (specHitsCompleted < config.specCount() && energyAfter >= specPerHit) return true;
        if (config.useSurgePotion() && specHitsCompleted >= config.specCount()) drinkSurgePotion();
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
        long now = System.currentTimeMillis();
        if (now < nextPrayerPotionCheckAt) return;
        nextPrayerPotionCheckAt = now + 300;
        int prayerPercent = (tickPrayer * 100) / tickMaxPrayer;
        if (prayerPercent < config.drinkPrayerAtPercent()) {
            Rs2Inventory.interact(prayerPotionVariants, "drink");
            nextPrayerPotionCheckAt = now + 1200;
            tickSleep();
        }
    }

    // ── Prayer Management ───────────────────────────────

    private void managePrayers(PlayerSnapshot player, Rs2NpcModel araxxor) {
        if (lastPrayerManageTick == diagnosticTick && !prayerDirty) return;
        // Attack animations/projectiles identify the attack already launched.
        // They take precedence over proximity: Araxxor can launch magic while
        // close, and switching back to melee prayer cannot mitigate that hit.
        if ((cleaveIncoming || incomingAttackStyle == IncomingAttackStyle.MELEE)
                && config.protectFromMelee()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        } else if (incomingAttackStyle == IncomingAttackStyle.MAGIC && config.protectFromMagic()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        } else if (incomingAttackStyle == IncomingAttackStyle.RANGED && config.protectFromMissiles()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        }

        int prayerLevel = tickPrayerLevel;
        int defenceLevel = tickDefenceLevel;
        if (isRangedWeaponEquipped()) {
            if (config.useRigour() && prayerLevel >= Rs2PrayerEnum.RIGOUR.getLevel() && defenceLevel >= 70) {
                Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, true);
            }
        } else if (config.usePiety()
                && prayerLevel >= Rs2PrayerEnum.PIETY.getLevel()
                && defenceLevel >= 70) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        }
        lastPrayerManageTick = diagnosticTick;
        prayerDirty = false;
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
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
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
     * Publish one cleave hazard to the script loop. Araxxor's animation and
     * overhead text can report the same attack independently; retaining the
     * first snapshot prevents the second event from moving the origin while a
     * dodge is already in progress.
     */
    public void onCleaveDetected() {
        int observedTick = diagnosticTick;
        nextPredictedCleaveTick = observedTick + 4;
        if (cleaveIncoming || observedTick == lastPredictedCleaveImpactTick) {
            traceEvent("CLEAVE_CONFIRMED", "observedTick=" + observedTick
                    + " next=" + nextPredictedCleaveTick);
            return;
        }
        cleaveDetectedTick = diagnosticTick;
        cleaveIncoming = true;
        computeCleaveTiles();
        traceEvent("CLEAVE_ARMED", "detectedTick=" + cleaveDetectedTick
                + " origin=" + cleaveOriginTile);
    }

    /** Arm the next cadence-based dodge before the live cleave event arrives. */
    private void armPredictedCleave() {
        int impactTick = nextPredictedCleaveTick;
        lastPredictedCleaveImpactTick = impactTick;
        nextPredictedCleaveTick = impactTick + 4;
        cleaveDetectedTick = diagnosticTick;
        cleaveIncoming = true;
        clearMovementState();
        computeCleaveTiles();
        traceEvent("CLEAVE_PREDICTED", "warningTick=" + diagnosticTick
                + " impactTick=" + impactTick + " origin=" + cleaveOriginTile);
    }

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
        cleaveOriginTile = playerLoc;
        cleaveDodgeDestination = null;

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
            if (containsCoordinate(danger, x0, y0, start.getPlane())) {
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
        cleaveOriginTile = null;
        cleaveDodgeDestination = null;
        cleaveAttackDirX = 0;
        cleaveAttackDirY = 0;
    }

    // ── Movement Helpers ────────────────────────────────

    /**
     * Score a candidate tile. Lower score = better.
     * Prefers tiles that are: not on acid, not on cleave, path doesn't cross
     * acid or cleave tiles, closer to arena center, away from walls.
     */
    private int scoreTile(WorldPoint candidate, WorldPoint playerLocation) {
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
        if (playerLocation != null) {
            int acidOnPath = countAcidOnPath(playerLocation, candidate);
            score += acidOnPath * 500;
            int cleaveOnPath = countCleaveTilesOnPath(playerLocation, candidate);
            score += cleaveOnPath * 500;
        }
        if (resolvedStartingTile != null) {
            // The captured tile is the stable tactical anchor. Hazard solvers may
            // leave it, but equivalent safe choices should remain near it instead
            // of gradually drifting around the arena.
            int anchorDistance = candidate.distanceTo(resolvedStartingTile);
            int radius = config.startingTileRadius();
            score += Math.min(anchorDistance, radius) * 50;
            if (anchorDistance > radius) {
                score += (anchorDistance - radius) * 250;
            }
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
            if (containsCoordinate(acidPools, x0, y0, start.getPlane())) {
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

    private boolean containsCoordinate(Set<WorldPoint> tiles, int x, int y, int plane) {
        for (WorldPoint tile : tiles) {
            if (tile.getX() == x && tile.getY() == y && tile.getPlane() == plane) return true;
        }
        return false;
    }

    /**
     * Pick the best tile from a list of candidates, preferring non-acid tiles
     * with a clean path, closer to arena center. Always returns a tile (never null).
     */
    private WorldPoint pickBestTile(WorldPoint[] candidates) {
        PlayerSnapshot player = readPlayer();
        if (player == null) return candidates[0];
        WorldPoint playerLocation = player.getWorldLocation();
        WorldPoint candidate = pickBestImmediateTile(playerLocation, candidates);
        return candidate != null ? candidate : findEmergencySafeTile(playerLocation);
    }

    private boolean isOnAcidPool(WorldPoint tile) {
        return tile != null && acidPools.contains(tile);
    }

    /**
     * One-time reconciliation for scripts started during an active encounter.
     * Afterwards object/projectile events maintain the set, avoiding a tile-object
     * cache query for every candidate examined by the movement solver.
     */
    private void seedAcidPools() {
        Microbot.getRs2TileObjectCache().query()
                .withIds(ACID_POOL_OBJ, VENOM_PUDDLE_1, VENOM_PUDDLE_2, VENOM_PUDDLE_3)
                .toList()
                .forEach(object -> acidPools.add(object.getWorldLocation()));
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

        if (normX == 0 && normY == 0) {
            WorldPoint[] radial = {
                    new WorldPoint(from.getX() + distance, from.getY(), from.getPlane()),
                    new WorldPoint(from.getX() - distance, from.getY(), from.getPlane()),
                    new WorldPoint(from.getX(), from.getY() + distance, from.getPlane()),
                    new WorldPoint(from.getX(), from.getY() - distance, from.getPlane()),
                    new WorldPoint(from.getX() + distance, from.getY() + distance, from.getPlane()),
                    new WorldPoint(from.getX() - distance, from.getY() - distance, from.getPlane()),
                    new WorldPoint(from.getX() + distance, from.getY() - distance, from.getPlane()),
                    new WorldPoint(from.getX() - distance, from.getY() + distance, from.getPlane())
            };
            moveTo(pickBestImmediateTile(from, radial));
            return;
        }

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
        boolean needsWeapon = !weapon.isEmpty() && !Rs2Equipment.isWearing(weapon);
        boolean needsOffhand = !offhand.isEmpty() && !Rs2Equipment.isWearing(offhand);
        if (!needsWeapon && !needsOffhand) return;
        long now = System.currentTimeMillis();
        if (now < nextEquipmentActionAt) return;
        // Queue both equips — weapon and offhand/ammo can process in the same game tick
        if (needsWeapon) {
            Rs2Inventory.wield(weapon);
        }
        if (needsOffhand) {
            Rs2Inventory.wield(offhand);
        }
        nextEquipmentActionAt = now + 600;
        prayerDirty = true;
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
            lootCollectionComplete = false;
            lootCompletionNotBefore = Math.max(lootCompletionNotBefore, now + 6_000);
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
        // This is a hard departure interlock, independent of encounter-phase
        // timing. If any future transition accidentally clears postFight early,
        // valuable instance-only loot still cannot be abandoned.
        if (!lootCollectionComplete) {
            state = AraxxorState.LOOTING;
            status = "Waiting for post-fight loot";
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
            // 54161 is the actual nearby lair door. Query it separately so a
            // decorative/outer tunnel variant cannot win the nearest-object sort.
            var entrance = Microbot.getRs2TileObjectCache().query()
                    .withId(ObjectID1.ARAXXOR_BOSS_TUNNEL_MULTI)
                    .within(15).nearestOnClientThread();
            if (entrance == null) {
                entrance = Microbot.getRs2TileObjectCache().query()
                    .where(object -> object.getId() == ObjectID1.ARAXXOR_CAVE_OUTER_TUNNEL_OP
                            || object.getId() == ObjectID1.ARAXXOR_CAVE_OUTER_TUNNEL_OP_2
                            || object.getId() == ObjectID1.ARAXXOR_CAVE_OUTER_TUNNEL_OP_3
                            || object.getId() == ObjectID1.ARAXXOR_CAVE_OUTER_TUNNEL_OP_4)
                    .within(15).nearestOnClientThread();
            }
            if (entrance != null) {
                if (!config.autoEnterAndFight()) {
                    state = AraxxorState.ENTERING_LAIR;
                    status = config.startingTile().trim().isEmpty()
                            ? "At Araxxor door — waiting for manual entry"
                            : "At Araxxor door — enter and walk to your starting tile";
                    entryRetryAt = now + 1000;
                    togglePrayers(false);
                    return;
                }
                if (entrance.getId() == ObjectID1.ARAXXOR_BOSS_TUNNEL_MULTI) {
                    state = AraxxorState.ENTERING_LAIR;
                    status = "Entering Araxxor lair — awaiting arena";
                    String action = preferredEntranceAction(entrance);
                    // Rs2TileObjectModel adjusts scene coordinates for large game
                    // objects. The previous synthetic first-option click used the
                    // object's unadjusted centre and could visibly click without
                    // invoking the tunnel action.
                    boolean clicked = action.isEmpty() ? entrance.click() : entrance.click(action);
                    traceEvent("LAIR_ENTRY", "id=54161 action="
                            + (action.isEmpty() ? "first-option" : action) + " dispatched=" + clicked
                            + " auto=" + config.autoEnterAndFight());
                    entryRetryAt = now + (clicked ? 1_500 : 500);
                    return;
                }
                var selectedEntrance = entrance;
                String action = readClient(() -> {
                    ObjectComposition composition = selectedEntrance.getObjectComposition();
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
                // Object 54161 can expose no actions through its transformed
                // composition even though its first menu option enters the lair.
                state = AraxxorState.ENTERING_LAIR;
                status = "Entering lair via first option";
                if (!entrance.click()) {
                    status = "Lair first-option click failed — retrying";
                }
                traceEvent("LAIR_ENTRY", "id=" + entrance.getId() + " fallbackFirstOption=true");
                entryRetryAt = now + 5000;
                return;
            }
            routeToAraxxorEntrance(now);
            return;
        }
        togglePrayers(false);
    }

    private String preferredEntranceAction(
            net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel object) {
        if (object == null) return "";
        return readClient(() -> {
            ObjectComposition composition = object.getObjectComposition();
            if (composition != null && composition.getImpostorIds() != null
                    && composition.getImpostor() != null) {
                composition = composition.getImpostor();
            }
            if (composition == null || composition.getActions() == null) return "";
            String firstAction = "";
            for (String rawAction : composition.getActions()) {
                if (rawAction == null) continue;
                String action = Rs2UiHelper.stripColTags(rawAction);
                if (firstAction.isEmpty()) firstAction = action;
                String normalized = action.toLowerCase(Locale.ROOT);
                if (normalized.contains("enter") || normalized.contains("fight")
                        || action.equalsIgnoreCase("Pass")) {
                    return action;
                }
            }
            return firstAction;
        });
    }

    private void routeToAraxxorEntrance(long now) {
        PlayerSnapshot player = readPlayer();
        if (player == null) {
            entryRetryAt = now + 2000;
            return;
        }

        WorldPoint location = player.getWorldLocation();
        boolean inSpiderCave = location.getPlane() == 0
                && location.getX() >= 3550 && location.getX() <= 3750
                && location.getY() >= 9700 && location.getY() <= 9900;

        if (!inSpiderCave) {
            var caveEntrance = Microbot.getRs2TileObjectCache().query()
                    .withId(ObjectID.ARAXYTE_CAVE_ENTRY)
                    .within(12).nearestOnClientThread();
            if (caveEntrance != null) {
                state = AraxxorState.ENTERING_LAIR;
                status = "Entering Morytania Spider Cave";
                if (!caveEntrance.click("Enter")) {
                    status = "Spider cave entrance click failed — retrying";
                }
                entryRetryAt = System.currentTimeMillis() + 3000;
                return;
            }
        }

        WorldPoint target = inSpiderCave ? ARAXXOR_LAIR_APPROACH : SPIDER_CAVE_ENTRANCE;
        state = AraxxorState.ENTERING_LAIR;
        status = inSpiderCave
                ? "Web-walking through spider cave to Araxxor"
                : "Web-walking to Morytania Spider Cave";

        if (inSpiderCave && config.protectFromMelee()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        } else if (!inSpiderCave) {
            togglePrayers(false);
        }

        entryRetryAt = System.currentTimeMillis() + 5000;
        WalkerState result = Rs2Walker.walkWithState(target, inSpiderCave ? 8 : 4);
        traceEvent("WEB_WALK", "target=" + target + " result=" + result);
        if (result == WalkerState.ARRIVED) {
            status = inSpiderCave
                    ? "At Araxxor tunnel — locating entrance"
                    : "At spider cave — locating entrance";
            entryRetryAt = System.currentTimeMillis() + 250;
        } else if (result == WalkerState.UNREACHABLE) {
            status = "Web-walker could not reach spider cave — retrying";
            entryRetryAt = System.currentTimeMillis() + 15000;
        } else {
            status = "Web-walk interrupted — retrying";
            entryRetryAt = System.currentTimeMillis() + 3000;
        }
    }
    /**
     * Reset all per-fight state flags. Called when transitioning to idle between kills
     * to ensure stale enraged/acid/cleave flags from a previous fight don't carry over.
     */
    private void resetFightState() {
        enraged = false;
        specHitsCompleted = 0;
        specPhase = SpecPhase.IDLE;
        specEnergyBeforeHit = 0;
        specPhaseDeadline = 0;
        araxxorAttackCount = 0;
        lastRangedMovementTick = -1000;
        ballistaHitPending = false;
        primaryWeaponRestoreRequested = false;
        ballistaHitDeadlineTick = -1;
        incomingAttackStyle = IncomingAttackStyle.RANGED;
        acidDripActive = false;
        acidDripPhase = 0;
        acidDripCornerIndex = -1;
        acidDripCycles = 0;
        acidDripRunStateCaptured = false;
        restoreRunAfterAcidDrip = false;
        cleaveIncoming = false;
        cleaveDetectedTick = -1000;
        enrageRecoveryThroughTick = -1000;
        nextPredictedCleaveTick = -1;
        lastPredictedCleaveImpactTick = -1;
        clearCleaveTiles();
        acidCannonIncoming = false;
        acidCannonExpected = false;
        acidCannonPrimed = false;
        acidCannonPredictionActive = false;
        predictedAcidCannonTick = -1;
        acidCannonPredictionHoldUntilTick = -1;
        acidCannonRunWasEnabled = false;
        acidCannonSourceTile = null;
        acidCannonOriginTile = null;
        acidCannonDodgeDestination = null;
        acidCannonHandledUntil = 0;
        arenaCenter = null;
        resolvedStartingTile = null;
        startingTileReady = false;
        lastAnchorReturnTick = -1000;
        acidPools.clear();
        movementDestination = null;
        movementOrigin = null;
        movementIssuedAt = 0;
        movementDeadline = 0;
        movementReassertDestination = null;
        movementReassertAt = 0;
        reservedMovementTile = null;
        reservedMovementUntilTick = 0;
        routineMovementBlockedUntilTick = 0;
        nextAttackReadyTick = 0;
        blockedMovementTiles.clear();
        escapingAcid = false;
        nextCannonFallbackPollAt = 0;
        nextPotionCheckAt = 0;
        nextThrallCheckAt = 0;
        nextPrayerPotionCheckAt = 0;
        nextEatTick = 0;
        nextEquipmentActionAt = 0;
        lastPrayerManageTick = -1;
        prayerDirty = true;
        acidicDeathLocation = null;
        acidicKillPending = false;
        enrageAttackResumeAt = 0;
        corpseRetryAt = 0;
        pendingLootId = -1;
        pendingLootTile = null;
        pendingLootQuantity = 0;
        lootRetryAt = 0;
        fightStartedAt = 0;
        fightDamageTaken = 0;
        fightFoodConsumed = 0;
        fightMovementCommands = 0;
        fightAttacks = 0;
        fightAcidExposureTicks = 0;
        fightPrayerMissTicks = 0;
        fightAttackReadyMovementTicks = 0;
        lastMetricsTick = -1;
        lastMovementLossTick = -1;
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
            if (now >= lootCompletionNotBefore && now - lootQuietSince >= 5_000) {
                PlayerSnapshot player = readPlayer();
                if (player != null) {
                    startingTileReady = false;
                    if (handleStartingTile(player)) {
                        status = "Loot complete — returning to starting tile";
                        return;
                    }
                }
                postFight = false;
                resetFightState();
                lootCollectionComplete = true;
                lootCompletionNotBefore = 0;
                state = AraxxorState.IDLE;
                status = "Loot complete";
                traceEvent("LOOT_COMPLETE", "quietMs=" + (now - lootQuietSince));
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

        return clickAttack(target, forceClick);
    }

    private boolean clickAttack(Rs2NpcModel target, boolean forceClick) {
        if (target == null) return false;

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().isWidgetSelected()) {
                Microbot.getClient().setWidgetSelected(false);
            }
            return true;
        });

        Rs2NpcModel current = Microbot.getRs2NpcCache().query()
                .withId(target.getId()).where(npc -> npc.getNpc() == target.getNpc()).firstOnClientThread();
        boolean clicked = current != null && !isDead(current) && current.click("Attack");
        if (clicked) {
            nextAttackReadyTick = Math.max(nextAttackReadyTick,
                    diagnosticTick + tickWeaponAttackSpeed);
        }
        traceEvent("ATTACK", "npcId=" + target.getId() + " name=" + target.getName()
                + " clicked=" + clicked + " force=" + forceClick + " reason=" + status);
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
        return (tickHitpoints * 100) / tickMaxHitpoints;
    }
}

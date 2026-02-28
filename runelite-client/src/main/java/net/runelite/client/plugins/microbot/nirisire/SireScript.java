package net.runelite.client.plugins.microbot.nirisire;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.niriaraxxor.CombatPotionType;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;

/**
 * Abyssal Sire boss fight automation script.
 * <p>
 * Handles all phases:
 * <ul>
 *   <li>Phase 1: Shadow Barrage stun + Scorching bow to destroy 4 respiratory systems</li>
 *   <li>Phase 2: Melee combat with Elder maul spec, prayer switching at 50% HP</li>
 *   <li>Phase 3: Melee combat with explosion dodge (2-tick window)</li>
 * </ul>
 * <p>
 * Miasma pools are dodged in all phases. Spawns/scions are tanked (ignored).
 */
@Slf4j
public class SireScript extends Script {

    // ── NPC IDs ──────────────────────────────────────────
    private static final int SIRE_SLEEPING = NpcID.ABYSSALSIRE_SIRE_STASIS_SLEEPING;
    private static final int SIRE_AWAKE = NpcID.ABYSSALSIRE_SIRE_STASIS_AWAKE;
    private static final int SIRE_STUNNED = NpcID.ABYSSALSIRE_SIRE_STASIS_STUNNED;
    private static final int SIRE_PUPPET = NpcID.ABYSSALSIRE_SIRE_PUPPET;
    private static final int SIRE_WANDERING = NpcID.ABYSSALSIRE_SIRE_WANDERING;
    private static final int SIRE_PANICKING = NpcID.ABYSSALSIRE_SIRE_PANICKING;
    private static final int SIRE_APOCALYPSE = NpcID.ABYSSALSIRE_SIRE_APOCALYPSE;
    private static final int LUNG = NpcID.ABYSSALSIRE_LUNG;
    private static final int LUNG_DYING = NpcID.ABYSSALSIRE_LUNG_DYING;

    private static final Set<Integer> SIRE_IDS = Set.of(
            SIRE_SLEEPING, SIRE_AWAKE, SIRE_STUNNED,
            SIRE_PUPPET, SIRE_WANDERING, SIRE_PANICKING, SIRE_APOCALYPSE
    );

    // ── Animation IDs ────────────────────────────────────
    private static final int ANIM_TELEPORT_PLAYER = AnimationID.SIRE_ATTACK_TELEPORT_PLAYER;

    // ── Stun Duration ────────────────────────────────────
    // Shadow Barrage stun lasts 30 seconds, but tentacles reawaken at 27 seconds.
    // Effective stun = 45 game ticks. Re-stun when <8 ticks remain to be safe.
    private static final int STUN_DURATION_TICKS = 45;
    private static final int RESTUN_THRESHOLD_TICKS = 8;
    // When casting Shadow Barrage on a SLEEPING Sire, the stun takes 10 ticks to apply.
    // On an already-awake Sire, the stun applies instantly.
    private static final int FIRST_STUN_DELAY_TICKS = 10;

    // ── SW Room Tile Positions (hardcoded from tile markers) ──
    // Each row has a left and right tile; we pick the closest one at runtime.
    private static final int SW_PLANE = 0; // ground floor

    // Shadow Barrage cast position (Phase 1)
    private static final WorldPoint STUN_TILE = new WorldPoint(2969, 4782, SW_PLANE);

    // Row 1: Phase 2 melee position (closest to Sire)
    private static final WorldPoint ROW1_LEFT = new WorldPoint(2969, 4780, SW_PLANE);
    private static final WorldPoint ROW1_RIGHT = new WorldPoint(2971, 4780, SW_PLANE);

    // Row 2: Phase 3 primary combat position; also Phase 2 panic / transition position
    private static final WorldPoint ROW2_LEFT = new WorldPoint(2969, 4772, SW_PLANE);
    private static final WorldPoint ROW2_RIGHT = new WorldPoint(2971, 4772, SW_PLANE);

    // Row 3: Explosion safe tiles (Phase 3 dodge destination)
    private static final WorldPoint ROW3_LEFT = new WorldPoint(2969, 4770, SW_PLANE);
    private static final WorldPoint ROW3_RIGHT = new WorldPoint(2971, 4770, SW_PLANE);

    // Explosion dodge: player is always teleported to western tile of Row 2.
    // Wiki: "The Sire teleports the player to the western tile of Row 2.
    //        Two ticks later it explodes, dealing up to 96 damage if the
    //        player is within the blast radius."
    // Dodge target is always ROW3_LEFT (same X, 2 tiles south).
    private static final WorldPoint EXPLOSION_TELEPORT_LANDING = ROW2_LEFT; // (2969, 4772)
    private static final WorldPoint EXPLOSION_DODGE_TARGET = ROW3_LEFT;     // (2969, 4770)

    // Vent stand-tiles: optimal positions to hit respiratory systems with a 10-range weapon.
    // Derived from unlabelled wiki tile markers in the SW room (regionId 11850).
    private static final WorldPoint VENT_STAND_NW = new WorldPoint(2968, 4776, SW_PLANE); // regionX=24,Y=40
    private static final WorldPoint VENT_STAND_NE = new WorldPoint(2972, 4776, SW_PLANE); // regionX=28,Y=40
    private static final WorldPoint VENT_STAND_W  = new WorldPoint(2965, 4778, SW_PLANE); // regionX=21,Y=42
    private static final WorldPoint VENT_STAND_E  = new WorldPoint(2975, 4772, SW_PLANE); // regionX=31,Y=36
    private static final WorldPoint[] VENT_STAND_TILES = {
            VENT_STAND_NW, VENT_STAND_NE, VENT_STAND_W, VENT_STAND_E
    };

    // Prescribed vent attack order:
    // 1. Right side top (NE)  2. Right side lower (E)  3. Left side bottom (W)  4. Left side top/closest to boss (NW)
    // Each entry is the STAND TILE from which to range the corresponding vent.
    private static final WorldPoint[] VENT_ATTACK_ORDER = {
            VENT_STAND_NE, VENT_STAND_E, VENT_STAND_W, VENT_STAND_NW
    };

    // ── State ────────────────────────────────────────────
    @Getter
    @Setter
    private volatile SireState state = SireState.IDLE;
    @Getter
    private volatile String status = "Starting...";
    @Getter
    private volatile int killCount = 0;

    // Phase tracking
    @Getter
    private volatile int currentPhase = 0; // 0=idle, 1, 2, 3
    @Getter
    private volatile int ventsDestroyed = 0;
    @Getter
    @Setter
    private volatile long stunStartTick = 0;
    private volatile boolean sireIsStunned = false;
    private volatile boolean sirePanicking = false;

    // Spec tracking
    private volatile int specHitsCompleted = 0;

    // Miasma tracking
    @Getter
    @Setter
    private volatile boolean miasmaIncoming = false;
    // Tracked miasma pool tiles — the miasma targets the player's position at cast time.
    // Pools persist for ~15 ticks. We remember them so we don't walk back onto one.
    @Getter
    private final Set<WorldPoint> miasmaPools = new HashSet<>();
    private volatile long lastMiasmaCleanTick = 0;

    // Anti-poison cooldown — prevent drinking multiple doses before the varp updates
    private volatile long lastAntiPoisonTick = 0;
    private static final int ANTIPOISON_COOLDOWN_TICKS = 15;

    // Explosion dodge (Phase 3)
    @Getter
    @Setter
    private volatile boolean explosionIncoming = false;
    // Tick when explosion dodge completed — used to keep player on Row 3 briefly after.
    // During this window, miasma dodge also prefers Row 3 over Row 2.
    private volatile long explosionDodgeTick = 0;
    private static final int EXPLOSION_SAFETY_WINDOW_TICKS = 5;
    // Phase 3 Stage II: After explosion, Sire does 3 more miasma attacks then stops
    // (as long as 15 minions are alive). Track post-explosion miasma count.
    @Getter
    private volatile boolean explosionHappened = false;
    @Getter
    private volatile int postExplosionMiasmaCount = 0;
    private static final int POST_EXPLOSION_MIASMA_LIMIT = 3;

    // Sire position tracking — set on first detection
    private volatile WorldPoint sireSpawnPos = null;
    // Track whether the first stun was on a sleeping sire (10-tick delay)
    private volatile boolean firstStunOnSleeping = false;
    // Tick when looting completed — used to give the Sire time to respawn
    // before checking supplies (prevents false "low supplies" teleport)
    private volatile long lastLootCompleteTick = 0;
    private static final int SIRE_RESPAWN_GRACE_TICKS = 25; // ~15 seconds

    // Sire engagement flag — once combat starts, the Sire is guaranteed to be
    // in the arena even if findSire() returns null (player moved out of render distance
    // e.g., while destroying vents or dodging). This prevents state loss.
    private volatile boolean sireEngaged = false;
    // Last known Sire NPC ID — preserved when Sire goes out of range
    private volatile int lastKnownSireId = -1;

    private SireConfig config;

    // ── Public API ───────────────────────────────────────

    public boolean run(SireConfig config) {
        this.config = config;
        state = SireState.IDLE;
        killCount = 0;
        currentPhase = 0;
        ventsDestroyed = 0;
        specHitsCompleted = 0;
        sireIsStunned = false;
        sirePanicking = false;
        miasmaIncoming = false;
        explosionIncoming = false;
        explosionHappened = false;
        postExplosionMiasmaCount = 0;
        sireSpawnPos = null;
        firstStunOnSleeping = false;
        sireEngaged = false;
        lastKnownSireId = -1;
        Microbot.enableAutoRunOn = false;
        lastLootCompleteTick = Microbot.getClient().getTickCount(); // startup grace period

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                loop();
            } catch (Exception ex) {
                Microbot.logStackTrace("SireScript", ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * Returns remaining stun ticks, or 0 if not stunned.
     * Accounts for the 10-tick delay when the first stun is cast on a sleeping Sire.
     */
    public long getStunTicksRemaining() {
        if (!sireIsStunned || stunStartTick == 0) return 0;
        long elapsed = Microbot.getClient().getTickCount() - stunStartTick;
        // If the first stun was cast on a sleeping sire, the stun doesn't apply
        // for 10 ticks, so effective duration is reduced accordingly
        int effectiveDuration = STUN_DURATION_TICKS;
        if (firstStunOnSleeping) {
            effectiveDuration = Math.max(0, STUN_DURATION_TICKS - FIRST_STUN_DELAY_TICKS);
        }
        long remaining = effectiveDuration - elapsed;
        return Math.max(0, remaining);
    }

    // ── Event Handlers (called by SirePlugin) ────────────

    /**
     * Called when Sire is first detected — sets phase and computes arena positions.
     */
    public void onSireDetected(int phase) {
        if (currentPhase == 0) {
            currentPhase = phase;
            log("Sire detected — Phase " + phase);
        }
        sireEngaged = true;
    }

    /**
     * Called when Sire becomes stunned (NPC ID changes to STUNNED).
     */
    public void onSireStunned() {
        sireIsStunned = true;
        sireEngaged = true;
        stunStartTick = Microbot.getClient().getTickCount();
        log("Sire stunned! Stun starts at tick " + stunStartTick);
    }

    /**
     * Called when miasma attack is detected — flag for dodge.
     * Records the player's current tile as a miasma pool (miasma targets where you stand).
     * Also tracks post-explosion miasma count in Phase 3 Stage II.
     */
    public void onMiasmaDetected() {
        miasmaIncoming = true;
        // Track post-explosion miasma (Sire does 3 more after explosion, then stops if 15 minions alive)
        if (explosionHappened) {
            postExplosionMiasmaCount++;
            log("Post-explosion miasma " + postExplosionMiasmaCount + "/" + POST_EXPLOSION_MIASMA_LIMIT);
        }
        // Miasma targets the player's current position
        Player player = Microbot.getClient().getLocalPlayer();
        if (player != null) {
            addMiasmaAoE(player.getWorldLocation());
            log("Miasma attack at " + player.getWorldLocation() + " (total tracked: " + miasmaPools.size() + ")");
        }
    }

    /**
     * Called when a miasma spot animation spawns on the ground.
     * Provides accurate ground-truth position (vs the prediction in onMiasmaDetected).
     */
    public void onMiasmaPoolSpawned(WorldPoint loc) {
        addMiasmaAoE(loc);
    }

    /**
     * Add a miasma pool at the given center tile + 3x3 AoE.
     */
    private void addMiasmaAoE(WorldPoint center) {
        miasmaPools.add(center);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                miasmaPools.add(new WorldPoint(
                        center.getX() + dx, center.getY() + dy, center.getPlane()));
            }
        }
    }

    /**
     * Called when Phase 3 explosion teleport is detected.
     * Wiki: Player is always teleported to the western tile of Row 2 (2969, 4772).
     * Two ticks later the explosion fires. Spam-click Row 3 immediately.
     *
     * Since we know the exact landing tile, we can pre-walk to ROW3_LEFT
     * immediately from the event thread. Even if the teleport hasn't visually
     * resolved yet, the walk command will queue and execute as soon as the
     * player lands.
     */
    public void onExplosionTeleport() {
        explosionIncoming = true;
        explosionHappened = true;
        postExplosionMiasmaCount = 0;
        log("Explosion teleport detected — spam-click Row 3! (target: " + EXPLOSION_DODGE_TARGET + ")");
        // Pre-emptively walk to ROW3_LEFT from the event thread.
        // We know the player will land at ROW2_LEFT, so ROW3_LEFT (2 tiles south)
        // is always the correct target. Fire it now to shave off main-loop latency.
        try {
            LocalPoint local = LocalPoint.fromWorld(Microbot.getClient(), EXPLOSION_DODGE_TARGET);
            if (local != null) {
                Rs2Walker.walkFastLocal(local);
            }
        } catch (Exception e) {
            // Non-critical — main loop will handle the dodge
        }
    }

    /**
     * Called on phase transition.
     */
    public void onPhaseTransition(int newPhase) {
        if (currentPhase != newPhase) {
            log("Phase transition: " + currentPhase + " → " + newPhase);
            currentPhase = newPhase;
            sireIsStunned = false;
            sireEngaged = true;
            if (newPhase == 2) {
                sirePanicking = false;
            }
        }
    }

    /**
     * Called when Sire enters panic mode (≤50% HP in Phase 2).
     */
    public void onSirePanicking() {
        if (!sirePanicking) {
            sirePanicking = true;
            log("Sire panicking — switching to Protect from Missiles!");
        }
    }

    /**
     * Called when a respiratory system (lung) is destroyed.
     */
    public void onVentDestroyed() {
        if (ventsDestroyed >= 4) return; // guard against double-counting
        ventsDestroyed++;
        log("Vent destroyed! Total: " + ventsDestroyed + "/4");
    }

    /**
     * Called when Sire dies.
     */
    public void onSireDeath() {
        if (state == SireState.LOOTING) return; // already processed
        killCount++;
        sireEngaged = false;
        lastKnownSireId = -1;
        resetFightState();
        state = SireState.LOOTING;
        log("Sire killed! Total: " + killCount);
    }

    @Override
    public void shutdown() {
        try {
            togglePrayers(false);
        } catch (Exception ignored) {
        }
        state = SireState.IDLE;
        status = "Stopped";
        super.shutdown();
    }

    // ── Tick-Aligned Interruptible Sleep ──────────────────

    /**
     * Sleep up to 1 game tick (600ms) but wake early if an urgent dodge event fires.
     *
     * @return true if interrupted by a dodge event
     */
    private boolean tickSleep() {
        return sleepUntil(() -> explosionIncoming || miasmaIncoming, 600);
    }

    // ── Main Loop ────────────────────────────────────────

    private void loop() {
        Client client = Microbot.getClient();
        Player player = client.getLocalPlayer();
        if (player == null) return;

        // ── 0. Safety: eat & emergency teleport ──
        int hpPercent = getHpPercent();
        boolean hasHealing = hasHealingSupplies();

        if (!hasHealing && hpPercent <= config.emergencyTeleportHp()) {
            status = "Emergency teleport!";
            emergencyTeleport();
            return;
        }
        if (hpPercent <= config.eatAtHpPercent() && hasHealing) {
            status = "Eating...";
            state = SireState.EATING;
            Rs2Player.eatAt(config.eatAtHpPercent());
            if (tickSleep()) return;
        }

        // ── 1. CRITICAL: Phase 3 explosion dodge ──
        // Wiki: Player is teleported to western tile of Row 2 (2969, 4772).
        // Two ticks later explosion fires dealing up to 96 damage.
        // Dodge target is ALWAYS ROW3_LEFT (2969, 4770) — 2 tiles straight south.
        // "As soon as the teleport occurs, spam-click on Row 3 to avoid the attack."
        if (explosionIncoming) {
            status = "Dodging explosion — spam-clicking Row 3!";
            state = SireState.DODGING_EXPLOSION;
            log("Explosion dodge: player at " + player.getWorldLocation() + " → target " + EXPLOSION_DODGE_TARGET);
            // Spam-click the known safe tile with zero gap
            walkToSafe(EXPLOSION_DODGE_TARGET);
            walkToSafe(EXPLOSION_DODGE_TARGET);
            walkToSafe(EXPLOSION_DODGE_TARGET);
            // Keep spam-clicking until we arrive (only 2 tiles, should be <1 game tick)
            sleepUntil(() -> {
                Player p = Microbot.getClient().getLocalPlayer();
                if (p == null) return false;
                if (p.getWorldLocation().distanceTo(EXPLOSION_DODGE_TARGET) <= 1) {
                    return true;
                }
                walkToSafe(EXPLOSION_DODGE_TARGET);
                return false;
            }, 1800);
            explosionDodgeTick = Microbot.getClient().getTickCount();
            explosionIncoming = false;
            return;
        }

        // ── 2. Miasma pool dodge ──
        if (miasmaIncoming) {
            status = "Dodging miasma!";
            state = SireState.DODGING_MIASMA;
            dodgeMiasmaPhaseAware(player);
            miasmaIncoming = false;
            // In Phase 3, re-attack immediately after dodge (“run back and forth while attacking”)
            if (currentPhase == 3) {
                Rs2NpcModel sireNow = findSire();
                if (sireNow != null) {
                    Rs2Npc.interact(sireNow, "attack");
                    tickSleep();
                }
            }
            return;
        }

        // ── 2b. Check if standing on a tracked miasma pool ──
        if (miasmaPools.contains(player.getWorldLocation())) {
            status = "Moving off miasma pool!";
            state = SireState.DODGING_MIASMA;
            dodgeMiasmaPhaseAware(player);
            // In Phase 3, re-attack immediately after dodge
            if (currentPhase == 3) {
                Rs2NpcModel sireNow = findSire();
                if (sireNow != null) {
                    Rs2Npc.interact(sireNow, "attack");
                    tickSleep();
                }
            }
            return;
        }

        // ── 2c. Periodically clean stale miasma pools ──
        // Miasma pools last ~15 ticks; clean every 20 ticks to remove expired ones
        // while keeping recent pools tracked.
        long currentTick = Microbot.getClient().getTickCount();
        if (!miasmaPools.isEmpty() && currentTick - lastMiasmaCleanTick > 20) {
            miasmaPools.clear();
            lastMiasmaCleanTick = currentTick;
        }

        // ── 3. Anti-poison check ──
        // Only drink when actually poisoned (varp > 0) or venomed, not preventatively.
        // The POISON varp is: >0 = poisoned, 0 = nothing, <0 = protection active.
        // Also enforce a cooldown to prevent double-drinking before the varp updates.
        if (config.useAntiPoison()
                && Rs2Player.antiPoisonTime > 0
                && !Rs2Player.hasAntiVenomActive()
                && currentTick - lastAntiPoisonTick > ANTIPOISON_COOLDOWN_TICKS) {
            if (Rs2Inventory.interact(
                    Rs2Potion.getAntiPoisonVariants().toArray(String[]::new), "drink")) {
                lastAntiPoisonTick = currentTick;
                tickSleep();
            }
        }

        // ── 4. Find Sire — determine phase ──
        Rs2NpcModel sire = findSire();

        if (sire == null) {
            // If we're engaged in a fight (sireEngaged=true, currentPhase>0),
            // the Sire is just out of render distance — don't reset state.
            // This happens during Phase 1 vent destruction (player moves to far vents)
            // or during miasma/explosion dodging.
            if (sireEngaged && currentPhase > 0) {
                // Still eat/pot/pray while Sire is out of view
                Rs2Player.eatAt(config.eatAtHpPercent());
                drinkPrayerPotion();
                drinkPotions();

                // Phase 1: We can still attack vents even if the Sire NPC is out of range.
                // The stun timer is tick-based, so we track it without needing the NPC reference.
                if (currentPhase == 1 && sireIsStunned) {
                    status = "Phase 1 — destroying vents (Sire out of range)";
                    handlePhase1VentsOnly(player);
                    return;
                }

                status = "Phase " + currentPhase + " — Sire out of range, waiting...";
                return;
            }
            handleNoBoss();
            return;
        }

        // Mark as engaged once we see the Sire
        sireEngaged = true;
        lastKnownSireId = sire.getId();

        // Set Sire position on first sighting (for tile scoring and miasma reference)
        if (sireSpawnPos == null) {
            sireSpawnPos = sire.getWorldLocation();
            log("Sire detected at " + sireSpawnPos);
        }

        // Determine phase from NPC ID if not yet set
        if (currentPhase == 0) {
            currentPhase = determinePhase(sire.getId());
        }

        // ── 5. Phase dispatch ──
        switch (currentPhase) {
            case 1:
                handlePhase1(player, sire);
                break;
            case 2:
                handlePhase2(player, sire);
                break;
            case 3:
                handlePhase3(player, sire);
                break;
            default:
                status = "Waiting for Sire...";
                state = SireState.IDLE;
                break;
        }
    }

    // ── Phase 1: Stun + Destroy Vents ────────────────────

    private void handlePhase1(Player player, Rs2NpcModel sire) {
        // Check if stun expired — need to re-stun
        long stunRemaining = getStunTicksRemaining();
        boolean needStun = !sireIsStunned || stunRemaining <= RESTUN_THRESHOLD_TICKS;

        // Track whether first stun targets a sleeping sire (10-tick delay)
        boolean targetIsSleeping = sire.getId() == SIRE_SLEEPING;

        // If Sire NPC ID shows awake (not stunned), definitely need stun
        if (sire.getId() == SIRE_AWAKE || targetIsSleeping) {
            needStun = true;
            sireIsStunned = false;
        }

        if (needStun) {
            status = "Stunning Sire with Shadow Barrage";
            state = SireState.STUNNING_SIRE;

            // Track if this is the first stun on a sleeping Sire (10-tick delay before stun applies)
            firstStunOnSleeping = targetIsSleeping;

            // Cast Shadow Barrage on the Sire
            Rs2Magic.castOn(MagicAction.SHADOW_BARRAGE, sire);
            if (tickSleep()) return;

            // Wait for stun to register (NPC ID changes to STUNNED)
            // If casting on sleeping Sire, allow extra time for 10-tick delay
            int stunWaitMs = targetIsSleeping ? 7000 : 3000;
            sleepUntil(() -> {
                Rs2NpcModel s = Rs2Npc.getNpc(SIRE_STUNNED);
                return s != null;
            }, stunWaitMs);

            Rs2NpcModel stunnedSire = Rs2Npc.getNpc(SIRE_STUNNED);
            if (stunnedSire != null) {
                sireIsStunned = true;
                stunStartTick = Microbot.getClient().getTickCount();
                log("Sire stunned successfully at tick " + stunStartTick
                        + (firstStunOnSleeping ? " (sleeping target — 10-tick delay applied)" : ""));
            } else {
                log("WARNING: Sire stun may have failed — retrying next loop");
                return;
            }
        }

        // ── Equip Scorching bow (only if not already equipped) ──
        String bow = config.scorchingBow();
        if (!bow.isEmpty() && !Rs2Equipment.isWearing(bow)) {
            status = "Equipping " + bow;
            if (!Rs2Inventory.wield(bow)) {
                log("WARNING: " + bow + " not in inventory!");
                return;
            }
            sleepUntil(() -> Rs2Equipment.isWearing(config.scorchingBow()), 600);
        }

        // ── Find ALL alive lungs ──
        List<Rs2NpcModel> aliveLungs = Rs2Npc.getNpcs(LUNG)
                .filter(l -> !l.isDead())
                .collect(Collectors.toList());

        if (aliveLungs.isEmpty()) {
            // Check for dying lungs too — they may still be transitioning
            boolean hasDying = Rs2Npc.getNpcs(LUNG_DYING).findAny().isPresent();
            if (ventsDestroyed >= 4 || (!hasDying && ventsDestroyed > 0)) {
                handlePhase1Transition(player, sire);
                return;
            }
            status = "Searching for next vent...";
            return;
        }

        // Pick the lung to attack based on prescribed order.
        Rs2NpcModel picked = pickNextLungInOrder(aliveLungs);
        final Rs2NpcModel targetLung = picked != null ? picked : aliveLungs.get(0);

        status = "Destroying vent " + (ventsDestroyed + 1) + "/4";
        state = SireState.DESTROYING_VENTS;

        // ── Position on a vent stand-tile within 10-range of the target lung ──
        WorldPoint lungLoc = targetLung.getWorldLocation();
        WorldPoint bestStand = bestVentStandForLung(lungLoc);

        // Walk to stand-tile and VERIFY we are actually within attack range (≤10 tiles)
        // before firing. If we're not in range, the interact() will make the player walk
        // instead of shoot — and we'd move on thinking the shot landed.
        if (bestStand != null) {
            int distToStand = player.getWorldLocation().distanceTo(bestStand);
            if (distToStand > 1) {
                walkToSafe(bestStand);
                sleepUntil(() -> {
                    Player p = Microbot.getClient().getLocalPlayer();
                    return p != null && p.getWorldLocation().distanceTo(bestStand) <= 1;
                }, 3000);
            }
        } else {
            // No good stand-tile — walk within 10 of the lung directly
            int distToLung = player.getWorldLocation().distanceTo(lungLoc);
            if (distToLung > 10) {
                walkToSafe(lungLoc);
                sleepUntil(() -> {
                    Player p = Microbot.getClient().getLocalPlayer();
                    return p != null && p.getWorldLocation().distanceTo(lungLoc) <= 10;
                }, 3000);
            }
        }

        // ── Double-check: are we actually within 10 tiles of the lung? ──
        // Re-fetch player position after walking.
        Player current = Microbot.getClient().getLocalPlayer();
        if (current != null && current.getWorldLocation().distanceTo(targetLung.getWorldLocation()) > 10) {
            log("WARNING: Still out of range of lung (" + current.getWorldLocation().distanceTo(targetLung.getWorldLocation())
                    + " tiles) — retrying next loop");
            return; // Will retry positioning next loop iteration
        }

        // ── Attack and CONFIRM the vent dies ──
        // We must verify the vent actually died (ventsDestroyed increments) before moving on.
        // Re-attack within the wait loop if the player stops interacting (shot missed or didn't fire).
        Rs2Npc.interact(targetLung, "attack");

        final int ventsBefore = ventsDestroyed;
        sleepUntil(() -> {
            if (explosionIncoming || miasmaIncoming) return true;
            if (getStunTicksRemaining() <= RESTUN_THRESHOLD_TICKS) return true;
            return ventsDestroyed > ventsBefore;
        }, () -> {
            // Re-attack if we stopped interacting — the shot may not have fired.
            // IMPORTANT: Do NOT send walk commands here. If the player is walking
            // toward the lung to attack, isInteracting() may be false during the
            // walk phase. Sending a walk-to-stand-tile would fight the game's
            // own pathfinding and cause bouncing.
            Player p = Microbot.getClient().getLocalPlayer();
            if (p == null) return;
            // Only re-attack if player is truly idle (not animating, not moving)
            boolean isMoving = p.getPoseAnimation() != p.getIdlePoseAnimation();
            if (!p.isInteracting() && !isMoving && p.getAnimation() == -1) {
                List<Rs2NpcModel> stillAlive = Rs2Npc.getNpcs(LUNG)
                        .filter(l -> !l.isDead())
                        .collect(Collectors.toList());
                if (!stillAlive.isEmpty()) {
                    // Just re-attack — let the game handle walking into range
                    Rs2NpcModel closest = stillAlive.stream()
                            .min(Comparator.comparingInt(l ->
                                    p.getWorldLocation().distanceTo(l.getWorldLocation())))
                            .orElse(null);
                    if (closest != null) {
                        Rs2Npc.interact(closest, "attack");
                    }
                }
            }
            // Eat only if actually low
            if (getHpPercent() <= config.eatAtHpPercent()) {
                Rs2Player.eatAt(config.eatAtHpPercent());
            }
        }, 5000, 100);

        // ── Once vent is confirmed dead, pre-walk to the NEXT vent's stand-tile ──
        // This is the "move" part of shoot-and-move, but only AFTER confirmation.
        if (ventsDestroyed > ventsBefore) {
            List<Rs2NpcModel> nextLungs = Rs2Npc.getNpcs(LUNG)
                    .filter(l -> !l.isDead())
                    .collect(Collectors.toList());
            if (!nextLungs.isEmpty() && getStunTicksRemaining() > RESTUN_THRESHOLD_TICKS) {
                Rs2NpcModel nextTarget = pickNextLungInOrder(nextLungs);
                if (nextTarget == null) nextTarget = nextLungs.get(0);
                WorldPoint nextStand = bestVentStandForLung(nextTarget.getWorldLocation());
                if (nextStand != null) {
                    status = "Moving to next vent";
                    walkToSafe(nextStand);
                    // Don't wait for arrival — the next loop iteration will handle positioning
                }
            }
        }

        // Only drink prayer potion if critically low
        int prayerPercent = (Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100)
                / Math.max(1, Microbot.getClient().getRealSkillLevel(Skill.PRAYER));
        if (prayerPercent < 15) {
            drinkPrayerPotion();
        }
    }

    /**
     * Attack the Sire during Phase 1→2 transition.
     * Wiki: damage is reduced by 50% but the transition time is significant enough to deal some damage.
     * Keep attacking with Scorching bow until the Sire reaches melee range, then switch to melee.
     */
    private void handlePhase1Transition(Player player, Rs2NpcModel sire) {
        status = "Phase 1→2 transition — attacking Sire!";
        state = SireState.TRANSITION_ATTACK;

        // Continue attacking with bow while Sire walks out to Phase 2 position
        if (!player.isInteracting()) {
            Rs2Npc.interact(sire, "attack");
            tickSleep();
        }

        // Check if Sire has transitioned to Phase 2 NPC ID
        Rs2NpcModel p2Sire = Rs2Npc.getNpc(SIRE_PUPPET);
        if (p2Sire == null) p2Sire = Rs2Npc.getNpc(SIRE_WANDERING);

        if (p2Sire != null) {
            // Phase 2 started — equip melee gear for Phase 2
            equipMeleeGear();
            currentPhase = 2;
            sireIsStunned = false;
            firstStunOnSleeping = false;
            log("Phase 2 transition complete — switching to melee");
        }
        // Otherwise, keep looping — transition still in progress
    }

    /**
     * Stripped-down Phase 1 vent destruction for when the Sire NPC is out of render distance.
     * Only handles equipping bow, finding lungs, positioning, and attacking.
     * Stun tracking continues via tick-based timer (no NPC reference needed).
     * If stun timer is about to expire, walks back toward the Sire to re-stun.
     */
    private void handlePhase1VentsOnly(Player player) {
        // Check stun timer — if about to expire, need to walk back to re-stun
        long stunRemaining = getStunTicksRemaining();
        if (stunRemaining <= RESTUN_THRESHOLD_TICKS) {
            status = "Stun expiring — returning to re-stun!";
            walkToSafe(STUN_TILE);
            sleepUntil(() -> {
                Player p = Microbot.getClient().getLocalPlayer();
                return p != null && p.getWorldLocation().distanceTo(STUN_TILE) <= 2;
            }, 3000);
            return; // Next loop will find the Sire and re-stun via handlePhase1
        }

        // Equip Scorching bow
        String bow = config.scorchingBow();
        if (!bow.isEmpty() && !Rs2Equipment.isWearing(bow)) {
            status = "Equipping " + bow;
            if (!Rs2Inventory.wield(bow)) {
                log("WARNING: " + bow + " not in inventory!");
                return;
            }
            if (tickSleep()) return;
            sleepUntil(() -> Rs2Equipment.isWearing(config.scorchingBow()), 1200);
        }

        // Find alive lungs
        List<Rs2NpcModel> aliveLungs = Rs2Npc.getNpcs(LUNG)
                .filter(l -> !l.isDead())
                .collect(Collectors.toList());

        if (aliveLungs.isEmpty()) {
            boolean hasDying = Rs2Npc.getNpcs(LUNG_DYING).findAny().isPresent();
            if (ventsDestroyed >= 4 || (!hasDying && ventsDestroyed > 0)) {
                // All vents done — walk back to Sire for Phase 2 transition
                status = "All vents destroyed — returning to Sire";
                walkToSafe(STUN_TILE);
                return;
            }
            status = "Searching for next vent...";
            return;
        }

        Rs2NpcModel targetLung = aliveLungs.get(0);
        status = "Destroying vent " + (ventsDestroyed + 1) + "/4 (Sire out of range)";
        state = SireState.DESTROYING_VENTS;

        // Position within range — verify distance ≤ 10 before attacking
        WorldPoint lungLoc = targetLung.getWorldLocation();
        WorldPoint bestStand = bestVentStandForLung(lungLoc);

        if (bestStand != null) {
            int distToStand = player.getWorldLocation().distanceTo(bestStand);
            if (distToStand > 1) {
                walkToSafe(bestStand);
                sleepUntil(() -> {
                    Player p = Microbot.getClient().getLocalPlayer();
                    return p != null && p.getWorldLocation().distanceTo(bestStand) <= 1;
                }, 3000);
            }
        } else {
            int distToLung = player.getWorldLocation().distanceTo(lungLoc);
            if (distToLung > 10) {
                walkToSafe(lungLoc);
                sleepUntil(() -> {
                    Player p = Microbot.getClient().getLocalPlayer();
                    return p != null && p.getWorldLocation().distanceTo(lungLoc) <= 10;
                }, 3000);
            }
        }

        // Double-check range
        Player current = Microbot.getClient().getLocalPlayer();
        if (current != null && current.getWorldLocation().distanceTo(targetLung.getWorldLocation()) > 10) {
            log("WARNING: VentsOnly — still out of range, retrying next loop");
            return;
        }

        // Attack and confirm vent death with re-attack fallback
        Rs2Npc.interact(targetLung, "attack");

        final int ventsBefore = ventsDestroyed;
        sleepUntil(() -> {
            if (explosionIncoming || miasmaIncoming) return true;
            if (getStunTicksRemaining() <= RESTUN_THRESHOLD_TICKS) return true;
            return ventsDestroyed > ventsBefore;
        }, () -> {
            Player p = Microbot.getClient().getLocalPlayer();
            if (p != null && !p.isInteracting()) {
                List<Rs2NpcModel> stillAlive = Rs2Npc.getNpcs(LUNG)
                        .filter(l -> !l.isDead())
                        .collect(Collectors.toList());
                if (!stillAlive.isEmpty()) {
                    Rs2NpcModel closest = stillAlive.stream()
                            .min(Comparator.comparingInt(l ->
                                    p.getWorldLocation().distanceTo(l.getWorldLocation())))
                            .orElse(null);
                    if (closest != null && p.getWorldLocation().distanceTo(closest.getWorldLocation()) <= 10) {
                        Rs2Npc.interact(closest, "attack");
                    }
                }
            }
        }, 5000, 100);

        // Pre-walk to next vent after confirmed kill
        if (ventsDestroyed > ventsBefore) {
            List<Rs2NpcModel> nextLungs = Rs2Npc.getNpcs(LUNG)
                    .filter(l -> !l.isDead())
                    .collect(Collectors.toList());
            if (!nextLungs.isEmpty() && getStunTicksRemaining() > RESTUN_THRESHOLD_TICKS) {
                WorldPoint nextStand = bestVentStandForLung(nextLungs.get(0).getWorldLocation());
                if (nextStand != null) {
                    walkToSafe(nextStand);
                }
            }
        }
    }

    // ── Phase 2: Melee Combat ────────────────────────────

    private void handlePhase2(Player player, Rs2NpcModel sire) {
        state = SireState.FIGHTING_MELEE;

        // ── Prayer management ──
        // Wiki: Protect from Melee in normal Phase 2.
        // When Sire panics (≤50% HP), switch to Protect from Missiles (scions use ranged).
        if (sirePanicking && config.protectFromMissiles()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        } else if (config.protectFromMelee()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        }
        if (config.usePiety()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        }

        // ── Defence drain spec at start of Phase 2 ──
        // Wiki: "If reducing the Sire's defence, use one special attack now"
        if (config.useSpecialAttack() && specHitsCompleted < config.specCount()) {
            if (performSpecialAttack(sire)) {
                return;
            }
        }

        // ── Ensure melee weapon is equipped ──
        String mainWeapon = config.mainWeapon();
        if (!mainWeapon.isEmpty() && !Rs2Equipment.isWearing(mainWeapon)) {
            equipMeleeGear();
            if (tickSleep()) return;
        }

        // ── Dodge miasma before engaging ──
        // Wiki: "Watch for miasma pools underneath the player; when they appear
        // run to the other marked tile on Row 1"
        if (miasmaPools.contains(player.getWorldLocation())) {
            status = "Phase 2 — dodging miasma!";
            state = SireState.DODGING_MIASMA;
            dodgeMiasmaPhaseAware(player);
            return;
        }

        // ── Position management ──
        if (sirePanicking) {
            // Wiki: "Run to Row 2 and activate Protect from Missiles"
            // Sire walks south when panicking — position at Row 2
            int distToRow2 = player.getWorldLocation().distanceTo(closestOf(player, ROW2_LEFT, ROW2_RIGHT));
            if (distToRow2 > 2) {
                status = "Phase 2 (panicking) — moving to Row 2!";
                WorldPoint target = closestOf(player, ROW2_LEFT, ROW2_RIGHT);
                if (!miasmaPools.contains(target)) {
                    walkToSafe(target);
                } else {
                    WorldPoint alt = target.equals(ROW2_LEFT) ? ROW2_RIGHT : ROW2_LEFT;
                    walkToSafe(alt);
                }
                sleep(600);
                return;
            }
        } else {
            // Wiki: "Stay on Row 1 during this phase, as attacking from outside melee
            // distance will cause the Sire to teleport you to itself and discharge
            // a damaging blast of energy."
            // Check distance to Row 1 tiles (not Sire) — the player must be ON Row 1.
            WorldPoint closestRow1 = closestOf(player, ROW1_LEFT, ROW1_RIGHT);
            int distToRow1 = player.getWorldLocation().distanceTo(closestRow1);
            if (distToRow1 > 1) {
                status = "Phase 2 — moving to Row 1 (" + distToRow1 + " tiles)!";
                if (!miasmaPools.contains(closestRow1)) {
                    walkToSafe(closestRow1);
                } else {
                    WorldPoint alt = closestRow1.equals(ROW1_LEFT) ? ROW1_RIGHT : ROW1_LEFT;
                    if (!miasmaPools.contains(alt)) {
                        walkToSafe(alt);
                    } else {
                        // Both Row 1 tiles contaminated — walk directly to Sire
                        walkToSafe(sire.getWorldLocation());
                    }
                }
                sleep(600);
                return;
            }
        }

        // ── Fight the Sire ──
        status = sirePanicking ? "Phase 2 (panicking) — fighting!" : "Phase 2 — fighting!";

        if (!Rs2Combat.inCombat() || !player.isInteracting()) {
            Rs2Npc.interact(sire, "attack");
            tickSleep();
        }

        // Potions and food
        drinkPotions();
        drinkPrayerPotion();
        Rs2Player.eatAt(config.eatAtHpPercent());
    }

    // ── Phase 3: Apocalypse ──────────────────────────────

    private void handlePhase3(Player player, Rs2NpcModel sire) {
        // ── Prayer: Protect from Missiles in Phase 3 ──
        // Wiki: "Protect from Missiles is recommended, as their ranged attacks are more accurate"
        if (config.protectFromMissiles()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        }
        if (config.usePiety()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        }

        // ── Ensure melee weapon is equipped ──
        String mainWeapon = config.mainWeapon();
        if (!mainWeapon.isEmpty() && !Rs2Equipment.isWearing(mainWeapon)) {
            equipMeleeGear();
            if (tickSleep()) return;
        }

        // ── CRITICAL: Explosion dodge (backup check inside handlePhase3) ──
        // Same logic as main loop: spam-click ROW3_LEFT.
        if (explosionIncoming) {
            status = "Phase 3 — dodging explosion to Row 3!";
            state = SireState.DODGING_EXPLOSION;
            log("P3 handlePhase3 explosion dodge: player at " + player.getWorldLocation() + " → " + EXPLOSION_DODGE_TARGET);
            walkToSafe(EXPLOSION_DODGE_TARGET);
            walkToSafe(EXPLOSION_DODGE_TARGET);
            walkToSafe(EXPLOSION_DODGE_TARGET);
            sleepUntil(() -> {
                Player p = Microbot.getClient().getLocalPlayer();
                if (p == null) return false;
                if (p.getWorldLocation().distanceTo(EXPLOSION_DODGE_TARGET) <= 1) {
                    return true;
                }
                walkToSafe(EXPLOSION_DODGE_TARGET);
                return false;
            }, 1800);
            explosionDodgeTick = Microbot.getClient().getTickCount();
            explosionIncoming = false;
            return;
        }

        // ── Dodge miasma (P3 miasma has NO animation — relies on GraphicsObject tracking) ──
        // Wiki: "run back and forth on Row 2 while attacking"
        // After dodging, immediately re-engage the Sire before returning.
        if (miasmaPools.contains(player.getWorldLocation())) {
            status = "Phase 3 — dodging miasma!";
            state = SireState.DODGING_MIASMA;
            dodgeMiasmaPhaseAware(player);
            // Re-attack immediately after dodge — don't waste a full loop cycle
            if (!Rs2Combat.inCombat() || !player.isInteracting()) {
                Rs2Npc.interact(sire, "attack");
                tickSleep();
            }
            return;
        }

        // ── Phase 3 low-HP safety retreat ──
        // Wiki: "If the player's health drops below 40, they should run away for a brief respite."
        // IMPORTANT: In P3, NEVER leave Row 2/Row 3 boundaries — tentacles do huge damage.
        // Retreat to Row 3 (safe from tentacles) and eat there.
        int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        if (currentHp > 0 && currentHp < config.phase3RetreatHp()) {
            boolean hasHealing = hasHealingSupplies();
            if (hasHealing) {
                status = "Phase 3 — retreating to Row 3 to eat! (HP: " + currentHp + ")";
                state = SireState.RETREATING;
                // Retreat to Row 3 — safe from tentacles and far enough from Sire
                WorldPoint retreatTarget = closestOf(player, ROW3_LEFT, ROW3_RIGHT);
                if (miasmaPools.contains(retreatTarget)) {
                    retreatTarget = retreatTarget.equals(ROW3_LEFT) ? ROW3_RIGHT : ROW3_LEFT;
                }
                walkToSafe(retreatTarget);
                sleep(600);
                Rs2Player.eatAt(100); // eat up as much as possible
                tickSleep();
                Rs2Player.eatAt(100);
                sleep(600);
                return;
            }
        }

        // ── Post-explosion Stage II logic ──
        if (explosionHappened) {
            state = SireState.FIGHTING_PHASE3_FINAL;

            // Wiki: "After the explosion the Sire creates a huge number of spawns.
            // It will use its miasma pool attack a minimum of three more times,
            // then stop as long as 15 minions are left alive."
            // Wiki: "should run back and forth on Row 2 while attacking until it has
            // made three miasma pools, after which it is safe to stop moving"
            boolean miasmaPhaseOver = postExplosionMiasmaCount >= POST_EXPLOSION_MIASMA_LIMIT;

            if (miasmaPhaseOver) {
                status = "Phase 3 Final — safe to stand! Finishing Sire";
            } else {
                status = "Phase 3 Final — dodging post-explosion miasma ("
                        + postExplosionMiasmaCount + "/" + POST_EXPLOSION_MIASMA_LIMIT + ")";
            }

            // ── Use damage specs in Phase 3 Stage II ──
            // Wiki: "Use any remaining specs" after the explosion
            if (config.useDamageSpec()) {
                if (performDamageSpec(sire)) {
                    return;
                }
            }

            // Keep fighting
            if (!Rs2Combat.inCombat() || !player.isInteracting()) {
                Rs2Npc.interact(sire, "attack");
                tickSleep();
            }
        } else {
            // ── Phase 3 Stage I: pre-explosion ──
            // Wiki: "When the Sire walks to the centre of the room at the start of Phase 3,
            //        run to Row 2." Tentacles reawaken so Row 1 is no longer safe.
            state = SireState.FIGHTING_PHASE3;

            // Ensure we are on Row 2 (coming from Phase 2 we may still be on Row 1)
            WorldPoint closestRow2 = closestOf(player, ROW2_LEFT, ROW2_RIGHT);
            int distToRow2 = player.getWorldLocation().distanceTo(closestRow2);
            if (distToRow2 > 2) {
                status = "Phase 3 — moving to Row 2!";
                if (!miasmaPools.contains(closestRow2)) {
                    walkToSafe(closestRow2);
                } else {
                    WorldPoint alt = closestRow2.equals(ROW2_LEFT) ? ROW2_RIGHT : ROW2_LEFT;
                    walkToSafe(alt);
                }
                sleep(600);
                return;
            }

            status = "Phase 3 — fighting!";

            if (!Rs2Combat.inCombat() || !player.isInteracting()) {
                Rs2Npc.interact(sire, "attack");
                tickSleep();
            }
        }

        // Potions and food
        drinkPotions();
        drinkPrayerPotion();
        Rs2Player.eatAt(config.eatAtHpPercent());
    }

    // ── Defence Drain Special Attack ─────────────────────

    /**
     * Perform special attack with Elder Maul / DWH to drain Sire's Defence.
     * Waits for the Sire to be within melee range before speccing.
     */
    private boolean performSpecialAttack(Rs2NpcModel sire) {
        int specEnergy = Microbot.getClient().getVarpValue(300) / 10; // 0-100
        String specWeapon = config.specWeapon();
        int specPerHit = 50; // Elder maul and DWH both use 50%

        if (specEnergy < specPerHit) return false;

        boolean specWeaponEquipped = Rs2Equipment.isWearing(specWeapon);
        if (!specWeaponEquipped && !Rs2Inventory.hasItem(specWeapon)) return false;

        // Wait for Sire to reach Row 1 before speccing.
        // Don't walk toward the Sire — stand on Row 1 and let it come to us.
        // Walking toward it triggers a punishing teleport+blast attack.
        Player player = Microbot.getClient().getLocalPlayer();
        if (player != null) {
            // First ensure we're on Row 1
            WorldPoint closestRow1 = closestOf(player, ROW1_LEFT, ROW1_RIGHT);
            int distToRow1 = player.getWorldLocation().distanceTo(closestRow1);
            if (distToRow1 > 1) {
                status = "Moving to Row 1 for spec...";
                walkToSafe(closestRow1);
                tickSleep();
                return true;
            }
            // Now wait for Sire to be within melee range of us at Row 1
            int npcSize = sire.getComposition() != null ? sire.getComposition().getSize() : 3;
            int dist = player.getWorldLocation().distanceTo(sire.getWorldLocation());
            if (dist > npcSize) {
                status = "Waiting for Sire at Row 1 (spec) — " + dist + " tiles";
                // Use downtime to drink combat pots and antivenom
                drinkPotions();
                drinkAntiVenom();
                drinkPrayerPotion();
                return true; // re-check next loop iteration; do NOT walk toward Sire
            }
        }

        state = SireState.SPEC_ATTACK;

        // Equip spec weapon if not already worn
        if (!specWeaponEquipped) {
            status = "Equipping " + specWeapon;
            if (!Rs2Inventory.wield(specWeapon)) {
                log("WARNING: " + specWeapon + " not in inventory!");
                return false;
            }
            if (tickSleep()) { equipMeleeGear(); return true; }
            sleepUntil(() -> Rs2Equipment.isWearing(config.specWeapon()), 1200);
        }

        status = "Spec " + (specHitsCompleted + 1) + "/" + config.specCount() + ": " + specWeapon;

        // Switch to combat tab and enable spec
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 1200);
        Rs2Combat.setSpecState(true, specPerHit * 10); // raw 0-1000 scale
        if (tickSleep()) { equipMeleeGear(); return true; }

        // Attack Sire with spec enabled
        Rs2Npc.interact(sire, "attack");
        tickSleep();

        // Wait for hit to register
        sleepUntil(() -> Microbot.getClient().getLocalPlayer() != null
                && Microbot.getClient().getLocalPlayer().getAnimation() != -1, 1800);
        tickSleep();

        specHitsCompleted++;
        log("Spec hit " + specHitsCompleted + "/" + config.specCount() + " landed");

        // If more spec hits needed and energy available, stay on spec weapon
        int energyAfter = Microbot.getClient().getVarpValue(300) / 10;
        if (specHitsCompleted < config.specCount() && energyAfter >= specPerHit) {
            return true;
        }

        // All specs done — switch to main weapon
        equipMeleeGear();
        return true;
    }

    // ── Phase 3 Damage Special Attack ────────────────────

    /**
     * Perform damage spec with Burning claws / Dragon claws / Voidwaker during Phase 3.
     * Wiki: "Use any remaining specs and watch your health, as the scions can do a lot of damage."
     */
    private boolean performDamageSpec(Rs2NpcModel sire) {
        String dmgWeapon = config.damageSpecWeapon();
        if (dmgWeapon.isEmpty()) return false;

        int specEnergy = Microbot.getClient().getVarpValue(300) / 10;
        int specCost = config.damageSpecCost();

        if (specEnergy < specCost) return false;

        boolean specEquipped = Rs2Equipment.isWearing(dmgWeapon);
        if (!specEquipped && !Rs2Inventory.hasItem(dmgWeapon)) return false;

        // Ensure close enough to melee — but NEVER walk north of Row 2 in P3
        // (tentacles do huge damage). Stay on Row 2 and let the Sire come to us.
        Player player = Microbot.getClient().getLocalPlayer();
        if (player != null) {
            int npcSize = sire.getComposition() != null ? sire.getComposition().getSize() : 3;
            int dist = player.getWorldLocation().distanceTo(sire.getWorldLocation());
            if (dist > npcSize) {
                // In Phase 3, don't walk toward Sire — stay on Row 2 and wait
                status = "Phase 3 — waiting for Sire to be in range for damage spec";
                WorldPoint row2 = closestOf(player, ROW2_LEFT, ROW2_RIGHT);
                int distToRow2 = player.getWorldLocation().distanceTo(row2);
                if (distToRow2 > 2) {
                    walkToSafe(row2);
                }
                tickSleep();
                return true;
            }
        }

        state = SireState.SPEC_ATTACK;

        // Equip damage spec weapon
        if (!specEquipped) {
            status = "Equipping " + dmgWeapon;
            if (!Rs2Inventory.wield(dmgWeapon)) {
                log("WARNING: " + dmgWeapon + " not in inventory!");
                return false;
            }
            if (tickSleep()) { equipMeleeGear(); return true; }
            sleepUntil(() -> Rs2Equipment.isWearing(config.damageSpecWeapon()), 1200);
        }

        status = "Phase 3 damage spec: " + dmgWeapon;

        // Enable spec and attack
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 1200);
        Rs2Combat.setSpecState(true, specCost * 10);
        if (tickSleep()) { equipMeleeGear(); return true; }

        Rs2Npc.interact(sire, "attack");
        tickSleep();

        // Wait for hit
        sleepUntil(() -> Microbot.getClient().getLocalPlayer() != null
                && Microbot.getClient().getLocalPlayer().getAnimation() != -1, 1800);
        tickSleep();

        log("Phase 3 damage spec landed with " + dmgWeapon);

        // Switch back to main weapon if no more spec energy
        int energyAfter = Microbot.getClient().getVarpValue(300) / 10;
        if (energyAfter < specCost) {
            equipMeleeGear();
        }
        return true;
    }

    // ── Miasma Dodge ─────────────────────────────────────

    /**
     * Phase-aware miasma dodge.
     * Wiki: In Phase 2, "run to the other marked tile on Row 1" when miasma appears.
     * In Phase 3, run back and forth on Row 2 while attacking.
     */
    private void dodgeMiasmaPhaseAware(Player player) {
        WorldPoint loc = player.getWorldLocation();

        if (currentPhase == 2 && !sirePanicking) {
            // Phase 2 normal: swap between Row 1 left/right
            WorldPoint current = closestOf(player, ROW1_LEFT, ROW1_RIGHT);
            WorldPoint other = current.equals(ROW1_LEFT) ? ROW1_RIGHT : ROW1_LEFT;
            if (!miasmaPools.contains(other)) {
                walkToSafe(other);
            } else {
                // Both Row 1 tiles contaminated — try Row 2
                WorldPoint r2 = closestOf(player, ROW2_LEFT, ROW2_RIGHT);
                walkToSafe(r2);
            }
            } else if (currentPhase == 2 && sirePanicking) {
            // Phase 2 panicking: swap between Row 2 left/right
            WorldPoint current = closestOf(player, ROW2_LEFT, ROW2_RIGHT);
            WorldPoint other = current.equals(ROW2_LEFT) ? ROW2_RIGHT : ROW2_LEFT;
            if (!miasmaPools.contains(other)) {
                walkToSafe(other);
            } else {
                // Both Row 2 tiles contaminated — go to Row 3 briefly
                WorldPoint r3 = closestOf(player, ROW3_LEFT, ROW3_RIGHT);
                walkToSafe(r3);
            }
        } else if (currentPhase == 3) {
            // Phase 3: "run back and forth on Row 2 while attacking"
            // EXCEPTION: right after explosion teleport, prefer Row 3 (explosion does 60-96 dmg).
            long ticksSinceExplosion = Microbot.getClient().getTickCount() - explosionDodgeTick;
            boolean inExplosionWindow = ticksSinceExplosion <= EXPLOSION_SAFETY_WINDOW_TICKS;

            if (inExplosionWindow) {
                // Stay on / dodge to Row 3 during explosion safety window
                WorldPoint current = closestOf(player, ROW3_LEFT, ROW3_RIGHT);
                WorldPoint other = current.equals(ROW3_LEFT) ? ROW3_RIGHT : ROW3_LEFT;
                if (!miasmaPools.contains(other)) {
                    walkToSafe(other);
                } else if (!miasmaPools.contains(current) && player.getWorldLocation().distanceTo(current) > 1) {
                    walkToSafe(current);
                } else {
                // Row 3 fully contaminated — swap to other Row 3 tile or stay put
                WorldPoint otherR3 = current.equals(ROW3_LEFT) ? ROW3_RIGHT : ROW3_LEFT;
                walkToSafe(otherR3);
            }
            } else {
                // Normal P3: dodge between Row 2 left/right
                WorldPoint current = closestOf(player, ROW2_LEFT, ROW2_RIGHT);
                WorldPoint other = current.equals(ROW2_LEFT) ? ROW2_RIGHT : ROW2_LEFT;
                if (!miasmaPools.contains(other)) {
                    walkToSafe(other);
                } else if (!miasmaPools.contains(current) && player.getWorldLocation().distanceTo(current) > 1) {
                    walkToSafe(current);
                } else {
                    // Both Row 2 tiles contaminated — go to Row 3 briefly
                    WorldPoint r3 = closestOf(player, ROW3_LEFT, ROW3_RIGHT);
                    walkToSafe(r3);
                }
            }
        } else {
            // Fallback: use generic dodge
            dodgeMiasma(player);
            return;
        }
        sleep(600);
    }

    /**
     * Generic dodge — move to the nearest safe tile.
     * In Phase 3, ONLY use Row 2/Row 3 tiles to avoid tentacle damage.
     * Miasma is 3x3: center does 10-30/tick, outer does 2-8/tick.
     */
    private void dodgeMiasma(Player player) {
        WorldPoint loc = player.getWorldLocation();
        WorldPoint[] candidates;

        if (currentPhase == 3) {
            // Phase 3: STRICTLY Row 2 and Row 3 only — tentacles hit everywhere else
            candidates = new WorldPoint[] {
                    ROW2_LEFT, ROW2_RIGHT, ROW3_LEFT, ROW3_RIGHT
            };
        } else {
            // Other phases: prefer safe row tiles, plus nearby offsets as fallback
            candidates = new WorldPoint[] {
                    closestOf(player, ROW1_LEFT, ROW1_RIGHT),
                    closestOf(player, ROW2_LEFT, ROW2_RIGHT),
                    STUN_TILE,
                    new WorldPoint(loc.getX() + 3, loc.getY(), loc.getPlane()),
                    new WorldPoint(loc.getX() - 3, loc.getY(), loc.getPlane()),
                    new WorldPoint(loc.getX(), loc.getY() + 3, loc.getPlane()),
                    new WorldPoint(loc.getX(), loc.getY() - 3, loc.getPlane()),
            };
        }

        WorldPoint best = pickBestTile(candidates);
        walkToSafe(best);
        sleep(600);
    }

    /**
     * Emergency dodge: run south (away from Sire).
     */
    private void dodgeSouth(Player player, int distance) {
        WorldPoint loc = player.getWorldLocation();
        WorldPoint target = new WorldPoint(loc.getX(), loc.getY() - distance, loc.getPlane());
        Rs2Walker.walkFastLocal(LocalPoint.fromWorld(Microbot.getClient(), target));
        sleep(600);
    }

    // ── Potion Management ────────────────────────────────

    private void drinkPotions() {
        CombatPotionType potionType = config.combatPotionType();
        if (potionType != CombatPotionType.NONE) {
            boolean hasCombatBoost = Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH)
                    > Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
            if (!hasCombatBoost) {
                Rs2Inventory.interact(potionType.getInventoryName(), "drink");
                tickSleep();
            }
        }
    }

    private void drinkPrayerPotion() {
        int prayerPercent = (Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100)
                / Math.max(1, Microbot.getClient().getRealSkillLevel(Skill.PRAYER));
        if (prayerPercent < config.drinkPrayerAtPercent()) {
            Rs2Inventory.interact(Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new), "drink");
            tickSleep();
        }
    }

    /**
     * Drink antivenom/anti-poison if not currently protected.
     * Separate from the main-loop anti-poison check so it can be called during idle waits.
     */
    private void drinkAntiVenom() {
        if (!config.useAntiPoison()) return;
        long currentTick = Microbot.getClient().getTickCount();
        // Drink if poisoned (antiPoisonTime > 0) or if no active antivenom protection
        if ((Rs2Player.antiPoisonTime > 0 || !Rs2Player.hasAntiVenomActive())
                && currentTick - lastAntiPoisonTick > ANTIPOISON_COOLDOWN_TICKS) {
            if (Rs2Inventory.interact(
                    Rs2Potion.getAntiPoisonVariants().toArray(String[]::new), "drink")) {
                lastAntiPoisonTick = currentTick;
                tickSleep();
            }
        }
    }

    // ── Prayer Management ────────────────────────────────

    private void togglePrayers(boolean on) {
        if (Rs2Prayer.isOutOfPrayer()) return;
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, on);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, on);
        if (config.usePiety()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, on);
        }
    }

    // ── Gear Switching ───────────────────────────────────

    private void equipMeleeGear() {
        String mainWeapon = config.mainWeapon();
        String shield = config.mainShield();
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

    // ── Post-Fight Handling ──────────────────────────────

    private void handleNoBoss() {
        if (state == SireState.LOOTING) {
            status = "Looting...";
            lootItems();
            return;
        }

        // During an active fight (currentPhase > 0), the Sire NPC can briefly
        // disappear during NPC ID transitions (Sleeping→Awake, Awake→Stunned, etc.).
        // Do NOT reset fight state — just wait for it to reappear.
        if (currentPhase > 0) {
            status = "Phase " + currentPhase + " — waiting for Sire NPC...";
            // Still eat/pot while waiting
            Rs2Player.eatAt(config.eatAtHpPercent());
            drinkPrayerPotion();
            return;
        }

        // Check supplies before next fight
        // BUT: after looting, the Sire needs time to respawn. If we check supplies
        // immediately, findSire()==null + currentPhase==0 triggers this path and
        // falsely teleports out with a full inventory. Wait for the grace period.
        long ticksSinceLoot = Microbot.getClient().getTickCount() - lastLootCompleteTick;
        if (lastLootCompleteTick > 0 && ticksSinceLoot < SIRE_RESPAWN_GRACE_TICKS) {
            status = "Waiting for Sire respawn... (" + ticksSinceLoot + "/" + SIRE_RESPAWN_GRACE_TICKS + ")";
            state = SireState.IDLE;
            return;
        }

        boolean hasHealing = hasHealingSupplies();
        boolean hasPrayerPots = Rs2Inventory.hasItem(
                Rs2Potion.getPrayerPotionsVariants().toArray(String[]::new));

        // Double-check: also count potions with a "drink" action as potential healing
        boolean hasDrinkables = !Rs2Inventory.getPotions().isEmpty();

        log("Supply check: hasHealing=" + hasHealing
                + ", hasPrayerPots=" + hasPrayerPots
                + ", hasDrinkables=" + hasDrinkables
                + ", invSize=" + Rs2Inventory.size());

        if (!hasHealing && !hasDrinkables) {
            status = "No food or potions — teleporting";
            log("Teleporting: no healing supplies at all");
            emergencyTeleport();
            return;
        }
        if (!hasHealing || !hasPrayerPots) {
            // Warn but DON'T teleport if we still have drinkables (brews, restores, etc.)
            // Only teleport if we truly have nothing left.
            if (!hasDrinkables) {
                status = "Low supplies — teleporting (food=" + hasHealing + ", prayer=" + hasPrayerPots + ")";
                log("Teleporting: hasHealing=" + hasHealing + ", hasPrayerPots=" + hasPrayerPots);
                emergencyTeleport();
                return;
            }
            log("Low food/prayer but have drinkables — continuing fight");
        }

        status = "Waiting for Sire...";
        state = SireState.IDLE;
    }

    private void lootItems() {
        togglePrayers(false);

        // Eat food to make room in inventory
        if (Rs2Inventory.isFull()) {
            boolean hasFood = !Rs2Inventory.getInventoryFood().isEmpty();
            if (hasFood) {
                Rs2Player.eatAt(100);
                tickSleep();
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

        Rs2GroundItem.lootItemBasedOnValue(params);

        // Always loot unique drops
        Rs2GroundItem.loot("Unsired", 20);
        Rs2GroundItem.loot("Abyssal whip", 20);
        Rs2GroundItem.loot("Abyssal dagger", 20);
        Rs2GroundItem.loot("Bludgeon claw", 20);
        Rs2GroundItem.loot("Bludgeon spine", 20);
        Rs2GroundItem.loot("Bludgeon axon", 20);
        Rs2GroundItem.loot("Abyssal orphan", 20);
        Rs2GroundItem.loot("Jar of miasma", 20);
        Rs2GroundItem.loot("Abyssal head", 20);

        sleep(600, 1200);

        // Check if done looting
        if (!Rs2GroundItem.isItemBasedOnValueOnGround(config.lootPriceThreshold(), 20)) {
            // Pick up supply drops
            Rs2GroundItem.loot("Shark", 20);
            Rs2GroundItem.loot("Prayer potion", 20);
            Rs2GroundItem.loot("Super combat potion", 20);
            sleep(600);

            state = SireState.IDLE;
            status = "Loot complete — returning to start";
            lastLootCompleteTick = Microbot.getClient().getTickCount();

            // Return to Row 1 or stun tile randomly for next kill
            returnToStartPosition();
        }
    }

    /**
     * After kill+loot, walk back to a starting position for the next kill.
     * Randomly picks either Row 1 (left or right) or the stun tile.
     * This keeps the bot near the optimal position when the Sire respawns.
     */
    private void returnToStartPosition() {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) return;

        WorldPoint target;
        double roll = Math.random();
        if (roll < 0.5) {
            // Go to stun tile (ready to Shadow Barrage immediately on respawn)
            target = STUN_TILE;
            status = "Returning to stun tile";
        } else {
            // Go to Row 1 (random left/right)
            target = Math.random() < 0.5 ? ROW1_LEFT : ROW1_RIGHT;
            status = "Returning to Row 1";
        }

        log("Post-kill: returning to " + target);
        walkToSafe(target);
        sleepUntil(() -> {
            Player p = Microbot.getClient().getLocalPlayer();
            return p != null && p.getWorldLocation().distanceTo(target) <= 1;
        }, 5000);
    }

    // ── NPC Lookup Helpers ───────────────────────────────

    /**
     * Find the Sire NPC in any of its forms.
     */
    private Rs2NpcModel findSire() {
        for (int id : SIRE_IDS) {
            Rs2NpcModel npc = Rs2Npc.getNpc(id);
            if (npc != null) return npc;
        }
        return null;
    }

    /**
     * Determine the phase from the Sire's NPC ID.
     */
    private int determinePhase(int npcId) {
        if (npcId == SIRE_SLEEPING || npcId == SIRE_AWAKE || npcId == SIRE_STUNNED) {
            return 1;
        }
        if (npcId == SIRE_PUPPET || npcId == SIRE_WANDERING || npcId == SIRE_PANICKING) {
            return 2;
        }
        if (npcId == SIRE_APOCALYPSE) {
            return 3;
        }
        return 0;
    }

    // ── Arena Position Helpers ─────────────────────────────

    /**
     * Pick whichever of the two row tiles (left/right) is closest to the player.
     * Falls back to left tile if player is null.
     */
    private WorldPoint closestOf(Player player, WorldPoint left, WorldPoint right) {
        if (player == null) return left;
        WorldPoint loc = player.getWorldLocation();
        return loc.distanceTo(left) <= loc.distanceTo(right) ? left : right;
    }

    /**
     * No longer needed — explosion dodge now uses the hardcoded EXPLOSION_DODGE_TARGET
     * (ROW3_LEFT) since the wiki confirms the player always lands on western Row 2.
     * Kept as a fallback utility in case any other code references it.
     */
    @SuppressWarnings("unused")
    private WorldPoint dodgeSouthTarget(WorldPoint current) {
        return EXPLOSION_DODGE_TARGET;
    }

    /**
     * Find the best vent stand-tile for a given lung position.
     * Picks the tile closest to the lung that is within 10-range.
     * Returns null if no vent stand-tile is within 10 range.
     */
    private WorldPoint bestVentStandForLung(WorldPoint lungLoc) {
        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        for (WorldPoint tile : VENT_STAND_TILES) {
            int d = tile.distanceTo(lungLoc);
            if (d <= 10 && d < bestDist) {
                bestDist = d;
                best = tile;
            }
        }
        return best;
    }

    /**
     * Pick the next lung to attack following the prescribed order:
     * 1. Right side top (nearest to NE stand)
     * 2. Right side lower (nearest to E stand)
     * 3. Left side bottom (nearest to W stand)
     * 4. Left side top / closest to boss (nearest to NW stand)
     *
     * We iterate through VENT_ATTACK_ORDER and return the first alive lung
     * that is closest to that stand-tile. This ensures we attack in the
     * correct order even if NPC positions vary slightly.
     */
    private Rs2NpcModel pickNextLungInOrder(List<Rs2NpcModel> aliveLungs) {
        for (WorldPoint standTile : VENT_ATTACK_ORDER) {
            Rs2NpcModel closest = null;
            int closestDist = Integer.MAX_VALUE;
            for (Rs2NpcModel lung : aliveLungs) {
                int d = lung.getWorldLocation().distanceTo(standTile);
                if (d < closestDist) {
                    closestDist = d;
                    closest = lung;
                }
            }
            // If a lung is reasonably close to this stand-tile (within 12 tiles), pick it.
            // This means this quadrant's vent is still alive — attack it now.
            if (closest != null && closestDist <= 12) {
                return closest;
            }
        }
        // Fallback: return any alive lung
        return aliveLungs.isEmpty() ? null : aliveLungs.get(0);
    }

    /**
     * Pick the closest tile from an array of candidates.
     */
    private WorldPoint closestOf(Player player, WorldPoint[] candidates) {
        if (player == null || candidates.length == 0) return candidates.length > 0 ? candidates[0] : null;
        WorldPoint loc = player.getWorldLocation();
        WorldPoint best = candidates[0];
        int bestDist = loc.distanceTo(best);
        for (int i = 1; i < candidates.length; i++) {
            int d = loc.distanceTo(candidates[i]);
            if (d < bestDist) {
                bestDist = d;
                best = candidates[i];
            }
        }
        return best;
    }

    // ── Movement Helpers ─────────────────────────────────

    /**
     * Walk to a target WorldPoint with null-safety on LocalPoint conversion.
     * Falls back to Rs2Walker.walkTo() if the tile is not in the current scene.
     */
    private void walkToSafe(WorldPoint target) {
        LocalPoint local = LocalPoint.fromWorld(Microbot.getClient(), target);
        if (local != null) {
            Rs2Walker.walkFastLocal(local);
        } else {
            log("walkToSafe: LocalPoint null for " + target + " — using walkTo fallback");
            Rs2Walker.walkTo(target);
        }
    }

    /**
     * Score a candidate tile. Lower score = better.
     * Penalizes miasma pools and prefers tiles closer to Sire spawn.
     */
    private int scoreTile(WorldPoint candidate) {
        int score = 0;
        // Heavy penalty for standing on a miasma pool
        if (miasmaPools.contains(candidate)) {
            score += 1000;
        }
        if (sireSpawnPos != null) {
            score += candidate.distanceTo(sireSpawnPos);
        }
        return score;
    }

    /**
     * Pick the best tile from candidates. Always returns a tile (never null).
     */
    private WorldPoint pickBestTile(WorldPoint[] candidates) {
        return Arrays.stream(candidates)
                .min(Comparator.comparingInt(this::scoreTile))
                .orElse(candidates[0]);
    }

    // ── State Reset ──────────────────────────────────────

    private void resetFightState() {
        currentPhase = 0;
        ventsDestroyed = 0;
        specHitsCompleted = 0;
        sireIsStunned = false;
        stunStartTick = 0;
        sirePanicking = false;
        miasmaIncoming = false;
        miasmaPools.clear();
        lastMiasmaCleanTick = 0;
        explosionIncoming = false;
        explosionHappened = false;
        postExplosionMiasmaCount = 0;
        sireSpawnPos = null;
        firstStunOnSleeping = false;
        sireEngaged = false;
        lastKnownSireId = -1;
    }

    // ── Utility ──────────────────────────────────────────

    private void emergencyTeleport() {
        state = SireState.DEAD;
        togglePrayers(false);

        String teleItem = config.teleportItem();
        if (!teleItem.isEmpty() && Rs2Inventory.hasItem(teleItem)) {
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

    /**
     * Check if player has any healing supplies: food ("eat" action) OR
     * Saradomin brews / other healing potions ("drink" action that heals HP).
     * Rs2Inventory.getInventoryFood() only checks for "eat" items,
     * so brews would be missed.
     */
    private boolean hasHealingSupplies() {
        if (!Rs2Inventory.getInventoryFood().isEmpty()) return true;
        // Also count Saradomin brews as healing supplies
        return Rs2Inventory.hasItem("Saradomin brew")
                || Rs2Inventory.hasItem("Guthix rest")
                || Rs2Inventory.hasItem("Blighted food");
    }

    /**
     * Placeholder for future banking implementation.
     */
    @SuppressWarnings("unused")
    private void handleBanking() {
        // TODO: Implement banking logic
        // - Teleport to house/bank
        // - Withdraw supplies
        // - Return to Sire arena
        log("Banking not yet implemented — stopping script");
        shutdown();
    }
}

package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
public class GrotesqueGuardiansScript extends StateMachineScript<GrotesqueGuardiansState> {
    private static final String[][] DAWN_WEAPON_BRACKETS = {
            {"Noxious halberd"},
            {"Crystal halberd", "Crystal halberd (i)"},
            {"Dragon halberd"},
            {"Rune halberd", "Adamant halberd", "Mithril halberd", "Steel halberd",
                    "Iron halberd", "Bronze halberd"}
    };
    private static final String[][] DUSK_WEAPON_BRACKETS = {
            {"Scythe of vitur", "Inquisitor's mace"},
            {"Soulreaper axe", "Noxious halberd", "Abyssal tentacle"},
            {"Osmumten's fang", "Abyssal whip"},
            {"Zamorakian hasta", "Dual macuahuitl", "Dragon scimitar"}
    };
    private static final String[][] DAWN_SPEC_WEAPON_BRACKETS = {
            {"Crystal halberd", "Crystal halberd (i)"},
            {"Dragon halberd"}
    };
    private static final String[][] DAWN_BODY_BRACKETS = {
            {"Oathplate chest", "Torva platebody"},
            {"Bandos chestplate"},
            {"Blood Moon chestplate", "Fighter torso"},
            {"Obsidian platebody", "Eclipse Moon chestplate", "Black d'hide body"}
    };
    private static final String[][] DUSK_BODY_BRACKETS = {
            {"Inquisitor's hauberk", "Oathplate chest", "Torva platebody"},
            {"Bandos chestplate"},
            {"Blood Moon chestplate", "Fighter torso"},
            {"Obsidian platebody", "Eclipse Moon chestplate", "Black d'hide body"}
    };
    private static final String[][] OFFHAND_BRACKETS = {
            {"Avernic defender"},
            {"Dragon defender"},
            {"Dragonfire shield"},
            {"Rune defender"}
    };
    private static final GrotesqueGuardiansFinisher[] AUTO_FINISHERS = {
            GrotesqueGuardiansFinisher.ROCK_HAMMER,
            GrotesqueGuardiansFinisher.GRANITE_HAMMER,
            GrotesqueGuardiansFinisher.ROCK_THROWNHAMMER
    };
    private static final Set<String> TWO_HANDED_WEAPONS = Set.of(
            "Scythe of vitur", "Soulreaper axe", "Noxious halberd", "Dual macuahuitl",
            "Crystal halberd", "Crystal halberd (i)", "Dragon halberd", "Rune halberd",
            "Adamant halberd", "Mithril halberd", "Steel halberd", "Iron halberd", "Bronze halberd"
    );
    private static final Set<Integer> PHASE_1_IDS = Set.of(
            NpcID.GARGBOSS_DAWN_PHASE1
    );
    private static final Set<Integer> PHASE_2_IDS = Set.of(
            NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING
    );
    private static final Set<Integer> PHASE_3_IDS = Set.of(
            NpcID.GARGBOSS_DAWN_PHASE3
    );
    private static final Set<Integer> PHASE_4_IDS = Set.of(
            NpcID.GARGBOSS_DUSK_PHASE4
    );
    private static final Set<Integer> PHASE_4_PRISON_IDS = Set.of(
            NpcID.GARGBOSS_DUSK_PHASE4_SPAWN, NpcID.GARGBOSS_DUSK_PHASE4
    );
    private static final int[] ALL_BOSS_IDS = {
            NpcID.GARGBOSS_DUSK_SPAWN, NpcID.GARGBOSS_DAWN_SPAWN,
            NpcID.GARGBOSS_DUSK_PHASE1_DEFENSIVE, NpcID.GARGBOSS_DAWN_PHASE1,
            NpcID.GARGBOSS_DAWN_PHASE1_TRANSITION, NpcID.GARGBOSS_DUSK_PHASE1_TRANSITION,
            NpcID.GARGBOSS_DUSK_PHASE1_FLYTRANSITION, NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING,
            NpcID.GARGBOSS_DUSK_PHASE3_DEFENSIVE, NpcID.GARGBOSS_DAWN_PHASE3,
            NpcID.GARGBOSS_DAWN_DEATH, NpcID.GARGBOSS_DUSK_PHASE3_TRANSITION,
            NpcID.GARGBOSS_DUSK_PHASE4_SPAWN, NpcID.GARGBOSS_DUSK_PHASE4,
            NpcID.GARGBOSS_DUSK_DEATH
    };
    private static final int[] BELL_IDS = {
            ObjectID.SLAYER_ROOF_BELL,
            ObjectID.SLAYER_ROOF_BELL_NOQUICKSTART,
            ObjectID.SLAYER_ROOF_BELL_QUICKSTART
    };
    private static final int[] SPHERE_IDS = {
            ObjectID.GARGBOSS_HEALSPHERE_SMALL, ObjectID.GARGBOSS_HEALSPHERE_MED,
            ObjectID.GARGBOSS_HEALSPHERE_LARGE, ObjectID.GARGBOSS_HEALSPHERE_1,
            ObjectID.GARGBOSS_HEALSPHERE_2, ObjectID.GARGBOSS_HEALSPHERE_3
    };
    private static final Set<Integer> LIGHTNING_GRAPHICS = Set.of(
            SpotanimID.GG_DUSK_LIGHTNING_1, SpotanimID.GG_DUSK_LIGHTNING_2,
            SpotanimID.GG_DUSK_LIGHTNING_3, SpotanimID.GG_DUSK_LIGHTNING_4,
            SpotanimID.GG_DUSK_LIGHTNING_5, SpotanimID.GG_DUSK_LIGHTNING_6,
            SpotanimID.GG_DUSK_LIGHTNING_7, SpotanimID.GG_DUSK_LIGHTNING_8,
            SpotanimID.GG_DAWN_LIGHTNING_1, SpotanimID.GG_DAWN_LIGHTNING_2,
            SpotanimID.GG_DAWN_LIGHTNING_3, SpotanimID.GG_DAWN_LIGHTNING_4,
            SpotanimID.GG_DAWN_LIGHTNING_5, SpotanimID.GG_DAWN_LIGHTNING_6,
            SpotanimID.GG_DAWN_LIGHTNING_7, SpotanimID.GG_DAWN_LIGHTNING_8,
            SpotanimID.GG_DAWN_LIGHTNING_FAST, SpotanimID.GG_DUSK_LIGHTNING_ENRAGED,
            SpotanimID.GG_DUSK_LIGHTNING_ENRAGED_START
    );
    private static final Set<Integer> DEBRIS_GRAPHICS = Set.of(
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_60, SpotanimID.GARGBOSS_DEBRIS_SHADOW_90,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_120, SpotanimID.GARGBOSS_DEBRIS_SHADOW_150,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_180, SpotanimID.GARGBOSS_DEBRIS_SHADOW_210,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_240, SpotanimID.GARGBOSS_DEBRIS_SHADOW_270,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_300, SpotanimID.GARGBOSS_DEBRIS_SHADOW_60_SMALL,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_90_SMALL, SpotanimID.GARGBOSS_DEBRIS_SHADOW_180_LARGE,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_270_LARGE, SpotanimID.GARGBOSS_DEBRIS_SHADOW_300_LARGE,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_120_SMALL, SpotanimID.GARGBOSS_RAIN_SHADOW_90,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_30,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_30_DARK, SpotanimID.GARGBOSS_DEBRIS_SHADOW_60_DARK,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_90_DARK, SpotanimID.GARGBOSS_DEBRIS_SHADOW_120_DARK,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_150_DARK, SpotanimID.GARGBOSS_DEBRIS_SHADOW_180_DARK,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_180_LARGE_DARK,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_210_DARK, SpotanimID.GARGBOSS_DEBRIS_SHADOW_240_DARK,
            SpotanimID.GARGBOSS_DEBRIS_SHADOW_270_DARK, SpotanimID.GARGBOSS_DEBRIS_SHADOW_300_DARK
    );
    private static final Set<Integer> STONE_BALL_PROJECTILES = Set.of(
            SpotanimID.GG_FALLING_PROJECTILE, SpotanimID.GARGBOSS_EXPLODING_PROJECTILE
    );
    private static final int P2_MINIMUM_SAFE_DISTANCE = 2;
    private static final int P4_PRISON_ESCAPE_TICKS = 5;

    private enum AttackStyle { MELEE, RANGED }

    private static final class Hazard {
        private final int radius;
        private final int expiresAtTick;

        private Hazard(int radius, int expiresAtTick) {
            this.radius = radius;
            this.expiresAtTick = expiresAtTick;
        }
    }

    private static final class HazardObservation {
        private final String type;
        private final int id;
        private final int startCycle;
        private final WorldPoint location;

        private HazardObservation(String type, int id, int startCycle, WorldPoint location) {
            this.type = type;
            this.id = id;
            this.startCycle = startCycle;
            this.location = location;
        }
    }

    private GrotesqueGuardiansConfig config;
    private final Map<WorldPoint, Hazard> hazards = new ConcurrentHashMap<>();
    private final Set<String> processedHazardObservations = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean p2DodgeQueued = new AtomicBoolean();
    private final AtomicBoolean p2DodgeInProgress = new AtomicBoolean();
    private final AtomicBoolean prisonEscapeQueued = new AtomicBoolean();
    private final AtomicBoolean prisonEscapeInProgress = new AtomicBoolean();
    @Getter
    private volatile GrotesqueGuardiansState displayState = GrotesqueGuardiansState.IDLE;
    @Getter
    private volatile String status = "Waiting";
    @Getter
    private volatile int killCount;
    @Getter
    private volatile int hitpoints;
    @Getter
    private volatile int prayer;
    @Getter
    private volatile String dawnWeapon = "None";
    @Getter
    private volatile String duskWeapon = "None";
    @Getter
    private volatile String duskOffhand = "None";
    private volatile String dawnBody = "None";
    private volatile String duskBody = "None";
    private volatile String dawnSpecWeapon = "None";
    @Getter
    private volatile String finisher = "None";
    private volatile int finisherItemId = -1;
    private volatile int finisherCount;
    private volatile int maxHitpoints = 1;
    private volatile int maxPrayer = 1;
    private volatile int boostedAttack;
    private volatile int realAttack;
    private volatile int boostedStrength;
    private volatile int realStrength;
    private volatile int gameTick;
    private volatile WorldPoint playerLocation;
    private volatile boolean playerDead;
    private volatile boolean killCompleted;
    private volatile int evadeUntilTick;
    private volatile int prisonUntilTick;
    private volatile WorldPoint prisonCenter;
    private volatile WorldPoint prisonOrigin;
    private volatile WorldPoint prisonOpening;
    private volatile int prisonDetectedTick = -1;
    private volatile boolean p4PrisonAnimationActive;
    private volatile AttackStyle lastDuskAttack = AttackStyle.MELEE;
    private volatile int specEnergy;
    private boolean specialAttackArmed;
    private boolean deathChargeHandled;
    private boolean dawnSpecDumpComplete;
    private int dawnSpecialsUsedThisPhase;
    private int deathChargeAttemptTick = -100;
    private int prisonEscapeAttempts;
    private volatile int lastPrisonEscapeAttemptTick = -100;
    private boolean waitingForSpecEnergyDrop;
    private boolean surgeUsedThisKill;
    private boolean brewRestorePending;
    private int specEnergyBeforeAttack;
    private int specObservationDeadlineTick;
    private int specArmRequestedTick = -100;
    private int lastSpecActionTick = -100;
    private volatile int lastP2DodgeAttemptTick = -100;

    // Refreshed immediately before step(); transition guards only read these fields.
    private boolean phase1Present;
    private boolean phase2Present;
    private boolean phase3Present;
    private boolean phase4Present;
    private boolean anyBossPresent;
    private volatile List<Integer> detectedBossIds = List.of();
    private boolean bellPresent;
    private boolean finisherPresent;
    private boolean combatGearPresent;
    private boolean foodPresent;
    private boolean lootFinished;
    private int emptyLootTicks;
    private int lastBellTick = -100;
    private int lastAttackTick = -100;
    private int lastGearTick = -100;
    private int lastSupplyTick = -100;
    private int lastMoveTick = -100;
    private String lastAction = "None";
    private String lastActionDetail = "Waiting for first action";
    private boolean lastActionSucceeded;
    private int lastActionTick = -1;
    private int consecutiveActionFailures;
    private volatile int lastBossNpcId = -1;
    private volatile int lastBossAnimation = -1;
    private volatile int lastBossAnimationTick = -1;
    private volatile int lastP2SpecialTick = -1;
    private volatile String lastP2SpecialDetectionSource = "None";
    private volatile int lastP4PrisonTick = -1;
    private volatile String lastP4PrisonDetectionSource = "None";
    private volatile int lastGraphicsId = -1;
    private volatile WorldPoint lastGraphicsLocation;
    private volatile int lastProjectileId = -1;
    private volatile WorldPoint lastProjectileTarget;
    private volatile String lastLoopFailure = "None";

    @Override
    protected GrotesqueGuardiansState initialState() {
        return GrotesqueGuardiansState.IDLE;
    }

    @Override
    protected List<Transition<GrotesqueGuardiansState>> defineTransitions() {
        List<Transition<GrotesqueGuardiansState>> transitions = new ArrayList<>();
        for (GrotesqueGuardiansState from : GrotesqueGuardiansState.values()) {
            if (from != GrotesqueGuardiansState.DEAD) {
                transitions.add(transition(from, GrotesqueGuardiansState.DEAD,
                        () -> playerDead, "playerDead", "Player died"));
            }
        }
        // Boss forms can overlap briefly while an NPC transforms. Prefer the latest visible
        // phase and never allow an established encounter to move backwards.
        addPhaseTransitions(transitions, GrotesqueGuardiansState.IDLE, 1);
        addPhaseTransitions(transitions, GrotesqueGuardiansState.STARTING, 1);
        addPhaseTransitions(transitions, GrotesqueGuardiansState.OUT_OF_SUPPLIES, 1);
        addPhaseTransitions(transitions, GrotesqueGuardiansState.LOOTING, 1);
        addPhaseTransitions(transitions, GrotesqueGuardiansState.DAWN_PHASE_1, 2);
        addPhaseTransitions(transitions, GrotesqueGuardiansState.DUSK_PHASE_2, 3);
        addPhaseTransitions(transitions, GrotesqueGuardiansState.DAWN_PHASE_3, 4);
        transitions.add(transition(GrotesqueGuardiansState.IDLE, GrotesqueGuardiansState.OUT_OF_SUPPLIES,
                () -> !finisherPresent || !foodPresent || !combatGearPresent,
                "!finisherPresent || !foodPresent || !combatGearPresent",
                "Food, finisher, or melee weapon is missing"));
        transitions.add(transition(GrotesqueGuardiansState.IDLE, GrotesqueGuardiansState.STARTING,
                () -> bellPresent && !anyBossPresent, "bellPresent && !anyBossPresent", "Ready to ring bell"));
        transitions.add(transition(GrotesqueGuardiansState.STARTING, GrotesqueGuardiansState.IDLE,
                () -> !bellPresent && !anyBossPresent, "!bellPresent && !anyBossPresent", "Not on the roof"));
        transitions.add(transition(GrotesqueGuardiansState.DUSK_PHASE_4, GrotesqueGuardiansState.LOOTING,
                () -> killCompleted && !phase4Present, "killCompleted && !phase4Present", "Dusk defeated"));
        transitions.add(transition(GrotesqueGuardiansState.LOOTING, GrotesqueGuardiansState.OUT_OF_SUPPLIES,
                () -> lootFinished && (!config.repeatKills() || !foodPresent
                        || !finisherPresent || !combatGearPresent),
                "lootFinished && stopRequested", "Trip complete or supplies exhausted"));
        transitions.add(transition(GrotesqueGuardiansState.LOOTING, GrotesqueGuardiansState.IDLE,
                () -> lootFinished && config.repeatKills() && foodPresent
                        && finisherPresent && combatGearPresent,
                "lootFinished && repeatKills", "Looting complete"));
        transitions.add(transition(GrotesqueGuardiansState.OUT_OF_SUPPLIES, GrotesqueGuardiansState.IDLE,
                () -> finisherPresent && foodPresent && combatGearPresent,
                "finisherPresent && foodPresent && combatGearPresent", "Supplies restored"));
        transitions.add(transition(GrotesqueGuardiansState.DEAD, GrotesqueGuardiansState.IDLE,
                () -> !playerDead, "!playerDead", "Player respawned"));
        transitions.add(transition(GrotesqueGuardiansState.ERROR, GrotesqueGuardiansState.IDLE,
                () -> !anyBossPresent && bellPresent, "!anyBossPresent && bellPresent", "Encounter reset"));
        return transitions;
    }

    private void addPhaseTransitions(List<Transition<GrotesqueGuardiansState>> transitions,
                                     GrotesqueGuardiansState from, int earliestPhase) {
        if (earliestPhase <= 4) {
            transitions.add(transition(from, GrotesqueGuardiansState.DUSK_PHASE_4,
                    () -> phase4Present, "phase4Present", "Dusk phase four detected"));
        }
        if (earliestPhase <= 3) {
            transitions.add(transition(from, GrotesqueGuardiansState.DAWN_PHASE_3,
                    () -> phase3Present, "phase3Present", "Dawn phase three detected"));
        }
        if (earliestPhase <= 2) {
            transitions.add(transition(from, GrotesqueGuardiansState.DUSK_PHASE_2,
                    () -> phase2Present, "phase2Present", "Dusk phase two detected"));
        }
        if (earliestPhase <= 1) {
            transitions.add(transition(from, GrotesqueGuardiansState.DAWN_PHASE_1,
                    () -> phase1Present, "phase1Present", "Dawn phase one detected"));
        }
    }

    private Transition<GrotesqueGuardiansState> transition(
            GrotesqueGuardiansState from, GrotesqueGuardiansState to,
            java.util.function.BooleanSupplier guard, String expression, String reason) {
        return Transition.<GrotesqueGuardiansState>from(from)
                .when(guard, expression).because(reason).goTo(to);
    }

    @Override
    protected void onState(GrotesqueGuardiansState state) {
        displayState = state;
        if (isEncounterState(state) && handleImmediateSafety()) return;
        if (isEncounterState(state) && config.absorbSpheres() && handleHealingSphere()) return;
        switch (state) {
            case IDLE:
                status = bellPresent ? "Ready" : "Start on the Slayer Tower roof";
                break;
            case STARTING:
                startFight();
                break;
            case DAWN_PHASE_1:
                fightDawn(false);
                break;
            case DUSK_PHASE_2:
                fightDusk(false);
                break;
            case DAWN_PHASE_3:
                fightDawn(true);
                break;
            case DUSK_PHASE_4:
                fightDusk(true);
                break;
            case LOOTING:
                loot();
                break;
            case OUT_OF_SUPPLIES:
                status = missingGearStatus();
                disablePrayers();
                break;
            case DEAD:
                status = "Stopped: player died";
                disablePrayers();
                break;
            case ERROR:
                status = "Stopped after an error";
                disablePrayers();
                break;
        }
    }

    private boolean isEncounterState(GrotesqueGuardiansState state) {
        return state == GrotesqueGuardiansState.STARTING
                || state == GrotesqueGuardiansState.DAWN_PHASE_1
                || state == GrotesqueGuardiansState.DUSK_PHASE_2
                || state == GrotesqueGuardiansState.DAWN_PHASE_3
                || state == GrotesqueGuardiansState.DUSK_PHASE_4;
    }

    @Override
    protected void onTransition(GrotesqueGuardiansState from, GrotesqueGuardiansState to, String reason) {
        super.onTransition(from, to, reason);
        displayState = to;
        if (to == GrotesqueGuardiansState.STARTING) {
            killCompleted = false;
            surgeUsedThisKill = false;
            brewRestorePending = false;
            hazards.clear();
            processedHazardObservations.clear();
            prisonCenter = null;
            prisonOrigin = null;
            prisonOpening = null;
            prisonEscapeAttempts = 0;
        }
        if (to == GrotesqueGuardiansState.DAWN_PHASE_3) {
            deathChargeHandled = false;
            deathChargeAttemptTick = -100;
        }
        if (to == GrotesqueGuardiansState.DUSK_PHASE_4 && gameTick > prisonUntilTick) {
            detectP4Prison("phase4Transition");
        }
        if (to == GrotesqueGuardiansState.DAWN_PHASE_1
                || to == GrotesqueGuardiansState.DAWN_PHASE_3) {
            dawnSpecDumpComplete = false;
            waitingForSpecEnergyDrop = false;
            specEnergyBeforeAttack = -1;
            specArmRequestedTick = -100;
            lastSpecActionTick = -100;
            dawnSpecialsUsedThisPhase = 0;
        }
        if (to == GrotesqueGuardiansState.LOOTING) {
            lootFinished = false;
            emptyLootTicks = 0;
        }
    }

    @Override
    protected GrotesqueGuardiansState onError(GrotesqueGuardiansState state, Exception exception) {
        log.error("Grotesque Guardians failed in {}: {}", state, exception.getMessage(), exception);
        return GrotesqueGuardiansState.ERROR;
    }

    @Override
    protected Map<String, Object> diagnosticSnapshot() {
        Map<String, Object> supplies = new LinkedHashMap<>();
        supplies.put("finisherPresent", finisherPresent);
        supplies.put("foodPresent", foodPresent);
        supplies.put("combatGearPresent", combatGearPresent);
        supplies.put("finisher", finisher);
        supplies.put("finisherItemId", finisherItemId);
        supplies.put("finisherCount", finisherCount);
        supplies.put("dawnWeapon", dawnWeapon);
        supplies.put("duskWeapon", duskWeapon);
        supplies.put("duskOffhand", duskOffhand);

        Map<String, Object> encounter = new LinkedHashMap<>();
        encounter.put("phase1Present", phase1Present);
        encounter.put("phase2Present", phase2Present);
        encounter.put("phase3Present", phase3Present);
        encounter.put("phase4Present", phase4Present);
        encounter.put("detectedBossIds", detectedBossIds);
        encounter.put("bellPresent", bellPresent);
        encounter.put("hazardCount", hazards.size());
        encounter.put("gameTick", gameTick);
        encounter.put("evadeUntilTick", evadeUntilTick);
        encounter.put("prisonUntilTick", prisonUntilTick);
        encounter.put("prisonCenter", prisonCenter == null ? null
                : prisonCenter.getX() + "," + prisonCenter.getY() + "," + prisonCenter.getPlane());
        encounter.put("prisonOrigin", pointText(prisonOrigin));
        encounter.put("prisonOpening", pointText(prisonOpening));
        encounter.put("prisonEscapeAttempts", prisonEscapeAttempts);
        encounter.put("prisonTicksRemaining", Math.max(0, prisonUntilTick - gameTick));
        encounter.put("evading", gameTick <= evadeUntilTick);
        encounter.put("escapingPrison", gameTick <= prisonUntilTick);
        encounter.put("dawnSpecialsUsedThisPhase", dawnSpecialsUsedThisPhase);
        encounter.put("waitingForSpecEnergyDrop", waitingForSpecEnergyDrop);
        encounter.put("specialAttackArmed", specialAttackArmed);
        encounter.put("loopRunning", isRunning());

        Map<String, Object> events = new LinkedHashMap<>();
        events.put("lastBossNpcId", lastBossNpcId);
        events.put("lastBossAnimation", lastBossAnimation);
        events.put("lastBossAnimationTick", lastBossAnimationTick);
        events.put("lastP2SpecialTick", lastP2SpecialTick);
        events.put("lastP2SpecialDetectionSource", lastP2SpecialDetectionSource);
        events.put("lastP4PrisonTick", lastP4PrisonTick);
        events.put("lastP4PrisonDetectionSource", lastP4PrisonDetectionSource);
        events.put("lastGraphicsId", lastGraphicsId);
        events.put("lastGraphicsLocation", pointText(lastGraphicsLocation));
        events.put("lastProjectileId", lastProjectileId);
        events.put("lastProjectileTarget", pointText(lastProjectileTarget));
        events.put("lastLoopFailure", lastLoopFailure);

        Map<String, Object> player = new LinkedHashMap<>();
        player.put("hitpoints", hitpoints);
        player.put("maxHitpoints", maxHitpoints);
        player.put("prayer", prayer);
        player.put("maxPrayer", maxPrayer);
        player.put("specEnergy", specEnergy);
        player.put("boostedAttack", boostedAttack);
        player.put("realAttack", realAttack);
        player.put("boostedStrength", boostedStrength);
        player.put("realStrength", realStrength);
        player.put("location", playerLocation == null ? null
                : playerLocation.getX() + "," + playerLocation.getY() + "," + playerLocation.getPlane());

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("name", lastAction);
        action.put("detail", lastActionDetail);
        action.put("succeeded", lastActionSucceeded);
        action.put("tick", lastActionTick);
        action.put("consecutiveFailures", consecutiveActionFailures);

        Map<String, Object> tuning = new LinkedHashMap<>();
        tuning.put("sphereMaxDistance", config.sphereMaxDistance());
        tuning.put("p2DodgeDistance", config.p2DodgeDistance());
        tuning.put("p2EvadeTicks", config.p2EvadeTicks());
        tuning.put("lightningAvoidTicks", config.lightningAvoidTicks());
        tuning.put("debrisAvoidTicks", config.debrisAvoidTicks());
        tuning.put("dawnP1SpecialCount", config.dawnP1SpecialCount());
        tuning.put("dawnP3SpecialCount", config.dawnP3SpecialCount());
        tuning.put("deathChargeAtPercent", config.deathChargeAtPercent());
        tuning.put("combatPotionBoostFloor", config.combatPotionBoostFloor());
        tuning.put("attackRetryTicks", config.attackRetryTicks());
        tuning.put("gearRetryTicks", config.gearRetryTicks());

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("status", status);
        diagnostics.put("supplies", supplies);
        diagnostics.put("encounter", encounter);
        diagnostics.put("player", player);
        diagnostics.put("lastAction", action);
        diagnostics.put("events", events);
        diagnostics.put("tuning", tuning);
        return diagnostics;
    }

    private void recordAction(String action, String detail, boolean succeeded) {
        lastAction = action;
        lastActionDetail = detail;
        lastActionSucceeded = succeeded;
        lastActionTick = gameTick;
        consecutiveActionFailures = succeeded ? 0 : consecutiveActionFailures + 1;
    }

    public boolean run(GrotesqueGuardiansConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                refreshSnapshot();
                step();
            } catch (Exception exception) {
                log.error("Grotesque Guardians loop error", exception);
                status = "Error: " + exception.getMessage();
                if (getSnapshot() != null) forceState(GrotesqueGuardiansState.ERROR, exception.getMessage());
            } catch (LinkageError | AssertionError error) {
                lastLoopFailure = error.getClass().getSimpleName() + ": " + error.getMessage();
                log.error("Grotesque Guardians loop recovered from fatal task error", error);
                status = "Loop failure: " + lastLoopFailure;
                if (getSnapshot() != null) forceState(GrotesqueGuardiansState.ERROR, lastLoopFailure);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void refreshSnapshot() {
        hazards.entrySet().removeIf(entry -> entry.getValue().expiresAtTick < gameTick);
        List<Rs2NpcModel> bosses = Microbot.getRs2NpcCache().query().withIds(ALL_BOSS_IDS).toList();
        detectedBossIds = bosses.stream().filter(npc -> npc != null).map(npc -> npc.getId()).distinct().sorted()
                .collect(Collectors.toList());
        phase1Present = bosses.stream().anyMatch(npc -> PHASE_1_IDS.contains(npc.getId()));
        phase2Present = bosses.stream().anyMatch(npc -> PHASE_2_IDS.contains(npc.getId()));
        phase3Present = bosses.stream().anyMatch(npc -> PHASE_3_IDS.contains(npc.getId()));
        phase4Present = bosses.stream().anyMatch(npc -> PHASE_4_IDS.contains(npc.getId()));
        anyBossPresent = !bosses.isEmpty();
        bosses.stream()
                .filter(npc -> PHASE_2_IDS.contains(npc.getId()))
                .findFirst()
                .ifPresent(this::pollP2SpecialAnimation);
        Rs2NpcModel p4Dusk = bosses.stream()
                .filter(npc -> PHASE_4_PRISON_IDS.contains(npc.getId()))
                .findFirst()
                .orElse(null);
        if (p4Dusk == null) {
            p4PrisonAnimationActive = false;
        } else {
            pollP4PrisonAnimation(p4Dusk);
        }
        pollTileHazards();
        bellPresent = Microbot.getRs2TileObjectCache().query().withIds(BELL_IDS).nearest() != null;
        dawnWeapon = resolveGearItem(config.customDawnWeapon(), DAWN_WEAPON_BRACKETS);
        duskWeapon = resolveGearItem(config.customDuskWeapon(), DUSK_WEAPON_BRACKETS);
        duskOffhand = resolveGearItem(config.meleeOffhand(), OFFHAND_BRACKETS);
        dawnBody = resolveGearItem("", DAWN_BODY_BRACKETS);
        duskBody = resolveGearItem("", DUSK_BODY_BRACKETS);
        dawnSpecWeapon = resolveDawnSpecWeapon();
        specEnergy = Rs2Combat.getSpecEnergy();
        specialAttackArmed = Rs2Combat.getSpecState();
        GrotesqueGuardiansFinisher resolvedFinisher = resolveFinisher();
        finisher = resolvedFinisher == null ? "None" : resolvedFinisher.itemName();
        finisherItemId = resolvedFinisher == null ? -1 : resolvedFinisher.itemId();
        finisherCount = finisherItemId > 0 ? Rs2Inventory.count(finisherItemId) : 0;
        finisherPresent = hasEnoughFinisherUses(resolvedFinisher);
        combatGearPresent = !"None".equals(dawnWeapon) && !"None".equals(duskWeapon);
        foodPresent = !Rs2Inventory.getInventoryFood().isEmpty()
                || Rs2Inventory.hasItem("Saradomin brew");
    }

    private boolean handleImmediateSafety() {
        if (playerLocation == null) return false;
        boolean standingInDanger = hazards.entrySet().stream().anyMatch(entry ->
                distance(entry.getKey(), playerLocation) <= entry.getValue().radius);
        Rs2NpcModel dusk = currentDusk();
        WorldArea duskArea = getWorldArea(dusk);
        boolean adjacentDuringP2Special = gameTick <= evadeUntilTick && duskArea != null
                && duskArea.distanceTo(playerLocation) < p2SafeDistance();
        boolean standingUnderDusk = duskArea != null && duskArea.contains(playerLocation);
        boolean prisonActive = gameTick <= prisonUntilTick && prisonDetectedTick >= 0;
        if (prisonActive && prisonCenter == null) {
            status = "Waiting for Dusk to place the prison";
            return true;
        }
        boolean insidePrison = gameTick <= prisonUntilTick && prisonCenter != null
                && distance(prisonCenter, playerLocation) <= 1;
        if (insidePrison) return escapePrisonImmediately();
        if (adjacentDuringP2Special) return dodgeP2ShockwaveImmediately();
        if (standingInDanger || standingUnderDusk) {
            WorldPoint safe = findSafeTile(dusk);
            if (safe != null && gameTick > lastMoveTick) {
                status = standingUnderDusk ? "Moving out from under Dusk" : "Dodging tile hazard";
                boolean moved = Rs2Walker.walkFastCanvas(safe);
                String action = standingUnderDusk ? "DODGE_DUSK_TRAMPLE" : "DODGE_HAZARD";
                recordAction(action,
                        "target=" + safe.getX() + "," + safe.getY(), moved);
                lastMoveTick = gameTick;
                return moved;
            }
        }
        if (gameTick - lastSupplyTick >= 3 && hitpoints * 100 / maxHitpoints <= config.eatAtPercent()) {
            List<Rs2ItemModel> food = Rs2Inventory.getInventoryFood();
            if (!food.isEmpty() && Rs2Inventory.interact(food.get(0), "Eat")) {
                status = "Eating";
                lastSupplyTick = gameTick;
                return true;
            }
            Rs2ItemModel brew = Rs2Inventory.get("Saradomin brew");
            if (brew != null && Rs2Inventory.interact(brew, "Drink")) {
                status = "Drinking Saradomin brew";
                brewRestorePending = true;
                lastSupplyTick = gameTick;
                return true;
            }
        }
        if (brewRestorePending && gameTick - lastSupplyTick >= 3) {
            if (Rs2Inventory.interact("Super restore", "Drink")) {
                status = "Restoring stats after Saradomin brew";
                brewRestorePending = false;
                lastSupplyTick = gameTick;
                return true;
            }
            if (!Rs2Inventory.hasItem("Super restore")) brewRestorePending = false;
        }
        if (gameTick - lastSupplyTick >= 3 && prayer * 100 / maxPrayer <= config.drinkPrayerAtPercent()
                && Rs2Player.drinkPrayerPotion()) {
            status = "Restoring prayer";
            lastSupplyTick = gameTick;
            return true;
        }
        if (config.useCombatPotions() && gameTick - lastSupplyTick >= 5
                && (boostedAttack <= realAttack + config.combatPotionBoostFloor()
                || boostedStrength <= realStrength + config.combatPotionBoostFloor())) {
            Rs2ItemModel potion = Rs2Inventory.get("Super combat potion");
            boolean drank = potion != null && Rs2Inventory.interact(potion, "Drink");
            recordAction("DRINK_SUPER_COMBAT",
                    potion == null ? "Super combat potion not found" : potion.getName(), drank);
            lastSupplyTick = gameTick;
            if (drank) {
                status = "Drinking super combat potion";
                return true;
            }
        }
        return false;
    }

    private void startFight() {
        if (gameTick - lastBellTick < config.bellRetryTicks()) return;
        Rs2TileObjectModel bell = Microbot.getRs2TileObjectCache().query().withIds(BELL_IDS).nearest();
        boolean clicked = bell != null && bell.click("Quick-start");
        recordAction("QUICK_START_BELL",
                bell == null ? "The Cloister Bell not found" : "Quick-start interaction", clicked);
        lastBellTick = gameTick;
        if (clicked) {
            status = "Quick-starting the encounter";
        }
    }

    private void fightDawn(boolean phaseThree) {
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        if (config.useOffensivePrayers()) Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        Rs2NpcModel dawn = nearestNpc(phaseThree ? PHASE_3_IDS : PHASE_1_IDS);
        if (dawn == null) return;
        int healthPercent = getHealthPercent(dawn);
        if (phaseThree && handleDeathCharge(healthPercent)) return;
        if (handleDawnSpecials(dawn, phaseThree)) return;
        if (equip(dawnWeapon, "", dawnBody)) return;
        status = (phaseThree ? "Fighting Dawn P3 with " : "Fighting Dawn P1 with ") + dawnWeapon;
        attackOrFinish(dawn);
    }

    private int getHealthPercent(Rs2NpcModel npc) {
        int[] health = Microbot.getClientThread().runOnClientThreadOptional(() ->
                new int[]{npc.getNpc().getHealthRatio(), npc.getNpc().getHealthScale()}).orElse(null);
        if (health == null || health[0] <= 0 || health[1] <= 0) return 100;
        return Math.min(100, health[0] * 100 / health[1]);
    }

    private boolean handleDeathCharge(int dawnHealthPercent) {
        if (deathChargeHandled || !config.useDeathCharge()
                || dawnHealthPercent > config.deathChargeAtPercent()) return false;
        boolean deathChargeUnavailable = Microbot.getVarbitValue(VarbitID.ARCEUUS_DEATH_CHARGE_ACTIVE) != 0
                || Microbot.getVarbitValue(VarbitID.ARCEUUS_DEATH_CHARGE_COOLDOWN) != 0;
        if (deathChargeUnavailable || !Rs2Magic.canCast(Rs2Spells.DEATH_CHARGE)) {
            deathChargeHandled = true;
            return false;
        }
        if (gameTick <= deathChargeAttemptTick) return true;
        deathChargeAttemptTick = gameTick;
        boolean cast = Rs2Magic.cast(Rs2Spells.DEATH_CHARGE);
        recordAction("CAST_DEATH_CHARGE", "Dawn health=" + dawnHealthPercent + "%", cast);
        status = cast ? "Casting Death Charge near Dawn's death" : "Retrying Death Charge";
        if (cast) deathChargeHandled = true;
        return true;
    }

    private boolean handleDawnSpecials(Rs2NpcModel dawn, boolean phaseThree) {
        if (!config.useDawnSpecialAttacks() || "None".equals(dawnSpecWeapon)) {
            dawnSpecDumpComplete = true;
            return false;
        }

        int specCost = specialAttackCost(dawnSpecWeapon);
        int phaseLimit = phaseThree ? config.dawnP3SpecialCount() : config.dawnP1SpecialCount();
        if (phaseLimit == 0) {
            dawnSpecDumpComplete = true;
            return false;
        }
        if (waitingForSpecEnergyDrop) {
            if (specEnergy < specEnergyBeforeAttack) {
                waitingForSpecEnergyDrop = false;
                dawnSpecialsUsedThisPhase++;
                if (dawnSpecialsUsedThisPhase >= phaseLimit || specEnergy < specCost) {
                    dawnSpecDumpComplete = true;
                }
            } else if (gameTick <= specObservationDeadlineTick) {
                status = "Waiting for Dawn special attack";
                return true;
            } else {
                waitingForSpecEnergyDrop = false;
            }
        }
        if (dawnSpecDumpComplete || dawnSpecialsUsedThisPhase >= phaseLimit
                || specEnergy < specCost || isDead(dawn)) {
            dawnSpecDumpComplete = true;
            return false;
        }
        if (equip(dawnSpecWeapon, "", dawnBody)) return true;
        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            status = "Opening combat tab for Dawn specs";
            Rs2Tab.switchTo(InterfaceTab.COMBAT);
            return true;
        }
        if (!specialAttackArmed) {
            if (gameTick <= specArmRequestedTick) {
                status = "Waiting for " + dawnSpecWeapon + " special attack to arm";
                return true;
            }
            status = "Arming " + dawnSpecWeapon + " special attack";
            boolean armed = Rs2Combat.setSpecState(true, specCost);
            recordAction("ARM_SPECIAL_ATTACK", dawnSpecWeapon + " cost=" + specCost, armed);
            if (armed) specArmRequestedTick = gameTick;
            return true;
        }
        if (gameTick - lastSpecActionTick < config.attackRetryTicks()) return true;
        status = "Using " + dawnSpecWeapon + " spec on Dawn P" + (phaseThree ? "3" : "1")
                + " (" + (dawnSpecialsUsedThisPhase + 1) + "/" + phaseLimit + ")";
        boolean attacked = dawn.click("Attack");
        recordAction("ATTACK_NPC", "Dawn id=" + dawn.getId() + " special=true specArmed=true", attacked);
        lastSpecActionTick = gameTick;
        if (attacked) {
            specEnergyBeforeAttack = specEnergy;
            specObservationDeadlineTick = gameTick + 4;
            waitingForSpecEnergyDrop = true;
        }
        return true;
    }

    private int specialAttackCost(String weapon) {
        if (weapon != null && (weapon.startsWith("Crystal halberd") || weapon.equals("Dragon halberd"))) {
            return 300;
        }
        return 500;
    }

    private void fightDusk(boolean phaseFour) {
        Rs2NpcModel dusk = currentDusk();
        if (dusk == null) return;
        if (phaseFour && lastDuskAttack == AttackStyle.RANGED) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        } else {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        }
        if (config.useOffensivePrayers()) Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
        if (specialAttackArmed) {
            if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
                status = "Opening combat tab to disarm special attack";
                Rs2Tab.switchTo(InterfaceTab.COMBAT);
                return;
            }
            status = "Disarming leftover halberd special attack";
            boolean disarmed = Rs2Combat.setSpecState(false, 0);
            recordAction("DISARM_SPECIAL_ATTACK", dawnSpecWeapon, disarmed);
            return;
        }
        String offhand = TWO_HANDED_WEAPONS.contains(duskWeapon) ? "" : duskOffhand;
        if (equip(duskWeapon, offhand, duskBody)) return;
        status = (phaseFour ? "Fighting Dusk P4 with " : "Fighting Dusk P2 with ") + duskWeapon;
        attackOrFinish(dusk);
    }

    private void attackOrFinish(Rs2NpcModel target) {
        if (gameTick - lastAttackTick < config.attackRetryTicks()) return;
        if (isDead(target)) {
            Rs2ItemModel finisherItem = Rs2Inventory.get(finisherItemId);
            net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel legacyTarget =
                    new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(target.getNpc());
            boolean finished = finisherItem != null
                    && Rs2Inventory.useItemOnNpc(finisherItem.getId(), legacyTarget);
            recordAction("FINISH_NPC", "targetId=" + target.getId() + " item=" + finisher,
                    finished);
            lastAttackTick = gameTick;
            if (finished) {
                status = "Using " + finisher;
            }
            return;
        }
        boolean attacked = target.click("Attack");
        recordAction("ATTACK_NPC", "targetId=" + target.getId(), attacked);
        lastAttackTick = gameTick;
    }

    private boolean equip(String weapon, String offhand, String body) {
        boolean weaponMissing = weapon != null && !weapon.isBlank()
                && !Rs2Equipment.isWearing(weapon, true);
        boolean offhandMissing = offhand != null && !offhand.isBlank()
                && !Rs2Equipment.isWearing(offhand, true);
        boolean bodyMissing = body != null && !body.isBlank() && !"None".equals(body)
                && !Rs2Equipment.isWearing(body, true);
        boolean pending = weaponMissing || offhandMissing || bodyMissing;
        if (!pending || gameTick - lastGearTick < config.gearRetryTicks()) return pending;

        boolean interacted = false;
        if (weaponMissing) interacted |= equipItem(weapon);
        if (offhandMissing) interacted |= equipItem(offhand);
        if (bodyMissing) interacted |= equipItem(body);
        if (interacted) lastGearTick = gameTick;
        return true;
    }

    private boolean equipItem(String name) {
        if (name == null || name.isBlank() || "None".equals(name)
                || Rs2Equipment.isWearing(name, true)) return false;
        Rs2ItemModel item = Rs2Inventory.get(name, true);
        boolean equipped = item != null
                && (Rs2Inventory.interact(item, "Wield") || Rs2Inventory.interact(item, "Wear"));
        recordAction("EQUIP_ITEM", name + (item == null ? " not found in inventory" : ""), equipped);
        return equipped;
    }

    private String resolveGearItem(String custom, String[][] brackets) {
        if (custom != null && !custom.isBlank() && hasItem(custom)) return custom;
        int firstBracket = config.gearTier().firstBracket();
        for (int bracket = firstBracket; bracket < brackets.length; bracket++) {
            for (String candidate : brackets[bracket]) {
                if (hasItem(candidate)) return candidate;
            }
        }
        return "None";
    }

    private String resolveDawnSpecWeapon() {
        String custom = config.customDawnSpecWeapon();
        if (custom != null && !custom.isBlank() && hasItem(custom)) return custom;
        for (String[] bracket : DAWN_SPEC_WEAPON_BRACKETS) {
            for (String candidate : bracket) {
                if (hasItem(candidate)) return candidate;
            }
        }
        return "None";
    }

    private GrotesqueGuardiansFinisher resolveFinisher() {
        GrotesqueGuardiansFinisher configured = config.finisher();
        if (configured != GrotesqueGuardiansFinisher.AUTO) {
            return Rs2Inventory.count(configured.itemId()) > 0 ? configured : null;
        }
        for (GrotesqueGuardiansFinisher candidate : AUTO_FINISHERS) {
            if (Rs2Inventory.count(candidate.itemId()) > 0) return candidate;
        }
        return null;
    }

    private boolean hasItem(String name) {
        return Rs2Inventory.get(name, true) != null || Rs2Equipment.isWearing(name, true);
    }

    private boolean hasEnoughFinisherUses(GrotesqueGuardiansFinisher resolved) {
        if (resolved == null) return false;
        int required = resolved.consumable() ? 2 : 1;
        return Rs2Inventory.count(resolved.itemId()) >= required;
    }

    private String missingGearStatus() {
        if ("None".equals(dawnWeapon)) return "Stopped: no eligible Dawn halberd";
        if ("None".equals(duskWeapon)) return "Stopped: no eligible Dusk weapon";
        if (!finisherPresent) return "Stopped: no usable gargoyle finisher";
        if (!foodPresent) return "Stopped: no food";
        return "Stopped: trip complete";
    }

    private boolean handleHealingSphere() {
        Rs2TileObjectModel sphere = Microbot.getRs2TileObjectCache().query().withIds(SPHERE_IDS)
                .within(config.sphereMaxDistance()).nearest();
        if (sphere == null) return false;
        if (gameTick <= lastMoveTick) return true;
        WorldPoint location = Microbot.getClientThread().runOnClientThreadOptional(
                sphere::getWorldLocation).orElse(null);
        if (location == null) return true;
        if (isDangerous(location)) {
            status = "Waiting to safely absorb healing sphere";
            return true;
        }
        status = "Absorbing healing sphere";
        boolean absorbed = sphere.click("Absorb");
        recordAction("ABSORB_HEALING_SPHERE",
                "target=" + location.getX() + "," + location.getY(), absorbed);
        lastMoveTick = gameTick;
        return true;
    }

    private void loot() {
        disablePrayers();
        if (drinkSurgePotionForNextFight()) return;
        Rs2TileItemModel item = Microbot.getRs2TileItemCache().query()
                .where(tileItem -> tileItem.isLootAble() && tileItem.getTotalValue() >= config.lootValue())
                .within(12).nearest();
        if (item == null) {
            emptyLootTicks++;
            lootFinished = emptyLootTicks >= config.lootEmptyTicks();
            status = lootFinished ? "Looting complete" : "Waiting for drops";
            return;
        }
        emptyLootTicks = 0;
        status = "Looting " + item.getName();
        item.pickup();
    }

    private boolean drinkSurgePotionForNextFight() {
        if (!config.useSurgePotion() || surgeUsedThisKill || specEnergy >= 1000
                || Microbot.getVarbitValue(VarbitID.SURGE_POTION_TIMER) != 0
                || !Rs2Inventory.hasItem("Surge potion")) return false;
        if (Rs2Inventory.interact("Surge potion", "Drink")) {
            surgeUsedThisKill = true;
            status = "Restoring spec for next fight with surge potion";
            lastSupplyTick = gameTick;
            return true;
        }
        return false;
    }

    private Rs2NpcModel nearestNpc(Set<Integer> ids) {
        return Microbot.getRs2NpcCache().query().where(npc -> ids.contains(npc.getId())).nearest();
    }

    private Rs2NpcModel currentDusk() {
        return Microbot.getRs2NpcCache().query().where(npc ->
                PHASE_2_IDS.contains(npc.getId()) || PHASE_4_IDS.contains(npc.getId())).nearest();
    }

    private WorldArea getWorldArea(Rs2NpcModel npc) {
        if (npc == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> npc.getNpc().getWorldArea()).orElse(null);
    }

    private boolean isDead(Rs2NpcModel npc) {
        return npc != null && Microbot.getClientThread().runOnClientThreadOptional(
                () -> npc.getNpc().isDead()).orElse(false);
    }

    private WorldPoint findSafeTile(Rs2NpcModel dusk) {
        if (playerLocation == null) return null;
        WorldArea duskArea = getWorldArea(dusk);
        List<WorldPoint> candidates = new ArrayList<>();
        for (int radius = 1; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    if (Math.max(Math.abs(x), Math.abs(y)) != radius) continue;
                    WorldPoint tile = playerLocation.dx(x).dy(y);
                    if (isDangerous(tile) || (duskArea != null && duskArea.contains(tile))) continue;
                    if (gameTick <= evadeUntilTick && duskArea != null
                            && duskArea.distanceTo(tile) < p2SafeDistance()) continue;
                    if (Rs2Walker.isWalkableInCollisionMap(tile)) candidates.add(tile);
                }
            }
            if (!candidates.isEmpty()) break;
        }
        return candidates.stream().min(Comparator.comparingInt(tile -> distance(tile, playerLocation))).orElse(null);
    }

    private WorldPoint findP2DodgeTile(Rs2NpcModel dusk) {
        if (playerLocation == null || dusk == null) return null;
        WorldArea duskArea = getWorldArea(dusk);
        if (duskArea == null) return null;
        int safeDistance = p2SafeDistance();
        for (int radius = 1; radius <= 6; radius++) {
            List<WorldPoint> safeTiles = new ArrayList<>();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    if (Math.max(Math.abs(x), Math.abs(y)) != radius) continue;
                    WorldPoint tile = playerLocation.dx(x).dy(y);
                    if (isDangerous(tile) || duskArea.distanceTo(tile) < safeDistance
                            || !Rs2Walker.isWalkableInCollisionMap(tile)) continue;
                    safeTiles.add(tile);
                }
            }
            if (!safeTiles.isEmpty()) {
                return safeTiles.stream()
                        .min(Comparator.comparingInt(duskArea::distanceTo))
                        .orElse(null);
            }
        }
        return null;
    }

    private int p2SafeDistance() {
        return Math.max(P2_MINIMUM_SAFE_DISTANCE, config.p2DodgeDistance());
    }

    private boolean dodgeP2ShockwaveImmediately() {
        if (!p2DodgeInProgress.compareAndSet(false, true)) return true;
        try {
            Rs2NpcModel dusk = currentDusk();
            WorldArea duskArea = getWorldArea(dusk);
            if (playerLocation == null || duskArea == null || gameTick > evadeUntilTick
                    || duskArea.distanceTo(playerLocation) >= p2SafeDistance()) return false;
            WorldPoint safe = findP2DodgeTile(dusk);
            if (safe == null) {
                status = "Finding minimum safe distance from Dusk";
                return true;
            }
            if (lastP2DodgeAttemptTick == gameTick) return true;
            lastP2DodgeAttemptTick = gameTick;
            status = "Escaping Dusk's shockwave to distance " + p2SafeDistance();
            boolean moved = Rs2Walker.walkFastCanvas(safe);
            recordAction("DODGE_P2_BLINDING_ATTACK",
                    "target=" + safe.getX() + "," + safe.getY()
                            + " duskDistance=" + duskArea.distanceTo(safe), moved);
            lastMoveTick = gameTick;
            return true;
        } finally {
            p2DodgeInProgress.set(false);
        }
    }

    private void queueImmediateP2Dodge() {
        if (!isRunning() || !p2DodgeQueued.compareAndSet(false, true)) return;
        scheduledExecutorService.execute(() -> {
            try {
                if (Microbot.isLoggedIn()) dodgeP2ShockwaveImmediately();
            } catch (Exception exception) {
                log.warn("Immediate P2 shockwave dodge failed: {}", exception.getMessage());
            } finally {
                p2DodgeQueued.set(false);
            }
        });
    }

    private WorldPoint findPrisonExit() {
        if (prisonCenter == null || playerLocation == null) return null;
        if (prisonOpening != null) return prisonOpening;

        List<WorldPoint> perimeter = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                if (x != 0 || y != 0) perimeter.add(prisonCenter.dx(x).dy(y));
            }
        }
        List<WorldPoint> missingFlames = perimeter.stream()
                .filter(tile -> !hasExactActiveHazard(tile))
                .collect(Collectors.toList());
        if (missingFlames.size() == 1) {
            prisonOpening = missingFlames.get(0);
            return prisonOpening;
        }

        List<WorldPoint> collisionOpenings = perimeter.stream()
                .filter(Rs2Walker::isWalkableInCollisionMap)
                .collect(Collectors.toList());
        if (collisionOpenings.size() == 1) {
            prisonOpening = collisionOpenings.get(0);
            return prisonOpening;
        }
        return null;
    }

    private boolean hasExactActiveHazard(WorldPoint tile) {
        Hazard hazard = hazards.get(tile);
        return hazard != null && hazard.expiresAtTick >= gameTick;
    }

    private boolean escapePrisonImmediately() {
        if (!prisonEscapeInProgress.compareAndSet(false, true)) return true;
        try {
            if (playerLocation == null || prisonCenter == null || gameTick > prisonUntilTick
                    || distance(prisonCenter, playerLocation) > 1) return false;
            WorldPoint safe = findPrisonExit();
            if (safe == null) {
                status = "Finding the missing prison flame";
                return true;
            }
            if (safe.equals(playerLocation)) {
                status = "Standing safely in the prison opening";
                return true;
            }
            if (lastPrisonEscapeAttemptTick == gameTick) return true;
            lastPrisonEscapeAttemptTick = gameTick;
            status = "Escaping prison immediately";
            boolean moved = Rs2Walker.walkFastCanvas(safe);
            recordAction("ESCAPE_PRISON",
                    "target=" + safe.getX() + "," + safe.getY(), moved);
            prisonEscapeAttempts++;
            lastMoveTick = gameTick;
            return true;
        } finally {
            prisonEscapeInProgress.set(false);
        }
    }

    private void queueImmediatePrisonEscape() {
        if (!isRunning() || !prisonEscapeQueued.compareAndSet(false, true)) return;
        scheduledExecutorService.execute(() -> {
            try {
                if (Microbot.isLoggedIn()) escapePrisonImmediately();
            } catch (Exception exception) {
                log.warn("Immediate prison escape failed: {}", exception.getMessage());
            } finally {
                prisonEscapeQueued.set(false);
            }
        });
    }

    private boolean isDangerous(WorldPoint tile) {
        return hazards.entrySet().stream().anyMatch(entry ->
                entry.getValue().expiresAtTick >= gameTick
                        && distance(entry.getKey(), tile) <= entry.getValue().radius);
    }

    private int distance(WorldPoint first, WorldPoint second) {
        return Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getY() - second.getY()));
    }

    private void disablePrayers() {
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, false);
    }

    public void onGameTick(int tick, WorldPoint location, int hp, int maxHp, int prayer, int maxPrayer,
                           int boostedAttack, int realAttack, int boostedStrength, int realStrength) {
        gameTick = tick;
        playerLocation = location;
        if (tick > prisonUntilTick) {
            prisonCenter = null;
            prisonOrigin = null;
            prisonOpening = null;
            prisonDetectedTick = -1;
            prisonEscapeAttempts = 0;
        } else if (prisonCenter == null && prisonDetectedTick >= 0
                && tick > prisonDetectedTick && location != null) {
            prisonCenter = location;
            prisonOpening = null;
            lastPrisonEscapeAttemptTick = -100;
            queueImmediatePrisonEscape();
        }
        hitpoints = hp;
        maxHitpoints = Math.max(1, maxHp);
        this.prayer = prayer;
        this.maxPrayer = Math.max(1, maxPrayer);
        this.boostedAttack = boostedAttack;
        this.realAttack = realAttack;
        this.boostedStrength = boostedStrength;
        this.realStrength = realStrength;
        playerDead = hp <= 0;
    }

    public void onBossAnimation(int npcId, int animation, WorldPoint location) {
        if (!isBossId(npcId)) return;
        lastBossNpcId = npcId;
        lastBossAnimation = animation;
        lastBossAnimationTick = gameTick;
        if (PHASE_2_IDS.contains(npcId) && isP2SpecialAnimation(animation)) {
            detectP2Special("AnimationChanged");
        } else if (PHASE_4_PRISON_IDS.contains(npcId) && isP4PrisonAnimation(animation)) {
            p4PrisonAnimationActive = true;
            detectP4Prison("AnimationChanged");
        } else if (animation == AnimationID.GG_DUSK_ENRAGE_ATTACK_RANGED) {
            lastDuskAttack = AttackStyle.RANGED;
        } else if (animation == AnimationID.GG_DUSK_ENRAGE_ATTACK_SLASH) {
            lastDuskAttack = AttackStyle.MELEE;
        }
    }

    private void pollP2SpecialAnimation(Rs2NpcModel dusk) {
        int animation = Microbot.getClientThread().runOnClientThreadOptional(
                () -> dusk.getNpc().getAnimation()).orElse(-1);
        if (!isP2SpecialAnimation(animation)) return;
        lastBossNpcId = dusk.getId();
        lastBossAnimation = animation;
        lastBossAnimationTick = gameTick;
        detectP2Special("snapshotPoll");
    }

    private boolean isP2SpecialAnimation(int animation) {
        return animation == AnimationID.GG_DUSK_ATTACK_SPECIAL;
    }

    private void detectP2Special(String source) {
        evadeUntilTick = Math.max(evadeUntilTick, gameTick + config.p2EvadeTicks());
        if (lastP2SpecialTick != gameTick) {
            lastP2SpecialTick = gameTick;
            lastP2SpecialDetectionSource = source;
            queueImmediateP2Dodge();
        }
    }

    private void pollP4PrisonAnimation(Rs2NpcModel dusk) {
        int animation = Microbot.getClientThread().runOnClientThreadOptional(
                () -> dusk.getNpc().getAnimation()).orElse(-1);
        if (!isP4PrisonAnimation(animation)) {
            p4PrisonAnimationActive = false;
            return;
        }
        lastBossNpcId = dusk.getId();
        lastBossAnimation = animation;
        lastBossAnimationTick = gameTick;
        if (!p4PrisonAnimationActive) detectP4Prison("snapshotPoll");
        p4PrisonAnimationActive = true;
    }

    private boolean isP4PrisonAnimation(int animation) {
        return animation == AnimationID.GG_DUSK_ENRAGE_ATTACK_SPECIAL
                || animation == AnimationID.GG_DUSK_ATTACK_FORCEFIELD;
    }

    private void detectP4Prison(String source) {
        if (playerLocation == null || gameTick <= prisonUntilTick) return;
        prisonDetectedTick = gameTick;
        prisonUntilTick = gameTick + P4_PRISON_ESCAPE_TICKS;
        prisonOrigin = playerLocation;
        prisonCenter = null;
        prisonOpening = null;
        prisonEscapeAttempts = 0;
        lastP4PrisonTick = gameTick;
        lastP4PrisonDetectionSource = source;
    }

    private void pollTileHazards() {
        List<HazardObservation> observations = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<HazardObservation> snapshot = new ArrayList<>();
            for (GraphicsObject graphics : Microbot.getClient().getTopLevelWorldView().getGraphicsObjects()) {
                int id = graphics.getId();
                if (!LIGHTNING_GRAPHICS.contains(id) && !DEBRIS_GRAPHICS.contains(id)) continue;
                WorldPoint location = WorldPoint.fromLocal(Microbot.getClient(), graphics.getLocation());
                if (location != null) {
                    snapshot.add(new HazardObservation("graphics", id, graphics.getStartCycle(), location));
                }
            }
            for (Projectile projectile : Microbot.getClient().getProjectiles()) {
                if (!STONE_BALL_PROJECTILES.contains(projectile.getId())) continue;
                WorldPoint target = projectile.getTargetPoint();
                if (target != null) {
                    snapshot.add(new HazardObservation(
                            "projectile", projectile.getId(), projectile.getStartCycle(), target));
                }
            }
            return snapshot;
        }).orElse(List.of());
        observations.forEach(this::registerHazardObservation);
    }

    private void registerHazardObservation(HazardObservation observation) {
        WorldPoint location = observation.location;
        String key = observation.type + ':' + observation.id + ':' + observation.startCycle + ':'
                + location.getX() + ':' + location.getY() + ':' + location.getPlane();
        if (!processedHazardObservations.add(key)) return;
        if ("projectile".equals(observation.type)) {
            lastProjectileId = observation.id;
            lastProjectileTarget = location;
            hazards.put(location, new Hazard(1, gameTick + config.debrisAvoidTicks()));
            return;
        }
        lastGraphicsId = observation.id;
        lastGraphicsLocation = observation.location;
        if (LIGHTNING_GRAPHICS.contains(observation.id)) {
            hazards.put(location, new Hazard(1, gameTick + config.lightningAvoidTicks()));
        } else if (DEBRIS_GRAPHICS.contains(observation.id)) {
            hazards.put(location, new Hazard(1, gameTick + config.debrisAvoidTicks()));
        }
    }

    public void onGraphicsObject(int id, WorldPoint location, int startCycle) {
        lastGraphicsId = id;
        lastGraphicsLocation = location;
        if (location == null
                || (!LIGHTNING_GRAPHICS.contains(id) && !DEBRIS_GRAPHICS.contains(id))) return;
        registerHazardObservation(new HazardObservation("graphics", id, startCycle, location));
    }

    public void onProjectile(int id, WorldPoint target, int startCycle) {
        if (target == null || !STONE_BALL_PROJECTILES.contains(id)) return;
        registerHazardObservation(new HazardObservation("projectile", id, startCycle, target));
    }

    public void onKillCompleted() {
        if (!killCompleted) killCount++;
        killCompleted = true;
    }

    public void onPlayerDeath() {
        playerDead = true;
    }

    public int getHazardCount() {
        return hazards.size();
    }

    private boolean isBossId(int npcId) {
        for (int id : ALL_BOSS_IDS) {
            if (id == npcId) return true;
        }
        return false;
    }

    private String pointText(WorldPoint point) {
        return point == null ? null : point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    @Override
    public void shutdown() {
        disablePrayers();
        hazards.clear();
        processedHazardObservations.clear();
        super.shutdown();
        displayState = GrotesqueGuardiansState.IDLE;
        status = "Stopped";
    }
}

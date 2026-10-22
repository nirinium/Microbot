package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class GrotesqueGuardiansMechanicsTest {
    private static final WorldPoint PLAYER = new WorldPoint(11877, 9310, 0);

    @Test
    public void shockwaveWindowIsNotExtendedByRepeatedAnimationSamples() throws Exception {
        GrotesqueGuardiansScript script = script();
        script.onBossAnimation(NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING,
                AnimationID.GG_DUSK_ATTACK_SPECIAL, PLAYER);
        assertEquals(13, getField(script, "evadeUntilTick"));
        setField(script, "gameTick", 12);
        script.onBossAnimation(NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING,
                AnimationID.GG_DUSK_ATTACK_SPECIAL, PLAYER);
        assertEquals(13, getField(script, "evadeUntilTick"));
    }

    @Test
    public void newShockwaveAfterAnAnimationChangeStartsANewWindow() throws Exception {
        GrotesqueGuardiansScript script = script();
        script.onBossAnimation(NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING,
                AnimationID.GG_DUSK_ATTACK_SPECIAL, PLAYER);
        script.onBossAnimation(NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING, -1, PLAYER);
        setField(script, "gameTick", 20);
        script.onBossAnimation(NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING,
                AnimationID.GG_DUSK_ATTACK_SPECIAL, PLAYER);
        assertEquals(23, getField(script, "evadeUntilTick"));
    }

    @Test
    public void clientTicksRefreshMovementOutsideThePrisonPhase() throws Exception {
        GrotesqueGuardiansScript script = spy(script());
        doReturn(true).when(script).isRunning();
        script.onClientTick(PLAYER.dx(2));
        assertEquals(PLAYER.dx(2), getField(script, "playerLocation"));
    }

    @Test
    public void sameTickRockForcesReplanningOfTheShockwaveDestination() throws Exception {
        GrotesqueGuardiansScript script = script();
        WorldArea dusk = new WorldArea(PLAYER.dx(-3).dy(-1), 3, 3);
        setField(script, "lastP2SpecialTick", 10);
        setField(script, "evadeUntilTick", 13);
        setField(script, "lastMoveTick", 10);
        setField(script, "lastDodgeTarget", PLAYER.dx(2));
        assertTrue((boolean) invoke(script, "canContinueDodge", new Class<?>[]{WorldArea.class}, dusk));
        script.onGraphicsObject(SpotanimID.GARGBOSS_DEBRIS_SHADOW_60, PLAYER.dx(2), 101);
        assertFalse((boolean) invoke(script, "canContinueDodge", new Class<?>[]{WorldArea.class}, dusk));
        assertTrue((boolean) invoke(script, "hasPendingDodgeThreat", new Class<?>[0]));
    }

    @Test
    public void projectileImmediatelyQueuesAReplanForThePendingWalk() throws Exception {
        GrotesqueGuardiansScript script = spy(script());
        doReturn(true).when(script).isRunning();
        setField(script, "lastMoveTick", 10);
        setField(script, "lastDodgeTarget", PLAYER.dx(2));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        Field field = Script.class.getDeclaredField("scheduledExecutorService");
        field.setAccessible(true);
        field.set(script, executor);
        script.onProjectile(SpotanimID.GG_FALLING_PROJECTILE, PLAYER.dx(2), 101);
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    public void doesNotSpamASafePartialShockwaveWaypointBeforeArrival() throws Exception {
        GrotesqueGuardiansScript script = script();
        WorldArea dusk = new WorldArea(PLAYER.dx(-3).dy(-1), 3, 3);
        setField(script, "lastP2SpecialTick", 10);
        setField(script, "evadeUntilTick", 13);
        setField(script, "lastMoveTick", 10);
        setField(script, "lastDodgeTarget", PLAYER.dy(1));
        assertFalse((boolean) invoke(script, "canContinueDodge", new Class<?>[]{WorldArea.class}, dusk));
        setField(script, "lastDodgeForShockwave", true);
        assertTrue((boolean) invoke(script, "canContinueDodge", new Class<?>[]{WorldArea.class}, dusk));
        setField(script, "playerLocation", PLAYER.dy(1));
        assertFalse((boolean) invoke(script, "canContinueDodge", new Class<?>[]{WorldArea.class}, dusk));
    }

    @Test
    public void changedLoadoutBypassesThePreviousGearRetryWindow() throws Exception {
        GrotesqueGuardiansScript script = script();
        setField(script, "requestedLoadout", "Old weapon::");
        setField(script, "lastGearTick", 10);
        try (MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            invoke(script, "equip", new Class<?>[]{String.class, String.class, String.class},
                    "New weapon", "", "");
            inventory.verify(() -> Rs2Inventory.get("New weapon", true));
        }
    }

    @Test
    public void sameLoadoutStillHonorsItsGearRetryWindow() throws Exception {
        GrotesqueGuardiansScript script = script();
        setField(script, "requestedLoadout", "Same weapon::");
        setField(script, "lastGearTick", 10);
        try (MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
             MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class)) {
            invoke(script, "equip", new Class<?>[]{String.class, String.class, String.class},
                    "Same weapon", "", "");
            inventory.verifyNoInteractions();
        }
    }

    @Test
    public void shockwaveBlocksAttackingEvenAfterReachingASafeTile() throws Exception {
        GrotesqueGuardiansScript script = script();
        setField(script, "lastP2SpecialTick", 10);
        setField(script, "evadeUntilTick", 13);
        Rs2NpcModel target = mock(Rs2NpcModel.class);
        invoke(script, "attackOrFinish", new Class<?>[]{Rs2NpcModel.class}, target);
        verifyNoInteractions(target);
        setField(script, "gameTick", 14);
        assertFalse((boolean) invoke(script, "isCombatMovementBlocked", new Class<?>[0]));
    }

    @Test
    public void lightningBlocksAnAttackBeforeTheDodgeWorkerRuns() throws Exception {
        GrotesqueGuardiansScript script = script();
        script.onGraphicsObject(SpotanimID.GG_DAWN_LIGHTNING_1, PLAYER, 100);
        Rs2NpcModel target = mock(Rs2NpcModel.class);
        invoke(script, "attackOrFinish", new Class<?>[]{Rs2NpcModel.class}, target);
        verifyNoInteractions(target);
        setField(script, "playerLocation", PLAYER.dx(3));
        assertFalse((boolean) invoke(script, "isCombatMovementBlocked", new Class<?>[0]));
    }

    @Test
    public void holdsRetreatUntilTheDangerAndMovementTickHavePassed() throws Exception {
        GrotesqueGuardiansScript script = script();
        setField(script, "hazardRetreatUntilTick", 16);
        setField(script, "gameTick", 16);
        assertTrue((boolean) invoke(script, "isCombatMovementBlocked", new Class<?>[0]));
        setField(script, "gameTick", 17);
        setField(script, "lastMoveTick", 17);
        assertTrue((boolean) invoke(script, "isCombatMovementBlocked", new Class<?>[0]));
        setField(script, "gameTick", 18);
        assertFalse((boolean) invoke(script, "isCombatMovementBlocked", new Class<?>[0]));
    }

    @Test
    public void selectsAnInstanceTileFromLiveCollisionInsteadOfTheGlobalMap() throws Exception {
        GrotesqueGuardiansScript script = script();
        script.onGraphicsObject(SpotanimID.GG_DAWN_LIGHTNING_1, PLAYER, 100);
        WorldPoint safe = PLAYER.dx(2);
        try (MockedStatic<Rs2Tile> tiles = mockStatic(Rs2Tile.class)) {
            tiles.when(() -> Rs2Tile.getWalkableTilesAroundTile(PLAYER, 4))
                    .thenReturn(List.of(PLAYER.dx(1), safe));
            assertEquals(safe, invoke(script, "findSafeTile", new Class<?>[]{Rs2NpcModel.class},
                    (Object) null));
        }
    }

    @Test
    public void preparesDuskGearWhileDawnIsStillTransitioning() throws Exception {
        GrotesqueGuardiansScript script = script();
        setField(script, "detectedBossIds", List.of(NpcID.GARGBOSS_DAWN_PHASE1_TRANSITION));
        assertEquals(GrotesqueGuardiansState.DUSK_PHASE_2,
                invoke(script, "equipmentPhase", new Class<?>[]{GrotesqueGuardiansState.class},
                        GrotesqueGuardiansState.DAWN_PHASE_1));
    }

    @Test
    public void preparesDawnGearWhenDuskBecomesDefensive() throws Exception {
        GrotesqueGuardiansScript script = script();
        setField(script, "detectedBossIds", List.of(NpcID.GARGBOSS_DUSK_PHASE3_DEFENSIVE));
        assertEquals(GrotesqueGuardiansState.DAWN_PHASE_3,
                invoke(script, "equipmentPhase", new Class<?>[]{GrotesqueGuardiansState.class},
                        GrotesqueGuardiansState.DUSK_PHASE_2));
    }

    @Test
    public void finalDuskTransitionTakesPriorityOverDawnsRemainingNpc() throws Exception {
        GrotesqueGuardiansScript script = script();
        setField(script, "phase3Present", true);
        setField(script, "detectedBossIds", List.of(NpcID.GARGBOSS_DAWN_PHASE3,
                NpcID.GARGBOSS_DUSK_PHASE3_TRANSITION));
        assertEquals(GrotesqueGuardiansState.DUSK_PHASE_4,
                invoke(script, "equipmentPhase", new Class<?>[]{GrotesqueGuardiansState.class},
                        GrotesqueGuardiansState.DAWN_PHASE_3));
    }

    @Test
    public void restartInitializesDirectlyIntoLatestObservedPhase() throws Exception {
        GrotesqueGuardiansScript script = script();
        setField(script, "phase1Present", true);
        setField(script, "phase2Present", true);
        assertEquals(GrotesqueGuardiansState.DUSK_PHASE_2,
                invoke(script, "initialState", new Class<?>[0]));
        setField(script, "phase3Present", true);
        setField(script, "phase4Present", true);
        assertEquals(GrotesqueGuardiansState.DUSK_PHASE_4,
                invoke(script, "initialState", new Class<?>[0]));
    }

    @Test
    public void arenaCantReachUsesLocalMovementAndClearsGlobalRetryState() throws Exception {
        GrotesqueGuardiansScript script = spy(script());
        Rs2NpcModel target = mock(Rs2NpcModel.class);
        net.runelite.api.NPC npc = mock(net.runelite.api.NPC.class);
        LocalPoint local = new LocalPoint(3200, 3200, -1);
        when(target.getNpc()).thenReturn(npc);
        when(target.getName()).thenReturn("Dusk");
        when(target.getId()).thenReturn(NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING);
        when(npc.getLocalLocation()).thenReturn(local);
        doReturn(true).when(script).walkEmergencyLocalTile(local);
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            net.runelite.api.Client client = mock(net.runelite.api.Client.class);
            net.runelite.api.WorldView worldView = mock(net.runelite.api.WorldView.class);
            net.runelite.client.callback.ClientThread clientThread = mock(net.runelite.client.callback.ClientThread.class);
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            when(client.isInInstancedRegion()).thenReturn(true);
            when(client.getWorldView(-1)).thenReturn(worldView);
            when(worldView.getSizeX()).thenReturn(104);
            when(worldView.getSizeY()).thenReturn(104);
            when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
                java.util.concurrent.Callable<?> callable = invocation.getArgument(0);
                return java.util.Optional.ofNullable(callable.call());
            });
            Microbot.isCantReachTargetDetectionEnabled = true;
            Microbot.cantReachTarget = true;
            Microbot.cantReachTargetRetries = 4;
            assertTrue(script.recoverCantReachTarget(target));
            verify(script).walkEmergencyLocalTile(local);
            assertFalse(Microbot.cantReachTarget);
            assertEquals(0, Microbot.cantReachTargetRetries);
        } finally {
            Microbot.cantReachTarget = false;
            Microbot.cantReachTargetRetries = 0;
        }
    }

    private GrotesqueGuardiansScript script() throws Exception {
        GrotesqueGuardiansScript script = new GrotesqueGuardiansScript();
        GrotesqueGuardiansConfig config = mock(GrotesqueGuardiansConfig.class);
        when(config.p2EvadeTicks()).thenReturn(3);
        when(config.gearRetryTicks()).thenReturn(2);
        when(config.lightningAvoidTicks()).thenReturn(6);
        when(config.debrisAvoidTicks()).thenReturn(8);
        setField(script, "config", config);
        setField(script, "gameTick", 10);
        setField(script, "playerLocation", PLAYER);
        return script;
    }

    private Object invoke(GrotesqueGuardiansScript script, String name, Class<?>[] types,
                          Object... arguments) throws Exception {
        Method method = GrotesqueGuardiansScript.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(script, arguments);
    }

    private Object getField(GrotesqueGuardiansScript script, String name) throws Exception {
        Field field = GrotesqueGuardiansScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(script);
    }

    private void setField(GrotesqueGuardiansScript script, String name, Object value) throws Exception {
        Field field = GrotesqueGuardiansScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(script, value);
    }
}

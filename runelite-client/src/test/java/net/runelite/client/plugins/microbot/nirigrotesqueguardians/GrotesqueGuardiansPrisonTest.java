package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class GrotesqueGuardiansPrisonTest {
    private static final WorldPoint CENTER = new WorldPoint(11877, 9310, 0);

    @Test
    public void findsEveryPossibleOpeningOnTheOuterRing() {
        for (WorldPoint opening : ring()) {
            Set<WorldPoint> flames = ring();
            flames.remove(opening);
            assertEquals(opening, GrotesqueGuardiansPrison.findOpening(CENTER, flames));
            assertEquals(CENTER, GrotesqueGuardiansPrison.findCenter(CENTER, flames));
        }
    }

    @Test
    public void refusesIncompleteFlameSpawns() {
        Set<WorldPoint> flames = ring();
        flames.remove(CENTER.dx(2));
        flames.remove(CENTER.dy(2));
        assertNull(GrotesqueGuardiansPrison.findOpening(CENTER, flames));
        assertNull(GrotesqueGuardiansPrison.findCenter(CENTER, flames));
        assertNull(GrotesqueGuardiansPrison.findOpening(CENTER, Set.of()));
    }

    @Test
    public void doesNotTreatTheEmptyInteriorAsAnOpening() {
        assertNull(GrotesqueGuardiansPrison.findOpening(CENTER, ring()));
    }

    @Test
    public void resolvesCenterEvenWhenThePlayerIsAlreadyAtTheOpening() {
        Set<WorldPoint> flames = ring();
        WorldPoint opening = CENTER.dx(2);
        flames.remove(opening);
        assertEquals(CENTER, GrotesqueGuardiansPrison.findCenter(opening, flames));
    }

    @Test
    public void rejectsFlamesOnAnotherPlane() {
        Set<WorldPoint> flames = new HashSet<>();
        for (WorldPoint tile : ring()) {
            flames.add(new WorldPoint(tile.getX(), tile.getY(), 1));
        }
        assertNull(GrotesqueGuardiansPrison.findOpening(CENTER, flames));
        assertNull(GrotesqueGuardiansPrison.findCenter(CENTER, flames));
    }

    @Test
    public void acceptsCryVariantsButNotUnrelatedChat() {
        assertTrue(GrotesqueGuardiansPrison.matchesCry("Arghhh!"));
        assertTrue(GrotesqueGuardiansPrison.matchesCry(" Aarrrgh! "));
        assertFalse(GrotesqueGuardiansPrison.matchesCry("hello Arghhh!"));
        assertFalse(GrotesqueGuardiansPrison.matchesCry(null));
    }

    private Set<WorldPoint> ring() {
        Set<WorldPoint> tiles = new HashSet<>();
        for (int offsetX = -2; offsetX <= 2; offsetX++) {
            for (int offsetY = -2; offsetY <= 2; offsetY++) {
                if (Math.max(Math.abs(offsetX), Math.abs(offsetY)) == 2) {
                    tiles.add(CENTER.dx(offsetX).dy(offsetY));
                }
            }
        }
        return tiles;
    }

    @Test
    public void doesNotReleaseCombatAtTheDeadlineWhileFlamesWereObserved() throws Exception {
        GrotesqueGuardiansScript script = new GrotesqueGuardiansScript();
        setField(script, "prisonDetectedTick", 10);
        setField(script, "prisonUntilTick", 15);
        setField(script, "prisonFlamesObserved", true);
        script.onGameTick(16, CENTER, 99, 99, 99, 99, 99, 99, 99, 99);
        Method active = GrotesqueGuardiansScript.class.getDeclaredMethod("isPrisonActive");
        active.setAccessible(true);
        assertEquals(true, active.invoke(script));
    }

    @Test
    public void missingFlamesDoNotLeaveAnUnconfirmedPrisonStuckForever() throws Exception {
        GrotesqueGuardiansScript script = new GrotesqueGuardiansScript();
        setField(script, "prisonDetectedTick", 10);
        setField(script, "prisonUntilTick", 15);
        script.onGameTick(16, CENTER, 99, 99, 99, 99, 99, 99, 99, 99);
        Method active = GrotesqueGuardiansScript.class.getDeclaredMethod("isPrisonActive");
        active.setAccessible(true);
        assertEquals(false, active.invoke(script));
    }

    @Test
    public void blocksAnAttackThatWasAlreadyPendingWhenPrisonStarted() throws Exception {
        GrotesqueGuardiansScript script = new GrotesqueGuardiansScript();
        setField(script, "prisonDetectedTick", 10);
        setField(script, "prisonUntilTick", 15);
        setField(script, "gameTick", 10);
        Rs2NpcModel target = mock(Rs2NpcModel.class);
        Method attack = GrotesqueGuardiansScript.class.getDeclaredMethod("attackOrFinish",
                Rs2NpcModel.class);
        attack.setAccessible(true);
        attack.invoke(script, target);
        verifyNoInteractions(target);
    }

    private void setField(GrotesqueGuardiansScript script, String name, Object value) throws Exception {
        Field field = GrotesqueGuardiansScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(script, value);
    }
}

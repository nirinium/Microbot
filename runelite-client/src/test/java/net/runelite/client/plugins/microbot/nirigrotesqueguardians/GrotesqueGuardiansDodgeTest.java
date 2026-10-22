package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GrotesqueGuardiansDodgeTest {
    private static final WorldArea DUSK = new WorldArea(100, 100, 3, 3, 0);
    private static final WorldPoint START = new WorldPoint(103, 101, 0);

    @Test
    public void takesOneStepAwayFromShockwaveWhenUnobstructed() {
        List<WorldPoint> path = GrotesqueGuardiansDodge.findPath(START, DUSK, 2,
                (from, to) -> true, tile -> 0);
        assertEquals(1, path.size());
        assertTrue(DUSK.distanceTo(path.get(0)) >= 2);
    }

    @Test
    public void escapesShockwaveAndAnOverlappingFallingRockTogether() {
        WorldPoint rock = START.dx(1);
        ToIntFunction<WorldPoint> danger = tile -> Math.max(0, 2 - tile.distanceTo(rock));
        List<WorldPoint> path = GrotesqueGuardiansDodge.findPath(START, DUSK, 2,
                (from, to) -> true, danger);
        assertFalse(path.isEmpty());
        WorldPoint previous = START;
        for (WorldPoint tile : path) {
            assertTrue(danger.applyAsInt(tile) <= danger.applyAsInt(previous));
            assertTrue(DUSK.distanceTo(tile) >= DUSK.distanceTo(previous));
            previous = tile;
        }
        assertEquals(0, danger.applyAsInt(previous));
        assertTrue(DUSK.distanceTo(previous) >= 2);
    }

    @Test
    public void neverCrossesAnotherRockToReachAClearEndpoint() {
        WorldPoint rock = START.dx(2);
        ToIntFunction<WorldPoint> danger = tile -> tile.equals(rock) ? 1 : 0;
        Set<WorldPoint> corridor = Set.of(START, START.dx(1), rock, START.dx(3));
        List<WorldPoint> path = GrotesqueGuardiansDodge.findPath(START, DUSK, 4,
                (from, to) -> corridor.contains(to), danger);
        assertTrue(path.isEmpty());
    }

    @Test
    public void respectsBlockedEdgesEvenWhenTheEndpointIsWalkable() {
        List<WorldPoint> path = GrotesqueGuardiansDodge.findPath(START, DUSK, 2,
                (from, to) -> false, tile -> 0);
        assertTrue(path.isEmpty());
    }

    @Test
    public void findsASidewaysRouteAroundABlockedDirectEscape() {
        WorldPoint side = START.dy(1);
        WorldPoint corner = side.dx(1);
        List<WorldPoint> path = GrotesqueGuardiansDodge.findPath(START, DUSK, 2,
                (from, to) -> (from.equals(START) && to.equals(side))
                        || (from.equals(side) && to.equals(corner)), tile -> 0);
        assertEquals(List.of(side, corner), path);
        assertEquals(side, GrotesqueGuardiansDodge.nextWaypoint(START, path));
    }

    @Test
    public void sendsTwoStraightStepsInOneWalkRequest() {
        assertEquals(START.dx(2), GrotesqueGuardiansDodge.nextWaypoint(START,
                List.of(START.dx(1), START.dx(2), START.dx(3))));
    }

    @Test
    public void newRockInvalidatesAPreviouslySafeDestinationImmediately() {
        WorldPoint target = START.dx(2);
        assertTrue(GrotesqueGuardiansDodge.canContinue(START, target, tile -> 0));
        assertFalse(GrotesqueGuardiansDodge.canContinue(START, target,
                tile -> tile.equals(target) ? 1 : 0));
    }

    @Test
    public void newRockOnTheIntermediateTileAlsoInvalidatesTheWalk() {
        assertFalse(GrotesqueGuardiansDodge.canContinue(START, START.dx(2),
                tile -> tile.equals(START.dx(1)) ? 1 : 0));
    }

    @Test
    public void canContinueWalkingOutOfAnExistingDangerZone() {
        assertTrue(GrotesqueGuardiansDodge.canContinue(START, START.dx(2),
                tile -> Math.max(0, 2 - tile.distanceTo(START))));
    }

    @Test
    public void doesNotTreatArrivalOrAnotherPlaneAsAPendingWalk() {
        assertFalse(GrotesqueGuardiansDodge.canContinue(START, START, tile -> 0));
        assertFalse(GrotesqueGuardiansDodge.canContinue(START,
                new WorldPoint(START.getX() + 1, START.getY(), 1), tile -> 0));
    }

    @Test
    public void choosesTheShortestEscapeRatherThanRunningAcrossTheArena() {
        List<WorldPoint> path = GrotesqueGuardiansDodge.findPath(START, DUSK, 3,
                (from, to) -> true, tile -> 0);
        assertEquals(2, path.size());
        assertNotNull(GrotesqueGuardiansDodge.nextWaypoint(START, path));
    }
}

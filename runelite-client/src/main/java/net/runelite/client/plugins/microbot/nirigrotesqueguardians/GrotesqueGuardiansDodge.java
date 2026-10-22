package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

final class GrotesqueGuardiansDodge {
    private GrotesqueGuardiansDodge() {
    }

    static List<WorldPoint> findPath(WorldPoint start, WorldArea dusk, int safeDistance,
                                     BiPredicate<WorldPoint, WorldPoint> canTravel,
                                     ToIntFunction<WorldPoint> dangerDepth) {
        ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
        Map<WorldPoint, WorldPoint> previous = new HashMap<>();
        Map<WorldPoint, Integer> steps = new HashMap<>();
        queue.add(start);
        previous.put(start, start);
        steps.put(start, 0);
        WorldPoint best = null;
        int bestSteps = Integer.MAX_VALUE;
        while (!queue.isEmpty()) {
            WorldPoint current = queue.removeFirst();
            int length = steps.get(current);
            if (length > bestSteps) break;
            int depth = dangerDepth.applyAsInt(current);
            if (length > 0 && depth == 0 && dusk.distanceTo(current) >= safeDistance) {
                if (best == null || dusk.distanceTo(current) > dusk.distanceTo(best)) best = current;
                bestSteps = length;
                continue;
            }
            if (length >= 6 || length >= bestSteps) continue;
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    if (offsetX == 0 && offsetY == 0) continue;
                    WorldPoint next = current.dx(offsetX).dy(offsetY);
                    if (previous.containsKey(next) || next.distanceTo(start) > 6
                            || (!dusk.contains(current) && dusk.contains(next))
                            || dusk.distanceTo(next) < dusk.distanceTo(current)
                            || dangerDepth.applyAsInt(next) > depth || !canTravel.test(current, next)) continue;
                    previous.put(next, current);
                    steps.put(next, length + 1);
                    queue.addLast(next);
                }
            }
        }
        if (best == null) return List.of();
        List<WorldPoint> path = new ArrayList<>();
        for (WorldPoint tile = best; !tile.equals(start); tile = previous.get(tile)) path.add(tile);
        Collections.reverse(path);
        return path;
    }

    static WorldPoint nextWaypoint(WorldPoint start, List<WorldPoint> path) {
        if (path.isEmpty()) return null;
        WorldPoint first = path.get(0);
        if (path.size() < 2) return first;
        WorldPoint second = path.get(1);
        return first.getX() - start.getX() == second.getX() - first.getX()
                && first.getY() - start.getY() == second.getY() - first.getY() ? second : first;
    }

    static boolean canContinue(WorldPoint start, WorldPoint target, ToIntFunction<WorldPoint> dangerDepth) {
        if (start == null || target == null || start.getPlane() != target.getPlane()
                || start.equals(target)) return false;
        WorldPoint current = start;
        int depth = dangerDepth.applyAsInt(current);
        for (int step = 0; step < 2; step++) {
            current = current.dx(Integer.signum(target.getX() - current.getX()))
                    .dy(Integer.signum(target.getY() - current.getY()));
            int nextDepth = dangerDepth.applyAsInt(current);
            if (nextDepth > depth) return false;
            if (current.equals(target)) return true;
            depth = nextDepth;
        }
        return false;
    }
}

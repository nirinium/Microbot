package net.runelite.client.plugins.microbot.nirigrotesqueguardiansbanking;

import net.runelite.api.coords.WorldPoint;

import java.util.Set;

final class BankingGuardiansPrison {
    static final int RADIUS = 2;

    private BankingGuardiansPrison() {
    }

    static boolean matchesCry(String text) {
        return text != null && text.trim().matches("(?i)a+r+g+h+!*");
    }

    static WorldPoint findOpening(WorldPoint center, Set<WorldPoint> flames) {
        if (center == null) return null;
        WorldPoint opening = null;
        for (int offsetX = -RADIUS; offsetX <= RADIUS; offsetX++) {
            for (int offsetY = -RADIUS; offsetY <= RADIUS; offsetY++) {
                if (Math.max(Math.abs(offsetX), Math.abs(offsetY)) != RADIUS) continue;
                WorldPoint tile = center.dx(offsetX).dy(offsetY);
                if (!flames.contains(tile)) {
                    if (opening != null) return null;
                    opening = tile;
                }
            }
        }
        return opening;
    }

    static WorldPoint findCenter(WorldPoint player, Set<WorldPoint> flames) {
        if (player == null) return null;
        WorldPoint center = null;
        for (int offsetX = -RADIUS; offsetX <= RADIUS; offsetX++) {
            for (int offsetY = -RADIUS; offsetY <= RADIUS; offsetY++) {
                WorldPoint candidate = player.dx(offsetX).dy(offsetY);
                if (findOpening(candidate, flames) != null) {
                    if (center != null) return null;
                    center = candidate;
                }
            }
        }
        return center;
    }
}


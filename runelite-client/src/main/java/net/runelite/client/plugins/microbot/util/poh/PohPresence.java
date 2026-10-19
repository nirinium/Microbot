package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** Lightweight house-presence check for pathfinder refreshes. */
public final class PohPresence {
    private PohPresence() {
    }

    public static boolean isInHouse() {
        if (!Rs2Player.IsInInstance()) {
            return false;
        }
        // The player's mapped world tile is unreliable inside a house instance. Some
        // generated layouts do not expose the exit portal through the cache immediately
        // (or at all while another room is the active scene), so built POH transport
        // objects are also direct evidence that this instance is a player-owned house.
        return Microbot.getRs2TileObjectCache()
                .query()
                .withIds(ObjectID.POH_EXIT_PORTAL, ObjectID.POH_FAIRY_RING, ObjectID.POH_SPIRIT_RING)
                .nearest() != null;
    }
}

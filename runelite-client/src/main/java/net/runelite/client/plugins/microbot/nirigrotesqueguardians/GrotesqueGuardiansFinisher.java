package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import net.runelite.api.gameval.ItemID;

public enum GrotesqueGuardiansFinisher {
    AUTO("Best available", -1, false),
    ROCK_HAMMER("Rock hammer", ItemID.SLAYER_ROCK_HAMMER, false),
    GRANITE_HAMMER("Granite hammer", ItemID.GRANITE_HAMMER, false),
    ROCK_THROWNHAMMER("Rock thrownhammer", ItemID.SLAYER_ROCK_THROWNHAMMER, true);

    private final String itemName;
    private final int itemId;
    private final boolean consumable;

    GrotesqueGuardiansFinisher(String itemName, int itemId, boolean consumable) {
        this.itemName = itemName;
        this.itemId = itemId;
        this.consumable = consumable;
    }

    String itemName() {
        return itemName;
    }

    int itemId() {
        return itemId;
    }

    boolean consumable() {
        return consumable;
    }

    @Override
    public String toString() {
        return itemName;
    }
}

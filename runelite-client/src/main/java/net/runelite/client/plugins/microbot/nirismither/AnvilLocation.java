package net.runelite.client.plugins.microbot.nirismither;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum AnvilLocation {
    VARROCK_WEST("Varrock West", new WorldPoint(3189, 3436, 0), new WorldPoint(3185, 3436, 0)),
    VARROCK_EAST("Varrock East", new WorldPoint(3286, 3401, 0), new WorldPoint(3253, 3420, 0)),
    FALADOR("Falador", new WorldPoint(2977, 3369, 0), new WorldPoint(3012, 3355, 0)),
    YANILLE("Yanille", new WorldPoint(2614, 3093, 0), new WorldPoint(2612, 3093, 0));
    
    private final String name;
    private final WorldPoint anvilLocation;
    private final WorldPoint bankLocation;
    
    AnvilLocation(String name, WorldPoint anvilLocation, WorldPoint bankLocation) {
        this.name = name;
        this.anvilLocation = anvilLocation;
        this.bankLocation = bankLocation;
    }
    
    @Override
    public String toString() {
        return name;
    }
}

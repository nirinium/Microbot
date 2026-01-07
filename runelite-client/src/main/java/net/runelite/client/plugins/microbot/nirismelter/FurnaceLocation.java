package net.runelite.client.plugins.microbot.nirismelter;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum FurnaceLocation {
    EDGEVILLE("Edgeville", new WorldPoint(3109, 3499, 0)),
    FALADOR("Falador", new WorldPoint(2974, 3369, 0)),
    AL_KHARID("Al Kharid", new WorldPoint(3275, 3185, 0)),
    LUMBRIDGE("Lumbridge", new WorldPoint(3222, 3255, 0)),
    PORT_PHASMATYS("Port Phasmatys", new WorldPoint(3687, 3489, 0)),
    NEITIZNOT("Neitiznot", new WorldPoint(2344, 3809, 0)),
    PRIFDDINAS("Prifddinas", new WorldPoint(3311, 6092, 0)),
    KOUREND("Kourend", new WorldPoint(1630, 3656, 0)),
    SHILO_VILLAGE("Shilo Village", new WorldPoint(2828, 2999, 0));
    
    private final String name;
    private final WorldPoint location;
    
    FurnaceLocation(String name, WorldPoint location) {
        this.name = name;
        this.location = location;
    }
    
    @Override
    public String toString() {
        return name;
    }
}

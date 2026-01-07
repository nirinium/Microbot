package net.runelite.client.plugins.microbot.nirismelter;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum BankLocation {
    EDGEVILLE("Edgeville", new WorldPoint(3094, 3492, 0)),
    FALADOR("Falador East", new WorldPoint(3013, 3355, 0)),
    AL_KHARID("Al Kharid", new WorldPoint(3269, 3167, 0)),
    LUMBRIDGE("Lumbridge", new WorldPoint(3208, 3220, 2)),
    PORT_PHASMATYS("Port Phasmatys", new WorldPoint(3687, 3466, 0)),
    NEITIZNOT("Neitiznot", new WorldPoint(2337, 3807, 0)),
    PRIFDDINAS("Prifddinas", new WorldPoint(3256, 6107, 0)),
    KOUREND("Kourend", new WorldPoint(1612, 3681, 0)),
    SHILO_VILLAGE("Shilo Village", new WorldPoint(2852, 2953, 0)),
    GRAND_EXCHANGE("Grand Exchange", new WorldPoint(3164, 3487, 0)),
    VARROCK_WEST("Varrock West", new WorldPoint(3185, 3436, 0)),
    VARROCK_EAST("Varrock East", new WorldPoint(3253, 3420, 0));
    
    private final String name;
    private final WorldPoint location;
    
    BankLocation(String name, WorldPoint location) {
        this.name = name;
        this.location = location;
    }
    
    @Override
    public String toString() {
        return name;
    }
}

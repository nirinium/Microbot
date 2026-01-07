package net.runelite.client.plugins.microbot.nirismelter;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum OreType {
    BRONZE("Bronze bar", "Copper ore", "Tin ore"),
    IRON("Iron bar", "Iron ore"),
    SILVER("Silver bar", "Silver ore"),
    STEEL("Steel bar", "Iron ore", "Coal", "Coal"),
    GOLD("Gold bar", "Gold ore"),
    MITHRIL("Mithril bar", "Mithril ore", "Coal", "Coal", "Coal", "Coal"),
    ADAMANTITE("Adamantite bar", "Adamantite ore", "Coal", "Coal", "Coal", "Coal", "Coal", "Coal"),
    RUNITE("Runite bar", "Runite ore", "Coal", "Coal", "Coal", "Coal", "Coal", "Coal", "Coal", "Coal");
    
    private final String barName;
    private final String[] requiredOres;
    
    OreType(String barName, String... requiredOres) {
        this.barName = barName;
        this.requiredOres = requiredOres;
    }
    
    public String getPrimaryOre() {
        return requiredOres[0];
    }
    
    public boolean requiresCoal() {
        return requiredOres.length > 1 && requiredOres[1].equals("Coal");
    }
}

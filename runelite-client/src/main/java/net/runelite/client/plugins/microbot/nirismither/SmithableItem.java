package net.runelite.client.plugins.microbot.nirismither;

import lombok.Getter;

@Getter
public enum SmithableItem {
    STEEL_PLATEBODY("Steel platebody", "Steel bar", 5, 18),
    STEEL_PLATELEGS("Steel platelegs", "Steel bar", 3, 16),
    STEEL_PLATESKIRT("Steel plateskirt", "Steel bar", 3, 16),
    STEEL_2H_SWORD("Steel 2h sword", "Steel bar", 3, 14),
    STEEL_KITESHIELD("Steel kiteshield", "Steel bar", 3, 13),
    STEEL_FULL_HELM("Steel full helm", "Steel bar", 2, 8),
    STEEL_SCIMITAR("Steel scimitar", "Steel bar", 2, 5),
    STEEL_LONGSWORD("Steel longsword", "Steel bar", 2, 6),
    STEEL_SWORD("Steel sword", "Steel bar", 1, 4),
    STEEL_DAGGER("Steel dagger", "Steel bar", 1, 2);
    
    private final String itemName;
    private final String barName;
    private final int barsRequired;
    private final int widgetChildId;
    
    SmithableItem(String itemName, String barName, int barsRequired, int widgetChildId) {
        this.itemName = itemName;
        this.barName = barName;
        this.barsRequired = barsRequired;
        this.widgetChildId = widgetChildId;
    }
}

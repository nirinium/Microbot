package net.runelite.client.plugins.microbot.niriaraxxor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CombatPotionType {
    SUPER_COMBAT("Super combat", "super combat"),
    DIVINE_SUPER_COMBAT("Divine super combat", "divine super combat"),
    NONE("None", "");

    private final String label;
    /**
     * Substring matched against inventory item names (case-insensitive by Rs2Inventory).
     */
    private final String inventoryName;

    @Override
    public String toString() {
        return label;
    }
}

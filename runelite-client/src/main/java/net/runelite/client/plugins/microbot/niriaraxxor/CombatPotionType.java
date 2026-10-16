package net.runelite.client.plugins.microbot.niriaraxxor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;

@Getter
@RequiredArgsConstructor
public enum CombatPotionType {
    AUTO("Automatic", "", null),
    SUPER_COMBAT("Super combat", "super combat", Skill.STRENGTH),
    DIVINE_SUPER_COMBAT("Divine super combat", "divine super combat", Skill.STRENGTH),
    RANGING("Ranging", "ranging", Skill.RANGED),
    DIVINE_RANGING("Divine ranging", "divine ranging", Skill.RANGED),
    NONE("None", "", null);

    private final String label;
    /**
     * Substring matched against inventory item names (case-insensitive by Rs2Inventory).
     */
    private final String inventoryName;
    private final Skill boostedSkill;

    @Override
    public String toString() {
        return label;
    }
}

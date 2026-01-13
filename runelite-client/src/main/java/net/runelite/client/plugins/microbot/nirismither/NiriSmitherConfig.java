package net.runelite.client.plugins.microbot.nirismither;

import net.runelite.client.config.*;

@ConfigGroup("nirismither")
public interface NiriSmitherConfig extends Config {
    
    @ConfigSection(
            name = "Smithing Settings",
            description = "Configure smithing options",
            position = 0
    )
    String smithingSection = "smithingSettings";
    
    @ConfigSection(
            name = "Location Settings",
            description = "Configure anvil and bank locations",
            position = 1
    )
    String locationSection = "locationSettings";
    
    @ConfigSection(
            name = "Stop Conditions",
            description = "Configure when to stop the script",
            position = 2
    )
    String stopSection = "stopConditions";
    
    // Smithing Settings
    @ConfigItem(
            keyName = "itemToSmith",
            name = "Item to Smith",
            description = "Select the item to smith",
            position = 0,
            section = smithingSection
    )
    default SmithableItem itemToSmith() {
        return SmithableItem.STEEL_PLATEBODY;
    }
    
    @ConfigItem(
            keyName = "useStaminaPotions",
            name = "Use Stamina Potions",
            description = "Automatically drink stamina potions when run energy is low",
            position = 1,
            section = smithingSection
    )
    default boolean useStaminaPotions() {
        return false;
    }
    
    @ConfigItem(
            keyName = "minStaminaEnergy",
            name = "Min Energy for Stamina",
            description = "Minimum run energy before drinking stamina potion",
            position = 2,
            section = smithingSection
    )
    default int minStaminaEnergy() {
        return 30;
    }
    
    // Location Settings
    @ConfigItem(
            keyName = "anvilLocation",
            name = "Anvil Location",
            description = "Select the anvil location to use",
            position = 0,
            section = locationSection
    )
    default AnvilLocation anvilLocation() {
        return AnvilLocation.VARROCK_WEST;
    }
    
    // Stop Conditions
    @ConfigItem(
            keyName = "stopAfterItems",
            name = "Stop After Items",
            description = "Stop after smithing this many items (0 = infinite)",
            position = 0,
            section = stopSection
    )
    default int stopAfterItems() {
        return 0;
    }
    
    @ConfigItem(
            keyName = "stopAfterLevel",
            name = "Stop After Level",
            description = "Stop after reaching this Smithing level (0 = never)",
            position = 1,
            section = stopSection
    )
    default int stopAfterLevel() {
        return 0;
    }
    
    @ConfigItem(
            keyName = "stopWhenOutOfBars",
            name = "Stop When Out of Bars",
            description = "Stop the script when there are no more bars in the bank",
            position = 2,
            section = stopSection
    )
    default boolean stopWhenOutOfBars() {
        return true;
    }
}

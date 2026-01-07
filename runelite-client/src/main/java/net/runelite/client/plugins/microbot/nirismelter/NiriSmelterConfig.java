package net.runelite.client.plugins.microbot.nirismelter;

import net.runelite.client.config.*;

@ConfigGroup("nirismelter")
public interface NiriSmelterConfig extends Config {
    
    @ConfigSection(
            name = "Ore Settings",
            description = "Configure which ore to smelt",
            position = 0
    )
    String oreSection = "oreSettings";
    
    @ConfigSection(
            name = "Location Settings",
            description = "Configure furnace and bank locations",
            position = 1
    )
    String locationSection = "locationSettings";
    
    @ConfigSection(
            name = "Advanced Settings",
            description = "Advanced configuration options",
            position = 2
    )
    String advancedSection = "advancedSettings";
    
    // Ore Settings
    @ConfigItem(
            keyName = "oreType",
            name = "Ore Type",
            description = "Select the type of ore to smelt",
            position = 0,
            section = oreSection
    )
    default OreType oreType() {
        return OreType.IRON;
    }
    
    @ConfigItem(
            keyName = "useStaminaPotions",
            name = "Use Stamina Potions",
            description = "Automatically drink stamina potions when run energy is low",
            position = 1,
            section = oreSection
    )
    default boolean useStaminaPotions() {
        return false;
    }
    
    @ConfigItem(
            keyName = "minStaminaEnergy",
            name = "Min Stamina Energy",
            description = "Drink stamina potion when energy drops below this percentage",
            position = 2,
            section = oreSection
    )
    @Range(min = 1, max = 100)
    default int minStaminaEnergy() {
        return 30;
    }
    
    // Location Settings
    @ConfigItem(
            keyName = "furnaceLocation",
            name = "Furnace Location",
            description = "Select which furnace to use (uses webwalker)",
            position = 0,
            section = locationSection
    )
    default FurnaceLocation furnaceLocation() {
        return FurnaceLocation.EDGEVILLE;
    }
    
    @ConfigItem(
            keyName = "bankLocation",
            name = "Bank Location",
            description = "Select which bank to use (uses webwalker)",
            position = 1,
            section = locationSection
    )
    default BankLocation bankLocation() {
        return BankLocation.EDGEVILLE;
    }
    
    // Advanced Settings
    @ConfigItem(
            keyName = "hopOnPlayerNearby",
            name = "Hop on Player Nearby",
            description = "Hop worlds if another player is detected at the furnace",
            position = 0,
            section = advancedSection
    )
    default boolean hopOnPlayerNearby() {
        return false;
    }
    
    @ConfigItem(
            keyName = "minBreakDelay",
            name = "Min Break Delay (ms)",
            description = "Minimum delay between actions",
            position = 1,
            section = advancedSection
    )
    @Range(min = 100, max = 5000)
    default int minBreakDelay() {
        return 600;
    }
    
    @ConfigItem(
            keyName = "maxBreakDelay",
            name = "Max Break Delay (ms)",
            description = "Maximum delay between actions",
            position = 2,
            section = advancedSection
    )
    @Range(min = 100, max = 5000)
    default int maxBreakDelay() {
        return 1000;
    }
    
    @ConfigItem(
            keyName = "stopAfterLevel",
            name = "Stop After Level",
            description = "Stop script when reaching this Smithing level (0 = disabled)",
            position = 3,
            section = advancedSection
    )
    @Range(min = 0, max = 99)
    default int stopAfterLevel() {
        return 0;
    }
    
    @ConfigItem(
            keyName = "stopAfterOres",
            name = "Stop After Ores",
            description = "Stop script after smelting this many ores (0 = disabled)",
            position = 4,
            section = advancedSection
    )
    @Range(min = 0, max = 100000)
    default int stopAfterOres() {
        return 0;
    }
}

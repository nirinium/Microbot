package net.runelite.client.plugins.microbot.nirisailing;

import net.runelite.client.config.*;

@ConfigGroup("nirisailing")
public interface NiriSailingConfig extends Config {
	
	@ConfigSection(
		name = "Sailing Settings",
		description = "Configure sailing options",
		position = 0
	)
	String sailingSection = "sailingSettings";
	
	@ConfigSection(
		name = "Location Settings",
		description = "Configure port and destination locations",
		position = 1
	)
	String locationSection = "locationSettings";
	
	@ConfigSection(
		name = "Stop Conditions",
		description = "Configure when to stop the script",
		position = 2
	)
	String stopSection = "stopConditions";
	
	// Sailing Settings
	@ConfigItem(
		keyName = "sailingActivity",
		name = "Sailing Activity",
		description = "Select the sailing activity to perform",
		position = 0,
		section = sailingSection
	)
	default SailingActivity sailingActivity() {
		return SailingActivity.FISHING_TRAWLER;
	}
	
	@ConfigItem(
		keyName = "useStaminaPotions",
		name = "Use Stamina Potions",
		description = "Automatically drink stamina potions when run energy is low",
		position = 1,
		section = sailingSection
	)
	default boolean useStaminaPotions() {
		return false;
	}
	
	@ConfigItem(
		keyName = "minStaminaEnergy",
		name = "Min Energy for Stamina",
		description = "Minimum run energy before drinking stamina potion",
		position = 2,
		section = sailingSection
	)
	default int minStaminaEnergy() {
		return 30;
	}
	
	@ConfigItem(
		keyName = "eatFood",
		name = "Eat Food",
		description = "Automatically eat food when health is low",
		position = 3,
		section = sailingSection
	)
	default boolean eatFood() {
		return true;
	}
	
	@ConfigItem(
		keyName = "minHealthPercent",
		name = "Min Health %",
		description = "Minimum health percentage before eating food",
		position = 4,
		section = sailingSection
	)
	default int minHealthPercent() {
		return 50;
	}
	
	// Location Settings
	@ConfigItem(
		keyName = "portLocation",
		name = "Port Location",
		description = "Select the port location to use",
		position = 0,
		section = locationSection
	)
	default PortLocation portLocation() {
		return PortLocation.PORT_SARIM;
	}
	
	// Stop Conditions
	@ConfigItem(
		keyName = "stopAfterTrips",
		name = "Stop After Trips",
		description = "Stop after completing this many trips (0 = infinite)",
		position = 0,
		section = stopSection
	)
	default int stopAfterTrips() {
		return 0;
	}
	
	@ConfigItem(
		keyName = "stopAfterLevel",
		name = "Stop At Level",
		description = "Stop when reaching this sailing level (0 = never stop)",
		position = 1,
		section = stopSection
	)
	default int stopAfterLevel() {
		return 0;
	}
	
	@ConfigItem(
		keyName = "stopAfterMinutes",
		name = "Stop After Minutes",
		description = "Stop after running for this many minutes (0 = infinite)",
		position = 2,
		section = stopSection
	)
	default int stopAfterMinutes() {
		return 0;
	}
}

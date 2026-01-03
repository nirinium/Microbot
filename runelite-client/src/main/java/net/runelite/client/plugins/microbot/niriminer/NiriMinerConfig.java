package net.runelite.client.plugins.microbot.niriminer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("niriminer")
public interface NiriMinerConfig extends Config {
	
	@ConfigSection(
		name = "General Settings",
		description = "General mining settings",
		position = 0
	)
	String generalSection = "general";
	
	@ConfigSection(
		name = "Banking Settings",
		description = "Banking and inventory settings",
		position = 1
	)
	String bankingSection = "banking";
	
	@ConfigItem(
		keyName = "miningRadius",
		name = "Mining Radius",
		description = "Maximum distance to search for ore veins",
		position = 0,
		section = generalSection
	)
	default int miningRadius() {
		return 20;
	}
	
	@ConfigItem(
		keyName = "depositThreshold",
		name = "Deposit Threshold",
		description = "Deposit pay-dirt when this many are collected",
		position = 1,
		section = generalSection
	)
	default int depositThreshold() {
		return 26;
	}
	
	@ConfigItem(
		keyName = "collectFromSack",
		name = "Collect from Sack",
		description = "Automatically collect ores from the sack",
		position = 2,
		section = generalSection
	)
	default boolean collectFromSack() {
		return true;
	}
	
	@ConfigItem(
		keyName = "bankOres",
		name = "Bank Ores",
		description = "Bank collected ores when inventory is full",
		position = 0,
		section = bankingSection
	)
	default boolean bankOres() {
		return false;
	}
	
	@ConfigItem(
		keyName = "keepGoldenNuggets",
		name = "Keep Golden Nuggets",
		description = "Don't bank golden nuggets",
		position = 1,
		section = bankingSection
	)
	default boolean keepGoldenNuggets() {
		return true;
	}
}

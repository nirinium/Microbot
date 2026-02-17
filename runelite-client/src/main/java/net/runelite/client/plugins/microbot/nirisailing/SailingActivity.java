package net.runelite.client.plugins.microbot.nirisailing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SailingActivity {
	FISHING_TRAWLER("Fishing Trawler", 15),
	TROUBLE_BREWING("Trouble Brewing", 40),
	PEST_CONTROL("Pest Control", 25),
	CHARTER_SHIPS("Charter Ships", 1);
	
	private final String name;
	private final int requiredLevel;
	
	@Override
	public String toString() {
		return name;
	}
}

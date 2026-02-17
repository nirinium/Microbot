package net.runelite.client.plugins.microbot.nirisailing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

@Getter
@RequiredArgsConstructor
public enum PortLocation {
	PORT_SARIM("Port Sarim", new WorldPoint(3045, 3234, 0)),
	PORT_PISCARILIUS("Port Piscarilius", new WorldPoint(1824, 3691, 0)),
	PORT_KHAZARD("Port Khazard", new WorldPoint(2674, 3144, 0)),
	CATHERBY("Catherby", new WorldPoint(2792, 3414, 0)),
	BRIMHAVEN("Brimhaven", new WorldPoint(2760, 3238, 0)),
	MOS_LE_HARMLESS("Mos Le'Harmless", new WorldPoint(3680, 2930, 0));
	
	private final String name;
	private final WorldPoint location;
	
	@Override
	public String toString() {
		return name;
	}
}

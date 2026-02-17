package net.runelite.client.plugins.microbot.nirisailing;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
	name = PluginDescriptor.NIRI + "Niri Sailing",
	description = "Automated sailing script for OSRS sailing skill",
	tags = {"sailing", "microbot", "niri", "skill"},
	enabledByDefault = false
)
@Slf4j
public class NiriSailingPlugin extends Plugin {
	
	@Inject
	private NiriSailingScript script;
	
	@Inject
	private NiriSailingOverlay overlay;
	
	@Inject
	private OverlayManager overlayManager;
	
	@Inject
	private NiriSailingConfig config;
	
	@Inject
	private ConfigManager configManager;
	
	@Provides
	NiriSailingConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NiriSailingConfig.class);
	}
	
	@Override
	protected void startUp() throws AWTException {
		if (overlayManager != null) {
			overlayManager.add(overlay);
		}
		script.run(config);
	}
	
	@Override
	protected void shutDown() {
		script.shutdown();
		if (overlayManager != null) {
			overlayManager.remove(overlay);
		}
	}
}

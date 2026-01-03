package net.runelite.client.plugins.microbot.niriminer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
	name = PluginDescriptor.Default + "Niri Motherlode Miner",
	description = "Automated Motherlode Mine script for mining pay-dirt",
	tags = {"mining", "motherlode", "microbot", "niri"},
	enabledByDefault = false
)
@Slf4j
public class NiriMinerPlugin extends Plugin {
	
	@Inject
	private NiriMinerScript script;
	
	@Inject
	private NiriMinerOverlay overlay;
	
	@Inject
	private OverlayManager overlayManager;
	
	@Inject
	private NiriMinerConfig config;
	
	@Inject
	private ConfigManager configManager;
	
	@Provides
	NiriMinerConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(NiriMinerConfig.class);
	}
	
	@Override
	protected void startUp() {
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

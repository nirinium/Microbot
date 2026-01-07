package net.runelite.client.plugins.microbot.nirismelter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Niri Smelter",
        description = "All-in-one smelting script that supports any ore and furnace with webwalker integration",
        tags = {"smelting", "smithing", "microbot", "skill"},
        enabledByDefault = false
)
@Slf4j
public class NiriSmelterPlugin extends Plugin {
    @Inject
    private NiriSmelterScript smelterScript;
    
    @Inject
    private NiriSmelterOverlay smelterOverlay;
    
    @Inject
    private OverlayManager overlayManager;
    
    @Inject
    private NiriSmelterConfig config;

    @Inject
    private ConfigManager configManager;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(smelterOverlay);
        }
        smelterScript.run(config);
    }

    @Override
    protected void shutDown() {
        smelterScript.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(smelterOverlay);
        }
    }
}

package net.runelite.client.plugins.microbot.nirismither;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Niri Smither",
        description = "Smiths steel platebodies and other items at various locations",
        tags = {"smithing", "crafting", "microbot", "skill"},
        enabledByDefault = false
)
@Slf4j
public class NiriSmitherPlugin extends Plugin {
    @Inject
    private NiriSmitherScript smitherScript;
    
    @Inject
    private NiriSmitherOverlay smitherOverlay;
    
    @Inject
    private OverlayManager overlayManager;
    
    @Inject
    private NiriSmitherConfig config;

    @Inject
    private ConfigManager configManager;

    @Provides
    NiriSmitherConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NiriSmitherConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(smitherOverlay);
        }
        smitherScript.run(config);
    }

    @Override
    protected void shutDown() {
        smitherScript.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(smitherOverlay);
        }
    }
}

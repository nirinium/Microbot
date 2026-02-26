package net.runelite.client.plugins.microbot.niribrutus;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.NIRI + "Niri Brutus",
        authors = {"Niri"},
        version = "1.1.0",
        description = "Lightweight instanced boss killer for Brutus",
        tags = {"brutus", "boss", "combat", "microbot", "niri"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class NiriBrutusPlugin extends Plugin {

    @Inject
    private NiriBrutusConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NiriBrutusOverlay overlay;

    @Getter
    private final NiriBrutusScript script = new NiriBrutusScript();

    @Provides
    NiriBrutusConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NiriBrutusConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        if (npc == null || npc.getName() == null) return;
        if (npc.getName().equalsIgnoreCase(config.npcName().trim())) {
            log.debug("Target NPC spawned: {} (id={})", npc.getName(), npc.getId());
            script.onTargetSpawned(npc);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (npc == null || npc.getName() == null) return;
        if (npc.getName().equalsIgnoreCase(config.npcName().trim())) {
            log.debug("Target NPC despawned: {} (id={}) dead={}", npc.getName(), npc.getId(), npc.isDead());
            if (npc.isDead()) {
                script.incrementKillCount();
            }
            script.onTargetDespawned();
        }
    }
}

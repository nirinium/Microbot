package net.runelite.client.plugins.microbot.chatgptresponder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.NIRI + "ChatGPT Responder",
        description = "Automatically responds to in-game chat messages using ChatGPT",
        tags = {"chat", "ai", "microbot", "chatgpt", "automation"},
        enabledByDefault = false
)
@Slf4j
public class ChatGPTResponderPlugin extends Plugin {
    
    @Inject
    private ChatGPTResponderConfig config;
    
    @Inject
    private ChatGPTResponderScript script;
    
    @Inject
    private ChatGPTResponderOverlay overlay;
    
    @Inject
    private OverlayManager overlayManager;

    @Provides
    ChatGPTResponderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChatGPTResponderConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (config.apiKey().isEmpty()) {
            log.warn("ChatGPT Responder: No API key configured. Please set your OpenAI API key in the plugin settings.");
        }
        overlayManager.add(overlay);
        script.run(config);
        log.info("ChatGPT Responder started");
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        script.shutdown();
        log.info("ChatGPT Responder stopped");
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        // Forward chat messages to the script
        script.onChatMessage(event);
    }

    @Subscribe
    public void onConfigChanged(final ConfigChanged event) {
        if (event.getGroup().equals("chatgptresponder")) {
            log.info("ChatGPT Responder config changed: " + event.getKey());
        }
    }
}

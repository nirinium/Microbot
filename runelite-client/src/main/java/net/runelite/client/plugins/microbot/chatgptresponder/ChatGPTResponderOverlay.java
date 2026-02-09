package net.runelite.client.plugins.microbot.chatgptresponder;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

/**
 * Overlay that displays the status of the ChatGPT Responder plugin.
 */
public class ChatGPTResponderOverlay extends OverlayPanel {

    @Inject
    private ChatGPTResponderScript script;

    @Inject
    private ChatGPTResponderConfig config;

    @Inject
    public ChatGPTResponderOverlay(ChatGPTResponderScript script, ChatGPTResponderConfig config) {
        super();
        this.script = script;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            if (!Microbot.isLoggedIn()) return null;

            panelComponent.getChildren().clear();

            // Title
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("ChatGPT Responder")
                    .color(config.apiKey().isEmpty() ? Color.RED : Color.GREEN)
                    .build());

            // API Key status
            if (config.apiKey().isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right("No API Key")
                        .rightColor(Color.RED)
                        .build());
            } else {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Status:")
                        .right("Active")
                        .rightColor(Color.GREEN)
                        .build());
            }

            // Messages queued
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Queued:")
                    .right(String.valueOf(script.getMessagesQueued()))
                    .build());

            // Messages processed
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Processed:")
                    .right(String.valueOf(script.getMessagesProcessed()))
                    .build());

            // Response settings
            String responseMode = "";
            if (config.respondToPublicChat() && config.respondToPrivateChat()) {
                responseMode = "Public + Private";
            } else if (config.respondToPublicChat()) {
                responseMode = "Public Only";
            } else if (config.respondToPrivateChat()) {
                responseMode = "Private Only";
            } else {
                responseMode = "Disabled";
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Mode:")
                    .right(responseMode)
                    .build());

            // Mention-only mode
            if (config.onlyRespondToMentions()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Mentions:")
                        .right("Only")
                        .rightColor(Color.YELLOW)
                        .build());
            }

            // Last error
            String lastError = script.getLastError();
            if (lastError != null && !lastError.isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Error:")
                        .right(lastError.length() > 20 ? lastError.substring(0, 20) + "..." : lastError)
                        .rightColor(Color.RED)
                        .build());
            }

        } catch (Exception e) {
            // Silently fail - don't crash the overlay
        }

        return super.render(graphics);
    }
}

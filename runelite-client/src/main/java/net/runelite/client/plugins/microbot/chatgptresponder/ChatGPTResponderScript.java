package net.runelite.client.plugins.microbot.chatgptresponder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import okhttp3.*;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Script that handles incoming chat messages and responds using ChatGPT API.
 */
@Slf4j
public class ChatGPTResponderScript extends Script {

    private ChatGPTResponderConfig config;
    private final ConcurrentLinkedQueue<PendingMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();
    private Set<String> ignoredPlayers = new HashSet<>();
    
    private int messagesProcessed = 0;
    private int messagesQueued = 0;
    private String lastError = null;

    public int getMessagesProcessed() {
        return messagesProcessed;
    }

    public int getMessagesQueued() {
        return messagesQueued;
    }

    public String getLastError() {
        return lastError;
    }

    public boolean run(ChatGPTResponderConfig config) {
        this.config = config;
        updateIgnoredPlayers();
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;

                // Process queued messages
                PendingMessage pending = messageQueue.poll();
                if (pending != null) {
                    messagesQueued = messageQueue.size();
                    processMessage(pending);
                }

            } catch (Exception e) {
                log.error("Error in ChatGPT Responder script", e);
                lastError = e.getMessage();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
        
        return true;
    }

    public void onChatMessage(ChatMessage event) {
        try {
            if (!Microbot.isLoggedIn()) return;

            // Handle quest completion messages in clan chat
            if (config.congratulateQuestCompletions() && isClanMessage(event.getType())) {
                if (isQuestCompletion(event.getMessage())) {
                    String gzzVariation = getRandomGzzVariation();
                    log.info("Quest completed! Responding with: {}", gzzVariation);
                    sleep(Rs2Random.between(500, 1500));
                    sendPublicMessage(gzzVariation);
                    return;
                }
            }

            // AI responses require API key
            if (config.apiKey().isEmpty()) return;

            // Check message type
            boolean isPublic = event.getType() == ChatMessageType.PUBLICCHAT;
            boolean isPrivate = event.getType() == ChatMessageType.MODPRIVATECHAT || 
                              event.getType() == ChatMessageType.PRIVATECHAT;

            if (!isPublic && !isPrivate) return;
            if (isPublic && !config.respondToPublicChat()) return;
            if (isPrivate && !config.respondToPrivateChat()) return;

            String senderName = event.getName();
            String message = event.getMessage();

            // Ignore our own messages
            if (Microbot.getClient().getLocalPlayer() != null &&
                senderName.equalsIgnoreCase(Microbot.getClient().getLocalPlayer().getName())) {
                return;
            }

            // Check if player is ignored
            if (ignoredPlayers.contains(senderName.toLowerCase())) {
                log.debug("Ignoring message from ignored player: {}", senderName);
                return;
            }

            // Check if we should only respond to mentions
            if (config.onlyRespondToMentions()) {
                String localPlayerName = Microbot.getClient().getLocalPlayer().getName();
                if (localPlayerName != null && !message.toLowerCase().contains(localPlayerName.toLowerCase())) {
                    return;
                }
            }

            log.info("Received message from {}: {}", senderName, message);

            // Add to queue for processing
            messageQueue.add(new PendingMessage(senderName, message, event.getType()));
            messagesQueued = messageQueue.size();

        } catch (Exception e) {
            log.error("Error handling chat message", e);
            lastError = e.getMessage();
        }
    }

    private void processMessage(PendingMessage pending) {
        try {
            // Add human-like delay before responding
            int delay = Rs2Random.between(config.minResponseDelay(), config.maxResponseDelay());
            sleep(delay);

            // Get response from ChatGPT
            String response = getChatGPTResponse(pending.message);
            
            if (response == null || response.isEmpty()) {
                log.warn("Received empty response from ChatGPT");
                return;
            }

            // Truncate response to max length
            if (response.length() > config.maxResponseLength()) {
                response = response.substring(0, config.maxResponseLength() - 3) + "...";
            }

            log.info("Responding to {}: {}", pending.senderName, response);

            // Send response in game - use Tab for private messages, Enter for public
            boolean isPrivate = pending.type == ChatMessageType.PRIVATECHAT || 
                               pending.type == ChatMessageType.MODPRIVATECHAT;
            sendChatMessage(response, isPrivate);
            
            messagesProcessed++;
            lastError = null;

        } catch (Exception e) {
            log.error("Error processing message from " + pending.senderName, e);
            lastError = e.getMessage();
        }
    }

    private String getChatGPTResponse(String userMessage) throws IOException {
        String apiUrl = "https://api.openai.com/v1/chat/completions";
        
        // Build request JSON
        JsonObject message1 = new JsonObject();
        message1.addProperty("role", "system");
        message1.addProperty("content", config.systemPrompt());
        
        JsonObject message2 = new JsonObject();
        message2.addProperty("role", "user");
        message2.addProperty("content", userMessage);
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.model());
        requestBody.add("messages", gson.toJsonTree(new JsonObject[]{message1, message2}));
        requestBody.addProperty("max_tokens", 100);
        requestBody.addProperty("temperature", 0.7);

        RequestBody body = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"),
            requestBody.toString()
        );

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + config.apiKey())
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                log.error("ChatGPT API error: {} - {}", response.code(), errorBody);
                lastError = "API error: " + response.code();
                return null;
            }

            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            return jsonResponse
                    .getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString()
                    .trim();
        }
    }

    private void sendChatMessage(String message, boolean isPrivateMessage) {
        if (isPrivateMessage) {
            // Press Tab to open private message dialogue box
            Rs2Keyboard.keyPress(KeyEvent.VK_TAB);
            sleep(300, 500);
        } else {
            // Press Enter to open public chat
            Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
            sleep(300, 500);
        }
        
        // Type the message
        Rs2Keyboard.typeString(message);
        sleep(300, 500);
        
        // Press Enter to send
        Rs2Keyboard.enter();
        sleep(200, 300);
    }

    private void updateIgnoredPlayers() {
        ignoredPlayers.clear();
        String ignored = config.ignoredPlayers();
        if (!ignored.isEmpty()) {
            Arrays.stream(ignored.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .forEach(ignoredPlayers::add);
        }
    }

    private boolean isClanMessage(ChatMessageType type) {
        return type == ChatMessageType.CLAN_CHAT ||
               type == ChatMessageType.CLAN_MESSAGE ||
               type == ChatMessageType.CLAN_GUEST_CHAT ||
               type == ChatMessageType.CLAN_GUEST_MESSAGE ||
               type == ChatMessageType.CLAN_GIM_CHAT ||
               type == ChatMessageType.CLAN_GIM_MESSAGE;
    }

    private boolean isQuestCompletion(String message) {
        String lower = message.toLowerCase();
        return lower.contains("has completed") && lower.contains("quest");
    }

    private String getRandomGzzVariation() {
        String[] variations = {
            "Gzzz!",
            "GzzZzzz!",
            "Gzzzz!",
            "GZ!",
            "Gzzzzz!",
            "GzzZzz!",
            "gratz!",
            "gz gz"
        };
        return variations[Rs2Random.between(0, variations.length - 1)];
    }

    private void sendPublicMessage(String message) {
        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
        sleep(300, 500);
        Rs2Keyboard.typeString(message);
        sleep(300, 500);
        Rs2Keyboard.enter();
        sleep(200, 300);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        messageQueue.clear();
    }

    private static class PendingMessage {
        final String senderName;
        final String message;
        final ChatMessageType type;

        PendingMessage(String senderName, String message, ChatMessageType type) {
            this.senderName = senderName;
            this.message = message;
            this.type = type;
        }
    }
}

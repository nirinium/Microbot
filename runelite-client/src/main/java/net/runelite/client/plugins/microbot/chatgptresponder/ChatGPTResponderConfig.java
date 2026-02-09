package net.runelite.client.plugins.microbot.chatgptresponder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("chatgptresponder")
public interface ChatGPTResponderConfig extends Config {
    
    @ConfigSection(
        name = "API Settings",
        description = "ChatGPT API configuration",
        position = 0
    )
    String apiSection = "apiSettings";
    
    @ConfigSection(
        name = "Behavior Settings",
        description = "Bot behavior configuration",
        position = 1
    )
    String behaviorSection = "behaviorSettings";

    @ConfigItem(
        keyName = "apiKey",
        name = "OpenAI API Key",
        description = "Your OpenAI API key for ChatGPT",
        section = apiSection,
        position = 0
    )
    default String apiKey() {
        return "";
    }

    @ConfigItem(
        keyName = "model",
        name = "Model",
        description = "ChatGPT model to use (e.g., gpt-3.5-turbo, gpt-4)",
        section = apiSection,
        position = 1
    )
    default String model() {
        return "gpt-3.5-turbo";
    }

    @ConfigItem(
        keyName = "systemPrompt",
        name = "System Prompt",
        description = "System prompt that defines the bot's personality and behavior",
        section = apiSection,
        position = 2
    )
    default String systemPrompt() {
        return "you are a laid back osrs player, use terrible spelling and punctuation, be brief. punctuation needs to be way worse no capitalization etc.";
    }

    @ConfigItem(
        keyName = "respondToPublicChat",
        name = "Respond to Public Chat",
        description = "Respond to messages in public chat",
        section = behaviorSection,
        position = 0
    )
    default boolean respondToPublicChat() {
        return true;
    }

    @ConfigItem(
        keyName = "respondToPrivateChat",
        name = "Respond to Private Chat",
        description = "Respond to private messages",
        section = behaviorSection,
        position = 1
    )
    default boolean respondToPrivateChat() {
        return false;
    }

    @ConfigItem(
        keyName = "minResponseDelay",
        name = "Min Response Delay (ms)",
        description = "Minimum delay before responding (to appear human)",
        section = behaviorSection,
        position = 2
    )
    default int minResponseDelay() {
        return 1000;
    }

    @ConfigItem(
        keyName = "maxResponseDelay",
        name = "Max Response Delay (ms)",
        description = "Maximum delay before responding",
        section = behaviorSection,
        position = 3
    )
    default int maxResponseDelay() {
        return 3000;
    }

    @ConfigItem(
        keyName = "maxResponseLength",
        name = "Max Response Length",
        description = "Maximum characters in response (OSRS chat limit is 80)",
        section = behaviorSection,
        position = 4
    )
    default int maxResponseLength() {
        return 80;
    }

    @ConfigItem(
        keyName = "ignoredPlayers",
        name = "Ignored Players",
        description = "Comma-separated list of player names to ignore",
        section = behaviorSection,
        position = 5
    )
    default String ignoredPlayers() {
        return "";
    }

    @ConfigItem(
        keyName = "onlyRespondToMentions",
        name = "Only Respond to Mentions",
        description = "Only respond when your username is mentioned",
        section = behaviorSection,
        position = 6
    )
    default boolean onlyRespondToMentions() {
        return false;
    }

    @ConfigItem(
        keyName = "congratulateQuestCompletions",
        name = "Congratulate Quest Completions",
        description = "Automatically say 'Gzzz!' when clan members complete quests",
        section = behaviorSection,
        position = 7
    )
    default boolean congratulateQuestCompletions() {
        return true;
    }
}

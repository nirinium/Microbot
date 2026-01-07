package net.runelite.client.plugins.microbot.nirismelter;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.DecimalFormat;

public class NiriSmelterOverlay extends OverlayPanel {
    
    private final NiriSmelterScript script;
    private final NiriSmelterConfig config;
    
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,###");
    
    @Inject
    public NiriSmelterOverlay(NiriSmelterScript script, NiriSmelterConfig config) {
        this.script = script;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();
            
            // Title
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Niri Smelter")
                    .color(Color.CYAN)
                    .build());
            
            // Version
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Version:")
                    .right("1.0.0")
                    .build());
            
            panelComponent.getChildren().add(LineComponent.builder().build());
            
            // Current state
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(script.getState().name())
                    .rightColor(getStateColor(script.getState()))
                    .build());
            
            // Ore type
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Ore:")
                    .right(config.oreType().name())
                    .build());
            
            // Location
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Furnace:")
                    .right(config.furnaceLocation().getName())
                    .build());
            
            panelComponent.getChildren().add(LineComponent.builder().build());
            
            // Statistics
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Ores Smelted:")
                    .right(NUMBER_FORMAT.format(script.getOresSmelted()))
                    .build());
            
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Trips:")
                    .right(NUMBER_FORMAT.format(script.getTripsCompleted()))
                    .build());
            
            // Runtime
            long runtime = System.currentTimeMillis() - script.getStartTime();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(formatRuntime(runtime))
                    .build());
            
            // Smithing level and XP
            if (Microbot.isLoggedIn()) {
                int currentLevel = Rs2Player.getRealSkillLevel(Skill.SMITHING);
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Smithing:")
                        .right(String.valueOf(currentLevel))
                        .build());
                
                // XP per hour
                if (runtime > 0) {
                    int currentXp = Microbot.getClient().getSkillExperience(Skill.SMITHING);
                    long xpGained = currentXp - getStartXp();
                    double xpPerHour = (xpGained / (runtime / 1000.0)) * 3600;
                    
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("XP/hr:")
                            .right(NUMBER_FORMAT.format((int) xpPerHour))
                            .build());
                }
            }
            
            // Stop conditions
            if (config.stopAfterLevel() > 0) {
                panelComponent.getChildren().add(LineComponent.builder().build());
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Stop at level:")
                        .right(String.valueOf(config.stopAfterLevel()))
                        .build());
            }
            
            if (config.stopAfterOres() > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Stop after ores:")
                        .right(NUMBER_FORMAT.format(config.stopAfterOres()))
                        .build());
            }
            
        } catch (Exception ex) {
            // Fail silently to avoid spam
        }
        
        return super.render(graphics);
    }
    
    private Color getStateColor(NiriSmelterScript.ScriptState state) {
        switch (state) {
            case SMELTING:
                return Color.GREEN;
            case BANKING:
                return Color.YELLOW;
            case WALKING_TO_BANK:
            case WALKING_TO_FURNACE:
                return Color.ORANGE;
            case STOPPED:
                return Color.RED;
            default:
                return Color.WHITE;
        }
    }
    
    private String formatRuntime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    private int getStartXp() {
        // This should be tracked in the script, but for now return current
        return Microbot.getClient().getSkillExperience(Skill.SMITHING);
    }
}

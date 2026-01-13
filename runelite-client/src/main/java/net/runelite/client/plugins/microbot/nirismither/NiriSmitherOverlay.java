package net.runelite.client.plugins.microbot.nirismither;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

public class NiriSmitherOverlay extends OverlayPanel {
    private final NiriSmitherScript script;
    
    @Inject
    public NiriSmitherOverlay(NiriSmitherScript script) {
        super();
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.getChildren().clear();
            
            // Title
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Niri Smither")
                    .color(Color.GREEN)
                    .build());
            
            // State
            String state = script.getState() != null ? script.getState().toString() : "UNKNOWN";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(state)
                    .build());
            
            // Items smithed
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Items Smithed:")
                    .right(String.valueOf(script.getItemsSmithed()))
                    .build());
            
            // Trips completed
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Trips:")
                    .right(String.valueOf(script.getTripsCompleted()))
                    .build());
            
            // Current level
            int currentLevel = Rs2Player.getRealSkillLevel(Skill.SMITHING);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Smithing Level:")
                    .right(String.valueOf(currentLevel))
                    .build());
            
            // Runtime
            if (script.getStartTime() > 0) {
                Duration runtime = Duration.between(
                        Instant.ofEpochMilli(script.getStartTime()),
                        Instant.now()
                );
                
                String formattedTime = String.format("%02d:%02d:%02d",
                        runtime.toHours(),
                        runtime.toMinutesPart(),
                        runtime.toSecondsPart());
                
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Runtime:")
                        .right(formattedTime)
                        .build());
            }
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        return super.render(graphics);
    }
}

package net.runelite.client.plugins.microbot.nirisailing;

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

public class NiriSailingOverlay extends OverlayPanel {
	private final NiriSailingScript script;
	
	@Inject
	public NiriSailingOverlay(NiriSailingScript script) {
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
				.text("Niri Sailing")
				.color(Color.CYAN)
				.build());
			
			// State
			String state = script.getState() != null ? script.getState().toString() : "UNKNOWN";
			panelComponent.getChildren().add(LineComponent.builder()
				.left("State:")
				.right(state)
				.build());
			
			// Trips completed
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Trips:")
				.right(String.valueOf(script.getTripsCompleted()))
				.build());
			
			// Current level (using Fishing as placeholder since Sailing isn't in OSRS yet)
			int currentLevel = Rs2Player.getRealSkillLevel(Skill.FISHING);
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Sailing Level:")
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
			
			// Items collected (if applicable)
			if (script.getItemsCollected() > 0) {
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Items:")
					.right(String.valueOf(script.getItemsCollected()))
					.build());
			}
			
		} catch (Exception e) {
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Error:")
				.right(e.getMessage())
				.build());
		}
		
		return super.render(graphics);
	}
}

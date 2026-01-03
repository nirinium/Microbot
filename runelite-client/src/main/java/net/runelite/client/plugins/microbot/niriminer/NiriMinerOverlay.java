package net.runelite.client.plugins.microbot.niriminer;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class NiriMinerOverlay extends OverlayPanel {
	
	@Inject
	private NiriMinerScript script;
	
	@Inject
	public NiriMinerOverlay(NiriMinerPlugin plugin) {
		super(plugin);
		setPosition(OverlayPosition.TOP_LEFT);
	}
	
	@Override
	public Dimension render(Graphics2D graphics) {
		try {
			panelComponent.getChildren().clear();
			
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Niri Motherlode Miner")
				.color(Color.CYAN)
				.build());
			
			panelComponent.setPreferredSize(new Dimension(200, 300));
			panelComponent.getChildren().add(LineComponent.builder()
				.left(Microbot.status)
				.build());
			
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Status:")
				.right(script.getStatus())
				.build());
			
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
		
		return super.render(graphics);
	}
}

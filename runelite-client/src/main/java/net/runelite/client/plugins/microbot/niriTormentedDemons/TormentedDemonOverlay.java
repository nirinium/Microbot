package net.runelite.client.plugins.microbot.niriTormentedDemons;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class TormentedDemonOverlay extends OverlayPanel {

    @Inject
    TormentedDemonOverlay(TormentedDemonPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 0));

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Niri Tormented Demons v" + TormentedDemonPlugin.VERSION)
                    .color(Color.CYAN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(TormentedDemonScript.getBotState().name())
                    .rightColor(stateColor(TormentedDemonScript.getBotState()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(TormentedDemonScript.getStatusText())
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kills:")
                    .right(String.valueOf(TormentedDemonScript.getKillCount()))
                    .rightColor(Color.GREEN)
                    .build());

            TormentedDemonConfig.CombatStyle style = TormentedDemonScript.getActiveCombatStyle();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Style:")
                    .right(style != null ? style.name() : "—")
                    .rightColor(styleColor(style))
                    .build());

        } catch (Exception ex) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Error:")
                    .right(ex.getMessage())
                    .rightColor(Color.RED)
                    .build());
        }

        return super.render(graphics);
    }

    private Color stateColor(TormentedDemonScript.BotState state) {
        switch (state) {
            case FIGHTING:
                return Color.RED;
            case TRAVELLING:
                return Color.YELLOW;
            case BANKING:
                return Color.GREEN;
            default:
                return Color.WHITE;
        }
    }

    private Color styleColor(TormentedDemonConfig.CombatStyle style) {
        if (style == null) return Color.GRAY;
        switch (style) {
            case MELEE:
                return Color.ORANGE;
            case RANGED:
                return Color.GREEN;
            case MAGIC:
                return Color.CYAN;
            default:
                return Color.WHITE;
        }
    }
}

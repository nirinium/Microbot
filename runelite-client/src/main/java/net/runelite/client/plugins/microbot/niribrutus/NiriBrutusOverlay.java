package net.runelite.client.plugins.microbot.niribrutus;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class NiriBrutusOverlay extends OverlayPanel {

    private final NiriBrutusPlugin plugin;
    private final NiriBrutusConfig config;

    @Inject
    NiriBrutusOverlay(NiriBrutusPlugin plugin, NiriBrutusConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 0));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Niri Brutus")
                    .color(Color.ORANGE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(plugin.getScript().status)
                    .build());

            int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("HP:")
                    .right(currentHp + " / " + maxHp)
                    .rightColor(currentHp < maxHp / 2 ? Color.RED : Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kills:")
                    .right(String.valueOf(plugin.getScript().getKillCount()))
                    .build());

            if (config.enableAntiban()) {
                Rs2Antiban.renderAntibanOverlayComponents(panelComponent);
            }

        } catch (Exception ex) {
            Microbot.logStackTrace("NiriBrutusOverlay", ex);
        }
        return super.render(graphics);
    }
}

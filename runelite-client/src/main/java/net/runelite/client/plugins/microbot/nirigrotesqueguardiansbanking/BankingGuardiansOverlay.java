package net.runelite.client.plugins.microbot.nirigrotesqueguardiansbanking;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class BankingGuardiansOverlay extends OverlayPanel {
    private final BankingGuardiansPlugin plugin;

    @Inject
    BankingGuardiansOverlay(BankingGuardiansPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        BankingGuardiansScript script = plugin.getScript();
        panelComponent.setPreferredSize(new Dimension(270, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Niri Grotesque Guardians Banking v" + BankingGuardiansPlugin.VERSION)
                .color(Color.ORANGE)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:").right(script.getDisplayState().name()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:").right(script.getStatus()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("HP / Prayer:").right(script.getHitpoints() + " / " + script.getPrayer()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dawn weapon:").right(script.getDawnWeapon()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dusk weapon:").right(script.getDuskWeapon()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dusk offhand:").right(script.getDuskOffhand()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Finisher:").right(script.getFinisher()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Danger tiles:").right(String.valueOf(script.getHazardCount())).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Kills:").right(String.valueOf(script.getKillCount())).build());
        return super.render(graphics);
    }
}


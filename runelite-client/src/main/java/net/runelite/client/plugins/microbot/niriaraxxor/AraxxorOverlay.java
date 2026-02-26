package net.runelite.client.plugins.microbot.niriaraxxor;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AraxxorOverlay extends OverlayPanel {

    private final AraxxorPlugin plugin;

    @Inject
    AraxxorOverlay(AraxxorPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            AraxxorScript script = plugin.getScript();

            panelComponent.setPreferredSize(new Dimension(220, 0));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Niri Araxxor v" + AraxxorPlugin.VERSION)
                    .color(Color.RED)
                    .build());

            // Status
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(script.getStatus())
                    .build());

            // State
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(script.getState().name())
                    .rightColor(getStateColor(script.getState()))
                    .build());

            // HP
            int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("HP:")
                    .right(currentHp + " / " + maxHp)
                    .rightColor(currentHp < maxHp / 2 ? Color.RED : Color.GREEN)
                    .build());

            // Prayer
            int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
            int maxPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Prayer:")
                    .right(currentPrayer + " / " + maxPrayer)
                    .rightColor(currentPrayer < maxPrayer / 3 ? Color.ORANGE : Color.CYAN)
                    .build());

            // Kills
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kills:")
                    .right(String.valueOf(script.getKillCount()))
                    .build());

            // Enrage indicator
            if (script.isEnraged()) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("ENRAGED")
                        .leftColor(Color.RED)
                        .right("Dodge cleaves!")
                        .rightColor(Color.YELLOW)
                        .build());
            }

            // Attack counter (for egg hatch tracking)
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Attacks:")
                    .right(String.valueOf(script.getAraxxorAttackCount()))
                    .build());

            // Acid pools count
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Acid pools:")
                    .right(String.valueOf(script.getAcidPools().size()))
                    .rightColor(script.getAcidPools().isEmpty() ? Color.GREEN : Color.ORANGE)
                    .build());

        } catch (Exception ex) {
            Microbot.logStackTrace("AraxxorOverlay", ex);
        }
        return super.render(graphics);
    }

    private Color getStateColor(AraxxorState state) {
        switch (state) {
            case FIGHTING:
                return Color.GREEN;
            case ENRAGED_DODGE_CLEAVE:
            case ENRAGED_STEP_UNDER:
                return Color.RED;
            case DODGING_ACID_BALL:
            case DODGING_ACID_SPLATTER:
            case DODGING_ACID_DRIP:
            case AVOIDING_ACID_POOLS:
                return Color.YELLOW;
            case KILLING_RUPTURA:
                return Color.ORANGE;
            case KILLING_MIRRORBACK:
                return Color.WHITE;
            case KILLING_ACIDIC:
                return new Color(0, 200, 0);
            case LOOTING:
                return Color.CYAN;
            case EATING:
            case DRINKING_POTIONS:
                return Color.PINK;
            default:
                return Color.LIGHT_GRAY;
        }
    }
}

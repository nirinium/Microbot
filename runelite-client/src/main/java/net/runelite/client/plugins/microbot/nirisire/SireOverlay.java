package net.runelite.client.plugins.microbot.nirisire;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class SireOverlay extends OverlayPanel {

    private final SirePlugin plugin;
    private final SireConfig config;

    @Inject
    SireOverlay(SirePlugin plugin, SireConfig config) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            SireScript script = plugin.getScript();

            panelComponent.setPreferredSize(new Dimension(220, 0));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Niri Sire v" + SirePlugin.VERSION)
                    .color(new Color(128, 0, 128)) // purple for abyssal theme
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

            // Phase
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Phase:")
                    .right(script.getCurrentPhase() == 0 ? "---" : String.valueOf(script.getCurrentPhase()))
                    .rightColor(getPhaseColor(script.getCurrentPhase()))
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

            // Phase 1 info: vents destroyed
            if (script.getCurrentPhase() == 1) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Vents:")
                        .right(script.getVentsDestroyed() + " / 4")
                        .rightColor(script.getVentsDestroyed() >= 4 ? Color.GREEN : Color.YELLOW)
                        .build());

                // Stun timer
                long stunRemaining = script.getStunTicksRemaining();
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Stun:")
                        .right(stunRemaining > 0 ? stunRemaining + " ticks" : "NOT STUNNED")
                        .rightColor(stunRemaining > 8 ? Color.GREEN : stunRemaining > 0 ? Color.ORANGE : Color.RED)
                        .build());
            }

            // Phase 3 info
            if (script.getCurrentPhase() == 3) {
                if (script.isExplosionHappened()) {
                    // Post-explosion Stage II
                    int miasmaCount = script.getPostExplosionMiasmaCount();
                    boolean miasmasDone = miasmaCount >= 3;
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Stage:")
                            .right("II (post-explosion)")
                            .rightColor(Color.RED)
                            .build());
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Miasma:")
                            .right(miasmaCount + " / 3" + (miasmasDone ? " ✔" : ""))
                            .rightColor(miasmasDone ? Color.GREEN : Color.YELLOW)
                            .build());
                } else {
                    // Pre-explosion Stage I
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Stage:")
                            .right("I (pre-explosion)")
                            .rightColor(Color.ORANGE)
                            .build());
                    // Show P3 attack counter for explosion prediction
                    int attacks = script.getP3AttackCount();
                    boolean imminent = script.isExplosionImminent();
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Attacks:")
                            .right(attacks + (imminent ? " ⚠ IMMINENT" : ""))
                            .rightColor(imminent ? Color.RED : Color.WHITE)
                            .build());
                }
            }

            // ── Dodge Debug Overlay ──
            if (config.dodgeDebugOverlay() && script.getDodgeStartTimeMs() > 0) {
                long elapsed = script.getDodgeEndTimeMs() > 0
                        ? (script.getDodgeEndTimeMs() - script.getDodgeStartTimeMs())
                        : (System.currentTimeMillis() - script.getDodgeStartTimeMs());
                // Only show for 10 seconds after dodge completes (or always while dodging)
                boolean dodgeActive = script.getState() == SireState.DODGING_EXPLOSION;
                boolean recentDodge = script.getDodgeEndTimeMs() > 0
                        && (System.currentTimeMillis() - script.getDodgeEndTimeMs()) < 10_000;

                if (dodgeActive || recentDodge) {
                    panelComponent.getChildren().add(TitleComponent.builder()
                            .text("─ Dodge Debug ─")
                            .color(dodgeActive ? Color.RED : Color.GRAY)
                            .build());

                    // Result
                    if (script.getDodgeEndTimeMs() > 0) {
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("Result:")
                                .right(script.isLastDodgeSucceeded() ? "SUCCESS" : "FAILED")
                                .rightColor(script.isLastDodgeSucceeded() ? Color.GREEN : Color.RED)
                                .build());
                    } else {
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("Result:")
                                .right("DODGING...")
                                .rightColor(Color.YELLOW)
                                .build());
                    }

                    // Time
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Time:")
                            .right(elapsed + "ms")
                            .rightColor(elapsed > 1200 ? Color.RED : elapsed > 600 ? Color.ORANGE : Color.GREEN)
                            .build());

                    // Clicks breakdown
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Clicks:")
                            .right(script.getDodgeClicksFired() + " total")
                            .build());
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("  Canvas:")
                            .right(String.valueOf(script.getDodgeCanvasClicks()))
                            .rightColor(Color.CYAN)
                            .build());
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("  Minimap:")
                            .right(String.valueOf(script.getDodgeMinimapClicks()))
                            .rightColor(Color.YELLOW)
                            .build());
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("  Fallback:")
                            .right(String.valueOf(script.getDodgeFallbackClicks()))
                            .rightColor(script.getDodgeFallbackClicks() > 0 ? Color.ORANGE : Color.WHITE)
                            .build());

                    // Tiles
                    if (script.getDodgeLandingTile() != null) {
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("Landing:")
                                .right("(" + script.getDodgeLandingTile().getX() + "," + script.getDodgeLandingTile().getY() + ")")
                                .build());
                    }
                    if (script.getExplosionDodgeTarget() != null) {
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("Target:")
                                .right("(" + script.getExplosionDodgeTarget().getX() + "," + script.getExplosionDodgeTarget().getY() + ")")
                                .rightColor(Color.CYAN)
                                .build());
                    }
                    if (script.getDodgePlayerFinalTile() != null) {
                        panelComponent.getChildren().add(LineComponent.builder()
                                .left("Final:")
                                .right("(" + script.getDodgePlayerFinalTile().getX() + "," + script.getDodgePlayerFinalTile().getY() + ")")
                                .rightColor(script.isLastDodgeSucceeded() ? Color.GREEN : Color.RED)
                                .build());
                    }
                }
            }

        } catch (Exception ex) {
            Microbot.logStackTrace("SireOverlay", ex);
        }
        return super.render(graphics);
    }

    private Color getStateColor(SireState state) {
        switch (state) {
            case STUNNING_SIRE:
                return Color.CYAN;
            case DESTROYING_VENTS:
            case TRANSITION_ATTACK:
                return Color.YELLOW;
            case SPEC_ATTACK:
                return Color.ORANGE;
            case FIGHTING_MELEE:
            case FIGHTING_PHASE3:
                return Color.GREEN;
            case FIGHTING_PHASE3_FINAL:
                return new Color(255, 100, 100); // light red
            case DODGING_EXPLOSION:
                return Color.RED;
            case DODGING_MIASMA:
                return new Color(138, 43, 226); // violet
            case RETREATING:
                return new Color(255, 165, 0); // orange
            case LOOTING:
                return Color.CYAN;
            case EATING:
                return Color.PINK;
            default:
                return Color.LIGHT_GRAY;
        }
    }

    private Color getPhaseColor(int phase) {
        switch (phase) {
            case 1: return Color.YELLOW;
            case 2: return Color.ORANGE;
            case 3: return Color.RED;
            default: return Color.LIGHT_GRAY;
        }
    }
}

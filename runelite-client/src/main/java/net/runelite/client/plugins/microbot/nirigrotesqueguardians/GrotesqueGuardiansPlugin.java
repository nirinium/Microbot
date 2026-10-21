package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.GraphicsEnvironment;

@PluginDescriptor(
        name = PluginDescriptor.NIRI + "Niri Grotesque Guardians",
        authors = {"Niri"},
        version = GrotesqueGuardiansPlugin.VERSION,
        description = "Fight-focused Grotesque Guardians automation",
        tags = {"grotesque", "guardians", "dawn", "dusk", "boss", "slayer", "niri"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class GrotesqueGuardiansPlugin extends Plugin {
    public static final String VERSION = "0.7.12";

    @Inject
    private Client client;
    @Inject
    private GrotesqueGuardiansConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GrotesqueGuardiansOverlay overlay;

    @Getter
    private final GrotesqueGuardiansScript script = new GrotesqueGuardiansScript();
    private GrotesqueGuardiansDiagnosticsWindow diagnosticsWindow;

    @Provides
    GrotesqueGuardiansConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GrotesqueGuardiansConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config);
        if (config.autoOpenDiagnostics()) openDiagnosticsWindow();
    }

    @Override
    protected void shutDown() {
        closeDiagnosticsWindow();
        script.shutdown();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (GrotesqueGuardiansConfig.GROUP.equals(event.getGroup())
                && "openDiagnostics".equals(event.getKey())) {
            openDiagnosticsWindow();
        }
    }

    private void openDiagnosticsWindow() {
        if (GraphicsEnvironment.isHeadless()) return;
        if (diagnosticsWindow == null) {
            diagnosticsWindow = new GrotesqueGuardiansDiagnosticsWindow(script, config,
                    () -> diagnosticsWindow = null);
        }
        diagnosticsWindow.showWindow();
    }

    private void closeDiagnosticsWindow() {
        if (diagnosticsWindow != null) {
            diagnosticsWindow.close();
            diagnosticsWindow = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getLocalPlayer() == null) return;
        script.onGameTick(client.getTickCount(), client.getLocalPlayer().getWorldLocation(),
                client.getBoostedSkillLevel(Skill.HITPOINTS), client.getRealSkillLevel(Skill.HITPOINTS),
                client.getBoostedSkillLevel(Skill.PRAYER), client.getRealSkillLevel(Skill.PRAYER),
                client.getBoostedSkillLevel(Skill.ATTACK), client.getRealSkillLevel(Skill.ATTACK),
                client.getBoostedSkillLevel(Skill.STRENGTH), client.getRealSkillLevel(Skill.STRENGTH));
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (client.getLocalPlayer() != null) {
            script.onClientTick(client.getLocalPlayer().getWorldLocation());
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (event.getActor() instanceof NPC) {
            NPC npc = (NPC) event.getActor();
            script.onBossAnimation(npc.getId(), npc.getAnimation(), npc.getWorldLocation());
        }
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        GraphicsObject graphics = event.getGraphicsObject();
        WorldPoint location = WorldPoint.fromLocal(client, graphics.getLocation());
        if (location != null) {
            script.onGraphicsObject(graphics.getId(), location, graphics.getStartCycle());
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        Projectile projectile = event.getProjectile();
        WorldPoint target = projectile.getTargetPoint();
        if (target == null) target = WorldPoint.fromLocal(client, event.getPosition());
        script.onProjectile(projectile.getId(), target, projectile.getStartCycle());
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (event.getNpc().getId() == NpcID.GARGBOSS_DUSK_DEATH) script.onKillCompleted();
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (event.getActor() == client.getLocalPlayer()) script.onPlayerDeath();
    }
}

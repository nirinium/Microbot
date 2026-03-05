package net.runelite.client.plugins.microbot.niriaraxxor;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Set;

/**
 * Plugin that wires RuneLite events into the {@link AraxxorScript}.
 * <p>
 * Listens for projectile, animation, NPC spawn/despawn, and game object events
 * to feed real-time boss mechanics data into the script's state machine.
 */
@PluginDescriptor(
        name = PluginDescriptor.NIRI + "Niri Araxxor",
        authors = {"Niri"},
        version = AraxxorPlugin.VERSION,
        description = "Advanced Araxxor boss fight automation — handles acid, minions, enrage, and dodging",
        tags = {"araxxor", "araxyte", "boss", "spider", "combat", "microbot", "niri", "slayer"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AraxxorPlugin extends Plugin {

    public static final String VERSION = "1.0.0";

    // Araxxor NPC IDs
    private static final int ARAXXOR_ID = NpcID.ARAXXOR;
    private static final int ARAXXOR_DEAD_ID = NpcID.ARAXXOR_DEAD;
    private static final int RUPTURA_ID = NpcID.ARAXXOR_MINION_EXPLODE;
    private static final int MIRRORBACK_ID = NpcID.ARAXXOR_MINION_MIRRORBACK;
    private static final int ACIDIC_ID = NpcID.ARAXXOR_MINION_VENOM;
    private static final int ACID_CANNON_PROJ_NPC = NpcID.ARAXXOR_ACID_CANNON_PROJECTILE; // 13676

    // Acid-related object IDs
    private static final Set<Integer> ACID_OBJECT_IDS = Set.of(
            ObjectID1.ARAXXOR_ACIDPOOL,          // 54148
            ObjectID1.ARAXXOR_VENOM_PUDDLE01,    // 54255
            ObjectID1.ARAXXOR_VENOM_PUDDLE02,    // 54256
            ObjectID1.ARAXXOR_VENOM_PUDDLE03     // 54257
    );

    // Acid projectile IDs
    private static final Set<Integer> ACID_PROJECTILE_IDS = Set.of(
            SpotanimID.ARAXXOR_POOLS_PROJ,       // 2924
            SpotanimID.ARAXXOR_POOLS_SPLASH       // 2923
    );

    @Inject
    private Client client;
    @Inject
    private AraxxorConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AraxxorOverlay overlay;

    @Getter
    private final AraxxorScript script = new AraxxorScript();

    @Provides
    AraxxorConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AraxxorConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    // ── Projectile Events ───────────────────────────────

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        int id = event.getProjectile().getId();

        // Track acid pool projectiles landing locations
        if (ACID_PROJECTILE_IDS.contains(id)) {
            WorldPoint landing = WorldPoint.fromLocal(client, event.getPosition());
            script.getAcidPools().add(landing);
        }
    }

    // ── NPC Animation Events ────────────────────────────

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC)) return;
        NPC npc = (NPC) event.getActor();

        if (npc.getId() == ARAXXOR_ID) {
            script.onAraxxorAnimation(npc.getAnimation());
        }

        // Also detect the acid cannon projectile NPC's own animation as a backup trigger
        if (npc.getId() == ACID_CANNON_PROJ_NPC) {
            log.debug("Acid cannon projectile NPC animation {} detected", npc.getAnimation());
            script.onAcidCannonDetected();
        }
    }

    // ── NPC Spawn/Despawn Events ────────────────────────

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        if (npc == null) return;

        // Acid cannon projectile NPC spawned — backup detection
        if (npc.getId() == ACID_CANNON_PROJ_NPC) {
            log.debug("Acid cannon projectile NPC spawned — flagging dodge!");
            script.onAcidCannonDetected();
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (npc == null) return;

        // Track Araxxor death — use isDead() OR check if the NPC is the dead variant
        if (npc.getId() == ARAXXOR_ID && npc.isDead()) {
            script.onAraxxorDeath();
        }
        // Fallback: also trigger on the dead NPC variant despawning (after corpse disappears)
        if (npc.getId() == ARAXXOR_DEAD_ID) {
            script.onAraxxorDeath();
        }
    }

    // ── Game Object Events (acid pool tracking) ─────────

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        int id = event.getGameObject().getId();
        if (ACID_OBJECT_IDS.contains(id)) {
            WorldPoint loc = event.getGameObject().getWorldLocation();
            script.getAcidPools().add(loc);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        int id = event.getGameObject().getId();
        if (ACID_OBJECT_IDS.contains(id)) {
            WorldPoint loc = event.getGameObject().getWorldLocation();
            script.getAcidPools().remove(loc);
        }
    }

    // ── Chat message for "Skree!" cleave detection ──────

    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged event) {
        if (!(event.getActor() instanceof NPC)) return;
        NPC npc = (NPC) event.getActor();

        if (npc.getId() == ARAXXOR_ID) {
            String text = event.getOverheadText();
            if (text != null && text.toLowerCase().contains("skree")) {
                log.debug("Araxxor shouted Skree! — cleave incoming");
                script.setCleaveIncoming(true);
                script.computeCleaveTiles();
                // Issue dodge walk on the SAME game tick for fastest possible reaction
                script.precomputeAndDodgeCleave();
            }
        }
    }
}

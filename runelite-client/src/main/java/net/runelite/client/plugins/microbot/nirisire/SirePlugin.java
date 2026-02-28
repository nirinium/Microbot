package net.runelite.client.plugins.microbot.nirisire;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
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
 * Plugin that wires RuneLite events into the {@link SireScript}.
 * <p>
 * Listens for animation, NPC spawn/despawn events to feed real-time
 * Abyssal Sire mechanics data into the script's state machine.
 */
@PluginDescriptor(
        name = PluginDescriptor.NIRI + "Niri Sire",
        authors = {"Niri"},
        version = SirePlugin.VERSION,
        description = "Abyssal Sire boss fight automation — handles stun, vents, melee phases, miasma, and explosion dodge",
        tags = {"sire", "abyssal", "boss", "combat", "microbot", "niri", "slayer"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class SirePlugin extends Plugin {

    public static final String VERSION = "1.1.0";

    // ── Sire NPC IDs ─────────────────────────────────────
    private static final int SIRE_SLEEPING = NpcID.ABYSSALSIRE_SIRE_STASIS_SLEEPING;
    private static final int SIRE_AWAKE = NpcID.ABYSSALSIRE_SIRE_STASIS_AWAKE;
    private static final int SIRE_STUNNED = NpcID.ABYSSALSIRE_SIRE_STASIS_STUNNED;
    private static final int SIRE_PUPPET = NpcID.ABYSSALSIRE_SIRE_PUPPET;
    private static final int SIRE_WANDERING = NpcID.ABYSSALSIRE_SIRE_WANDERING;
    private static final int SIRE_PANICKING = NpcID.ABYSSALSIRE_SIRE_PANICKING;
    private static final int SIRE_APOCALYPSE = NpcID.ABYSSALSIRE_SIRE_APOCALYPSE;

    // Respiratory systems (lungs/vents)
    private static final int LUNG = NpcID.ABYSSALSIRE_LUNG;
    private static final int LUNG_DYING = NpcID.ABYSSALSIRE_LUNG_DYING;

    // All Sire NPC IDs for animation detection
    private static final Set<Integer> SIRE_IDS = Set.of(
            SIRE_SLEEPING, SIRE_AWAKE, SIRE_STUNNED,
            SIRE_PUPPET, SIRE_WANDERING, SIRE_PANICKING, SIRE_APOCALYPSE
    );

    // ── Sire Animation IDs ───────────────────────────────
    private static final int ANIM_MIASMA = AnimationID.SIRE_ATTACK_MIASMA;
    private static final int ANIM_MIASMA_TWO = AnimationID.SIRE_ATTACK_MIASMA_TWO;
    private static final int ANIM_TELEPORT_PLAYER = AnimationID.SIRE_ATTACK_TELEPORT_PLAYER;
    private static final int ANIM_MOBILISING = AnimationID.SIRE_MOBILISING;
    private static final int ANIM_PANIC_MODE = AnimationID.SIRE_PANIC_MODE;
    private static final int ANIM_APOCALYPSE = AnimationID.SIRE_APOCALYPSE;
    private static final int ANIM_DEATH = AnimationID.SIRE_DEATH;

    // ── Miasma Spot Animation ID ─────────────────────────
    private static final int MIASMA_SPOTANIM = SpotanimID.ABYSSAL_MIASMA_SPOTANIM; // 1275

    @Inject
    private Client client;
    @Inject
    private SireConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private SireOverlay overlay;

    @Getter
    private final SireScript script = new SireScript();

    @Provides
    SireConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SireConfig.class);
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

    // ── Animation Events ─────────────────────────────────

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC)) return;
        NPC npc = (NPC) event.getActor();

        if (!SIRE_IDS.contains(npc.getId())) return;

        int anim = npc.getAnimation();

        // Miasma pool attacks (all phases)
        if (anim == ANIM_MIASMA || anim == ANIM_MIASMA_TWO) {
            log.debug("Sire miasma attack detected (anim {})", anim);
            script.onMiasmaDetected();
        }

        // Phase 3 explosion teleport — CRITICAL: 2-tick dodge window
        if (anim == ANIM_TELEPORT_PLAYER) {
            log.debug("Sire explosion teleport detected — DODGE NOW!");
            script.onExplosionTeleport();
        }

        // Phase 1→2 transition
        if (anim == ANIM_MOBILISING) {
            log.debug("Sire mobilising — transitioning to Phase 2");
            script.onPhaseTransition(2);
        }

        // Phase 2 panic mode (≤50% HP)
        if (anim == ANIM_PANIC_MODE) {
            log.debug("Sire panicking — below 50% HP");
            script.onSirePanicking();
        }

        // Phase 2→3 transition (apocalypse)
        if (anim == ANIM_APOCALYPSE) {
            log.debug("Sire apocalypse — transitioning to Phase 3");
            script.onPhaseTransition(3);
        }

        // Death animation
        if (anim == ANIM_DEATH) {
            log.debug("Sire death animation detected");
            script.onSireDeath();
        }
    }

    // ── NPC Spawn/Despawn Events ─────────────────────────

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        if (npc == null) return;

        // Track Sire phase by NPC ID on spawn
        if (npc.getId() == SIRE_SLEEPING || npc.getId() == SIRE_AWAKE) {
            script.onSireDetected(1);
        } else if (npc.getId() == SIRE_STUNNED) {
            script.onSireStunned();
        } else if (npc.getId() == SIRE_PUPPET || npc.getId() == SIRE_WANDERING) {
            script.onPhaseTransition(2);
        } else if (npc.getId() == SIRE_PANICKING) {
            script.onSirePanicking();
        } else if (npc.getId() == SIRE_APOCALYPSE) {
            script.onPhaseTransition(3);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (npc == null) return;

        // Backup: catch LUNG despawn if it dies without transforming to LUNG_DYING
        if (npc.getId() == LUNG && npc.isDead()) {
            log.debug("Respiratory system destroyed (despawn)!");
            script.onVentDestroyed();
        }

        // Sire death via despawn
        if (SIRE_IDS.contains(npc.getId()) && npc.isDead()) {
            script.onSireDeath();
        }
    }

    // ── NPC Composition Change Events ─────────────────────

    @Subscribe
    public void onNpcChanged(NpcChanged event) {
        NPC npc = event.getNpc();
        if (npc == null) return;

        // LUNG → LUNG_DYING transformation = vent destroyed immediately
        // (much faster than waiting for LUNG_DYING to fully despawn after death animation)
        if (npc.getId() == LUNG_DYING) {
            log.debug("Respiratory system transforming to dying state — vent destroyed!");
            script.onVentDestroyed();
        }
    }

    // ── Graphics Object Events (miasma pool tracking) ────

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        GraphicsObject go = event.getGraphicsObject();
        if (go.getId() == MIASMA_SPOTANIM) {
            WorldPoint loc = WorldPoint.fromLocal(client, go.getLocation());
            log.debug("Miasma spot anim spawned at {}", loc);
            script.onMiasmaPoolSpawned(loc);
        }
    }
}

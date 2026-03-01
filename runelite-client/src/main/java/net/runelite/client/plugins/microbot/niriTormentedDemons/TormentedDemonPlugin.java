package net.runelite.client.plugins.microbot.niriTormentedDemons;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Plugin for Tormented Demon automation.
 * <p>
 * Handles real-time events that need immediate response:
 * <ul>
 *   <li>Animation changes → protection prayer switching</li>
 *   <li>Graphics objects → fire bomb dodging</li>
 * </ul>
 */
@PluginDescriptor(
        name = PluginDescriptor.zerozero + "Tormented Demons",
        description = "Automates Tormented Demon kills with gear switching, prayer, dodging, and banking",
        tags = {"tormented", "demon", "prayer", "switch", "microbot"},
        version = TormentedDemonPlugin.VERSION,
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class TormentedDemonPlugin extends Plugin {

    public static final String VERSION = "2.0.0";

    // ─── Animation IDs ──────────────────────────────────────────────────────────

    /**
     * Played when the demon switches its attack style.
     */
    private static final int STYLE_SWITCH_ANIM = 11387;

    /**
     * Melee attack animation.
     */
    private static final int MELEE_ATTACK_ANIM = 11392;

    /**
     * Magic attack animation.
     */
    private static final int MAGIC_ATTACK_ANIM = 11388;

    /**
     * Ranged attack animation.
     */
    private static final int RANGE_ATTACK_ANIM = 11389;

    /**
     * Fire bomb (vengeance special) graphics ID.
     */
    private static final int FIRE_BOMB_GFX = 2856;

    /**
     * Shield break animation — played when the demon's fire shield is hit and goes down.
     */
    private static final int SHIELD_BREAK_ANIM = 11399;

    /**
     * Fire bomb AoE radius (Chebyshev). The bomb hits a 3×3 area (center ± 1).
     */
    private static final int FIRE_BOMB_AOE_RADIUS = 1;

    /**
     * Minimum Chebyshev distance from ALL fire bomb centers for a tile to be considered safe.
     * Must be > AoE radius to guarantee safety.
     */
    private static final int SAFE_DISTANCE = 2;

    /**
     * Search radius around the player when looking for safe tiles.
     */
    private static final int DODGE_SEARCH_RADIUS = 5;

    // ─── Injected dependencies ──────────────────────────────────────────────────

    @Inject
    private ConfigManager configManager;

    @Inject
    private TormentedDemonConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private TormentedDemonOverlay overlay;

    @Inject
    private TormentedDemonScript script;

    private ScheduledExecutorService dodgeExecutor;

    /**
     * Active fire bomb centers mapped to their remaining duration (ms).
     * Updated every 600ms by the cleanup task.
     */
    private final Map<WorldPoint, Integer> activeFireBombs = new ConcurrentHashMap<>();

    @Provides
    TormentedDemonConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TormentedDemonConfig.class);
    }

    // ─── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void startUp() throws AWTException {
        dodgeExecutor = Executors.newSingleThreadScheduledExecutor();
        activeFireBombs.clear();
        // Cleanup task: decrement fire bomb timers and remove expired entries every tick
        dodgeExecutor.scheduleWithFixedDelay(() -> {
            if (!activeFireBombs.isEmpty()) {
                Iterator<Map.Entry<WorldPoint, Integer>> it = activeFireBombs.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<WorldPoint, Integer> entry = it.next();
                    int remaining = entry.getValue() - 600;
                    if (remaining <= 0) {
                        it.remove();
                    } else {
                        entry.setValue(remaining);
                    }
                }
            }
        }, 600, 600, TimeUnit.MILLISECONDS);
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
        activeFireBombs.clear();
        if (dodgeExecutor != null && !dodgeExecutor.isShutdown()) {
            dodgeExecutor.shutdown();
        }
    }

    // ─── Fire Bomb Dodging ──────────────────────────────────────────────────────

    /**
     * Detects fire bomb graphics, tracks them, and walks the player to a truly safe tile.
     * <p>
     * Fire bombs deal damage in a 3×3 area (center ± 1 tile). The old approach only marked
     * the center tile as dangerous, so the player could dodge INTO the blast radius.
     * Now we:
     * <ul>
     *   <li>Track all active fire bomb centers with expiry timers</li>
     *   <li>Check if the player is within the 3×3 AoE of ANY fire bomb</li>
     *   <li>Calculate a safe tile that is ≥ 2 Chebyshev distance from ALL bomb centers</li>
     *   <li>Prefer tiles that are walkable, reachable, and close to our combat target</li>
     * </ul>
     */
    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        GraphicsObject gfx = event.getGraphicsObject();
        if (gfx.getId() != FIRE_BOMB_GFX) return;

        // Resolve the fire bomb center world point
        final WorldPoint bombCenter = Microbot.getClient().getTopLevelWorldView().getScene().isInstance()
                ? WorldPoint.fromLocalInstance(Microbot.getClient(), gfx.getLocation())
                : WorldPoint.fromLocal(Microbot.getClient(), gfx.getLocation());
        if (bombCenter == null) return;

        // Track this fire bomb (4 ticks = 2400ms duration)
        int dangerDurationMs = 600 * 4;
        activeFireBombs.merge(bombCenter, dangerDurationMs, Math::max);

        int clickCount = config.dodgeClickCount();
        int clickInterval = config.dodgeClickInterval();

        try {
            // Schedule dodge after the configured delay
            dodgeExecutor.schedule(() -> {
                try {
                    dodgeFireBombs(clickCount, clickInterval);
                } catch (Exception e) {
                    log.warn("Error dodging fire bomb", e);
                }
            }, config.dodgeDelay(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Failed to schedule fire bomb dodge", e);
        }
    }

    /**
     * Check if the player is in danger from any active fire bomb and walk to safety.
     * Supports configurable spam clicking for walk reliability.
     */
    private void dodgeFireBombs(int clickCount, int clickInterval) {
        if (activeFireBombs.isEmpty()) return;

        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null) return;

        // Check if the player is within the 3×3 AoE of any fire bomb
        boolean inDanger = activeFireBombs.keySet().stream()
                .anyMatch(bomb -> chebyshevDistance(playerLoc, bomb) <= FIRE_BOMB_AOE_RADIUS);

        if (!inDanger) return;

        // Get the target NPC's location for preferring tiles near our fight
        WorldPoint targetLoc = null;
        Rs2NpcModel target = script.getCurrentTarget();
        if (target != null && target.getRuneliteNpc() != null) {
            targetLoc = target.getRuneliteNpc().getWorldLocation();
        }

        WorldPoint safeTile = calculateSafeTile(playerLoc, targetLoc);
        if (safeTile == null || safeTile.equals(playerLoc)) {
            log.warn("No safe tile found for fire bomb dodge!");
            return;
        }

        log.info("Dodging fire bomb: {} → {}", playerLoc, safeTile);

        // First walk click
        Microbot.getClientThread().runOnSeperateThread(() -> {
            Rs2Walker.walkFastCanvas(safeTile);
            return true;
        });

        // Additional spam clicks for reliability
        for (int i = 1; i < clickCount; i++) {
            long delay = (long) i * clickInterval;
            dodgeExecutor.schedule(() -> {
                try {
                    Microbot.getClientThread().runOnSeperateThread(() -> {
                        Rs2Walker.walkFastCanvas(safeTile);
                        return true;
                    });
                } catch (Exception e) {
                    log.warn("Error during fire bomb dodge spam click", e);
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Find the best safe tile to dodge to.
     * <p>
     * A tile is "safe" if its Chebyshev distance to ALL active fire bomb centers is ≥ {@link #SAFE_DISTANCE}.
     * Among safe tiles, we prefer:
     * <ol>
     *   <li>Tiles that are walkable (no collision)</li>
     *   <li>Tiles closest to the player (minimize travel time)</li>
     *   <li>Tiles closest to the combat target (stay in fight range)</li>
     * </ol>
     *
     * @param playerLoc current player world point
     * @param targetLoc current target NPC world point (nullable)
     * @return the best safe tile, or null if none found
     */
    private WorldPoint calculateSafeTile(WorldPoint playerLoc, WorldPoint targetLoc) {
        WorldPoint bestTile = null;
        int bestScore = Integer.MAX_VALUE;

        for (int dx = -DODGE_SEARCH_RADIUS; dx <= DODGE_SEARCH_RADIUS; dx++) {
            for (int dy = -DODGE_SEARCH_RADIUS; dy <= DODGE_SEARCH_RADIUS; dy++) {
                WorldPoint candidate = playerLoc.dx(dx).dy(dy);

                // Must be far enough from ALL fire bomb centers
                boolean safe = activeFireBombs.keySet().stream()
                        .allMatch(bomb -> chebyshevDistance(candidate, bomb) >= SAFE_DISTANCE);
                if (!safe) continue;

                // Must be walkable
                if (!Rs2Tile.isWalkable(candidate)) continue;

                // Score: primarily by distance to player, secondarily by distance to target
                int distToPlayer = chebyshevDistance(candidate, playerLoc);
                int distToTarget = targetLoc != null ? chebyshevDistance(candidate, targetLoc) : 0;
                // Weight player distance heavily so we pick the closest safe tile,
                // but use target distance as a tiebreaker
                int score = distToPlayer * 10 + distToTarget;

                if (score < bestScore) {
                    bestScore = score;
                    bestTile = candidate;
                }
            }
        }

        return bestTile;
    }

    /**
     * Chebyshev ("king move") distance between two world points.
     */
    private static int chebyshevDistance(WorldPoint a, WorldPoint b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    // ─── Prayer Switching ───────────────────────────────────────────────────────

    /**
     * Reacts to tormented demon animations for:
     * <ul>
     *   <li>Protection prayer switching (attack animations)</li>
     *   <li>Shield break detection for Dharok punish mechanic (animation 11399)</li>
     * </ul>
     */
    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC)) return;

        NPC npc = (NPC) event.getActor();
        Player local = Microbot.getClient().getLocalPlayer();
        if (local == null) return;

        // Only react to NPCs we're currently fighting
        Rs2NpcModel target = script.getCurrentTarget();
        if (target == null) return;

        // Check this is the NPC we're targeting, or it's targeting us
        boolean isOurTarget = npc.getIndex() == target.getIndex();
        boolean isTargetingUs = npc.getInteracting() == local;
        if (!isOurTarget && !isTargetingUs) return;

        int anim = npc.getAnimation();

        // Shield break detection (for Dharok punish) — always track regardless of prayer settings
        if (anim == SHIELD_BREAK_ANIM) {
            script.onShieldBroken();
        }

        // Prayer switching — only if defensive prayer is enabled
        if (!config.enableDefensivePrayer()) return;

        switch (anim) {
            case MELEE_ATTACK_ANIM:
                ensurePrayer(Rs2PrayerEnum.PROTECT_MELEE);
                break;
            case RANGE_ATTACK_ANIM:
                ensurePrayer(Rs2PrayerEnum.PROTECT_RANGE);
                break;
            case MAGIC_ATTACK_ANIM:
                ensurePrayer(Rs2PrayerEnum.PROTECT_MAGIC);
                break;
            case STYLE_SWITCH_ANIM:
                // The demon is switching its attack style.
                // Per Wiki: if it approaches → melee; if not → magic/ranged.
                // We can't predict perfectly so we wait for the next actual attack anim.
                // However, if melee was active, default to protect range as a safe bet.
                if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) {
                    ensurePrayer(Rs2PrayerEnum.PROTECT_RANGE);
                } else if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) {
                    ensurePrayer(Rs2PrayerEnum.PROTECT_MAGIC);
                } else if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
                    ensurePrayer(Rs2PrayerEnum.PROTECT_MELEE);
                } else {
                    // Nothing active, default to melee
                    ensurePrayer(Rs2PrayerEnum.PROTECT_MELEE);
                }
                break;
        }
    }

    /**
     * Activate a protection prayer, deactivating conflicting ones.
     */
    private void ensurePrayer(Rs2PrayerEnum prayer) {
        if (Rs2Prayer.isPrayerActive(prayer)) return;

        // Turn off other protection prayers
        if (prayer != Rs2PrayerEnum.PROTECT_MELEE) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
        if (prayer != Rs2PrayerEnum.PROTECT_RANGE) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
        if (prayer != Rs2PrayerEnum.PROTECT_MAGIC) Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);

        Rs2Prayer.toggle(prayer, true);
    }
}

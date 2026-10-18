package net.runelite.client.plugins.microbot.niriaraxxor;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

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

    public static final String VERSION = "1.0.47";

    private int lastRangedExperience;
    private int lastHitpointsExperience;

    // Araxxor NPC IDs
    private static final int ARAXXOR_ID = NpcID.ARAXXOR;
    private static final int ARAXXOR_DEAD_ID = NpcID.ARAXXOR_DEAD;
    private static final int RUPTURA_ID = NpcID.ARAXXOR_MINION_EXPLODE;
    private static final int MIRRORBACK_ID = NpcID.ARAXXOR_MINION_MIRRORBACK;
    private static final int ACIDIC_ID = NpcID.ARAXXOR_MINION_VENOM;
    private static final int ACID_CANNON_PROJ_NPC = NpcID.ARAXXOR_ACID_CANNON_PROJECTILE; // 13676
    private static final int VENOM_DRIP_GFX = SpotanimID.ARAXXOR_VENOM_DRIP;

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
    @Inject
    private ItemManager itemManager;
    @Inject
    private ConfigManager configManager;

    private int cachedWeaponId = Integer.MIN_VALUE;
    private int cachedAttackStyle = Integer.MIN_VALUE;
    private int cachedWeaponCategory = Integer.MIN_VALUE;
    private int cachedAttackSpeed = 4;
    private final Set<Projectile> observedAttackProjectiles =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Getter
    private final AraxxorScript script = new AraxxorScript();

    @Provides
    AraxxorConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AraxxorConfig.class);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!AraxxorConfig.GROUP.equals(event.getGroup())
                || !"captureStartingTile".equals(event.getKey())) {
            return;
        }
        var player = client.getLocalPlayer();
        if (player == null || player.getLocalLocation() == null) return;
        WorldPoint templateTile = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
        if (templateTile == null) return;
        String value = templateTile.getX() + "," + templateTile.getY() + "," + templateTile.getPlane();
        configManager.setConfiguration(AraxxorConfig.GROUP, "startingTile", value);
        log.info("Araxxor starting tile captured: {}", value);
    }

    @Override
    protected void startUp() throws AWTException {
        lastRangedExperience = client.getSkillExperience(Skill.RANGED);
        lastHitpointsExperience = client.getSkillExperience(Skill.HITPOINTS);
        observedAttackProjectiles.clear();
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        observedAttackProjectiles.clear();
        script.shutdown();
        overlayManager.remove(overlay);
    }

    // ── Projectile Events ───────────────────────────────

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        var projectile = event.getProjectile();
        int id = projectile.getId();
        if (observedAttackProjectiles.add(projectile)) {
            script.onAraxxorProjectile(id);
        }

        // Track the projectile's destination rather than its transient position.
        if (ACID_PROJECTILE_IDS.contains(id)) {
            WorldPoint landing = projectile.getTargetPoint();
            if (landing == null) {
                landing = WorldPoint.fromLocal(client, event.getPosition());
            }
            if (landing != null) {
                script.onAcidTileSpawned(landing);
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        var player = client.getLocalPlayer();
        if (player == null) return;
        int hp = client.getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS);
        int maxHp = client.getRealSkillLevel(net.runelite.api.Skill.HITPOINTS);
        int prayer = client.getBoostedSkillLevel(net.runelite.api.Skill.PRAYER);
        int maxPrayer = client.getRealSkillLevel(net.runelite.api.Skill.PRAYER);
        int defence = client.getRealSkillLevel(net.runelite.api.Skill.DEFENCE);
        int specEnergy = client.getVarpValue(300) / 10;
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        int weaponId = equipmentItemId(equipment, EquipmentInventorySlot.WEAPON.getSlotIdx());
        int attackSpeed = resolveAttackSpeed(weaponId);
        script.onGameTickSnapshot(client.getTickCount(), player.getWorldLocation(), hp, maxHp,
                prayer, maxPrayer, maxPrayer, defence, specEnergy, attackSpeed,
                client.isPrayerActive(Prayer.PROTECT_FROM_MELEE),
                client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES),
                client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC));
        if (!script.isFightDiagnosticsEnabled()) return;
        String target = player.getInteracting() instanceof NPC
                ? ((NPC) player.getInteracting()).getName() + "#" + ((NPC) player.getInteracting()).getId()
                : "none";
        int ammoId = equipmentItemId(equipment, EquipmentInventorySlot.AMMO.getSlotIdx());
        String prayers = activeProtectionPrayers();
        script.onDiagnosticTick(client.getTickCount(), player.getWorldLocation(), hp, maxHp,
                target, weaponId, ammoId, prayers);
    }

    private int equipmentItemId(ItemContainer equipment, int slot) {
        if (equipment == null || equipment.getItems().length <= slot) return -1;
        Item item = equipment.getItems()[slot];
        return item == null ? -1 : item.getId();
    }

    private int resolveAttackSpeed(int weaponId) {
        int attackStyle = client.getVarpValue(VarPlayerID.COM_MODE);
        int weaponCategory = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
        if (weaponId == cachedWeaponId && attackStyle == cachedAttackStyle
                && weaponCategory == cachedWeaponCategory) {
            return cachedAttackSpeed;
        }

        int speed = 4; // unarmed/unknown safe default
        ItemStats stats = weaponId < 0 ? null : itemManager.getItemStats(weaponId);
        if (stats != null && stats.getEquipment() != null && stats.getEquipment().getAspeed() > 0) {
            speed = stats.getEquipment().getAspeed();
        }

        int weaponStyleEnum = client.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponCategory);
        if (weaponStyleEnum != -1) {
            int[] styleStructs = client.getEnum(weaponStyleEnum).getIntVals();
            if (attackStyle >= 0 && attackStyle < styleStructs.length) {
                String styleName = client.getStructComposition(styleStructs[attackStyle])
                        .getStringValue(ParamID.ATTACK_STYLE_NAME);
                if ("Rapid".equalsIgnoreCase(styleName)) speed--;
            }
        }

        cachedWeaponId = weaponId;
        cachedAttackStyle = attackStyle;
        cachedWeaponCategory = weaponCategory;
        cachedAttackSpeed = Math.max(1, speed);
        return cachedAttackSpeed;
    }

    private String activeProtectionPrayers() {
        StringBuilder active = new StringBuilder();
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)) active.append("MELEE,");
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)) active.append("RANGE,");
        if (client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC)) active.append("MAGIC,");
        if (client.isPrayerActive(Prayer.PIETY)) active.append("PIETY,");
        if (client.isPrayerActive(Prayer.RIGOUR)) active.append("RIGOUR,");
        return active.length() == 0 ? "none" : active.substring(0, active.length() - 1);
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (event.getActor() == client.getLocalPlayer()) {
            script.onPlayerDamage(event.getHitsplat().getAmount());
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        Skill skill = event.getSkill();
        int previous;
        if (skill == Skill.RANGED) {
            previous = lastRangedExperience;
            lastRangedExperience = event.getXp();
        } else if (skill == Skill.HITPOINTS) {
            previous = lastHitpointsExperience;
            lastHitpointsExperience = event.getXp();
        } else {
            return;
        }
        if (event.getXp() > previous) {
            script.onCombatExperienceDrop(skill);
        }
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event) {
        if (event.getActor() == client.getLocalPlayer()
                && event.getActor().getGraphic() == VENOM_DRIP_GFX) {
            script.onAcidDripLanded(client.getTickCount());
        }
    }

    // ── NPC Animation Events ────────────────────────────

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (event.getActor() == client.getLocalPlayer()) {
            var player = client.getLocalPlayer();
            if (player != null && player.getAnimation() != -1 && player.getInteracting() instanceof NPC) {
                int targetId = ((NPC) player.getInteracting()).getId();
                if (targetId == ARAXXOR_ID || targetId == MIRRORBACK_ID || targetId == ACIDIC_ID) {
                    script.onPlayerAttackAnimation(client.getTickCount(), cachedAttackSpeed);
                }
            }
            return;
        }
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

        if (npc.getId() == RUPTURA_ID || npc.getId() == MIRRORBACK_ID || npc.getId() == ACIDIC_ID) {
            script.traceEvent("MINION_SPAWN", "id=" + npc.getId() + " tile=" + npc.getWorldLocation());
            script.onAraxyteSpawned(npc.getId());
        }

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
            script.onAcidTileSpawned(loc);
            script.traceEvent("ACID_SPAWN", "id=" + id + " tile=" + loc);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        int id = event.getGameObject().getId();
        if (ACID_OBJECT_IDS.contains(id)) {
            WorldPoint loc = event.getGameObject().getWorldLocation();
            script.getAcidPools().remove(loc);
            script.traceEvent("ACID_DESPAWN", "id=" + id + " tile=" + loc);
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
                script.onCleaveDetected();
                script.traceEvent("CLEAVE", "Skree detected");
            }
        }
    }
}

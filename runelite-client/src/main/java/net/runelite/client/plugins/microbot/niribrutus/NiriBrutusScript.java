package net.runelite.client.plugins.microbot.niribrutus;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NiriBrutusScript extends Script {

    public volatile String status = "Starting...";
    @Getter
    private volatile int killCount = 0;
    private volatile boolean prayersActive = false;

    // Spawn-detection state: set by the plugin's NpcSpawned event
    private volatile long spawnDetectedAt = 0;
    private volatile int spawnAttackDelay = 0; // randomised 600-1200 ms
    private volatile NPC spawnedNpc = null;

    /**
     * Called from the plugin on the client thread when the target NPC spawns.
     */
    public void onTargetSpawned(NPC npc) {
        spawnedNpc = npc;
        spawnDetectedAt = System.currentTimeMillis();
        spawnAttackDelay = ThreadLocalRandom.current().nextInt(600, 1201); // 600-1200 ms
        log.debug("Spawn flagged – will attack in {}ms", spawnAttackDelay);
    }

    /**
     * Called from the plugin on the client thread when the target NPC despawns.
     */
    public void onTargetDespawned() {
        spawnedNpc = null;
        spawnDetectedAt = 0;
    }

    public boolean run(NiriBrutusConfig config) {
        // Antiban setup
        try {
            Rs2Antiban.resetAntibanSettings();
            if (config.enableAntiban()) {
                Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
                Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
            }
        } catch (Exception e) {
            log.warn("Antiban init failed", e);
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                // ── 1. Safety: eat food ─────────────────────────
                double hpPercent = (double) (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100)
                        / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
                if (hpPercent <= config.eatAtHpPercent()) {
                    status = "Eating...";
                    Rs2Player.eatAt(config.eatAtHpPercent());
                    return;
                }

                // ── 2. Quick prayers: on during combat, off when idle ──
                if (config.useQuickPrayer()) {
                    boolean shouldPray = Rs2Combat.inCombat();
                    if (shouldPray && !prayersActive) {
                        Rs2Prayer.toggleQuickPrayer(true);
                        prayersActive = true;
                    } else if (!shouldPray && prayersActive) {
                        Rs2Prayer.toggleQuickPrayer(false);
                        prayersActive = false;
                    }
                }

                // ── 3. Special attack while in combat ───────────
                if (config.useSpecialAttack() && Rs2Combat.inCombat()) {
                    int specEnergy = Microbot.getClient().getVarpValue(300) / 10;
                    if (specEnergy >= config.specEnergyThreshold()) {
                        Rs2Combat.setSpecState(true, 250);
                    }
                }

                // ── 4. Already fighting — antiban cooldown ──────
                if (Rs2Combat.inCombat()) {
                    status = "Fighting " + config.npcName();
                    if (config.enableAntiban()) {
                        Rs2Antiban.actionCooldown();
                    }
                    return;
                }

                // ── 5. Fast spawn attack ────────────────────────
                // If the event listener detected a spawn, wait the randomised
                // delay (600-1200ms) and then attack immediately.
                if (spawnedNpc != null && spawnDetectedAt > 0) {
                    long elapsed = System.currentTimeMillis() - spawnDetectedAt;
                    if (elapsed < spawnAttackDelay) {
                        status = "Spawn detected – attacking in " + (spawnAttackDelay - elapsed) + "ms";
                        return; // wait for the delay
                    }
                    // Delay passed — attempt attack via the spawn reference
                    try {
                        Rs2NpcModel target = new Rs2NpcModel(spawnedNpc);
                        if (!target.isDead()) {
                            if (!Rs2Camera.isTileOnScreen(target.getLocalLocation())) {
                                Rs2Camera.turnTo(target);
                            }
                            status = "Attacking " + target.getName() + " (spawn)";
                            Rs2Npc.interact(target, "attack");
                            spawnedNpc = null;
                            spawnDetectedAt = 0;
                            return;
                        }
                    } catch (Exception e) {
                        log.debug("Spawn-attack failed, falling through to normal scan", e);
                    }
                    spawnedNpc = null;
                    spawnDetectedAt = 0;
                }

                // ── 6. Normal NPC scan (fallback) ───────────────
                String targetName = config.npcName().trim();
                Rs2NpcModel target = Rs2Npc.getNpcs(npc ->
                                npc.getName() != null
                                        && npc.getName().equalsIgnoreCase(targetName)
                                        && npc.getCombatLevel() > 0
                                        && !npc.isDead()
                                        && (!npc.isInteracting() || Objects.equals(npc.getInteracting(), Microbot.getClient().getLocalPlayer())))
                        .sorted(Comparator.comparingInt(npc ->
                                npc.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
                        .findFirst()
                        .orElse(null);

                if (target == null) {
                    status = "Waiting for " + targetName + "...";
                    // Antiban idle behavior
                    if (config.enableAntiban()) {
                        Rs2Antiban.actionCooldown();
                    }
                    return;
                }

                if (!Rs2Camera.isTileOnScreen(target.getLocalLocation())) {
                    Rs2Camera.turnTo(target);
                }

                status = "Attacking " + target.getName();
                Rs2Npc.interact(target, "attack");

            } catch (Exception ex) {
                Microbot.logStackTrace("NiriBrutusScript", ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS); // 300ms tick for responsive spawn attacks
        return true;
    }

    public void incrementKillCount() {
        killCount++;
    }

    @Override
    public void shutdown() {
        // Turn off quick prayers on shutdown
        if (prayersActive) {
            try {
                Rs2Prayer.toggleQuickPrayer(false);
            } catch (Exception ignored) {}
            prayersActive = false;
        }
        spawnedNpc = null;
        spawnDetectedAt = 0;
        killCount = 0;
        status = "Stopped";
        super.shutdown();
    }
}

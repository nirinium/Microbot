package net.runelite.client.plugins.microbot.nirifighter.combat;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.nirifighter.NiriFighterConfig;
import net.runelite.client.plugins.microbot.nirifighter.NiriFighterPlugin;
import net.runelite.client.plugins.microbot.nirifighter.enums.State;
import net.runelite.client.plugins.microbot.nirifighter.model.InventorySetupUtil;
import net.runelite.client.plugins.microbot.util.npc.MonsterLocation;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.skills.slayer.Rs2Slayer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
@Slf4j
public class SlayerScript extends Script {

    static WorldPoint cachedMonsterLocation = null;
    static String cachedMonsterLocationName = null;
    NiriFighterConfig config;
    @SneakyThrows
    public boolean run(NiriFighterConfig config) {
        this.config = config;
        Microbot.enableAutoRunOn = false;
        Rs2NpcManager.loadJson();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.slayerMode()) return;


                handleSlayerTask();


            } catch (Exception ex) {
                log.error("Error: " + ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }


    // set attackableNpcs
    public void setAttackableNpcs() {
        List<String> npcNames = Rs2Slayer.getSlayerMonsters();
        // convert npcNames array to string, remove brackets
        assert npcNames != null;
        String npcNamesString = Arrays.toString(npcNames.toArray()).replace("[", "").replace("]", "");
         NiriFighterPlugin.setAttackableNpcs(npcNamesString);
    }

    // handle slayer task
    public void handleSlayerTask() {
         NiriFighterPlugin.setSlayerTask(Rs2Slayer.getSlayerTask());
         NiriFighterPlugin.setRemainingSlayerKills(Rs2Slayer.getSlayerTaskSize());
        if (Rs2Slayer.hasSlayerTask()) {
            setAttackableNpcs();
            if(Rs2Slayer.hasSlayerTaskWeakness()){
                 NiriFighterPlugin.setSlayerHasTaskWeakness(true);
                 NiriFighterPlugin.setSlayerTaskWeaknessItem(Rs2Slayer.getSlayerTaskWeaknessName());
                 NiriFighterPlugin.setSlayerTaskWeaknessThreshold(Rs2Slayer.getSlayerTaskWeaknessThreshold());
            }
            else {
                 NiriFighterPlugin.setSlayerHasTaskWeakness(false);
                 NiriFighterPlugin.setSlayerTaskWeaknessItem("");
                 NiriFighterPlugin.setSlayerTaskWeaknessThreshold(0);
            }
            if (cachedMonsterLocation == null) {
                MonsterLocation monsterLocation = Rs2Slayer.getSlayerTaskLocation(3, true);
                assert monsterLocation != null;
                WorldPoint slayerTaskLocation = monsterLocation.getBestClusterCenter();
                log.info("Monster location: " + slayerTaskLocation);
                InventorySetupUtil.config = config;
                InventorySetupUtil.determineInventorySetup(Rs2Slayer.slayerTaskMonsterTarget);

                cachedMonsterLocation = slayerTaskLocation;
                cachedMonsterLocationName = monsterLocation.getLocationName();
                 NiriFighterPlugin.setSlayerLocationName(cachedMonsterLocationName);
            }
            if (cachedMonsterLocation != null && config.centerLocation() != cachedMonsterLocation) {
                 NiriFighterPlugin.setCenter(cachedMonsterLocation);
            }

        }
        else {
            Microbot.log("No slayer task");
            reset();
            NiriFighterPlugin.setState(State.GETTING_TASK);
            if(Rs2Slayer.walkToSlayerMaster(config.slayerMaster())) {
                Rs2NpcModel npc = Rs2Npc.getNpc(config.slayerMaster().getName());
                if(npc != null) {
                    Rs2Npc.interact(npc, "Assignment");
                    sleepUntil(Rs2Slayer::hasSlayerTask, 5000);
                }
            }
        }
    }

    public static void reset() {
        cachedMonsterLocation = null;
        cachedMonsterLocationName = null;
         NiriFighterPlugin.setSlayerLocationName("None");
         NiriFighterPlugin.setSlayerTask("None");
         NiriFighterPlugin.setSlayerHasTaskWeakness(false);
         NiriFighterPlugin.setSlayerTaskWeaknessItem("");
         NiriFighterPlugin.setSlayerTaskWeaknessThreshold(0);
         NiriFighterPlugin.resetLocation();
         NiriFighterPlugin.setAttackableNpcs("");
         Rs2Slayer.blacklistedSlayerMonsters = NiriFighterPlugin.getBlacklistedSlayerNpcs();
    }


    @Override
    public void shutdown() {
        cachedMonsterLocation = null;
        super.shutdown();
    }
}

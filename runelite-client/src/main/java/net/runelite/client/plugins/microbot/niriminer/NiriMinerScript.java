package net.runelite.client.plugins.microbot.niriminer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;

import java.util.concurrent.TimeUnit;

@Slf4j
public class NiriMinerScript extends Script {

    @Getter
    private String status = "Initializing...";

    private NiriMinerConfig config;

    private static final int PAY_DIRT_ID = ItemID.PAYDIRT;
    private static final int GOLDEN_NUGGET_ID = ItemID.GOLDEN_NUGGET;

    // Motherlode Mine object IDs
    private static final int ORE_VEIN_26661 = 26661;
    private static final int ORE_VEIN_26662 = 26662;
    private static final int HOPPER = ObjectID.HOPPER_26674;
    private static final int SACK = 26688;

    public boolean run(NiriMinerConfig config) {
        this.config = config;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    status = "Not logged in or script paused";
                    return;
                }

                if (shouldBank()) {
                    handleBanking();
                    return;
                }

                if (Rs2Inventory.isFull() || (Rs2Inventory.count(PAY_DIRT_ID) >= config.depositThreshold())) {
                    depositPayDirt();
                    return;
                }

                if (config.collectFromSack() && shouldCheckSack()) {
                    collectFromSack();
                    return;
                }

                minePayDirt();

            } catch (Exception e) {
                log.error("Error in Motherlode Mine loop", e);
                status = "Error: " + e.getMessage();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean shouldBank() {
        if (!config.bankOres()) return false;
        int totalOres = getTotalOreCount();
        return totalOres > 0 && Rs2Inventory.count() >= 27;
    }

    private int getTotalOreCount() {
        return Rs2Inventory.count(ItemID.COAL) +
            Rs2Inventory.count(ItemID.GOLD_ORE) +
            Rs2Inventory.count(ItemID.MITHRIL_ORE) +
            Rs2Inventory.count(ItemID.ADAMANTITE_ORE) +
            Rs2Inventory.count(ItemID.RUNITE_ORE);
    }

    private void handleBanking() {
        status = "Banking ores...";

        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank()) {
                status = "Failed to open bank";
                sleep(1000);
                return;
            }
            sleepUntil(Rs2Bank::isOpen, 5000);
            return;
        }

        Rs2Bank.depositAll(item -> {
            String name = item.getName().toLowerCase();
            return !name.contains("hammer") &&
                !name.contains("pickaxe") &&
                item.getId() != GOLDEN_NUGGET_ID;
        });

        sleepUntil(() -> getTotalOreCount() == 0, 3000);
        Rs2Bank.closeBank();
        status = "Banking complete";
    }

    private void depositPayDirt() {
        status = "Depositing pay-dirt at hopper...";

        if (Rs2Inventory.count(PAY_DIRT_ID) == 0) {
            status = "No pay-dirt to deposit";
            return;
        }

        if (Rs2GameObject.interact(HOPPER, "Deposit")) {
            sleepUntil(() -> Rs2Inventory.count(PAY_DIRT_ID) == 0 || !Rs2Player.isAnimating(), 5000);
            sleep(600, 1200);
        } else {
            status = "Hopper not found";
            sleep(1000);
        }
    }

    private boolean shouldCheckSack() {
        return !Rs2Inventory.isFull() && Rs2Inventory.count(PAY_DIRT_ID) < 5;
    }

    private void collectFromSack() {
        status = "Collecting from sack...";

        if (Rs2GameObject.interact(SACK, "Search")) {
            sleepUntil(() -> Rs2Inventory.isFull() || Rs2Inventory.count() > 26, 5000);
            sleep(600, 1200);
        } else {
            status = "Sack not found";
            sleep(1000);
        }
    }

    private void minePayDirt() {
        if (Rs2Player.isAnimating()) {
            status = "Mining...";
            sleep(1000, 2000);
            return;
        }

        status = "Looking for ore vein...";

        if (Rs2GameObject.interact(ORE_VEIN_26661, "Mine", config.miningRadius())) {
            status = "Mining ore vein...";
            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Inventory.isFull(), 3000);
            sleep(600, 1200);
        } else {
            status = "No ore veins found nearby";
            sleep(1000);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        status = "Stopped";
    }
}

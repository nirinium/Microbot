package net.runelite.client.plugins.microbot.nirigrotesqueguardiansbanking;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Restocking steps observe game state; long travel uses the walker's transport pipeline off-thread. */
final class BankingGuardiansBanking {
    // Existing Slayer Tower transport endpoint in shortestpath/transports.tsv.
    private static final WorldPoint TOWER_TOP_FLOOR = new WorldPoint(3417, 3540, 2);
    enum Stage { EXIT, TO_BANK, RESTOCK, RETURN, ENTER, COMPLETE, BLOCKED }
    private Stage stage = Stage.EXIT;
    private WorldPoint entrance;
    private int lastActionTick = -100;
    private long stageStarted = System.nanoTime();
    private String status = "Preparing banking";
    private Map<String, Integer> loadout;
    private boolean roofInteractionRequested;

    String status() { return status; }
    boolean complete() { return stage == Stage.COMPLETE; }
    String stageName() { return stage.name(); }
    static boolean shouldStart(boolean enabled, boolean bossPresent, boolean onRoof, boolean needsSupplies) {
        return enabled && !bossPresent && (!onRoof || needsSupplies);
    }
    void reset() {
        stage = Stage.EXIT;
        entrance = null;
        loadout = null;
        lastActionTick = -100;
        stageStarted = System.nanoTime();
        status = "Preparing banking";
        roofInteractionRequested = false;
    }

    static Map<String, Integer> parseLoadout(String text) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String entry : text.split(",")) {
            String[] parts = entry.trim().split("=", -1);
            if (parts.length != 2 || parts[0].trim().isEmpty())
                throw new IllegalArgumentException("Use exact item name=quantity, separated by commas");
            int quantity = Integer.parseInt(parts[1].trim());
            if (quantity < 1 || quantity > 28000)
                throw new IllegalArgumentException("Bank quantity must be between 1 and 28000");
            if (result.put(parts[0].trim(), quantity) != null)
                throw new IllegalArgumentException("Duplicate loadout item: " + parts[0].trim());
        }
        return result;
    }

    void step(BankingGuardiansConfig config, int tick, WorldPoint location, boolean onRoof,
              boolean suppliesReady) {
        if (stage == Stage.BLOCKED || stage == Stage.COMPLETE || location == null) return;
        if (System.nanoTime() - stageStarted > java.util.concurrent.TimeUnit.MINUTES.toNanos(5)) {
            block("Banking timed out during " + stage + "; restart after checking the route/bank");
            return;
        }
        if (tick - lastActionTick < 3) return;
        lastActionTick = tick;
        if (loadout == null) {
            try { loadout = parseLoadout(config.bankLoadout()); }
            catch (IllegalArgumentException ex) { block("Invalid bank loadout: " + ex.getMessage()); return; }
        }
        if (roofInteractionRequested && (stage == Stage.EXIT || stage == Stage.ENTER)
                && Rs2Dialogue.isInDialogue()) {
            status = "Confirming roof " + (stage == Stage.EXIT ? "exit" : "entry");
            if (Rs2Dialogue.hasContinue()) Rs2Dialogue.clickContinue();
            else Rs2Dialogue.clickOption("Yes", false);
            return;
        }
        switch (stage) {
            case EXIT:
                status = "Leaving the roof to bank";
                if (!onRoof) {
                    // A fresh start outside the instance can use the mapped tower stairs.
                    entrance = entrance == null ? TOWER_TOP_FLOOR : location;
                    advance(suppliesReady ? Stage.RETURN : Stage.TO_BANK);
                    return;
                }
                Rs2TileObjectModel exit = Microbot.getRs2TileObjectCache().query()
                        .withIds(ObjectID.SLAYER_ROOF_EXIT).nearest();
                if (exit != null) {
                    entrance = location;
                    roofInteractionRequested = clickRoofObject(exit, location, "Climb-down", "Exit", "Leave", "Climb");
                } else status = "Waiting for the roof exit to load";
                return;
            case TO_BANK:
                status = "Walking to Canifis bank";
                if (Rs2Bank.isOpen()) { advance(Stage.RESTOCK); return; }
                if (location.distanceTo(BankLocation.CANIFIS.getWorldPoint()) <= 6) Rs2Bank.openBank();
                else travel(BankLocation.CANIFIS.getWorldPoint(), 4);
                return;
            case RESTOCK:
                status = "Depositing loot and restocking";
                if (!Rs2Bank.isOpen()) { Rs2Bank.openBank(); return; }
                // Deposit one stack per step, preserving equipment switches and explicit keep items.
                Rs2ItemModel excess = Rs2Inventory.items().filter(item ->
                        !loadout.containsKey(item.getName()) && !keep(item, config.bankKeepNames()))
                        .findFirst().orElse(null);
                if (excess != null) { Rs2Bank.depositAll(excess.getId()); return; }
                for (Map.Entry<String, Integer> entry : loadout.entrySet()) {
                    int count = Rs2Inventory.count(entry.getKey(), true);
                    if (count > entry.getValue()) {
                        Rs2Bank.depositX(Rs2Inventory.get(entry.getKey(), true).getId(), count - entry.getValue());
                        return;
                    }
                    if (count < entry.getValue()) {
                        int deficit = entry.getValue() - count;
                        if (Rs2Bank.count(entry.getKey(), true) < deficit) {
                            block("Bank missing " + deficit + " x " + entry.getKey()); return;
                        }
                        if (Rs2Inventory.emptySlotCount() == 0 && count == 0) {
                            block("Loadout does not fit: " + entry.getKey()); return;
                        }
                        if (!Rs2Bank.hasWithdrawAsItem()) { Rs2Bank.setWithdrawAsItem(); return; }
                        Rs2Bank.withdrawX(entry.getKey(), deficit, true);
                        return;
                    }
                }
                if (!suppliesReady) {
                    block("Loadout needs food above the banking threshold, prayer restore, finisher and combat gear");
                    return;
                }
                advance(Stage.RETURN);
                return;
            case RETURN:
                status = "Returning to the Slayer Tower roof";
                if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return; }
                if (onRoof) { advance(Stage.COMPLETE); return; }
                if (location.distanceTo(entrance) <= 5) { advance(Stage.ENTER); return; }
                travel(entrance, 2);
                return;
            case ENTER:
                status = "Entering the roof";
                if (onRoof) { advance(Stage.COMPLETE); return; }
                Rs2TileObjectModel door = Microbot.getRs2TileObjectCache().query()
                        .withIds(ObjectID.SLAYER_ROOF_ENTRANCE_UNLOCKED, ObjectID.SLAYER_ROOF_ENTRANCE).nearest();
                if (door != null) roofInteractionRequested = clickRoofObject(door, location, "Climb-up", "Enter", "Climb", "Open");
                else if (Microbot.getRs2TileObjectCache().query()
                        .withIds(ObjectID.SLAYER_ROOF_ENTRANCE_LOCKED).nearest() != null)
                    block("The roof entrance is locked; unlock it before using this plugin");
                else status = "Waiting for the unlocked roof entrance to load";
                return;
            default:
                return;
        }
    }

    private void travel(WorldPoint destination, int distance) {
        // walkStep cannot traverse doors/stairs. This runs on the script executor, never the client thread.
        WalkerState result = Rs2Walker.walkWithState(destination, distance);
        if (result == WalkerState.UNREACHABLE) block("No walking route to " + destination);
        // Even ARRIVED is verified against fresh player/object state on the next script iteration.
    }

    private boolean clickRoofObject(Rs2TileObjectModel object, WorldPoint location, String... preferredActions) {
        WorldPoint objectLocation = Microbot.getClientThread().runOnClientThreadOptional(object::getWorldLocation).orElse(null);
        if (objectLocation == null) return false;
        if (location.distanceTo(objectLocation) > 5) {
            status = "Walking to the roof entrance/exit";
            travel(objectLocation, 2);
            return false;
        }
        String[] actions = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            net.runelite.api.ObjectComposition composition = object.getObjectComposition();
            return composition == null ? new String[0] : composition.getActions();
        }).orElse(new String[0]);
        String action = chooseAction(actions, preferredActions);
        if (action == null) {
            block("Roof object " + object.getId() + " has no supported action: " + Arrays.toString(actions));
            return false;
        }
        boolean requested = object.click(action);
        status = requested ? "Requested roof action: " + action : "Retrying roof action: " + action;
        return requested;
    }

    static String chooseAction(String[] available, String... preferred) {
        if (available == null) return null;
        for (String action : preferred)
            for (String candidate : available)
                if (action.equalsIgnoreCase(candidate)) return candidate;
        return null;
    }

    private static boolean keep(Rs2ItemModel item, String names) {
        if (Arrays.stream(names.split(",")).map(String::trim).anyMatch(item.getName()::equalsIgnoreCase)) return true;
        String[] actions = item.getInventoryActions();
        return actions != null && Arrays.stream(actions).anyMatch(action ->
                "Wear".equalsIgnoreCase(action) || "Wield".equalsIgnoreCase(action) || "Equip".equalsIgnoreCase(action));
    }
    private void advance(Stage next) {
        stage = next;
        stageStarted = System.nanoTime();
        roofInteractionRequested = false;
    }
    private void block(String reason) { stage = Stage.BLOCKED; status = reason; }
}


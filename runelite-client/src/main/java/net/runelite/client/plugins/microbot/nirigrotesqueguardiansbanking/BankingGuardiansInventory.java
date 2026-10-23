package net.runelite.client.plugins.microbot.nirigrotesqueguardiansbanking;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.util.Text;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntToLongFunction;
import java.util.stream.Collectors;

final class BankingGuardiansInventory {
    enum Result { IDLE, WAITING, ACTION_REQUESTED, SPACE_AVAILABLE, BLOCKED }

    private final AtomicBoolean requested = new AtomicBoolean();
    private final Set<Integer> discardedIds = new HashSet<>();
    private int lastAttemptTick = -100;
    private int attempts;
    private String blockedSnapshot;
    private String status = "";

    static boolean isSpaceMessage(String message) {
        if (message == null) return false;
        String normalized = Text.removeTags(message).replace('\u2019', '\'').toLowerCase(Locale.ROOT);
        return normalized.contains("not enough inventory space")
                || normalized.contains("not enough space in your inventory")
                || normalized.contains("don't have enough free inventory space")
                || normalized.contains("do not have enough free inventory space")
                || normalized.contains("don't have enough inventory space")
                || normalized.contains("do not have enough inventory space")
                || normalized.contains("your inventory is full")
                || normalized.contains("inventory is too full");
    }

    void request() {
        requested.set(true);
    }

    boolean isRequested() {
        return requested.get();
    }

    boolean wasDiscarded(int id) {
        return discardedIds.contains(id);
    }

    String status() {
        return status;
    }

    void reset() {
        requested.set(false);
        discardedIds.clear();
        lastAttemptTick = -100;
        attempts = 0;
        blockedSnapshot = null;
        status = "";
    }

    Result step(BankingGuardiansConfig config, int tick, boolean canEat,
                Set<String> protectedNames, IntToLongFunction unitPrice) {
        if (!requested.get()) return Result.IDLE;
        if (!config.manageInventorySpace()) {
            requested.set(false);
            attempts = 0;
            blockedSnapshot = null;
            return Result.IDLE;
        }
        if (Rs2Inventory.emptySlotCount() > 0) {
            requested.set(false);
            attempts = 0;
            blockedSnapshot = null;
            status = "Inventory space available";
            return Result.SPACE_AVAILABLE;
        }
        List<Rs2ItemModel> items = Rs2Inventory.all();
        String snapshot = items.stream().sorted(Comparator.comparingInt(Rs2ItemModel::getSlot))
                .map(item -> item.getSlot() + ":" + item.getId() + ":" + item.getQuantity())
                .collect(Collectors.joining(","));
        snapshot += ":" + config.eatForInventorySpace() + ":" + config.inventoryFoodReserve()
                + ":" + config.dropForInventorySpace() + ":" + config.inventoryDiscardNames()
                + ":" + config.inventoryDiscardValue()
                + ":" + protectedNames.stream().sorted().collect(Collectors.joining(","));
        if (snapshot.equals(blockedSnapshot)) return Result.BLOCKED;
        if (blockedSnapshot != null) {
            attempts = 0;
            blockedSnapshot = null;
        }
        if (tick - lastAttemptTick < 3) return Result.WAITING;
        if (attempts >= 6) return block(snapshot);

        List<Rs2ItemModel> food = items.stream().filter(Rs2ItemModel::isFood)
                .filter(item -> hasAction(item, "Eat")).collect(Collectors.toList());
        if (config.eatForInventorySpace() && food.size() > config.inventoryFoodReserve()) {
            if (!canEat) {
                status = "Waiting for food cooldown before making inventory space";
                return Result.WAITING;
            }
            Rs2ItemModel item = food.get(0);
            lastAttemptTick = tick;
            attempts++;
            Rs2Inventory.interact(item, "Eat");
            status = "Trying to eat " + item.getName() + " for inventory space";
            return Result.ACTION_REQUESTED;
        }

        Set<String> allowed = Arrays.stream(config.inventoryDiscardNames().split(","))
                .map(String::trim).map(name -> name.toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty()).collect(Collectors.toSet());
        Rs2ItemModel cheapest = null;
        long cheapestValue = Long.MAX_VALUE;
        if (config.dropForInventorySpace()) {
            for (Rs2ItemModel item : items) {
                if (!canDiscard(item, allowed, protectedNames)) continue;
                long price = unitPrice.applyAsLong(item.getId());
                long value = price > 0 && price <= Integer.MAX_VALUE
                        ? price * item.getQuantity() : 0;
                if (value > 0 && value <= config.inventoryDiscardValue() && value < cheapestValue) {
                    cheapest = item;
                    cheapestValue = value;
                }
            }
        }
        if (cheapest == null) return block(snapshot);
        lastAttemptTick = tick;
        attempts++;
        discardedIds.add(cheapest.getId());
        Rs2Inventory.interact(cheapest, "Drop");
        status = "Trying to drop " + cheapest.getName() + " (stack " + cheapestValue + " gp) for space";
        return Result.ACTION_REQUESTED;
    }

    private Result block(String snapshot) {
        blockedSnapshot = snapshot;
        status = "Inventory full: no safe space action; adjust food reserve/discard list or bank";
        return Result.BLOCKED;
    }

    private boolean canDiscard(Rs2ItemModel item, Set<String> allowed, Set<String> protectedNames) {
        String name = item.getName().toLowerCase(Locale.ROOT);
        if (!allowed.contains(name) || protectedNames.stream().anyMatch(name::equalsIgnoreCase)
                || !item.isTradeable() || item.isFood() || !hasAction(item, "Drop")) return false;
        if (name.equals("coins") || name.contains("rune") || name.contains("teleport")
                || name.contains("hammer") || name.contains("potion") || name.contains("brew")
                || name.contains("restore")) return false;
        return Arrays.stream(new String[]{"Wear", "Wield", "Equip", "Drink", "Break", "Rub", "Teleport", "Activate"})
                .noneMatch(action -> hasAction(item, action));
    }

    private static boolean hasAction(Rs2ItemModel item, String action) {
        String[] actions = item.getInventoryActions();
        return actions != null && Arrays.stream(actions).anyMatch(action::equalsIgnoreCase);
    }
}


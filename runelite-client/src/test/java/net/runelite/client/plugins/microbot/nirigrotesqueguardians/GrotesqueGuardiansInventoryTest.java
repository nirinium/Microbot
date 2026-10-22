package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static net.runelite.client.plugins.microbot.nirigrotesqueguardians.GrotesqueGuardiansInventory.Result.ACTION_REQUESTED;
import static net.runelite.client.plugins.microbot.nirigrotesqueguardians.GrotesqueGuardiansInventory.Result.BLOCKED;
import static net.runelite.client.plugins.microbot.nirigrotesqueguardians.GrotesqueGuardiansInventory.Result.IDLE;
import static net.runelite.client.plugins.microbot.nirigrotesqueguardians.GrotesqueGuardiansInventory.Result.SPACE_AVAILABLE;
import static net.runelite.client.plugins.microbot.nirigrotesqueguardians.GrotesqueGuardiansInventory.Result.WAITING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

public class GrotesqueGuardiansInventoryTest {
    private final GrotesqueGuardiansConfig config = new GrotesqueGuardiansConfig() {};
    private final GrotesqueGuardiansInventory recovery = new GrotesqueGuardiansInventory();

    @Test
    public void recognizesTaggedAndCurlyApostropheSpaceMessages() {
        assertTrue(GrotesqueGuardiansInventory.isSpaceMessage(
                "<col=ff0000>You don't have enough free inventory space to do that.</col>"));
        assertTrue(GrotesqueGuardiansInventory.isSpaceMessage("You don\u2019t have enough inventory space."));
        assertTrue(GrotesqueGuardiansInventory.isSpaceMessage("There is not enough space in your inventory."));
        assertTrue(GrotesqueGuardiansInventory.isSpaceMessage("Your inventory is full."));
        assertFalse(GrotesqueGuardiansInventory.isSpaceMessage("You don't have enough prayer points."));
        assertFalse(GrotesqueGuardiansInventory.isSpaceMessage(null));
    }

    @Test
    public void chatOnlyRequestsRecoveryWhileRunningInAnEncounter() throws Exception {
        GrotesqueGuardiansScript script = spy(new GrotesqueGuardiansScript());
        setField(script, "config", config);
        setField(script, "displayState", GrotesqueGuardiansState.DUSK_PHASE_2);
        script.onInventorySpaceMessage("Your inventory is full.");
        GrotesqueGuardiansInventory manager = (GrotesqueGuardiansInventory) getField(script, "inventorySpace");
        assertFalse(manager.isRequested());
        doReturn(true).when(script).isRunning();
        script.onInventorySpaceMessage("Your inventory is full.");
        assertTrue(manager.isRequested());
        manager.reset();
        setField(script, "displayState", GrotesqueGuardiansState.DEAD);
        script.onInventorySpaceMessage("Your inventory is full.");
        assertFalse(manager.isRequested());
    }

    @Test
    public void ignoresPlayerChatEvenIfItContainsASpaceMessage() throws Exception {
        GrotesqueGuardiansPlugin plugin = new GrotesqueGuardiansPlugin();
        GrotesqueGuardiansScript script = mock(GrotesqueGuardiansScript.class);
        Field field = GrotesqueGuardiansPlugin.class.getDeclaredField("script");
        field.setAccessible(true);
        field.set(plugin, script);
        ChatMessage event = mock(ChatMessage.class);
        when(event.getMessage()).thenReturn("Your inventory is full.");
        when(event.getType()).thenReturn(ChatMessageType.PUBLICCHAT);
        plugin.onChatMessage(event);
        verify(script, never()).onInventorySpaceMessage(anyString());
        when(event.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
        plugin.onChatMessage(event);
        when(event.getType()).thenReturn(ChatMessageType.SPAM);
        plugin.onChatMessage(event);
        verify(script, times(2)).onInventorySpaceMessage("Your inventory is full.");
    }

    @Test
    public void eatsBeforeDroppingAndWaitsForObservedFreeSpace() {
        Rs2ItemModel shark = item(1, "Shark", 1, "Eat", "Drop");
        Rs2ItemModel spare = item(2, "Manta ray", 1, "Eat", "Drop");
        Rs2ItemModel vial = item(3, "Vial", 1, "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(shark, spare, vial)) {
            recovery.request();
            assertEquals(ACTION_REQUESTED, step(10));
            inventory.verify(() -> Rs2Inventory.interact(shark, "Eat"));
            inventory.verify(() -> Rs2Inventory.interact(vial, "Drop"), never());
            recovery.request();
            assertEquals(WAITING, step(10));
            assertEquals(WAITING, step(11));
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(1);
            assertEquals(SPACE_AVAILABLE, step(11));
            assertFalse(recovery.isRequested());
            assertEquals(IDLE, step(12));
        }
    }

    @Test
    public void retainsFoodReserveAndDropsOnlyCheapestAllowedItem() {
        Rs2ItemModel food = item(1, "Shark", 1, "Eat", "Drop");
        Rs2ItemModel bowl = item(2, "Bowl", 1, "Drop");
        Rs2ItemModel vial = item(3, "Vial", 1, "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(food, bowl, vial)) {
            recovery.request();
            assertEquals(ACTION_REQUESTED, recovery.step(config, 10, true, Set.of(), id -> id == 3 ? 2 : 100));
            inventory.verify(() -> Rs2Inventory.interact(food, "Eat"), never());
            inventory.verify(() -> Rs2Inventory.interact(bowl, "Drop"), never());
            inventory.verify(() -> Rs2Inventory.interact(vial, "Drop"));
            assertTrue(recovery.wasDiscarded(3));
        }
    }

    @Test
    public void doesNotDropUnlistedLootOrUnknownPricedItems() {
        Rs2ItemModel ore = item(1, "Iron ore", 1, "Drop");
        Rs2ItemModel vial = item(2, "Vial", 1, "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(ore, vial)) {
            recovery.request();
            assertEquals(BLOCKED, recovery.step(config, 10, true, Set.of(), id -> id == 1 ? 100 : 0));
            inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), never());
        }
    }

    @Test
    public void checksEntireStackWithWidenedArithmetic() {
        Rs2ItemModel vials = item(1, "Vial", 1_500_000_000, "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(vials)) {
            recovery.request();
            assertEquals(BLOCKED, recovery.step(config, 10, true, Set.of(), id -> 3));
            inventory.verify(() -> Rs2Inventory.interact(vials, "Drop"), never());
        }
    }

    @Test
    public void protectsGearRunesCoinsPotionsTeleportsAndFinishersEvenIfListed() {
        GrotesqueGuardiansConfig unsafeList = new GrotesqueGuardiansConfig() {
            @Override
            public String inventoryDiscardNames() {
                return "Sword, Robe, Coin pouch, Coins, Air rune, Prayer potion(1), Rock hammer, Tablet, Vial";
            }
        };
        List<Rs2ItemModel> items = List.of(item(1, "Sword", 1, "Wield", "Drop"),
                item(2, "Robe", 1, "Wear", "Drop"), item(3, "Coin pouch", 1, "Drop"),
                item(4, "Coins", 100, "Drop"), item(5, "Air rune", 1, "Drop"),
                item(6, "Prayer potion(1)", 1, "Drink", "Drop"), item(7, "Rock hammer", 1, "Drop"),
                item(8, "Tablet", 1, "Break", "Drop"), item(9, "Vial", 1, "Drop"));
        when(items.get(2).isTradeable()).thenReturn(false);
        try (MockedStatic<Rs2Inventory> inventory = inventory(items.toArray(new Rs2ItemModel[0]))) {
            recovery.request();
            assertEquals(BLOCKED, recovery.step(unsafeList, 10, true, Set.of("Vial"), id -> 1));
            inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), never());
        }
    }

    @Test
    public void cooldownDoesNotCauseFoodToBeReplacedByADrop() {
        Rs2ItemModel food = item(1, "Shark", 1, "Eat", "Drop");
        Rs2ItemModel spare = item(2, "Manta ray", 1, "Eat", "Drop");
        Rs2ItemModel vial = item(3, "Vial", 1, "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(food, spare, vial)) {
            recovery.request();
            assertEquals(WAITING, recovery.step(config, 10, false, Set.of(), id -> 1));
            inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), never());
        }
    }

    @Test
    public void multiBiteFoodIsNotMistakenForAFreeSlot() {
        Rs2ItemModel pizza = item(1, "Meat pizza", 1, "Eat", "Drop");
        Rs2ItemModel spare = item(2, "Shark", 1, "Eat", "Drop");
        Rs2ItemModel halfPizza = item(3, "1/2 meat pizza", 1, "Eat");
        try (MockedStatic<Rs2Inventory> inventory = inventory(pizza, spare)) {
            recovery.request();
            assertEquals(ACTION_REQUESTED, step(10));
            inventory.when(Rs2Inventory::all).thenReturn(List.of(halfPizza, spare));
            assertEquals(WAITING, step(11));
            assertTrue(recovery.isRequested());
            assertEquals(ACTION_REQUESTED, step(13));
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(1);
            assertEquals(SPACE_AVAILABLE, step(14));
        }
    }

    @Test
    public void repeatedChatCannotBypassRetryLimitOrCooldown() {
        Rs2ItemModel vial = item(1, "Vial", 1, "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(vial)) {
            for (int tick = 10; tick < 28; tick++) {
                recovery.request();
                step(tick);
            }
            assertEquals(BLOCKED, step(28));
            recovery.request();
            assertEquals(BLOCKED, step(100));
            inventory.verify(() -> Rs2Inventory.interact(vial, "Drop"), times(6));
        }
    }

    @Test
    public void blockedRecoveryReevaluatesChangedInventoryOrSettings() {
        Rs2ItemModel ore = item(1, "Iron ore", 1, "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(ore)) {
            recovery.request();
            assertEquals(BLOCKED, step(10));
            GrotesqueGuardiansConfig revised = new GrotesqueGuardiansConfig() {
                @Override
                public String inventoryDiscardNames() { return "Iron ore"; }
            };
            assertEquals(ACTION_REQUESTED, recovery.step(revised, 11, true, Set.of(), id -> 10));
            inventory.verify(() -> Rs2Inventory.interact(ore, "Drop"));
        }
    }

    @Test
    public void disabledManagerAndAlreadyFreeInventoryTakeNoAction() {
        try (MockedStatic<Rs2Inventory> inventory = inventory()) {
            recovery.request();
            GrotesqueGuardiansConfig disabled = new GrotesqueGuardiansConfig() {
                @Override
                public boolean manageInventorySpace() { return false; }
            };
            assertEquals(IDLE, recovery.step(disabled, 10, true, Set.of(), id -> 1));
            assertFalse(recovery.isRequested());
            recovery.request();
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(1);
            assertEquals(SPACE_AVAILABLE, step(10));
            inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), never());
        }
    }

    @Test
    public void resetClearsPendingRequestAndDiscardSuppression() {
        Rs2ItemModel vial = item(1, "Vial", 1, "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(vial)) {
            recovery.request();
            assertEquals(ACTION_REQUESTED, step(10));
            recovery.reset();
            assertFalse(recovery.isRequested());
            assertFalse(recovery.wasDiscarded(1));
        }
    }

    @Test
    public void lootingWaitsForObservedSpaceThenClearsTheRequestBeforePickup() throws Exception {
        GrotesqueGuardiansScript script = lootScript();
        GrotesqueGuardiansBrews brews = (GrotesqueGuardiansBrews) getField(script, "brewSequence");
        brews.requested(GrotesqueGuardiansBrews.Action.RESTORE, 1, 9);
        Rs2TileItemModel loot = mock(Rs2TileItemModel.class);
        Rs2TileItemCache cache = lootCache(loot);
        Rs2ItemModel food = item(1, "Shark", 1, "Eat", "Drop");
        Rs2ItemModel spare = item(2, "Manta ray", 1, "Eat", "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(food, spare);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Prayer> prayers = mockStatic(Rs2Prayer.class)) {
            microbot.when(Microbot::getRs2TileItemCache).thenReturn(cache);
            invokeLoot(script);
            inventory.verify(() -> Rs2Inventory.interact(food, "Eat"));
            verify(loot, never()).pickup();
            GrotesqueGuardiansInventory manager = (GrotesqueGuardiansInventory) getField(script, "inventorySpace");
            assertTrue(manager.isRequested());
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(1);
            invokeLoot(script);
            assertFalse(manager.isRequested());
            verify(loot).pickup();
        }
    }

    @Test
    public void existingStackableLootDoesNotConsumeFoodToCreateAnUnneededSlot() throws Exception {
        GrotesqueGuardiansScript script = lootScript();
        Rs2TileItemModel loot = mock(Rs2TileItemModel.class);
        when(loot.isStackable()).thenReturn(true);
        when(loot.getId()).thenReturn(99);
        Rs2TileItemCache cache = lootCache(loot);
        try (MockedStatic<Rs2Inventory> inventory = inventory();
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Prayer> prayers = mockStatic(Rs2Prayer.class)) {
            microbot.when(Microbot::getRs2TileItemCache).thenReturn(cache);
            inventory.when(() -> Rs2Inventory.hasItem(99)).thenReturn(true);
            invokeLoot(script);
            verify(loot).pickup();
            assertFalse(((GrotesqueGuardiansInventory) getField(script, "inventorySpace")).isRequested());
            inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), never());
        }
    }

    @Test
    public void unresolvableFullInventorySkipsLootWithoutDroppingGear() throws Exception {
        GrotesqueGuardiansScript script = lootScript();
        Rs2TileItemModel loot = mock(Rs2TileItemModel.class);
        when(loot.getId()).thenReturn(99);
        Rs2TileItemCache cache = lootCache(loot);
        Rs2ItemModel weapon = item(1, "Sword", 1, "Wield", "Drop");
        try (MockedStatic<Rs2Inventory> inventory = inventory(weapon);
             MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
             MockedStatic<Rs2Prayer> prayers = mockStatic(Rs2Prayer.class)) {
            microbot.when(Microbot::getRs2TileItemCache).thenReturn(cache);
            invokeLoot(script);
            assertTrue(((Set<?>) getField(script, "skippedLootIds")).contains(99));
            verify(loot, never()).pickup();
            inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), never());
        }
    }

    private GrotesqueGuardiansScript lootScript() throws Exception {
        GrotesqueGuardiansScript script = new GrotesqueGuardiansScript();
        setField(script, "config", config);
        setField(script, "gameTick", 10);
        setField(script, "surgeUsedThisKill", true);
        return script;
    }

    private Rs2TileItemCache lootCache(Rs2TileItemModel loot) {
        Rs2TileItemCache cache = mock(Rs2TileItemCache.class, RETURNS_DEEP_STUBS);
        when(cache.query().where(any()).within(12).nearest()).thenReturn(loot);
        return cache;
    }

    private void invokeLoot(GrotesqueGuardiansScript script) throws Exception {
        Method method = GrotesqueGuardiansScript.class.getDeclaredMethod("loot");
        method.setAccessible(true);
        method.invoke(script);
    }

    private GrotesqueGuardiansInventory.Result step(int tick) {
        return recovery.step(config, tick, true, Set.of(), id -> 10);
    }

    private MockedStatic<Rs2Inventory> inventory(Rs2ItemModel... items) {
        MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
        inventory.when(Rs2Inventory::all).thenReturn(List.of(items));
        inventory.when(Rs2Inventory::emptySlotCount).thenReturn(0);
        return inventory;
    }

    private Rs2ItemModel item(int id, String name, int quantity, String... actions) {
        Rs2ItemModel item = mock(Rs2ItemModel.class);
        when(item.getId()).thenReturn(id);
        when(item.getSlot()).thenReturn(id);
        when(item.getName()).thenReturn(name);
        when(item.getQuantity()).thenReturn(quantity);
        when(item.getInventoryActions()).thenReturn(actions);
        when(item.isTradeable()).thenReturn(true);
        when(item.isFood()).thenReturn(List.of(actions).contains("Eat"));
        return item;
    }

    private Object getField(GrotesqueGuardiansScript script, String name) throws Exception {
        Field field = GrotesqueGuardiansScript.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(script);
    }

    private void setField(GrotesqueGuardiansScript script, String name, Object value) throws Exception {
        Field field = GrotesqueGuardiansScript.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(script, value);
    }
}

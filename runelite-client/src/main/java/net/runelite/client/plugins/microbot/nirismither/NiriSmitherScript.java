package net.runelite.client.plugins.microbot.nirismither;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

@Slf4j
public class NiriSmitherScript extends Script {
    
    @Getter
    private ScriptState state = ScriptState.IDLE;
    
    @Getter
    private int itemsSmithed = 0;
    
    @Getter
    private int tripsCompleted = 0;
    
    @Getter
    private long startTime = 0;
    
    private NiriSmitherConfig config;
    private int startingLevel;
    private int startingXp;
    
    private static final int SMITHING_INTERFACE_PARENT = 312;
    private static final String ANVIL_NAME = "Anvil";
    
    public enum ScriptState {
        IDLE,
        WALKING_TO_BANK,
        BANKING,
        WALKING_TO_ANVIL,
        SMITHING,
        ANIMATING
    }
    
    public boolean run(NiriSmitherConfig config) {
        this.config = config;
        this.startTime = System.currentTimeMillis();
        this.startingLevel = Rs2Player.getRealSkillLevel(Skill.SMITHING);
        this.startingXp = Microbot.getClient().getSkillExperience(Skill.SMITHING);
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                
                // Check stop conditions
                if (shouldStop()) {
                    log.info("Stopping: reached configured limit");
                    shutdown();
                    return;
                }
                
                // Main logic
                if (Rs2Player.isAnimating()) {
                    state = ScriptState.ANIMATING;
                    return;
                }
                
                // Check if we need to drink stamina potion
                if (config.useStaminaPotions() && !Rs2Player.hasStaminaActive()) {
                    if (Rs2Player.getRunEnergy() < config.minStaminaEnergy()) {
                        if (drinkStaminaPotion()) {
                            sleep(600, 1200);
                        }
                    }
                }
                
                // Check if smithing interface is open
                if (isSmithingInterfaceOpen()) {
                    selectItemToSmith();
                    return;
                }
                
                // Determine what to do based on inventory
                boolean hasBars = hasBarsInInventory();
                boolean nearAnvil = isNearAnvil();
                boolean nearBank = isNearBank();
                
                log.info("State check - HasBars: {}, NearAnvil: {}, NearBank: {}, BankOpen: {}", 
                    hasBars, nearAnvil, nearBank, Rs2Bank.isOpen());
                
                if (hasBars) {
                    // We have bars, go smith them
                    if (nearAnvil) {
                        smithItems();
                    } else {
                        walkToAnvil();
                    }
                } else {
                    // No bars, go bank
                    if (Rs2Bank.isOpen()) {
                        bankBars();
                    } else if (nearBank) {
                        openBank();
                    } else {
                        walkToBank();
                    }
                }
                
            } catch (Exception ex) {
                // Handle client thread interruption gracefully
                if (ex instanceof RuntimeException && ex.getMessage() != null && 
                    ex.getMessage().contains("Interrupted waiting for client thread")) {
                    log.debug("Client thread busy, will retry on next iteration");
                    sleep(300);
                } else {
                    log.error("Error in smither script", ex);
                }
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    private boolean shouldStop() {
        // Check level condition
        if (config.stopAfterLevel() > 0) {
            int currentLevel = Rs2Player.getRealSkillLevel(Skill.SMITHING);
            if (currentLevel >= config.stopAfterLevel()) {
                return true;
            }
        }
        
        // Check items smithed condition
        if (config.stopAfterItems() > 0 && itemsSmithed >= config.stopAfterItems()) {
            return true;
        }
        
        return false;
    }
    
    private boolean hasBarsInInventory() {
        SmithableItem item = config.itemToSmith();
        int barCount = Rs2Inventory.count(item.getBarName());
        return barCount >= 3;
    }
    
    private boolean isNearAnvil() {
        AnvilLocation location = config.anvilLocation();
        return Rs2Player.getWorldLocation().distanceTo(location.getAnvilLocation()) < 15;
    }
    
    private boolean isNearBank() {
        AnvilLocation location = config.anvilLocation();
        return Rs2Player.getWorldLocation().distanceTo(location.getBankLocation()) < 15;
    }
    
    private void walkToAnvil() {
        state = ScriptState.WALKING_TO_ANVIL;
        AnvilLocation location = config.anvilLocation();
        log.info("Walking to anvil at {} (location: {})", location.getName(), location.getAnvilLocation());
        
        if (Rs2Walker.walkTo(location.getAnvilLocation(), 3)) {
            sleepUntil(() -> isNearAnvil(), 15000);
        }
    }
    
    private void walkToBank() {
        state = ScriptState.WALKING_TO_BANK;
        AnvilLocation location = config.anvilLocation();
        log.info("Walking to bank at {}", location.getName());
        Rs2Walker.walkTo(location.getBankLocation(), 5);
        sleepUntil(() -> isNearBank() || Rs2Player.isAnimating(), 10000);
    }
    
    private void smithItems() {
        state = ScriptState.SMITHING;
        
        // Find nearest anvil
        Rs2TileObjectModel anvil = Microbot.getRs2TileObjectCache().query()
                .withName(ANVIL_NAME)
                .nearest();
        
        if (anvil == null) {
            log.warn("Could not find anvil");
            return;
        }
        
        log.info("Clicking anvil");
        if (anvil.click("Smith")) {
            if (sleepUntil(this::isSmithingInterfaceOpen, 5000)) {
                log.info("Smithing interface opened");
            } else {
                log.warn("Smithing interface did not open");
            }
        }
    }
    
    private boolean isSmithingInterfaceOpen() {
        return Rs2Widget.isSmithingWidgetOpen();
    }
    
    private void selectItemToSmith() {
        SmithableItem item = config.itemToSmith();
        
        // Use Rs2Widget.clickWidget with the varbit parameter for quantity selection
        // First, try to find and click the item widget
        if (Rs2Widget.clickWidget(item.getWidgetChildId())) {
            log.info("Selected {} to smith", item.getItemName());
            
            // Wait a moment for the selection to register
            sleep(300, 600);
            
            // Now click to smith all (spacebar or click again on the selected item)
            // The smithing interface usually requires clicking the item again or pressing space
            Rs2Widget.clickWidget(item.getWidgetChildId());
            
            int barsInInventory = Rs2Inventory.count(item.getBarName());
            int itemsToMake = barsInInventory / item.getBarsRequired();
            
            sleepUntil(() -> !hasBarsInInventory() || Rs2Player.isAnimating(), 60000);
            
            itemsSmithed += itemsToMake;
            tripsCompleted++;
            
            sleep(1200, 1800);
        } else {
            log.warn("Failed to click smithing widget for {}", item.getItemName());
        }
    }
    
    private void openBank() {
        state = ScriptState.BANKING;
        log.info("Opening bank");
        
        if (Rs2Bank.openBank()) {
            sleepUntil(Rs2Bank::isOpen, 5000);
        }
    }
    
    private void bankBars() {
        state = ScriptState.BANKING;
        SmithableItem item = config.itemToSmith();
        
        // Check if we have bars in bank
        if (!Rs2Bank.hasItem(item.getBarName())) {
            if (config.stopWhenOutOfBars()) {
                log.info("Out of {} in bank, stopping", item.getBarName());
                shutdown();
                return;
            }
        }
        
        // Deposit all items first
        if (Rs2Inventory.hasItem(item.getItemName())) {
            Rs2Bank.depositAll(item.getItemName());
            sleep(600, 900);
        }
        
        Rs2Bank.depositAllExcept(item.getBarName());
        sleep(300, 600);
        
        // Withdraw bars (always withdraw max we can carry)
        int barsNeeded = 28; // Fill inventory with bars
        
        if (Rs2Bank.withdrawAll(item.getBarName())) {
            log.info("Withdrew {} bars", item.getBarName());
            sleepUntil(() -> Rs2Inventory.hasItem(item.getBarName()), 3000);
            sleep(600, 900);
        }
        
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
    }
    
    private boolean drinkStaminaPotion() {
        if (Rs2Inventory.hasItem("Stamina potion(1)", "Stamina potion(2)", 
                "Stamina potion(3)", "Stamina potion(4)")) {
            Rs2Inventory.interact("Stamina potion", "Drink");
            log.info("Drinking stamina potion");
            return true;
        }
        return false;
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
        log.info("Niri Smither stopped. Items smithed: {}, Trips: {}", itemsSmithed, tripsCompleted);
    }
}

package net.runelite.client.plugins.microbot.nirismelter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NiriSmelterScript extends Script {
    
    @Inject
    private Rs2TileObjectCache tileObjectCache;
    
    @Getter
    private ScriptState state = ScriptState.IDLE;
    
    @Getter
    private int oresSmelted = 0;
    
    @Getter
    private int tripsCompleted = 0;
    
    @Getter
    private long startTime = 0;
    
    private NiriSmelterConfig config;
    private int startingLevel;
    private int startingXp;
    
    public boolean run(NiriSmelterConfig config) {
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
                if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
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
                
                // Determine what to do based on inventory
                if (hasOresInInventory()) {
                    // We have ores, go smelt them
                    if (isNearFurnace()) {
                        smeltOres();
                    } else {
                        walkToFurnace();
                    }
                } else {
                    // No ores, go bank
                    if (Rs2Bank.isOpen()) {
                        bankOres();
                    } else if (isNearBank()) {
                        openBank();
                    } else {
                        walkToBank();
                    }
                }
                
            } catch (Exception ex) {
                log.error("Error in smelter script", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    private boolean shouldStop() {
        if (config.stopAfterLevel() > 0 && Rs2Player.getRealSkillLevel(Skill.SMITHING) >= config.stopAfterLevel()) {
            return true;
        }
        if (config.stopAfterOres() > 0 && oresSmelted >= config.stopAfterOres()) {
            return true;
        }
        return false;
    }
    
    private boolean hasOresInInventory() {
        OreType oreType = config.oreType();
        for (String ore : oreType.getRequiredOres()) {
            if (Rs2Inventory.hasItem(ore)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasBarsInInventory() {
        return Rs2Inventory.hasItem(config.oreType().getBarName());
    }
    
    private boolean isNearFurnace() {
        return Rs2Player.getWorldLocation().distanceTo(config.furnaceLocation().getLocation()) < 15;
    }
    
    private boolean isNearBank() {
        return Rs2Player.getWorldLocation().distanceTo(config.bankLocation().getLocation()) < 15;
    }
    
    private void walkToFurnace() {
        state = ScriptState.WALKING_TO_FURNACE;
        log.info("Walking to furnace: {}", config.furnaceLocation().getName());
        Rs2Walker.walkTo(config.furnaceLocation().getLocation(), 3);
        sleepUntil(() -> isNearFurnace() || !Rs2Player.isMoving(), 30000);
    }
    
    private void walkToBank() {
        state = ScriptState.WALKING_TO_BANK;
        log.info("Walking to bank: {}", config.bankLocation().getName());
        Rs2Walker.walkTo(config.bankLocation().getLocation(), 3);
        sleepUntil(() -> isNearBank() || !Rs2Player.isMoving(), 30000);
    }
    
    private void openBank() {
        state = ScriptState.OPENING_BANK;
        log.info("Opening bank");
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen, 5000);
    }
    
    private void bankOres() {
        state = ScriptState.BANKING;
        
        // Deposit bars if we have any
        if (hasBarsInInventory()) {
            Rs2Bank.depositAll(config.oreType().getBarName());
            sleepUntil(() -> !hasBarsInInventory(), 2000);
        }
        
        // Deposit everything
        Rs2Bank.depositAll();
        sleep(300, 600);
        
        // Check if we have required ores in bank
        OreType oreType = config.oreType();
        boolean hasOres = true;
        
        for (String ore : oreType.getRequiredOres()) {
            if (!Rs2Bank.hasItem(ore)) {
                log.warn("Out of {}, stopping script", ore);
                hasOres = false;
                shutdown();
                return;
            }
        }
        
        if (!hasOres) {
            return;
        }
        
        // Withdraw ores
        log.info("Withdrawing ores for {}", oreType.name());
        
        // For bronze, we need equal amounts of copper and tin
        if (oreType == OreType.BRONZE) {
            Rs2Bank.withdrawAll("Copper ore");
            sleepUntil(() -> Rs2Inventory.hasItem("Copper ore"), 2000);
            
            int copperCount = Rs2Inventory.count("Copper ore");
            Rs2Bank.withdrawX("Tin ore", copperCount);
            sleepUntil(() -> Rs2Inventory.count("Tin ore") > 0, 2000);
        }
        // For ores that need coal
        else if (oreType.requiresCoal()) {
            // Withdraw primary ore first to see how much we can smelt
            Rs2Bank.withdrawX(oreType.getPrimaryOre(), 9);
            sleepUntil(() -> Rs2Inventory.hasItem(oreType.getPrimaryOre()), 2000);
            
            int primaryOreCount = Rs2Inventory.count(oreType.getPrimaryOre());
            int coalNeeded = (oreType.getRequiredOres().length - 1) * primaryOreCount;
            
            Rs2Bank.withdrawX("Coal", coalNeeded);
            sleepUntil(() -> Rs2Inventory.count("Coal") > 0, 2000);
        }
        // For simple ores (iron, silver, gold)
        else {
            Rs2Bank.withdrawAll(oreType.getPrimaryOre());
            sleepUntil(() -> Rs2Inventory.hasItem(oreType.getPrimaryOre()), 2000);
        }
        
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        
        // Extra delay to ensure bank is fully closed
        sleep(300, 600);
        
        tripsCompleted++;
        log.info("Trip {} complete, heading to furnace", tripsCompleted);
        sleep(config.minBreakDelay(), config.maxBreakDelay());
    }
    
    private void smeltOres() {
        state = ScriptState.SMELTING;
        
        // Check for nearby players if configured
        if (config.hopOnPlayerNearby()) {
            // Could implement player detection here
        }
        
        // Find nearest furnace
        Rs2TileObjectModel furnace = tileObjectCache.query()
                .withName("Furnace")
                .within(15)
                .nearest();
        
        if (furnace == null) {
            log.warn("No furnace found nearby");
            return;
        }
        
        log.info("Smelting {} ores", config.oreType().getPrimaryOre());
        
        // Count ores and current bars before smelting
        int oresBeforeSmelting = countOresInInventory();
        int barsBeforeSmelting = Rs2Inventory.count(config.oreType().getBarName());
        
        // Click furnace
        furnace.click("Smelt");
        sleepUntil(() -> isSmelterInterfaceOpen(), 5000);
        
        if (isSmelterInterfaceOpen()) {
            // Select the bar to smelt
            selectBar();
            sleep(600, 900);
            
            // Wait for smelting animation to start
            sleepUntil(Rs2Player::isAnimating, 3000);
            
            // Wait for all bars to be created - check that inventory changed
            sleepUntil(() -> {
                int currentBars = Rs2Inventory.count(config.oreType().getBarName());
                int currentOres = countOresInInventory();
                // Smelting complete when we have more bars or no ores left
                return currentBars > barsBeforeSmelting || currentOres == 0;
            }, 60000);
            
            // Wait for animation to fully complete
            sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
            sleep(600, 1200);
            
            // Count bars created
            int barsAfterSmelting = Rs2Inventory.count(config.oreType().getBarName());
            int barsCreated = barsAfterSmelting - barsBeforeSmelting;
            oresSmelted += barsCreated;
            
            log.info("Smelted {} bars (total: {})", barsCreated, oresSmelted);
        } else {
            log.warn("Smelting interface did not open");
        }
        
        sleep(config.minBreakDelay(), config.maxBreakDelay());
    }
    
    private int countOresInInventory() {
        int count = 0;
        for (String ore : config.oreType().getRequiredOres()) {
            count += Rs2Inventory.count(ore);
        }
        return count;
    }
    
    private boolean isSmelterInterfaceOpen() {
        // Check if smelting interface is open (widget 270 is the smelting interface)
        return Rs2Widget.hasWidget("What would you like to smelt?");
    }
    
    private void selectBar() {
        // Click on the appropriate bar based on ore type
        OreType oreType = config.oreType();
        
        log.info("Selecting bar type: {}", oreType.getBarName());
        
        // Try to click the bar widget by name
        if (Rs2Widget.clickWidget(oreType.getBarName())) {
            log.info("Clicked bar widget by name");
            sleep(100, 300);
            return;
        }
        
        // Try clicking "Smelt All" text if available
        if (Rs2Widget.clickWidget("Smelt All")) {
            log.info("Clicked Smelt All widget");
            sleep(100, 300);
            return;
        }
        
        // Fallback: press space to smelt all
        log.info("Using space bar to smelt all");
        Microbot.getMouse().click();
        sleep(100, 300);
    }
    
    private boolean drinkStaminaPotion() {
        String[] staminaPotions = {"Stamina potion(4)", "Stamina potion(3)", "Stamina potion(2)", "Stamina potion(1)"};
        
        for (String potion : staminaPotions) {
            if (Rs2Inventory.hasItem(potion)) {
                log.info("Drinking stamina potion");
                Rs2Inventory.interact(potion, "Drink");
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
        state = ScriptState.STOPPED;
    }
    
    public enum ScriptState {
        IDLE,
        WALKING_TO_BANK,
        OPENING_BANK,
        BANKING,
        WALKING_TO_FURNACE,
        SMELTING,
        ANIMATING,
        STOPPED
    }
}

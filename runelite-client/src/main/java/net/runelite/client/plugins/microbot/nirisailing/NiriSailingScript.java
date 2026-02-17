package net.runelite.client.plugins.microbot.nirisailing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

@Slf4j
public class NiriSailingScript extends Script {
	
	@Getter
	private SailingState state = SailingState.INITIALIZING;
	
	@Getter
	private int tripsCompleted = 0;
	
	@Getter
	private int itemsCollected = 0;
	
	@Getter
	private long startTime = 0;
	
	private NiriSailingConfig config;
	
	public enum SailingState {
		INITIALIZING,
		WALKING_TO_PORT,
		BOARDING_SHIP,
		SAILING,
		COLLECTING_REWARDS,
		BANKING,
		STOPPED
	}
	
	public boolean run(NiriSailingConfig config) {
		this.config = config;
		this.startTime = System.currentTimeMillis();
		
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try {
				if (!Microbot.isLoggedIn() || !super.run()) {
					state = SailingState.STOPPED;
					return;
				}
				
				// Check stop conditions
				if (shouldStop()) {
					log.info("Stop condition met, shutting down");
					shutdown();
					return;
				}
				
				// Handle stamina potions
				if (config.useStaminaPotions() && Rs2Player.getRunEnergy() < config.minStaminaEnergy()) {
					useStaminaPotion();
				}
				
				// Handle food
				if (config.eatFood() && shouldEat()) {
					eatFood();
				}
				
				// Main state machine
				switch (state) {
					case INITIALIZING:
						handleInitializing();
						break;
					case WALKING_TO_PORT:
						handleWalkingToPort();
						break;
					case BOARDING_SHIP:
						handleBoardingShip();
						break;
					case SAILING:
						handleSailing();
						break;
					case COLLECTING_REWARDS:
						handleCollectingRewards();
						break;
					case BANKING:
						handleBanking();
						break;
					case STOPPED:
						break;
				}
				
			} catch (Exception e) {
				log.error("Error in sailing loop", e);
				state = SailingState.STOPPED;
			}
		}, 0, 600, TimeUnit.MILLISECONDS);
		
		return true;
	}
	
	private boolean shouldStop() {
		// Check trip limit
		if (config.stopAfterTrips() > 0 && tripsCompleted >= config.stopAfterTrips()) {
			return true;
		}
		
		// Check level limit (using Fishing as placeholder)
		if (config.stopAfterLevel() > 0) {
			int currentLevel = Rs2Player.getRealSkillLevel(Skill.FISHING);
			if (currentLevel >= config.stopAfterLevel()) {
				return true;
			}
		}
		
		// Check time limit
		if (config.stopAfterMinutes() > 0) {
			long minutesRun = (System.currentTimeMillis() - startTime) / 60000;
			if (minutesRun >= config.stopAfterMinutes()) {
				return true;
			}
		}
		
		return false;
	}
	
	private void handleInitializing() {
		log.info("Initializing sailing script for activity: {}", config.sailingActivity());
		state = SailingState.WALKING_TO_PORT;
	}
	
	private void handleWalkingToPort() {
		PortLocation port = config.portLocation();
		
		if (Rs2Player.getWorldLocation().distanceTo(port.getLocation()) < 10) {
			state = SailingState.BOARDING_SHIP;
			return;
		}
		
		if (!Rs2Player.isMoving()) {
			log.info("Walking to port: {}", port.getName());
			Rs2Walker.walkTo(port.getLocation());
			sleepUntil(() -> Rs2Player.isMoving() || 
				Rs2Player.getWorldLocation().distanceTo(port.getLocation()) < 10, 5000);
		}
	}
	
	private void handleBoardingShip() {
		log.info("Boarding ship for activity: {}", config.sailingActivity());
		// TODO: Implement specific boarding logic based on activity type
		// For now, placeholder logic
		sleep(2000, 3000);
		state = SailingState.SAILING;
	}
	
	private void handleSailing() {
		log.info("Sailing...");
		// TODO: Implement sailing minigame logic based on activity type
		// This would include activity-specific actions
		
		// Placeholder: simulate sailing time
		sleep(30000, 35000);
		state = SailingState.COLLECTING_REWARDS;
	}
	
	private void handleCollectingRewards() {
		log.info("Collecting rewards");
		// TODO: Implement reward collection logic
		
		// Placeholder logic
		itemsCollected += 10; // Simulate collecting items
		tripsCompleted++;
		
		if (Rs2Inventory.isFull()) {
			state = SailingState.BANKING;
		} else {
			state = SailingState.BOARDING_SHIP;
		}
	}
	
	private void handleBanking() {
		if (!Rs2Bank.isOpen()) {
			Rs2Bank.openBank();
			sleepUntil(Rs2Bank::isOpen, 5000);
			return;
		}
		
		// Deposit all except food and stamina potions
		Rs2Bank.depositAll(item -> {
			String name = item.getName().toLowerCase();
			return !name.contains("stamina") && !name.contains("food") && 
				   !name.contains("shark") && !name.contains("lobster");
		});
		
		sleep(1000, 1500);
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
		
		state = SailingState.WALKING_TO_PORT;
	}
	
	private void useStaminaPotion() {
		if (Rs2Inventory.hasItem("Stamina potion")) {
			Rs2Inventory.interact("Stamina potion", "Drink");
			sleep(600, 1000);
		}
	}
	
	private boolean shouldEat() {
		double healthPercent = Rs2Player.getHealthPercentage();
		return healthPercent < config.minHealthPercent();
	}
	
	private void eatFood() {
		// Try to eat common food items
		String[] foodItems = {"Shark", "Lobster", "Swordfish", "Tuna", "Monkfish"};
		for (String food : foodItems) {
			if (Rs2Inventory.hasItem(food)) {
				Rs2Inventory.interact(food, "Eat");
				sleep(600, 1000);
				return;
			}
		}
	}
	
	@Override
	public void shutdown() {
		super.shutdown();
		state = SailingState.STOPPED;
		log.info("Sailing script stopped. Trips completed: {}, Items collected: {}", 
			tripsCompleted, itemsCollected);
	}
}

package net.runelite.client.plugins.microbot.nirigrotesqueguardiansbanking;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class BankingGuardiansStartupTest {
    private static BankingGuardiansScript script(boolean onRoof, boolean needsSupplies, boolean enabled) throws Exception {
        BankingGuardiansScript script = new BankingGuardiansScript();
        set(script, "config", new BankingGuardiansConfig() {
            @Override public boolean bankingEnabled() { return enabled; }
        });
        set(script, "bellPresent", onRoof);
        set(script, "bankNeeded", needsSupplies);
        set(script, "finisherPresent", true);
        set(script, "foodPresent", true);
        set(script, "combatGearPresent", true);
        return script;
    }
    private static void set(BankingGuardiansScript script, String field, Object value) throws Exception {
        Field target = BankingGuardiansScript.class.getDeclaredField(field);
        target.setAccessible(true); target.set(script, value);
    }
    private static BankingGuardiansState next(BankingGuardiansScript script, BankingGuardiansState from) {
        for (Transition<BankingGuardiansState> transition : script.defineTransitions())
            if (transition.from() == from && transition.condition().getAsBoolean()) return transition.to();
        return from;
    }
    @Test public void startsOutsideRoofWithSupplies() throws Exception {
        assertEquals(BankingGuardiansState.BANKING, next(script(false, false, true), BankingGuardiansState.IDLE));
    }
    @Test public void startsOutsideRoofWithoutSupplies() throws Exception {
        assertEquals(BankingGuardiansState.BANKING, next(script(false, true, true), BankingGuardiansState.IDLE));
    }
    @Test public void banksOnRoofWhenLow() throws Exception {
        assertEquals(BankingGuardiansState.BANKING, next(script(true, true, true), BankingGuardiansState.IDLE));
    }
    @Test public void healthyRoofStartsFight() throws Exception {
        assertEquals(BankingGuardiansState.STARTING, next(script(true, false, true), BankingGuardiansState.IDLE));
    }
    @Test public void disabledBankingDoesNotTravel() throws Exception {
        assertEquals(BankingGuardiansState.IDLE, next(script(false, true, false), BankingGuardiansState.IDLE));
    }
    @Test public void activeBossTakesPriority() throws Exception {
        BankingGuardiansScript script = script(true, true, true);
        set(script, "phase1Present", true); set(script, "anyBossPresent", true);
        assertEquals(BankingGuardiansState.DAWN_PHASE_1, next(script, BankingGuardiansState.IDLE));
    }
    @Test public void finishesLootBeforeBanking() throws Exception {
        BankingGuardiansScript script = script(true, true, true);
        assertEquals(BankingGuardiansState.LOOTING, next(script, BankingGuardiansState.LOOTING));
        set(script, "lootFinished", true);
        assertEquals(BankingGuardiansState.BANKING, next(script, BankingGuardiansState.LOOTING));
    }
    @Test public void outsideStartRoutesToBankWhenSuppliesMissing() {
        BankingGuardiansBanking banking = new BankingGuardiansBanking();
        banking.step(new BankingGuardiansConfig() {}, 10, new WorldPoint(3163,3486,0), false, false);
        assertEquals("TO_BANK", banking.stageName());
    }
    @Test public void outsideStartRoutesToTowerWhenSupplied() {
        BankingGuardiansBanking banking = new BankingGuardiansBanking();
        banking.step(new BankingGuardiansConfig() {}, 10, new WorldPoint(3163,3486,0), false, true);
        assertEquals("RETURN", banking.stageName());
    }
    @Test public void selectsAvailableRoofActionsAndRejectsUnknownActions() {
        assertEquals("Exit", BankingGuardiansBanking.chooseAction(new String[]{null,"Exit"}, "Climb-down","Exit"));
        assertEquals("Enter", BankingGuardiansBanking.chooseAction(new String[]{"Enter",null}, "Climb-up","Enter"));
        assertNull(BankingGuardiansBanking.chooseAction(new String[]{"Examine"}, "Climb-up","Enter"));
    }
    @Test public void newBankingPluginDefaultsToEnabledBanking() {
        assertTrue(new BankingGuardiansConfig() {}.bankingEnabled());
    }
}

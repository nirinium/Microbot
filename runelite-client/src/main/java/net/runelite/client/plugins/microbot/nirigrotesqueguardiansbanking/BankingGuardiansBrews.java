package net.runelite.client.plugins.microbot.nirigrotesqueguardiansbanking;

/** Non-blocking, inventory-confirmed brew -> restore -> boost sequence. */
final class BankingGuardiansBrews {
    enum Action { NONE, BREW, RESTORE, COMBAT }

    private enum Stage { IDLE, HEALING, RESTORING, BOOSTING }

    private Stage stage = Stage.IDLE;
    private Action pending = Action.NONE;
    private int dosesBefore;
    private int requestedTick;
    private int brewSips;

    static int healingPerSip(int baseHitpoints) {
        return baseHitpoints * 15 / 100 + 2;
    }

    static boolean canUseFullSip(int hitpoints, int baseHitpoints) {
        return hitpoints + healingPerSip(baseHitpoints)
                <= baseHitpoints + healingPerSip(baseHitpoints);
    }

    void reset() {
        stage = Stage.IDLE;
        pending = Action.NONE;
        brewSips = 0;
    }

    boolean active() {
        return stage != Stage.IDLE || pending != Action.NONE;
    }

    void requested(Action action, int doses, int tick) {
        pending = action;
        dosesBefore = doses;
        requestedTick = tick;
    }

    void observe(int remainingDoses, int tick) {
        if (pending == Action.NONE) return;
        if (remainingDoses < dosesBefore) {
            if (pending == Action.BREW) {
                brewSips++;
                stage = Stage.HEALING;
            } else if (pending == Action.RESTORE) {
                stage = Stage.BOOSTING;
            } else {
                stage = Stage.IDLE;
                brewSips = 0;
            }
            pending = Action.NONE;
        } else if (tick - requestedTick >= 4) {
            // A dispatched click is not a consumed dose. Retry without advancing the batch.
            pending = Action.NONE;
        }
    }

    Action pendingAction() {
        return pending;
    }

    Action next(int hp, int baseHp, boolean lowHp, boolean hasBrew, boolean hasRestore,
                boolean statsDrained, boolean useCombat, boolean hasCombat) {
        if (pending != Action.NONE) return Action.NONE;
        if (stage == Stage.IDLE) {
            if (!lowHp || !hasBrew) return Action.NONE;
            stage = Stage.HEALING;
            brewSips = 0;
        }
        if (stage == Stage.HEALING) {
            if (hasBrew && brewSips < 3 && canUseFullSip(hp, baseHp)) return Action.BREW;
            stage = Stage.RESTORING;
        }
        if (stage == Stage.RESTORING) {
            if (hasRestore) return Action.RESTORE;
            // Do not waste a combat dose trying to replace a missing restore.
            if (statsDrained) return Action.NONE;
            stage = Stage.BOOSTING;
        }
        if (stage == Stage.BOOSTING) {
            if (statsDrained) {
                stage = Stage.RESTORING;
                return hasRestore ? Action.RESTORE : Action.NONE;
            }
            // If damage outpaced the last batch, heal again before spending a combat dose.
            if (lowHp && hasBrew) {
                stage = Stage.HEALING;
                brewSips = 0;
                return Action.BREW;
            }
            if (useCombat && hasCombat) return Action.COMBAT;
            reset();
        }
        return Action.NONE;
    }

    static int doses(String name, String potion) {
        if (name == null || !name.startsWith(potion + "(")) return 0;
        int index = potion.length() + 1;
        if (name.length() != index + 2 || name.charAt(index + 1) != ')') return 0;
        int doses = name.charAt(index) - '0';
        return doses >= 1 && doses <= 4 ? doses : 0;
    }
}



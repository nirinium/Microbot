package net.runelite.client.plugins.microbot.nirigrotesqueguardians;

/**
 * Maximum gear bracket to consider. Every bracket falls back through all lower
 * brackets so the script can use the best eligible weapon currently available.
 */
public enum GrotesqueGuardiansGearTier {
    BEST_AVAILABLE("Best available", 0),
    HIGH_END("High end", 1),
    MID_GAME("Mid game", 2),
    BUDGET("Budget", 3);

    private final String label;
    private final int firstBracket;

    GrotesqueGuardiansGearTier(String label, int firstBracket) {
        this.label = label;
        this.firstBracket = firstBracket;
    }

    int firstBracket() {
        return firstBracket;
    }

    @Override
    public String toString() {
        return label;
    }
}

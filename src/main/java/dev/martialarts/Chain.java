package dev.martialarts;

/**
 * One fighter's string of strikes, ticked once per game tick (pure Java, no Minecraft). A press is buffered and starts
 * its move as soon as the current one allows: when it ends, or in the last {@link #CANCEL} ticks of its recovery. A
 * press within {@link #LINK} ticks of a move ending continues the chain; a chain with no next move starts over from that
 * press. Hits landed within {@link #COMBO_GAP} ticks of each other count as one combo, and each hit in it is scaled down.
 */
public final class Chain {
    /** Ticks a press is remembered while it waits for the current move. */
    public static final int BUFFER = 8;
    /** The last ticks of a move's recovery the next move may cut. */
    public static final int CANCEL = 3;
    /** Ticks after a move ends that a press still continues its chain. */
    public static final int LINK = 8;
    /** Ticks between hits that keep a combo going. */
    public static final int COMBO_GAP = 30;

    /** What one tick did: the move that started (or null) and the move whose first active tick this is (or null). */
    public record Step(Move started, Move strike) {}

    private Move move;
    private Move.Input queued;
    private String path = "";
    private int t, idle, queuedAge, hits;
    private long clock, lastHit = Long.MIN_VALUE / 2;

    public void press(Move.Input in) {
        queued = in;
        queuedAge = 0;
    }

    public Step tick(boolean air) {
        clock++;
        Move strike = null, started = null;
        if (move != null) {
            if (++t == move.startup()) strike = move;
            if (t >= move.total()) { move = null; idle = 0; }
        }
        if (queued != null && ++queuedAge > BUFFER) queued = null;
        if (queued != null && (move == null || t >= move.total() - CANCEL)) {
            Move next = Move.find(path + queued.letter, air);
            if (next == null) next = Move.find(String.valueOf(queued.letter), air);
            queued = null;
            if (next != null) {
                move = started = next;
                path = next.chain();
                t = 0;
            }
        } else if (move == null && ++idle > LINK) path = "";
        return new Step(started, strike);
    }

    /** Counts a landed hit and returns the combo's length with it. */
    public int hit() {
        hits = clock - lastHit > COMBO_GAP ? 1 : hits + 1;
        lastHit = clock;
        return hits;
    }

    /** The current combo's length, 0 once it has dropped. */
    public int hits() { return clock - lastHit > COMBO_GAP ? 0 : hits; }

    /** Damage and hitstun for the n-th hit of a combo: 8% less per hit after the first, never under 40%. */
    public static float scale(int n) { return Math.max(.4F, 1 - .08F * (n - 1)); }

    public Move move() { return move; }
}

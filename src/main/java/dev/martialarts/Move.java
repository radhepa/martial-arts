package dev.martialarts;

import java.util.List;

/**
 * One strike: the inputs that lead to it (its chain, P for punch and K for kick, e.g. "PPK"), whether it is thrown in
 * the air, its frame data in ticks, and what a hit does. Push is horizontal speed away from the striker, lift is
 * vertical speed (a launcher lifts, an axe kick spikes down), lunge carries the striker forward as it starts.
 */
public record Move(String id, String chain, boolean air, int startup, int active, int recovery,
                   float damage, double push, double lift, double lunge, int hitstun, double reach) {

    public enum Input {
        PUNCH('P'), KICK('K');
        public final char letter;
        Input(char letter) { this.letter = letter; }
    }

    public int total() { return startup + active + recovery; }

    // Ground strings: a four-punch string ends in a launching uppercut; a kick at any point turns the string into its
    // kick ender. A knee (K then P) launches too. In the air, punches hold the target up and a kick spikes it down.
    public static final List<Move> ALL = List.of(
            //        id            chain   air    su ac rc  dmg  push lift  lunge stun reach
            new Move("jab",         "P",    false, 2, 2, 4, 2.0F, .15,  0,   .20, 10, 3.0),
            new Move("cross",       "PP",   false, 3, 2, 5, 2.5F, .20,  0,   .25, 10, 3.0),
            new Move("hook",        "PPP",  false, 3, 2, 6, 3.0F, .25,  0,   .20, 12, 3.0),
            new Move("uppercut",    "PPPP", false, 4, 3, 8, 4.0F, .10, .85,  .10, 20, 3.0),
            new Move("front_kick",  "K",    false, 4, 2, 7, 3.0F, .70, .10,  .30, 12, 3.5),
            new Move("low_kick",    "PK",   false, 3, 2, 6, 2.5F, .10,  0,   .20, 14, 3.5),
            new Move("roundhouse",  "PPK",  false, 5, 3, 8, 4.5F, .90, .30,  .25, 14, 3.5),
            new Move("spin_kick",   "PPPK", false, 6, 3, 10, 5.5F, 1.3, .35, .30, 16, 3.5),
            new Move("side_kick",   "KK",   false, 5, 3, 9, 4.5F, 1.1, .25,  .30, 14, 3.5),
            new Move("knee",        "KP",   false, 3, 2, 7, 3.0F, .10, .70,  .35, 18, 2.8),
            new Move("air_jab",     "P",    true,  2, 2, 4, 2.0F, .10, .45,   0,  10, 3.0),
            new Move("air_cross",   "PP",   true,  3, 2, 4, 2.5F, .10, .45,   0,  10, 3.0),
            new Move("air_hook",    "PPP",  true,  3, 2, 6, 3.0F, .30, .50,   0,  12, 3.0),
            new Move("axe_kick",    "K",    true,  5, 2, 8, 4.0F, .20, -1.2,  0,  12, 3.5));

    /** The move a chain of inputs reaches on the ground or in the air, or null if that chain has no move. */
    public static Move find(String chain, boolean air) {
        for (Move m : ALL) if (m.air == air && m.chain.equals(chain)) return m;
        return null;
    }

    public static Move byId(String id) {
        for (Move m : ALL) if (m.id.equals(id)) return m;
        return null;
    }
}

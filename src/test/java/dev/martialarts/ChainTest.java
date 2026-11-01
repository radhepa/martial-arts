package dev.martialarts;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.martialarts.Move.Input.KICK;
import static dev.martialarts.Move.Input.PUNCH;
import static org.junit.jupiter.api.Assertions.*;

class ChainTest {
    /** Presses the inputs every 6 ticks (a press replaces one still buffered) and returns the ids of the moves started on the ground. */
    private static List<String> mash(Move.Input... inputs) {
        var c = new Chain();
        var out = new ArrayList<String>();
        int i = 0;
        for (int tick = 0; tick < 200; tick++) {
            if (i < inputs.length && tick % 6 == 0) c.press(inputs[i++]);
            var s = c.tick(false);
            if (s.started() != null) out.add(s.started().id());
        }
        return out;
    }

    @Test void punchStringEndsInUppercut() {
        assertEquals(List.of("jab", "cross", "hook", "uppercut"), mash(PUNCH, PUNCH, PUNCH, PUNCH));
    }

    @Test void kickTurnsTheStringIntoItsEnder() {
        assertEquals(List.of("jab", "cross", "roundhouse"), mash(PUNCH, PUNCH, KICK));
        assertEquals(List.of("front_kick", "knee"), mash(KICK, PUNCH));
    }

    @Test void aChainWithNoNextMoveStartsOver() {
        assertEquals(List.of("jab", "low_kick", "front_kick"), mash(PUNCH, KICK, KICK));
    }

    @Test void pressBufferedDuringAMoveStartsInItsCancelWindow() {
        var c = new Chain();
        c.press(PUNCH);
        assertEquals("jab", c.tick(false).started().id());
        c.press(PUNCH);   // during the jab's startup: held, then cut into its recovery
        int startedAt = -1;
        for (int t = 1; t <= 10; t++) if (c.tick(false).started() != null) { startedAt = t; break; }
        assertEquals(Move.byId("jab").total() - Chain.CANCEL, startedAt);
        assertEquals("cross", c.move().id());
    }

    @Test void strikeLandsOnTheFirstActiveTick() {
        var c = new Chain();
        c.press(KICK);
        c.tick(false);
        int at = 0;
        for (int t = 1; t < 20; t++) if (c.tick(false).strike() != null) { at = t; break; }
        assertEquals(Move.byId("front_kick").startup(), at);
    }

    @Test void chainDropsAfterTheLinkWindow() {
        var c = new Chain();
        c.press(PUNCH);
        for (int t = 0; t < Move.byId("jab").total() + Chain.LINK + 2; t++) c.tick(false);
        c.press(PUNCH);
        assertEquals("jab", c.tick(false).started().id());
    }

    @Test void airUsesTheAirStrings() {
        var c = new Chain();
        c.press(KICK);
        assertEquals("axe_kick", c.tick(true).started().id());
    }

    @Test void comboCountsAndDrops() {
        var c = new Chain();
        assertEquals(1, c.hit());
        c.tick(false);
        assertEquals(2, c.hit());
        for (int t = 0; t <= Chain.COMBO_GAP; t++) c.tick(false);
        assertEquals(0, c.hits());
        assertEquals(1, c.hit());
    }

    @Test void scalingFloorsAtFortyPercent() {
        assertEquals(1F, Chain.scale(1));
        assertEquals(.4F, Chain.scale(50));
    }

    @Test void movesFitTheirCancelWindowAfterTheyStrike() {
        for (var m : Move.ALL) assertTrue(m.startup() >= 1 && m.total() - Chain.CANCEL >= m.startup() + m.active(), m.id());
    }
}

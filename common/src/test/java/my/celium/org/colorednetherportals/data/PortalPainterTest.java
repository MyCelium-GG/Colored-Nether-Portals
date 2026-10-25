package my.celium.org.colorednetherportals.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** {@link PortalPainter}: which color each block of a portal gets. */
class PortalPainterTest {
    private static final int RED = 0xFF0000;
    private static final int BLUE = 0x0000FF;

    @Test
    void vanillaPaintsNothing() {
        assertEquals(-1, PortalPainter.colorAt(PortalStyle.VANILLA, 0, 0, 2, 3, 0));
        assertEquals(0xFFFFFF, PortalPainter.baseColor(PortalStyle.VANILLA, 0));
    }

    @Test
    void solidIsTheSameEverywhere() {
        PortalStyle style = PortalStyle.solid(RED);
        for (int u = 0; u < 4; u++) {
            for (int v = 0; v < 5; v++) {
                assertEquals(RED, PortalPainter.colorAt(style, u, v, 4, 5, 1234));
            }
        }
    }

    @Test
    void gradientRunsBottomEdgeToTopEdge() {
        PortalStyle style = new PortalStyle(PortalStyle.Mode.GRADIENT, List.of(RED, BLUE), 2);
        assertEquals(RED, PortalPainter.colorAt(style, 0, 0, 2, 3, 0));
        assertEquals(BLUE, PortalPainter.colorAt(style, 1, 3, 2, 3, 0));
        assertEquals(Colors.lerp(RED, BLUE, 0.5f), PortalPainter.colorAt(style, 0, 1.5, 2, 3, 0));
        // Same height, same color: the gradient only depends on height.
        assertEquals(PortalPainter.colorAt(style, 0, 1.2, 2, 3, 0), PortalPainter.colorAt(style, 1.7, 1.2, 2, 3, 0));
    }

    @Test
    void cellsFadeSmoothlyInsideABlock() {
        PortalStyle style = new PortalStyle(PortalStyle.Mode.GRADIENT, List.of(RED, BLUE), 2);
        int[] cells = PortalPainter.cells(style, 0, 0, 1, 1, 0);
        assertEquals(PortalPainter.CELLS * PortalPainter.CELLS, cells.length);
        int n = PortalPainter.CELLS;
        // Bottom row is reddest, top row bluest, each row bluer than the one below.
        for (int row = 1; row < n; row++) {
            assertTrue((cells[row * n] & 0xFF) > (cells[(row - 1) * n] & 0xFF), "row " + row);
        }
        assertEquals(cells[0], cells[n - 1], "a row is one color");
    }

    @Test
    void swirlTintIsLighterThanTheColor() {
        int tint = PortalPainter.swirlTint(0x204080);
        assertTrue((tint >> 16 & 0xFF) > 0x20 && (tint >> 8 & 0xFF) > 0x40 && (tint & 0xFF) > 0x80);
    }

    @Test
    void rainbowVariesUpTheAndAcrossAndMoves() {
        PortalStyle style = new PortalStyle(PortalStyle.Mode.RAINBOW, List.of(RED), 2);
        int bottom = PortalPainter.colorAt(style, 0, 0, 3, 8, 0);
        assertNotEquals(bottom, PortalPainter.colorAt(style, 0, 4, 3, 8, 0));
        assertNotEquals(bottom, PortalPainter.colorAt(style, 2, 0, 3, 8, 0));
        assertNotEquals(bottom, PortalPainter.colorAt(style, 0, 0, 3, 8, 40));
    }

    @Test
    void cycleAndPulseAnimateTheWholePortal() {
        for (PortalStyle.Mode mode : List.of(PortalStyle.Mode.CYCLE, PortalStyle.Mode.PULSE)) {
            PortalStyle style = new PortalStyle(mode, List.of(RED, BLUE), 2);
            int now = PortalPainter.colorAt(style, 0, 0, 2, 3, 30);
            assertEquals(now, PortalPainter.colorAt(style, 1, 2, 2, 3, 30), mode + " is uniform");
            assertNotEquals(now, PortalPainter.colorAt(style, 0, 0, 2, 3, 70), mode + " moves");
        }
    }

    @Test
    void pulseStartsOnTheFirstColor() {
        PortalStyle style = new PortalStyle(PortalStyle.Mode.PULSE, List.of(RED, BLUE), 2);
        assertEquals(RED, PortalPainter.colorAt(style, 0, 0, 1, 1, 0));
        assertEquals(BLUE, PortalPainter.colorAt(style, 0, 0, 1, 1, PortalPainter.PERIOD[2] / 2.0));
    }
}

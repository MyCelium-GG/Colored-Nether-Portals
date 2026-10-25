package my.celium.org.colorednetherportals.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColorsTest {
    @Test
    void hexRoundTrips() {
        assertEquals("#3AB3DA", Colors.toHex(0x3AB3DA));
        assertEquals(0x3AB3DA, Colors.parseHex("#3ab3da"));
        assertEquals(0x3AB3DA, Colors.parseHex(" 3AB3DA "));
        assertEquals(0xFF0000, Colors.parseHex("#f00"));
        assertEquals(-1, Colors.parseHex("#12345"));
        assertEquals(-1, Colors.parseHex("zzzzzz"));
        assertEquals(-1, Colors.parseHex(null));
    }

    @Test
    void hsvCornersAndRoundTrip() {
        assertEquals(0xFF0000, Colors.hsv(0f, 1f, 1f));
        assertEquals(0x00FF00, Colors.hsv(1f / 3, 1f, 1f));
        assertEquals(0x0000FF, Colors.hsv(2f / 3, 1f, 1f));
        assertEquals(0xFFFFFF, Colors.hsv(0.5f, 0f, 1f));
        assertEquals(0x000000, Colors.hsv(0.5f, 1f, 0f));
        assertEquals(0xFF0000, Colors.hsv(1f, 1f, 1f), "hue wraps");
        for (Colors.Preset preset : Colors.PRESETS) {
            float[] hsv = Colors.toHsv(preset.rgb());
            int back = Colors.hsv(hsv[0], hsv[1], hsv[2]);
            for (int shift = 0; shift <= 16; shift += 8) {
                int a = (preset.rgb() >> shift) & 0xFF;
                int b = (back >> shift) & 0xFF;
                assertTrue(Math.abs(a - b) <= 1, preset.name() + " " + Colors.toHex(back));
            }
        }
    }

    @Test
    void lerpEnds() {
        assertEquals(0x000000, Colors.lerp(0x000000, 0xFFFFFF, 0f));
        assertEquals(0xFFFFFF, Colors.lerp(0x000000, 0xFFFFFF, 1f));
        assertEquals(0x808080, Colors.lerp(0x000000, 0xFFFFFF, 0.5f), "midpoint");
    }

    @Test
    void namesAndContrast() {
        assertEquals("Light Blue", Colors.name(0x3AB3DA));
        assertEquals("#123456", Colors.name(0x123456));
        assertTrue(Colors.isLight(0xF9FFFE));
        assertFalse(Colors.isLight(0x1D1D21));
        assertEquals(24, Colors.PRESETS.length);
    }
}

package my.celium.org.colorednetherportals.data;

import java.util.Locale;

/** RGB helpers: hex text, HSV, blending, and the preset palette. Colors are {@code 0xRRGGBB}. */
public final class Colors {
    private Colors() {
    }

    /** A named swatch in the color menu. */
    public record Preset(String name, int rgb) {
    }

    /**
     * The 16 dye colors, then brighter
     * extras glass can't make.
     */
    public static final Preset[] PRESETS = {
            new Preset("White", 0xF9FFFE), new Preset("Light Gray", 0x9D9D97), new Preset("Gray", 0x474F52),
            new Preset("Black", 0x1D1D21), new Preset("Brown", 0x835432), new Preset("Red", 0xB02E26),
            new Preset("Orange", 0xF9801D), new Preset("Yellow", 0xFED83D), new Preset("Lime", 0x80C71F),
            new Preset("Green", 0x5E7C16), new Preset("Cyan", 0x169C9C), new Preset("Light Blue", 0x3AB3DA),
            new Preset("Blue", 0x3C44AA), new Preset("Purple", 0x8932B8), new Preset("Magenta", 0xC74EBD),
            new Preset("Pink", 0xF38BAA),
            new Preset("Crimson", 0xFF1A3C), new Preset("Gold", 0xFFC400), new Preset("Mint", 0x5CFFB0),
            new Preset("Aqua", 0x00F0FF), new Preset("Sky", 0x7CC8FF), new Preset("Royal", 0x3D5AFE),
            new Preset("Violet", 0xB388FF), new Preset("Hot Pink", 0xFF2E9A),
    };

    /** The RGB of a dye by its id ({@code "light_blue"}); the first 16 presets are the dyes. White if unknown. */
    public static int forDye(String id) {
        for (int i = 0; i < 16; i++) {
            if (PRESETS[i].name().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').equals(id)) {
                return PRESETS[i].rgb();
            }
        }
        return 0xFFFFFF;
    }

    /** {@code "#RRGGBB"}. */
    public static String toHex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Parses {@code RRGGBB}, {@code #RRGGBB}, {@code RGB} or {@code #RGB}; -1 if it isn't one. */
    public static int parseHex(String text) {
        if (text == null) {
            return -1;
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Hue in [0,1), saturation and value in [0,1]. */
    public static int hsv(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        float s = clamp(saturation);
        float v = clamp(value);
        int sector = (int) Math.floor(h) % 6;
        float f = h - (float) Math.floor(h);
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return rgb(r, g, b);
    }

    /** {hue, saturation, value}, each in [0,1]. */
    public static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0;
        if (delta > 0) {
            if (max == r) {
                hue = ((g - b) / delta) % 6;
            } else if (max == g) {
                hue = (b - r) / delta + 2;
            } else {
                hue = (r - g) / delta + 4;
            }
            hue /= 6;
            if (hue < 0) {
                hue += 1;
            }
        }
        return new float[] {hue, max == 0 ? 0 : delta / max, max};
    }

    /** {@code a} at t=0, {@code b} at t=1. */
    public static int lerp(int a, int b, float t) {
        float k = clamp(t);
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return r << 16 | g << 8 | bl;
    }

    /** Readable text on top of {@code rgb}: black on light colors, white on dark ones. */
    public static boolean isLight(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r * 299 + g * 587 + b * 114 > 150_000;
    }

    /** The preset's name, or the hex code. */
    public static String name(int rgb) {
        for (Preset preset : PRESETS) {
            if (preset.rgb() == (rgb & 0xFFFFFF)) {
                return preset.name();
            }
        }
        return toHex(rgb);
    }

    private static int rgb(float r, float g, float b) {
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}

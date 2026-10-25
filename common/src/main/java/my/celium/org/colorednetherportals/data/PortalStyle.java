package my.celium.org.colorednetherportals.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How one portal looks. Colors are RGB ({@code 0xRRGGBB}).
 *
 * <ul>
 * <li>{@link Mode#VANILLA}: no override, stained glass decides.</li>
 * <li>{@link Mode#SOLID}: the first color.</li>
 * <li>{@link Mode#GRADIENT}: fades through all colors, the first at the bottom, the last at the top.</li>
 * <li>{@link Mode#RAINBOW}: rainbow stripes that flow up the portal.</li>
 * <li>{@link Mode#CYCLE}: the whole portal slowly runs through every hue.</li>
 * <li>{@link Mode#PULSE}: the whole portal fades through all colors and back.</li>
 * </ul>
 *
 * There is always at least one color and at most {@link #MAX_COLORS}; a mode
 * that needs more gets the last one repeated. Colors a mode doesn't use are
 * kept, so switching modes back and forth loses nothing. {@code speed}
 * (1 slow, 2 normal, 3 fast) only matters for the animated modes.
 */
public record PortalStyle(Mode mode, List<Integer> colors, int speed) {
    public static final int MIN_SPEED = 1;
    public static final int MAX_SPEED = 3;
    public static final int MAX_COLORS = 16;
    public static final PortalStyle VANILLA = new PortalStyle(Mode.VANILLA, 0xFFFFFF, 0xFFFFFF, 2);

    public enum Mode {
        VANILLA("Vanilla", false, 0, 0),
        SOLID("Solid", false, 1, 1),
        GRADIENT("Gradient", false, 2, MAX_COLORS),
        RAINBOW("Rainbow", true, 0, 0),
        CYCLE("Color cycle", true, 0, 0),
        PULSE("Pulse", true, 2, MAX_COLORS);

        public final String label;
        public final boolean animated;
        /** How many colors the mode needs at least. */
        public final int colors;
        /** How many colors the mode can use. */
        public final int maxColors;

        Mode(String label, boolean animated, int colors, int maxColors) {
            this.label = label;
            this.animated = animated;
            this.colors = colors;
            this.maxColors = maxColors;
        }

        public static Mode byName(String name) {
            if (name != null) {
                for (Mode mode : values()) {
                    if (mode.name().equalsIgnoreCase(name.trim())) {
                        return mode;
                    }
                }
            }
            return VANILLA;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public PortalStyle {
        mode = mode == null ? Mode.VANILLA : mode;
        List<Integer> clean = new ArrayList<>();
        if (colors != null) {
            for (Integer rgb : colors) {
                if (rgb != null && clean.size() < MAX_COLORS) {
                    clean.add(rgb & 0xFFFFFF);
                }
            }
        }
        if (clean.isEmpty()) {
            clean.add(0xFFFFFF);
        }
        while (clean.size() < mode.colors) {
            clean.add(clean.get(clean.size() - 1));
        }
        colors = List.copyOf(clean);
        speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    public PortalStyle(Mode mode, int color, int color2, int speed) {
        this(mode, List.of(color, color2), speed);
    }

    public static PortalStyle solid(int color) {
        return new PortalStyle(Mode.SOLID, List.of(color), 2);
    }

    public boolean isVanilla() {
        return mode == Mode.VANILLA;
    }

    public boolean animated() {
        return mode.animated;
    }

    /** The first color. */
    public int color() {
        return colors.get(0);
    }

    /** The second color, or the first when there is only one. */
    public int color2() {
        return colors.get(Math.min(1, colors.size() - 1));
    }

    /** The colors this mode actually draws with. */
    public List<Integer> used() {
        return colors.subList(0, Math.min(colors.size(), mode.maxColors));
    }

    /** Whether both draw the same portal, ignoring colors the mode doesn't use. */
    public boolean sameLook(PortalStyle other) {
        if (mode != other.mode) {
            return false;
        }
        return mode == Mode.VANILLA || used().equals(other.used()) && (!animated() || speed == other.speed);
    }

    public PortalStyle withMode(Mode mode) {
        return new PortalStyle(mode, colors, speed);
    }

    public PortalStyle withColors(List<Integer> colors) {
        return new PortalStyle(mode, colors, speed);
    }

    /** Replaces the color at {@code index}. */
    public PortalStyle withColor(int index, int rgb) {
        List<Integer> out = new ArrayList<>(colors);
        out.set(index, rgb);
        return withColors(out);
    }

    public PortalStyle withSpeed(int speed) {
        return new PortalStyle(mode, colors, speed);
    }
}

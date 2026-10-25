package my.celium.org.colorednetherportals.data;

import java.util.List;

/**
 * Turns a {@link PortalStyle} into colors across a portal. A portal is a
 * rectangle {@code width} by {@code height} blocks; {@code u} is how far
 * across (0 at one side, {@code width} at the other) and {@code v} how far up
 * (0 at the bottom edge, {@code height} at the top), in blocks with
 * fractions, so each block can be painted in several cells for smooth
 * gradients. Pure, so it is unit tested.
 */
public final class PortalPainter {
    /** Color cells per block edge: each block is painted as CELLS x CELLS cells. */
    public static final int CELLS = 4;
    /**
     * How much the drawn tint is lightened towards white. Tinting can only
     * darken the grey swirl, so the tint is a little lighter than the chosen
     * color: mid-tones land on it and highlights glow above it.
     */
    static final float SWIRL_LIGHTEN = 0.1f;
    /** Blocks per full rainbow, top to bottom. */
    static final double RAINBOW_SPAN = 8;
    /** How much each block across shifts the rainbow, so the stripes run diagonally. */
    static final double RAINBOW_SLANT = 0.035;
    /** Ticks for one color cycle, pulse or rainbow pass at speed 1, 2, 3. */
    static final int[] PERIOD = {0, 400, 160, 60};

    private PortalPainter() {
    }

    /**
     * The RGB color ({@code 0xRRGGBB}) at ({@code u}, {@code v}) at game time
     * {@code ticks}, or {@code -1} for {@link PortalStyle.Mode#VANILLA}.
     */
    public static int colorAt(PortalStyle style, double u, double v, int width, int height, double ticks) {
        return switch (style.mode()) {
            case VANILLA -> -1;
            case SOLID -> style.color();
            case GRADIENT -> sample(style.used(), height <= 0 ? 0 : (float) (v / height));
            case RAINBOW -> {
                double shift = ticks / PERIOD[style.speed()];
                yield Colors.hsv((float) (v / RAINBOW_SPAN + u * RAINBOW_SLANT - shift), 0.85f, 1f);
            }
            case CYCLE -> Colors.hsv((float) (ticks / PERIOD[style.speed()]), 0.85f, 1f);
            case PULSE -> {
                double phase = ticks / PERIOD[style.speed()] * 2 * Math.PI;
                yield sample(style.used(), (float) ((1 - Math.cos(phase)) / 2));
            }
        };
    }

    /** The color at {@code t} (0 to 1) along evenly spaced {@code colors}. */
    static int sample(List<Integer> colors, float t) {
        int n = colors.size();
        if (n == 1) {
            return colors.get(0);
        }
        float p = Math.max(0, Math.min(1, t)) * (n - 1);
        int i = Math.min((int) p, n - 2);
        return Colors.lerp(colors.get(i), colors.get(i + 1), p - i);
    }

    /**
     * The colors of one block's cells, {@code CELLS * CELLS} of them, row by row
     * from the bottom; block ({@code bu}, {@code bv}) counted from the corner.
     */
    public static int[] cells(PortalStyle style, int bu, int bv, int width, int height, double ticks) {
        int[] out = new int[CELLS * CELLS];
        for (int cy = 0; cy < CELLS; cy++) {
            for (int cu = 0; cu < CELLS; cu++) {
                out[cy * CELLS + cu] = colorAt(style, bu + (cu + 0.5) / CELLS, bv + (cy + 0.5) / CELLS, width,
                        height, ticks);
            }
        }
        return out;
    }

    /** The tint that makes the grey swirl show {@code rgb}. */
    public static int swirlTint(int rgb) {
        return Colors.lerp(rgb, 0xFFFFFF, SWIRL_LIGHTEN);
    }

    /** The color at the bottom of a portal right now, for swatches. White for vanilla. */
    public static int baseColor(PortalStyle style, double ticks) {
        int rgb = colorAt(style, 0, 0, 1, 1, ticks);
        return rgb < 0 ? 0xFFFFFF : rgb;
    }
}

package my.celium.org.colorednetherportals;

import my.celium.org.stats.ModStats;

/**
 * Anonymous stats for the FastStats project {@code colored-nether-portals}, sent through Mycel under its
 * consent: how many portals are colored and in which modes, which dyes and modes get used, and the settings.
 */
public final class PortalStats {
    private static final String TOKEN = "47cf29a3bce788a79aa7b4788dac97c8";

    private static volatile ModStats stats;

    private PortalStats() {
    }

    static void start() {
        stats = ModStats.builder(ColoredNetherPortalsMod.META, TOKEN)
                .bool("enabled", () -> ColoredNetherPortalsMod.enabled.get())
                .bool("allow_animated", () -> ColoredNetherPortalsMod.allowAnimated.get())
                .bool("dye_coloring", () -> ColoredNetherPortalsMod.dyeColoring.get())
                .bool("consume_dye", () -> ColoredNetherPortalsMod.consumeDye.get())
                .number("colored_portals", my.celium.org.colorednetherportals.server.PortalColors::coloredCount)
                .numberMap("portal_modes", my.celium.org.colorednetherportals.server.PortalColors::modeCounts)
                .counters("chosen_modes")
                .counters("dye_colors")
                .errors("my.celium.org.colorednetherportals.")
                .start();
    }

    /** Counts a feature use (sent as {@code usage}). */
    public static void count(String what) {
        ModStats current = stats;
        if (current != null) {
            current.count(what);
        }
    }

    /** Counts {@code key} in the counter map {@code id}. */
    public static void count(String id, String key) {
        ModStats current = stats;
        if (current != null && key != null) {
            current.count(id, key.startsWith("minecraft:") ? key.substring(10) : key);
        }
    }
}

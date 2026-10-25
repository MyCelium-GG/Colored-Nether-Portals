package my.celium.org.colorednetherportals;

import my.celium.org.Mycel;
import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.config.MycelConfig;
import my.celium.org.event.MycelEvents;
import my.celium.org.event.ServerLifecycleEvent;
import my.celium.org.logging.MycelLog;
import my.celium.org.metadata.ModMetadata;
import my.celium.org.colorednetherportals.net.PortalsPacket;
import my.celium.org.colorednetherportals.net.SetPortalPacket;
import my.celium.org.colorednetherportals.server.PortalColors;
import my.celium.org.network.MycelNetwork;
import my.celium.org.network.PacketDefinition;

/**
 * Colored Nether Portals: sneak and right-click a Nether portal with an empty
 * hand to open a color menu (any solid color, gradients, rainbows, color
 * cycles and pulses), or right-click it with a dye for that color.
 *
 * <p>The server keeps each portal's style in the world folder and tells every
 * player with the mod; their clients repaint the portal. On a server without
 * the mod, the client remembers colors for itself (only you see them).
 * Vanilla players see the usual purple.
 */
public final class ColoredNetherPortalsMod {
    public static final String ID = "colorednetherportals";

    public static final ModMetadata META = ModMetadata.builder(ID, "Colored Nether Portals", "1.0.0")
            .author("MyCelium")
            .website("https://www.curseforge.com/minecraft/mc-mods/colored-nether-portals")
            .updateUrl("https://wiki.mycelium-gg.org/updates/colorednetherportals.json")
            .build();

    public static ConfigValue<Boolean> enabled;
    public static ConfigValue<Boolean> allowAnimated;
    public static ConfigValue<Boolean> dyeColoring;
    public static ConfigValue<Boolean> consumeDye;
    public static ConfigValue<Boolean> debug;
    public static MycelConfig config;

    public static PacketDefinition<SetPortalPacket> setPortal;
    public static PacketDefinition<PortalsPacket> portals;

    private ColoredNetherPortalsMod() {
    }

    /** Called once from each loader's entrypoint. */
    public static void init() {
        Mycel.initialize();
        Mycel.registerMod(META);

        ConfigBuilder builder = Mycel.config(META);
        enabled = builder.booleanValue("enabled", true,
                "Whether the mod does anything at all.");
        allowAnimated = builder.booleanValue("allowAnimated", true,
                "Allow rainbow, color cycle and pulse portals (server).");
        dyeColoring = builder.booleanValue("dyeColoring", true,
                "Right-clicking a portal with a dye colors it, also for players without the mod (server).");
        consumeDye = builder.booleanValue("consumeDye", true,
                "Coloring a portal with a dye uses up one dye, except in creative (server).");
        debug = builder.booleanValue("debug", false,
                "Log color changes, syncing and clean-ups to the game log.");
        config = builder.build();

        setPortal = PacketDefinition.toServer(SetPortalPacket.TYPE, SetPortalPacket.CODEC,
                (packet, context) -> PortalColors.onSetPortal(packet, context.sender()));
        portals = PacketDefinition.toClient(PortalsPacket.TYPE, PortalsPacket.CODEC, PortalsPacket::receive);
        // Mycel 1.4.0+ registers both channels as optional on every loader,
        // so players with the mod can join servers without it and the other
        // way round; senders skip clients without the channel.
        MycelNetwork.register(META, setPortal);
        MycelNetwork.register(META, portals);

        MycelEvents.onServerLifecycle(event -> {
            if (event.phase() == ServerLifecycleEvent.Phase.STARTED) {
                PortalColors.start(event.server());
            } else {
                PortalColors.stop();
            }
        });
        MycelEvents.onPlayerJoin(event -> PortalColors.sendAll(event.player()));
    }

    /** Info-level log line tagged {@code [debug]}, only while the {@code debug} option is on. */
    public static void debug(String message, Object... args) {
        try {
            if (debug == null || !debug.get()) {
                return;
            }
            MycelLog.infoMod(ID, "[debug] " + message, args);
        } catch (RuntimeException e) {
            // Logging must never break the caller.
        }
    }

    public static void warn(String message, Object... args) {
        try {
            MycelLog.warnMod(ID, message, args);
        } catch (RuntimeException e) {
            // Logging must never break the caller.
        }
    }

    public static boolean isModEnabled() {
        return Mycel.isEnabled(ID) && enabled.get();
    }
}

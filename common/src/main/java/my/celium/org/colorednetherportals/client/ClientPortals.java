package my.celium.org.colorednetherportals.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import my.celium.org.Mycel;
import my.celium.org.client.MycelScreens;
import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import my.celium.org.colorednetherportals.data.PortalPainter;
import my.celium.org.colorednetherportals.data.PortalShape;
import my.celium.org.colorednetherportals.data.PortalStore;
import my.celium.org.colorednetherportals.data.PortalStyle;
import my.celium.org.colorednetherportals.net.PortalsPacket;
import my.celium.org.colorednetherportals.net.SetPortalPacket;
import my.celium.org.network.MycelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The client half. Styles come from the server when it has the mod
 * ({@link #receive}); otherwise from a local store in
 * {@code config/colorednetherportals-local.json}, keyed by server address,
 * that only this player sees.
 *
 * <p>Drawing: the portal model has two layers on one plane, vanilla's texture
 * (tint 0) and a grey swirl split into 4x4 cells (tints 1 to 16). Our tint
 * sources show exactly one layer per block: vanilla for portals without a
 * style, otherwise the swirl with every cell in its own color, so gradients
 * fade smoothly. Colors live in {@link #COLORS}, read by the chunk builder
 * threads; whenever a portal's colors change (a new style, a portal lit or
 * broken, an animation step) its chunk sections are rebuilt. Portal
 * particles take the color of the block they start at, and the screen effect
 * while traveling takes the color of the portal you stand in.
 * Client only.
 */
public final class ClientPortals {
    /** Animated portals within this many blocks change color every {@link #ANIMATION_TICKS}. */
    private static final double ANIMATION_RANGE = 64;
    private static final int ANIMATION_TICKS = 2;
    /** How often shapes are re-checked: a portal lit or broken shows its style within a second. */
    private static final int SHAPE_TICKS = 20;

    private static final PortalStore SYNCED = new PortalStore();
    private static final PortalStore LOCAL = new PortalStore();
    private static boolean localLoaded;
    private static ClientPacketListener syncedConnection;
    private static ClientPacketListener seenConnection;

    /** Block position (as a long) to its cells' RGB colors, for every block of every colored portal in view. */
    private static final Map<Long, int[]> COLORS = new ConcurrentHashMap<>();
    /** What each colored portal looked like last time: shape and colors. */
    private static final Map<PortalStore.Key, Drawn> DRAWN = new HashMap<>();
    private static boolean stylesChanged = true;
    private static boolean tintsRegistered;
    private static long ticks;

    private record Drawn(PortalShape shape, Map<Long, int[]> colors) {
        boolean sameAs(Drawn other) {
            if (other == null || !shape.equals(other.shape) || colors.size() != other.colors.size()) {
                return false;
            }
            for (Map.Entry<Long, int[]> entry : colors.entrySet()) {
                if (!java.util.Arrays.equals(entry.getValue(), other.colors.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    private ClientPortals() {
    }

    // ------------------------------------------------------------ state

    public static void receive(PortalsPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (syncedConnection != minecraft.getConnection()) {
            SYNCED.clear();
            syncedConnection = minecraft.getConnection();
        }
        if (packet.replace()) {
            SYNCED.clear();
        }
        for (PortalsPacket.Entry entry : packet.entries()) {
            SYNCED.put(entry.key(), entry.style());
        }
        stylesChanged = true;
        ColoredNetherPortalsMod.debug("Synced {} portal styles ({} total)", packet.entries().size(), SYNCED.size());
    }

    /** Whether the server we are connected to has the mod. */
    public static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection == syncedConnection;
    }

    /** The style of the portal whose corner is {@code corner}. */
    public static PortalStyle style(Level level, BlockPos corner) {
        if (serverHasMod()) {
            return SYNCED.get(syncedKey(level, corner));
        }
        loadLocal();
        return LOCAL.get(localKey(level, corner));
    }

    /** Applies a style: through the server when it has the mod, otherwise locally. */
    public static void set(Level level, BlockPos corner, PortalStyle style) {
        if (serverHasMod()) {
            MycelNetwork.sendToServer(ColoredNetherPortalsMod.setPortal, new SetPortalPacket(corner, style));
            return;
        }
        loadLocal();
        if (LOCAL.put(localKey(level, corner), style)) {
            saveLocal();
            stylesChanged = true;
        }
    }

    private static PortalStore.Key syncedKey(Level level, BlockPos pos) {
        return new PortalStore.Key(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** Local styles are remembered per server: {@code address|dimension}. */
    private static PortalStore.Key localKey(Level level, BlockPos pos) {
        return new PortalStore.Key(serverId() + "|" + level.dimension().identifier(), pos.getX(), pos.getY(), pos.getZ());
    }

    private static String serverId() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        return server == null ? "local" : server.ip.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Path localFile() {
        return Mycel.getConfigDir().resolve(ColoredNetherPortalsMod.ID + "-local.json");
    }

    private static void loadLocal() {
        if (localLoaded) {
            return;
        }
        localLoaded = true;
        try {
            LOCAL.load(localFile());
        } catch (IOException e) {
            ColoredNetherPortalsMod.warn("{}", e.getMessage());
        }
    }

    private static void saveLocal() {
        try {
            LOCAL.save(localFile());
        } catch (IOException e) {
            ColoredNetherPortalsMod.warn("Could not save {}: {}", localFile(), e.toString());
        }
    }

    /** The portal a block belongs to in the client's world, or null. */
    public static PortalShape shapeAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.NETHER_PORTAL)) {
            return null;
        }
        var axis = state.getValue(NetherPortalBlock.AXIS);
        return PortalShape.find(pos, axis, p -> {
            BlockState s = level.getBlockState(p);
            return s.is(Blocks.NETHER_PORTAL) && s.getValue(NetherPortalBlock.AXIS) == axis;
        });
    }

    // ------------------------------------------------------------ drawing

    /** Tint 0: vanilla's texture, hidden on colored blocks. */
    private static final BlockTintSource VANILLA_LAYER = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return 0xFFFFFFFF;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return COLORS.containsKey(pos.asLong()) ? 0x00FFFFFF : 0xFFFFFFFF;
        }
    };

    /** Tints 1 to 16: one cell of the grey swirl, in that cell's color; hidden on vanilla blocks. */
    private static BlockTintSource cellLayer(int cell) {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return 0x00FFFFFF;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                int[] cells = COLORS.get(pos.asLong());
                return cells == null ? 0x00FFFFFF : 0xFF000000 | PortalPainter.swirlTint(cells[cell]);
            }
        };
    }

    private static void registerTints(Minecraft minecraft) {
        if (tintsRegistered || minecraft.getBlockColors() == null) {
            return;
        }
        List<BlockTintSource> layers = new java.util.ArrayList<>();
        layers.add(VANILLA_LAYER);
        for (int cell = 0; cell < PortalPainter.CELLS * PortalPainter.CELLS; cell++) {
            layers.add(cellLayer(cell));
        }
        minecraft.getBlockColors().register(layers, Blocks.NETHER_PORTAL);
        tintsRegistered = true;
        ColoredNetherPortalsMod.debug("Portal tints registered");
    }

    /**
     * The color a portal particle starting at ({@code x}, {@code y}, {@code z})
     * should have, or -1 to keep vanilla's purple. Particles start in or just
     * beside a portal block, so the neighbours across the portal count too.
     */
    static int particleColor(double x, double y, double z) {
        if (COLORS.isEmpty()) {
            return -1;
        }
        BlockPos pos = BlockPos.containing(x, y, z);
        int[] cells = COLORS.get(pos.asLong());
        for (int i = 0; cells == null && i < NEIGHBOURS.length; i++) {
            cells = COLORS.get(pos.offset(NEIGHBOURS[i][0], 0, NEIGHBOURS[i][1]).asLong());
        }
        if (cells == null) {
            return -1;
        }
        int row = Math.max(0, Math.min(PortalPainter.CELLS - 1, (int) ((y - Math.floor(y)) * PortalPainter.CELLS)));
        return cells[row * PortalPainter.CELLS + PortalPainter.CELLS / 2];
    }

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * The color of the colored portal block at the player's eyes (or feet), or
     * -1 when they are not standing in one.
     */
    static int colorAtEyes(double x, double eyeY, double z) {
        if (COLORS.isEmpty()) {
            return -1;
        }
        for (double y : new double[] {eyeY, eyeY - 1.2}) {
            int[] cells = COLORS.get(BlockPos.containing(x, y, z).asLong());
            if (cells != null) {
                int row = Math.max(0, Math.min(PortalPainter.CELLS - 1, (int) ((y - Math.floor(y)) * PortalPainter.CELLS)));
                return cells[row * PortalPainter.CELLS + PortalPainter.CELLS / 2];
            }
        }
        return -1;
    }

    /** Called at the end of every client tick. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        registerTints(minecraft);
        ColoredPortalParticles.install(minecraft);
        PortalOverlayTint.tick(minecraft);
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != seenConnection) {
            // New world or server: forget what was drawn and synced for the old one.
            seenConnection = connection;
            DRAWN.clear();
            COLORS.clear();
            stylesChanged = true;
            if (connection != syncedConnection) {
                SYNCED.clear();
            }
        }
        ClientLevel level = minecraft.level;
        if (level == null || !tintsRegistered) {
            return;
        }
        ticks++;
        boolean on = ColoredNetherPortalsMod.isModEnabled();
        Map<PortalStore.Key, PortalStyle> styles;
        if (!on) {
            styles = Map.of();
        } else if (serverHasMod()) {
            styles = SYNCED.in(level.dimension().identifier().toString());
        } else {
            loadLocal();
            styles = LOCAL.in(serverId() + "|" + level.dimension().identifier());
        }
        boolean recheck = stylesChanged || ticks % SHAPE_TICKS == 0;
        boolean animate = ticks % ANIMATION_TICKS == 0;
        if (!recheck && !animate) {
            return;
        }
        stylesChanged = false;
        Vec3 eye = minecraft.player == null ? null : minecraft.player.getEyePosition();
        double time = level.getGameTime();

        Map<PortalStore.Key, Drawn> next = new HashMap<>();
        for (Map.Entry<PortalStore.Key, PortalStyle> entry : styles.entrySet()) {
            PortalStore.Key key = entry.getKey();
            PortalStyle style = entry.getValue();
            BlockPos corner = new BlockPos(key.x(), key.y(), key.z());
            if (!level.isLoaded(corner)) {
                continue;
            }
            Drawn before = DRAWN.get(key);
            boolean moving = style.animated() && eye != null
                    && eye.distanceToSqr(corner.getX(), corner.getY(), corner.getZ()) < ANIMATION_RANGE * ANIMATION_RANGE;
            if (before != null && !recheck && !moving) {
                next.put(key, before);
                continue;
            }
            PortalShape shape = recheck || before == null ? shapeAt(level, corner) : before.shape();
            if (shape == null || !shape.corner().equals(corner)) {
                continue; // no portal here (yet), or this block is not its corner any more
            }
            Map<Long, int[]> colors = new HashMap<>();
            if (!style.isVanilla()) {
                for (int u = 0; u < shape.width(); u++) {
                    for (int v = 0; v < shape.height(); v++) {
                        colors.put(shape.at(u, v).asLong(),
                                PortalPainter.cells(style, u, v, shape.width(), shape.height(), time));
                    }
                }
            }
            next.put(key, new Drawn(shape, colors));
        }

        // Rebuild what changed: portals added, removed or repainted.
        for (Map.Entry<PortalStore.Key, Drawn> entry : next.entrySet()) {
            Drawn before = DRAWN.get(entry.getKey());
            Drawn after = entry.getValue();
            if (!after.sameAs(before)) {
                if (before != null) {
                    before.colors().keySet().forEach(COLORS::remove);
                    if (!before.shape().equals(after.shape())) {
                        markDirty(level, before.shape());
                    }
                }
                COLORS.putAll(after.colors());
                markDirty(level, after.shape());
            }
        }
        for (Map.Entry<PortalStore.Key, Drawn> entry : DRAWN.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                entry.getValue().colors().keySet().forEach(COLORS::remove);
                markDirty(level, entry.getValue().shape());
            }
        }
        DRAWN.clear();
        DRAWN.putAll(next);
    }

    private static void markDirty(ClientLevel level, PortalShape shape) {
        BlockPos a = shape.corner();
        BlockPos b = shape.opposite();
        level.setSectionRangeDirty(
                SectionPos.blockToSectionCoord(Math.min(a.getX(), b.getX())),
                SectionPos.blockToSectionCoord(Math.min(a.getY(), b.getY())),
                SectionPos.blockToSectionCoord(Math.min(a.getZ(), b.getZ())),
                SectionPos.blockToSectionCoord(Math.max(a.getX(), b.getX())),
                SectionPos.blockToSectionCoord(Math.max(a.getY(), b.getY())),
                SectionPos.blockToSectionCoord(Math.max(a.getZ(), b.getZ())));
    }

    // ------------------------------------------------------------ opening the menu

    /**
     * Sneak + right-click on a portal with an empty hand opens the color menu.
     * Returns whether the click was ours (the loader then cancels it).
     */
    public static boolean onUse(BlockPos pos, boolean sneaking, boolean emptyHand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!sneaking || !emptyHand || minecraft.level == null || !ColoredNetherPortalsMod.isModEnabled()) {
            return false;
        }
        PortalShape shape = shapeAt(minecraft.level, pos);
        if (shape == null) {
            return false;
        }
        MycelScreens.open(new PortalColorScreen(shape));
        return true;
    }
}

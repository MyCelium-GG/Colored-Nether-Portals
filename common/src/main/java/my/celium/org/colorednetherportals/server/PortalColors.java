package my.celium.org.colorednetherportals.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import my.celium.org.colorednetherportals.data.Colors;
import my.celium.org.colorednetherportals.data.PortalShape;
import my.celium.org.colorednetherportals.data.PortalStore;
import my.celium.org.colorednetherportals.data.PortalStyle;
import my.celium.org.colorednetherportals.net.PortalsPacket;
import my.celium.org.colorednetherportals.net.SetPortalPacket;
import my.celium.org.data.MycelDataFile;
import my.celium.org.network.MycelNetwork;
import my.celium.org.schedule.MycelScheduler;
import my.celium.org.schedule.ScheduledTask;
import my.celium.org.util.ChatVoice;
import my.celium.org.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The server half: one {@link PortalStore} per world, saved as
 * {@code <world>/data/colorednetherportals/portals.json}. A portal is known by
 * its corner block (see {@link PortalShape}). Players with the mod get every
 * style when they join and each change as it happens. Every 5 seconds,
 * entries whose portal is gone (in loaded chunks) are dropped.
 */
public final class PortalColors {
    static final ChatVoice VOICE = TextUtil.voice("Portals");
    private static final long SWEEP_TICKS = 100;
    /** How far a player may be from the nearest block of the portal. */
    private static final double REACH = 8;

    private static final PortalStore STORE = new PortalStore();
    private static MinecraftServer server;
    private static MycelDataFile file;
    private static ScheduledTask sweep;

    private PortalColors() {
    }

    public static synchronized void start(MinecraftServer started) {
        server = started;
        file = MycelDataFile.inWorld(started, ColoredNetherPortalsMod.META, "portals.json");
        com.google.gson.JsonObject saved = file.readObject();
        if (saved.size() > 0) {
            try {
                STORE.fromJson(saved.toString());
            } catch (IllegalArgumentException e) {
                // Parsed, but not our layout: keep the file aside rather than overwrite it.
                file.quarantine(e.getMessage());
                STORE.clear();
            }
        }
        ColoredNetherPortalsMod.debug("Loaded {} colored portals", STORE.size());
        sweep = MycelScheduler.runEvery(SWEEP_TICKS, PortalColors::sweep);
    }

    public static synchronized void stop() {
        if (sweep != null) {
            sweep.cancel();
            sweep = null;
        }
        STORE.clear();
        server = null;
        file = null;
    }

    private static void save() {
        if (file != null) {
            file.write(com.google.gson.JsonParser.parseString(STORE.toJson()));
        }
    }

    public static PortalStore.Key key(Level level, BlockPos pos) {
        return new PortalStore.Key(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** Sends every style to a player who just joined; an empty set still tells the client the server has the mod. */
    public static void sendAll(ServerPlayer player) {
        List<PortalsPacket.Entry> entries = new ArrayList<>();
        STORE.all().forEach((key, style) -> entries.add(new PortalsPacket.Entry(key, style)));
        boolean first = true;
        int i = 0;
        do {
            List<PortalsPacket.Entry> chunk = entries.subList(i, Math.min(entries.size(), i + PortalsPacket.MAX_ENTRIES));
            MycelNetwork.sendToPlayer(player, ColoredNetherPortalsMod.portals, new PortalsPacket(first, new ArrayList<>(chunk)));
            first = false;
            i += PortalsPacket.MAX_ENTRIES;
        } while (i < entries.size());
    }

    private static void broadcast(List<PortalsPacket.Entry> entries) {
        if (server == null || entries.isEmpty()) {
            return;
        }
        MycelNetwork.sendToAll(server, ColoredNetherPortalsMod.portals, new PortalsPacket(false, entries));
    }

    /** The portal at {@code pos} in a server world, or null. */
    static PortalShape shapeAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.NETHER_PORTAL)) {
            return null;
        }
        var axis = state.getValue(NetherPortalBlock.AXIS);
        return PortalShape.find(pos, axis, p -> {
            if (!level.isLoaded(p)) {
                return false;
            }
            BlockState s = level.getBlockState(p);
            return s.is(Blocks.NETHER_PORTAL) && s.getValue(NetherPortalBlock.AXIS) == axis;
        });
    }

    private static boolean inReach(ServerPlayer player, PortalShape shape) {
        BlockPos a = shape.corner();
        BlockPos b = shape.opposite();
        AABB box = new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, b.getY() + 1, Math.max(a.getZ(), b.getZ()) + 1);
        return box.distanceToSqr(player.getEyePosition()) <= REACH * REACH;
    }

    /** A player picked a style in the color menu. */
    public static void onSetPortal(SetPortalPacket packet, ServerPlayer player) {
        if (player == null || server == null) {
            return;
        }
        if (!ColoredNetherPortalsMod.isModEnabled()) {
            player.sendSystemMessage(VOICE.fail("Portal colors are turned off on this server."));
            return;
        }
        PortalStyle style = packet.style();
        if (style.animated() && !ColoredNetherPortalsMod.allowAnimated.get()) {
            player.sendSystemMessage(VOICE.fail("Animated portals are turned off on this server."));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        PortalShape shape = shapeAt(level, packet.pos());
        if (shape == null || !inReach(player, shape)) {
            ColoredNetherPortalsMod.debug("{} tried to color a portal at {} they can't reach",
                    player.getName().getString(), packet.pos());
            return;
        }
        apply(level, shape, style, player);
    }

    /**
     * Right-clicking a portal with a dye colors it in that dye's color. Works
     * for players without the mod too. Returns whether the click was used.
     */
    public static boolean onUseDye(ServerPlayer player, BlockPos pos, ItemStack stack) {
        if (server == null || !ColoredNetherPortalsMod.isModEnabled() || !ColoredNetherPortalsMod.dyeColoring.get()) {
            return false;
        }
        DyeColor dye = stack.get(DataComponents.DYE);
        if (dye == null) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        PortalShape shape = shapeAt(level, pos);
        if (shape == null || !inReach(player, shape)) {
            return false;
        }
        PortalStyle style = PortalStyle.solid(Colors.forDye(dye.getName()));
        if (style.equals(STORE.get(key(level, shape.corner())))) {
            return false;
        }
        apply(level, shape, style, player);
        if (ColoredNetherPortalsMod.consumeDye.get() && !player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return true;
    }

    /** Loader hook for any right-click on a block, server side. Returns whether it was ours. */
    public static boolean onUse(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            BlockPos pos) {
        return player instanceof ServerPlayer serverPlayer && onUseDye(serverPlayer, pos, player.getItemInHand(hand));
    }

    private static void apply(ServerLevel level, PortalShape shape, PortalStyle style, ServerPlayer player) {
        PortalStore.Key key = key(level, shape.corner());
        if (STORE.put(key, style)) {
            save();
            broadcast(List.of(new PortalsPacket.Entry(key, style)));
            ColoredNetherPortalsMod.debug("{} set {} to {}", player.getName().getString(), key, style);
        }
    }

    /** Drops entries whose portal went out (or whose corner moved), in chunks that are loaded right now. */
    private static void sweep() {
        if (server == null || STORE.size() == 0) {
            return;
        }
        List<PortalsPacket.Entry> removed = new ArrayList<>();
        for (Map.Entry<PortalStore.Key, PortalStyle> entry : new ArrayList<>(STORE.all().entrySet())) {
            PortalStore.Key key = entry.getKey();
            ServerLevel level = levelOf(key.dimension());
            if (level == null) {
                continue;
            }
            BlockPos pos = new BlockPos(key.x(), key.y(), key.z());
            if (!level.isLoaded(pos)) {
                continue;
            }
            PortalShape shape = shapeAt(level, pos);
            if (shape == null || !shape.corner().equals(pos)) {
                STORE.remove(key);
                removed.add(new PortalsPacket.Entry(key, PortalStyle.VANILLA));
            }
        }
        if (!removed.isEmpty()) {
            ColoredNetherPortalsMod.debug("Forgot {} portals that went out", removed.size());
            save();
            broadcast(removed);
        }
    }

    private static ServerLevel levelOf(String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }
}

package my.celium.org.colorednetherportals.net;

import java.util.ArrayList;
import java.util.List;

import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import my.celium.org.colorednetherportals.data.PortalStore;
import my.celium.org.colorednetherportals.data.PortalStyle;
import my.celium.org.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: portal styles. {@code replace} sends the whole set (on
 * join); otherwise these entries change, and a vanilla style removes one.
 * Arriving at all also tells the client that the server has the mod.
 */
public record PortalsPacket(boolean replace, List<Entry> entries) implements CustomPacketPayload {
    /** Keeps a single packet well under the custom payload limit. */
    public static final int MAX_ENTRIES = 4096;

    public static final Type<PortalsPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ColoredNetherPortalsMod.ID, "portals"));
    public static final StreamCodec<FriendlyByteBuf, PortalsPacket> CODEC = StreamCodec.of(PortalsPacket::write,
            PortalsPacket::read);

    public record Entry(PortalStore.Key key, PortalStyle style) {
    }

    private static void write(FriendlyByteBuf buf, PortalsPacket packet) {
        buf.writeBoolean(packet.replace());
        int count = Math.min(MAX_ENTRIES, packet.entries().size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = packet.entries().get(i);
            buf.writeUtf(entry.key().dimension(), 256);
            buf.writeInt(entry.key().x());
            buf.writeInt(entry.key().y());
            buf.writeInt(entry.key().z());
            Styles.write(buf, entry.style());
        }
    }

    private static PortalsPacket read(FriendlyByteBuf buf) {
        boolean replace = buf.readBoolean();
        int count = Math.min(MAX_ENTRIES, buf.readVarInt());
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            PortalStore.Key key = new PortalStore.Key(buf.readUtf(256), buf.readInt(), buf.readInt(), buf.readInt());
            entries.add(new Entry(key, Styles.read(buf)));
        }
        return new PortalsPacket(replace, entries);
    }

    /** Client side: hand over to the client store (never runs on a dedicated server). */
    public static void receive(PortalsPacket packet, PacketContext context) {
        my.celium.org.colorednetherportals.client.ClientPortals.receive(packet);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

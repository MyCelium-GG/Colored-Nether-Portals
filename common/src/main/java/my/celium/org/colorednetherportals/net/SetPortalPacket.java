package my.celium.org.colorednetherportals.net;

import my.celium.org.colorednetherportals.ColoredNetherPortalsMod;
import my.celium.org.colorednetherportals.data.PortalStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: "make the portal this block belongs to look like this".
 * The server finds the portal itself and checks the player can reach it.
 */
public record SetPortalPacket(BlockPos pos, PortalStyle style) implements CustomPacketPayload {
    public static final Type<SetPortalPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ColoredNetherPortalsMod.ID, "set_portal"));
    public static final StreamCodec<FriendlyByteBuf, SetPortalPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.pos());
                Styles.write(buf, packet.style());
            },
            buf -> new SetPortalPacket(buf.readBlockPos(), Styles.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

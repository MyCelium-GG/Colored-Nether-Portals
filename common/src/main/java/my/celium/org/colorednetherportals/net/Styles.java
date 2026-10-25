package my.celium.org.colorednetherportals.net;

import java.util.ArrayList;
import java.util.List;

import my.celium.org.colorednetherportals.data.PortalStyle;
import net.minecraft.network.FriendlyByteBuf;

/** Wire format of a {@link PortalStyle}: mode, colors, speed. */
final class Styles {
    private Styles() {
    }

    static void write(FriendlyByteBuf buf, PortalStyle style) {
        buf.writeVarInt(style.mode().ordinal());
        buf.writeVarInt(style.colors().size());
        for (int rgb : style.colors()) {
            buf.writeInt(rgb);
        }
        buf.writeByte(style.speed());
    }

    static PortalStyle read(FriendlyByteBuf buf) {
        int mode = buf.readVarInt();
        PortalStyle.Mode[] modes = PortalStyle.Mode.values();
        int count = buf.readVarInt();
        if (count < 0 || count > PortalStyle.MAX_COLORS) {
            throw new IllegalArgumentException("Bad portal color count " + count);
        }
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            colors.add(buf.readInt());
        }
        return new PortalStyle(mode >= 0 && mode < modes.length ? modes[mode] : PortalStyle.Mode.VANILLA, colors,
                buf.readByte());
    }
}

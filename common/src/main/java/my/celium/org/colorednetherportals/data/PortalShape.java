package my.celium.org.colorednetherportals.data;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The rectangle of portal blocks one block belongs to: its bottom corner with
 * the lowest coordinate, the horizontal axis it spans, and its size. Portals
 * are always flat rectangles, so walking down and then towards the low side
 * finds the corner from any of their blocks; that corner is the portal's id.
 */
public record PortalShape(BlockPos corner, Direction.Axis axis, int width, int height) {
    /** Far bigger than any portal vanilla lights (21 by 21 inside the frame); stops runaway walks. */
    static final int LIMIT = 64;

    /**
     * The portal {@code pos} belongs to. {@code isPortal} answers whether a
     * position holds a portal block spanning {@code axis}. Null when {@code pos}
     * itself is not one.
     */
    public static PortalShape find(BlockPos pos, Direction.Axis axis, Predicate<BlockPos> isPortal) {
        if (!isPortal.test(pos)) {
            return null;
        }
        Direction low = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < LIMIT && isPortal.test(cursor.below()); i++) {
            cursor.move(Direction.DOWN);
        }
        for (int i = 0; i < LIMIT && isPortal.test(cursor.relative(low)); i++) {
            cursor.move(low);
        }
        BlockPos corner = cursor.immutable();
        Direction high = low.getOpposite();
        int width = 1;
        while (width < LIMIT && isPortal.test(corner.relative(high, width))) {
            width++;
        }
        int height = 1;
        while (height < LIMIT && isPortal.test(corner.above(height))) {
            height++;
        }
        return new PortalShape(corner, axis, width, height);
    }

    /** The block {@code u} across and {@code v} up from the corner. */
    public BlockPos at(int u, int v) {
        return corner.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), u).above(v);
    }

    public BlockPos opposite() {
        return at(width - 1, height - 1);
    }
}

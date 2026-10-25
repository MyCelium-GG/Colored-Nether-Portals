package my.celium.org.colorednetherportals.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** {@link PortalShape}: every block of a portal finds the same corner and size. */
class PortalShapeTest {
    /** A width x height portal along {@code axis} whose low corner is {@code corner}. */
    private static Set<BlockPos> portal(BlockPos corner, Direction.Axis axis, int width, int height) {
        Set<BlockPos> blocks = new HashSet<>();
        Direction along = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        for (int u = 0; u < width; u++) {
            for (int v = 0; v < height; v++) {
                blocks.add(corner.relative(along, u).above(v));
            }
        }
        return blocks;
    }

    @Test
    void everyBlockFindsTheSameShape() {
        for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
            BlockPos corner = new BlockPos(-10, 64, 37);
            Set<BlockPos> blocks = portal(corner, axis, 4, 5);
            PortalShape expected = new PortalShape(corner, axis, 4, 5);
            for (BlockPos pos : blocks) {
                assertEquals(expected, PortalShape.find(pos, axis, blocks::contains), axis + " from " + pos);
            }
            assertEquals(corner.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), 3)
                    .above(4), expected.opposite());
        }
    }

    @Test
    void notAPortalBlock() {
        Set<BlockPos> blocks = portal(BlockPos.ZERO, Direction.Axis.X, 2, 3);
        assertNull(PortalShape.find(new BlockPos(5, 5, 5), Direction.Axis.X, blocks::contains));
    }

    @Test
    void neighbouringPortalsStaySeparate() {
        // Two portals side by side along X but on different Z planes.
        Set<BlockPos> blocks = portal(new BlockPos(0, 0, 0), Direction.Axis.X, 2, 3);
        Set<BlockPos> other = portal(new BlockPos(0, 0, 1), Direction.Axis.X, 3, 4);
        Set<BlockPos> all = new HashSet<>(blocks);
        all.addAll(other);
        assertEquals(new PortalShape(new BlockPos(0, 0, 0), Direction.Axis.X, 2, 3),
                PortalShape.find(new BlockPos(1, 2, 0), Direction.Axis.X, all::contains));
        assertEquals(new PortalShape(new BlockPos(0, 0, 1), Direction.Axis.X, 3, 4),
                PortalShape.find(new BlockPos(2, 3, 1), Direction.Axis.X, all::contains));
    }
}

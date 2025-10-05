package net.chaoscraft.chaoscrafts_device_mod.Core.Util.GeckoLib;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.ArrayList;

public class LaptopHitboxHelper {
    // Convert model coordinates to block coordinates (0-1 range)
    private static final double MODEL_TO_BLOCK = 1.0 / 16.0;

    // Hitbox definitions from geometry files
    private static final AABB BOTTOM_BASE = createAABB(-7, 0, -5, 14, 1, 11);
    private static final AABB TOP_BASE = createAABB(-7, 1, 6, 14, 11, 1);
    private static final AABB SCREEN = createAABB(-6.5, -9.5, 6, 13, 10, 0.1);

    // Closed state: top base is rotated 90 degrees and positioned differently
    private static final AABB CLOSED_TOP_BASE = createAABB(-7, 1, -5, 14, 1, 11);

    private static AABB createAABB(double x, double y, double z, double width, double height, double depth) {
        double minX = (x + 8) * MODEL_TO_BLOCK;
        double minY = y * MODEL_TO_BLOCK;
        double minZ = (z + 8) * MODEL_TO_BLOCK;
        double maxX = minX + width * MODEL_TO_BLOCK;
        double maxY = minY + height * MODEL_TO_BLOCK;
        double maxZ = minZ + depth * MODEL_TO_BLOCK;

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static VoxelShape getShapeForState(boolean isOpen, Direction facing) {
        List<AABB> hitboxes = new ArrayList<>();

        if (isOpen) {
            // Opened state: bottom base, top base, and screen
            hitboxes.add(BOTTOM_BASE);
            hitboxes.add(TOP_BASE);

            // Screen is positioned above the top base
            AABB screenBox = SCREEN.move(0, TOP_BASE.getYsize(), 0);
            hitboxes.add(screenBox);
        } else {
            // Closed state: bottom base and rotated top base
            hitboxes.add(BOTTOM_BASE);
            hitboxes.add(CLOSED_TOP_BASE);
        }

        // Combine all hitboxes and rotate to facing direction
        VoxelShape shape = Shapes.empty();
        for (AABB aabb : hitboxes) {
            shape = Shapes.or(shape, Shapes.create(rotateAABBToFacing(aabb, facing)));
        }

        return shape;
    }

    public static boolean isPointOnScreen(Vec3 point, boolean isOpen, Direction facing) {
        if (!isOpen) {
            return false; // Screen is not visible when closed
        }

        // Screen is positioned above the top base
        AABB screenBox = SCREEN.move(0, TOP_BASE.getYsize(), 0);

        // Rotate the screen box according to facing direction
        AABB rotatedScreenBox = rotateAABBToFacing(screenBox, facing)
                .inflate(0.0005); // Slight inflation to avoid precision miss on very thin plane

        // Check if the point is inside the screen box
        return rotatedScreenBox.contains(point);
    }

    private static AABB rotateAABBToFacing(AABB aabb, Direction facing) {
        // Fast path
        if (facing == Direction.NORTH) return aabb; // Base orientation assumed to be NORTH

        double minX = Double.POSITIVE_INFINITY;
        double minY = aabb.minY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = aabb.maxY;
        double maxZ = Double.NEGATIVE_INFINITY;

        double[] xs = {aabb.minX, aabb.maxX};
        double[] zs = {aabb.minZ, aabb.maxZ};

        for (double x : xs) {
            for (double z : zs) {
                double rx = x;
                double rz = z;
                switch (facing) {
                    case EAST: // 90 deg clockwise
                        rx = 1 - z;
                        rz = x;
                        break;
                    case SOUTH: // 180 deg
                        rx = 1 - x;
                        rz = 1 - z;
                        break;
                    case WEST: // 270 deg clockwise (or 90 ccw)
                        rx = z;
                        rz = 1 - x;
                        break;
                    default:
                        break;
                }
                if (rx < minX) minX = rx;
                if (rx > maxX) maxX = rx;
                if (rz < minZ) minZ = rz;
                if (rz > maxZ) maxZ = rz;
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
















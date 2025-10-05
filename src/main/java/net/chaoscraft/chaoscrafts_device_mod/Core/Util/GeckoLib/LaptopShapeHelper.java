package net.chaoscraft.chaoscrafts_device_mod.Core.Util.GeckoLib;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.core.Direction;
import java.util.HashMap;
import java.util.Map;

public class LaptopShapeHelper {
    private static final Map<Integer, VoxelShape> COLLISION_SHAPES = new HashMap<>();
    private static final Map<Integer, VoxelShape> OUTLINE_SHAPES = new HashMap<>();
    private static final int ANGLE_STEP = 5;
    private static final int MAX_ANGLE = 90;

    private static final double TOP_PART_PIVOT_X = 0.5;
    private static final double TOP_PART_PIVOT_Y = 1.0 / 16.0;
    private static final double TOP_PART_PIVOT_Z = 6.0 / 16.0;

    private static final double SCREEN_PIVOT_X = 0.5;
    private static final double SCREEN_PIVOT_Y = 1.75 / 16.0;
    private static final double SCREEN_PIVOT_Z = 6.5 / 16.0;

    private static final double BOTTOM_PART_PIVOT_X = 0.5;
    private static final double BOTTOM_PART_PIVOT_Y = 1.75 / 16.0;
    private static final double BOTTOM_PART_PIVOT_Z = 7.5 / 16.0;

    // Hinge position (back center)
    private static final double HINGE_X = 0.5;
    private static final double HINGE_Y = 2.0 / 16.0; // 2 pixels from bottom
    private static final double HINGE_Z = 0.0; // Back edge

    // Cube data from geometry file
    private static final AABB TOP_BASE_CUBE = new AABB(
            -7.0 / 16.0, 1.0 / 16.0, -5.0 / 16.0,
            7.0 / 16.0, 2.0 / 16.0, 6.0 / 16.0
    );

    private static final AABB SCREEN_CUBE = new AABB(
            -6.5 / 16.0, 1.0 / 16.0, -4.5 / 16.0,
            6.5 / 16.0, 1.1 / 16.0, 5.5 / 16.0
    );

    private static final AABB BOTTOM_BASE_CUBE = new AABB(
            -7.0 / 16.0, 0.0 / 16.0, -5.0 / 16.0,
            7.0 / 16.0, 1.0 / 16.0, 6.0 / 16.0
    );

    static {
        precomputeShapes();
    }

    private static void precomputeShapes() {
        for (int angle = 0; angle <= MAX_ANGLE; angle += ANGLE_STEP) {
            COLLISION_SHAPES.put(angle, computeShapeForAngle(angle, true));
            OUTLINE_SHAPES.put(angle, computeShapeForAngle(angle, false));
        }
    }

    private static VoxelShape computeShapeForAngle(int angle, boolean forCollision) {
        VoxelShape shape = Shapes.empty();

        // Always add bottom base (doesn't move)
        AABB bottomBase = adjustAABBToPivot(BOTTOM_BASE_CUBE, BOTTOM_PART_PIVOT_X, BOTTOM_PART_PIVOT_Y, BOTTOM_PART_PIVOT_Z);
        shape = Shapes.or(shape, Shapes.create(bottomBase));

        if (angle == 90) { // Closed
            // For closed state, add top base without rotation
            AABB topBase = adjustAABBToPivot(TOP_BASE_CUBE, TOP_PART_PIVOT_X, TOP_PART_PIVOT_Y, TOP_PART_PIVOT_Z);
            shape = Shapes.or(shape, Shapes.create(topBase));
        } else { // Open or partially open
            // Rotate the top base around the hinge
            AABB topBase = adjustAABBToPivot(TOP_BASE_CUBE, TOP_PART_PIVOT_X, TOP_PART_PIVOT_Y, TOP_PART_PIVOT_Z);
            AABB rotatedTopBase = rotateAABB(topBase, HINGE_X, HINGE_Y, HINGE_Z, angle);
            shape = Shapes.or(shape, Shapes.create(rotatedTopBase));

            // For outline, add the screen
            if (!forCollision) {
                AABB screen = adjustAABBToPivot(SCREEN_CUBE, SCREEN_PIVOT_X, SCREEN_PIVOT_Y, SCREEN_PIVOT_Z);
                AABB rotatedScreen = rotateAABB(screen, HINGE_X, HINGE_Y, HINGE_Z, angle);
                shape = Shapes.or(shape, Shapes.create(rotatedScreen));
            }
        }

        return shape;
    }

    private static AABB adjustAABBToPivot(AABB aabb, double pivotX, double pivotY, double pivotZ) {
        // Adjust AABB coordinates to account for pivot point
        double minX = aabb.minX + pivotX;
        double minY = aabb.minY + pivotY;
        double minZ = aabb.minZ + pivotZ;
        double maxX = aabb.maxX + pivotX;
        double maxY = aabb.maxY + pivotY;
        double maxZ = aabb.maxZ + pivotZ;

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static AABB rotateAABB(AABB aabb, double pivotX, double pivotY, double pivotZ, double angleDeg) {
        double angleRad = Math.toRadians(angleDeg);
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);

        // Get all 8 corners of the AABB
        double[] xs = {aabb.minX, aabb.maxX};
        double[] ys = {aabb.minY, aabb.maxY};
        double[] zs = {aabb.minZ, aabb.maxZ};

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        // Rotate each corner and find the new AABB bounds
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    // Translate to pivot origin
                    double tx = x - pivotX;
                    double ty = y - pivotY;
                    double tz = z - pivotZ;

                    // Rotate around X axis (hinge line)
                    double ry = ty * cos - tz * sin;
                    double rz = ty * sin + tz * cos;

                    // Translate back
                    double fx = tx + pivotX;
                    double fy = ry + pivotY;
                    double fz = rz + pivotZ;

                    // Update bounds
                    minX = Math.min(minX, fx);
                    minY = Math.min(minY, fy);
                    minZ = Math.min(minZ, fz);
                    maxX = Math.max(maxX, fx);
                    maxY = Math.max(maxY, fy);
                    maxZ = Math.max(maxZ, fz);
                }
            }
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static VoxelShape getCollisionShapeForAngle(double angle, Direction facing) {
        int snappedAngle = snapToNearestStep(angle, ANGLE_STEP);
        VoxelShape shape = COLLISION_SHAPES.get(snappedAngle);
        return shape != null ? rotateShapeToFacing(shape, facing) : Shapes.block();
    }

    public static VoxelShape getOutlineShapeForAngle(double angle, Direction facing) {
        int snappedAngle = snapToNearestStep(angle, ANGLE_STEP);
        VoxelShape shape = OUTLINE_SHAPES.get(snappedAngle);
        return shape != null ? rotateShapeToFacing(shape, facing) : Shapes.block();
    }

    private static int snapToNearestStep(double angle, int step) {
        return (int) (Math.round(angle / step) * step);
    }

    private static VoxelShape rotateShapeToFacing(VoxelShape shape, Direction facing) {
        if (facing == Direction.NORTH) return shape;

        // Convert shape to AABBs, rotate each, and recombine
        return shape.toAabbs().stream()
                .map(aabb -> rotateAabbByFacing(aabb, facing))
                .map(Shapes::create)
                .reduce(Shapes::or)
                .orElse(Shapes.block());
    }

    private static AABB rotateAabbByFacing(AABB aabb, Direction facing) {
        double cx = 0.5, cz = 0.5;
        double[] xs = {aabb.minX, aabb.maxX};
        double[] zs = {aabb.minZ, aabb.maxZ};
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        double angleDeg = switch (facing) {
            case NORTH -> 0.0;
            case EAST -> 90.0;
            case SOUTH -> 180.0;
            case WEST -> 270.0;
            default -> 0.0;
        };

        double a = Math.toRadians(angleDeg);
        double cos = Math.cos(a), sin = Math.sin(a);

        for (double x : xs) {
            for (double z : zs) {
                double tx = x - cx;
                double tz = z - cz;
                double rx = tx * cos - tz * sin;
                double rz = tx * sin + tz * cos;
                double fx = rx + cx;
                double fz = rz + cz;

                minX = Math.min(minX, fx);
                maxX = Math.max(maxX, fx);
                minZ = Math.min(minZ, fz);
                maxZ = Math.max(maxZ, fz);
            }
        }

        return new AABB(minX, aabb.minY, minZ, maxX, aabb.maxY, maxZ);
    }

    // Method to check if a point is on the screen
    public static boolean isPointOnScreen(Vec3 localPoint, double lidAngleDeg) {
        if (lidAngleDeg > 45) return false; // Screen not visible when mostly closed

        // Calculate the screen AABB at the given angle
        AABB screen = adjustAABBToPivot(SCREEN_CUBE, SCREEN_PIVOT_X, SCREEN_PIVOT_Y, SCREEN_PIVOT_Z);
        AABB screenAabb = rotateAABB(screen, HINGE_X, HINGE_Y, HINGE_Z, lidAngleDeg);

        // Check if the point is inside the screen AABB
        return screenAabb.contains(localPoint);
    }
}
package net.chaoscraft.chaoscrafts_device_mod.util;

import com.google.gson.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

/**
 * GeoModelHelper — computes a lid-angle-aware AABB for the laptop screen using
 * constants taken from the laptop.geo.json. This avoids runtime JSON parsing and
 * gives accurate screen hit detection by rotating the screen cube about the
 * top_part pivot.
 */
public class GeoModelHelper {
    // actual path inside resources (geckolib asset):
    private static final String GEO_PATH = "/assets/geckolib/laptop/laptop.geo.json";

    // simple cached bone cubes: boneName -> list of Cube entries (origin,size,pivot)
    private static final Map<String, List<Cube>> boneCubes = new HashMap<>();
    private static final Map<String, String> boneParent = new HashMap<>();
    // bone-level pivot (if present in geo) — boneName -> [x,y,z]
    private static final Map<String, double[]> bonePivot = new HashMap<>();
    private static volatile boolean parsed = false;

    private static class Cube { double ox, oy, oz; double sx, sy, sz; double pvx, pvy, pvz; double rotX, rotY, rotZ; }

    private static void parseGeo() {
        if (parsed) return;
        synchronized (GeoModelHelper.class) {
            if (parsed) return;
            try (Reader r = new InputStreamReader(GeoModelHelper.class.getResourceAsStream(GEO_PATH))) {
                JsonElement root = JsonParser.parseReader(r);
                JsonObject obj = root.getAsJsonObject();
                JsonArray geom = obj.getAsJsonArray("minecraft:geometry");
                if (geom == null || geom.size() == 0) { parsed = true; return; }
                JsonObject geom0 = geom.get(0).getAsJsonObject();
                JsonArray bones = geom0.getAsJsonArray("bones");
                if (bones == null) { parsed = true; return; }

                for (JsonElement be : bones) {
                    JsonObject bo = be.getAsJsonObject();
                    String name = bo.has("name") ? bo.get("name").getAsString() : null;
                    if (name == null) continue;
                    if (bo.has("parent")) boneParent.put(name, bo.get("parent").getAsString());
                    // parse bone-level pivot if present
                    if (bo.has("pivot")) {
                        JsonArray pv = bo.getAsJsonArray("pivot");
                        if (pv != null && pv.size() >= 3) {
                            double[] arr = new double[3];
                            arr[0] = pv.get(0).getAsDouble(); arr[1] = pv.get(1).getAsDouble(); arr[2] = pv.get(2).getAsDouble();
                            bonePivot.put(name, arr);
                        }
                    }
                    if (!bo.has("cubes")) continue;
                    JsonArray cubes = bo.getAsJsonArray("cubes");
                    List<Cube> list = new ArrayList<>();
                    for (JsonElement ce : cubes) {
                        JsonObject co = ce.getAsJsonObject();
                        JsonArray origin = co.getAsJsonArray("origin");
                        JsonArray size = co.getAsJsonArray("size");
                        JsonArray pivot = co.has("pivot") ? co.getAsJsonArray("pivot") : null;
                        JsonArray rotation = co.has("rotation") ? co.getAsJsonArray("rotation") : null;
                        Cube c = new Cube();
                        c.ox = origin.get(0).getAsDouble(); c.oy = origin.get(1).getAsDouble(); c.oz = origin.get(2).getAsDouble();
                        c.sx = size.get(0).getAsDouble(); c.sy = size.get(1).getAsDouble(); c.sz = size.get(2).getAsDouble();
                        if (pivot != null) { c.pvx = pivot.get(0).getAsDouble(); c.pvy = pivot.get(1).getAsDouble(); c.pvz = pivot.get(2).getAsDouble(); }
                        else { c.pvx = c.pvy = c.pvz = 0.0; }
                        if (rotation != null) { c.rotX = rotation.get(0).getAsDouble(); c.rotY = rotation.get(1).getAsDouble(); c.rotZ = rotation.get(2).getAsDouble(); }
                        else { c.rotX = c.rotY = c.rotZ = 0.0; }
                        list.add(c);
                    }
                    boneCubes.put(name, list);
                }
            } catch (Exception e) {
                // ignore parsing errors — we'll fall back to previous constants
            }
            parsed = true;
        }
    }

    // compute AABB for a named bone rotated by lidAngle around top_part pivot if applicable
    public static AABB getBoneAabb(String boneName, double lidAngleDegrees) {
        parseGeo();
        List<Cube> cubes = boneCubes.get(boneName);
        if (cubes == null || cubes.isEmpty()) {
            // fallback: if boneName is "screen", use old computation
            if ("screen".equals(boneName)) return computeAabbForAngle(lidAngleDegrees);
            // fallback small box
            return new AABB(0.12, 0.55, 0.18, 0.88, 0.92, 0.82);
        }

        // accumulate pivots up the parent chain to get bone offset
        double accX = 0, accY = 0, accZ = 0;
        String cur = boneName;
        while (cur != null) {
            double[] bp = bonePivot.get(cur);
            if (bp != null) { accX += bp[0]; accY += bp[1]; accZ += bp[2]; }
            cur = boneParent.get(cur);
        }

        // Use top_part pivot if exists for rotation
        double topPivotX = 0.0, topPivotY = 1.0, topPivotZ = 6.0; // fallback values
        double[] tpv = bonePivot.get("top_part");
        if (tpv != null) { topPivotX = tpv[0]; topPivotY = tpv[1]; topPivotZ = tpv[2]; }
        try {
            // if parsed, attempt to read top_part cube pivot
            List<Cube> topCubes = boneCubes.get("top_part");
            if (topCubes != null && !topCubes.isEmpty()) {
                Cube tc = topCubes.get(0);
                topPivotX = tc.pvx; topPivotY = tc.pvy; topPivotZ = tc.pvz;
            }
        } catch (Exception ignored) {}

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        double angleRad = Math.toRadians(-lidAngleDegrees);
        double cos = Math.cos(angleRad), sin = Math.sin(angleRad);

        for (Cube c : cubes) {
            double ox = c.ox, oy = c.oy, oz = c.oz, sx = c.sx, sy = c.sy, sz = c.sz;
            double pvx = c.pvx, pvy = c.pvy, pvz = c.pvz;
            double rX = Math.toRadians(c.rotX), rY = Math.toRadians(c.rotY), rZ = Math.toRadians(c.rotZ);
            double[] dx = {0.0, sx};
            double[] dy = {0.0, sy};
            double[] dz = {0.0, sz};
            for (double xi : dx) for (double yi : dy) for (double zi : dz) {
                double cx = ox + xi;
                double cy = oy + yi;
                double cz = oz + zi;
                // apply cube pivot rotation (rotate point around cube pivot by cube's rotation)
                double relX = cx - pvx;
                double relY = cy - pvy;
                double relZ = cz - pvz;
                // rotate X
                double ry = relY * Math.cos(rX) - relZ * Math.sin(rX);
                double rz = relY * Math.sin(rX) + relZ * Math.cos(rX);
                double rx = relX;
                // rotate Y
                double rz2 = rz * Math.cos(rY) - rx * Math.sin(rY);
                double rx2 = rz * Math.sin(rY) + rx * Math.cos(rY);
                double ry2 = ry;
                // rotate Z
                double rx3 = rx2 * Math.cos(rZ) - ry2 * Math.sin(rZ);
                double ry3 = rx2 * Math.sin(rZ) + ry2 * Math.cos(rZ);
                double rz3 = rz2;
                double worldX = accX + pvx + (rx3 + pvx);
                double worldY = accY + pvy + (ry3 + pvy);
                double worldZ = accZ + pvz + (rz3 + pvz);

                // rotate around top pivot
                double tx = worldX - topPivotX;
                double ty = worldY - topPivotY;
                double tz = worldZ - topPivotZ;
                double ry2p = ty * cos - tz * sin;
                double rz2p = ty * sin + tz * cos;
                double fx = tx + topPivotX;
                double fy = ry2p + topPivotY;
                double fz = rz2p + topPivotZ;

                double bx = fx / 16.0 + 0.5;
                double by = fy / 16.0;
                double bz = fz / 16.0 + 0.5;

                if (bx < minX) minX = bx; if (bx > maxX) maxX = bx;
                if (by < minY) minY = by; if (by > maxY) maxY = by;
                if (bz < minZ) minZ = bz; if (bz > maxZ) maxZ = bz;
            }
        }

        double pad = 0.01;
        return new AABB(minX - pad, minY - pad, minZ - pad, maxX + pad, maxY + pad, maxZ + pad);
    }

    // New: return per-cube AABBs for a bone (no extra padding) so shapes can match model exactly
    public static List<AABB> getBoneCubeAabbs(String boneName, double lidAngleDegrees) {
        parseGeo();
        List<Cube> cubes = boneCubes.get(boneName);
        if (cubes == null || cubes.isEmpty()) {
            // fallback to single AABB for screen
            AABB f = getBoneAabb(boneName, lidAngleDegrees);
            return Collections.singletonList(f);
        }

        // Use same pivot logic as getBoneAabb
        double topPivotX = 0.0, topPivotY = 1.0, topPivotZ = 6.0;
        double[] tpv2 = bonePivot.get("top_part");
        if (tpv2 != null) { topPivotX = tpv2[0]; topPivotY = tpv2[1]; topPivotZ = tpv2[2]; }
        try {
            List<Cube> topCubes = boneCubes.get("top_part");
            if (topCubes != null && !topCubes.isEmpty()) {
                Cube tc = topCubes.get(0);
                if (tc.pvx != 0.0 || tc.pvy != 0.0 || tc.pvz != 0.0) {
                    topPivotX = tc.pvx; topPivotY = tc.pvy; topPivotZ = tc.pvz;
                }
            }
        } catch (Exception ignored) {}

        double angleRad = Math.toRadians(-lidAngleDegrees);
        double cos = Math.cos(angleRad), sin = Math.sin(angleRad);

        List<AABB> out = new ArrayList<>();
        for (Cube c : cubes) {
            double ox = c.ox, oy = c.oy, oz = c.oz, sx = c.sx, sy = c.sy, sz = c.sz;
            double pvx = c.pvx, pvy = c.pvy, pvz = c.pvz;
            double rX = Math.toRadians(c.rotX), rY = Math.toRadians(c.rotY), rZ = Math.toRadians(c.rotZ);

            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

            double[] dx = {0.0, sx};
            double[] dy = {0.0, sy};
            double[] dz = {0.0, sz};
            for (double xi : dx) for (double yi : dy) for (double zi : dz) {
                double cx = ox + xi;
                double cy = oy + yi;
                double cz = oz + zi;
                // apply cube pivot rotation
                double relX = cx - pvx;
                double relY = cy - pvy;
                double relZ = cz - pvz;
                double ry = relY * Math.cos(rX) - relZ * Math.sin(rX);
                double rz = relY * Math.sin(rX) + relZ * Math.cos(rX);
                double rx = relX;
                double rz2 = rz * Math.cos(rY) - rx * Math.sin(rY);
                double rx2 = rz * Math.sin(rY) + rx * Math.cos(rY);
                double ry2 = ry;
                double rx3 = rx2 * Math.cos(rZ) - ry2 * Math.sin(rZ);
                double ry3 = rx2 * Math.sin(rZ) + ry2 * Math.cos(rZ);
                double rz3 = rz2;
                double worldX = pvx + (rx3 + pvx);
                double worldY = pvy + (ry3 + pvy);
                double worldZ = pvz + (rz3 + pvz);

                // rotate around top pivot
                double tx = worldX - topPivotX;
                double ty = worldY - topPivotY;
                double tz = worldZ - topPivotZ;
                double ry2p = ty * cos - tz * sin;
                double rz2p = ty * sin + tz * cos;
                double fx = tx + topPivotX;
                double fy = ry2p + topPivotY;
                double fz = rz2p + topPivotZ;

                double bx = fx / 16.0 + 0.5;
                double by = fy / 16.0;
                double bz = fz / 16.0 + 0.5;

                if (bx < minX) minX = bx; if (bx > maxX) maxX = bx;
                if (by < minY) minY = by; if (by > maxY) maxY = by;
                if (bz < minZ) minZ = bz; if (bz > maxZ) maxZ = bz;
            }

            // construct AABB for this cube (no padding)
            AABB a = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            out.add(a);
        }
        return out;
    }

    // New: test whether a model-local point is inside a named bone's cubes (accurate to cubes)
    public static boolean isPointInBone(Vec3 local, String boneName, double lidAngleDegrees) {
        if (local == null) return false;
        List<AABB> aabbs = getBoneCubeAabbs(boneName, lidAngleDegrees);
        for (AABB a : aabbs) {
            if (local.x >= a.minX && local.x <= a.maxX
                    && local.y >= a.minY && local.y <= a.maxY
                    && local.z >= a.minZ && local.z <= a.maxZ) return true;
        }
        return false;
    }

    // keep previous computeAabbForAnglePublic signature for backward compatibility
    public static AABB computeAabbForAnglePublic(double lidAngleDegrees) {
        return getBoneAabb("screen", lidAngleDegrees);
    }

    // previous fallback compute (kept private for compatibility)
    private static AABB computeAabbForAngle(double lidAngleDegrees) {
        // same conservative fallback as before
        return new AABB(0.12, 0.55, 0.18, 0.88, 0.92, 0.82);
    }

    public static boolean isPointOnScreen(Vec3 local, double lidAngleDegrees) {
        if (local == null) return false;
        AABB aabb = getBoneAabb("screen", lidAngleDegrees);
        return local.x >= aabb.minX && local.x <= aabb.maxX
                && local.y >= aabb.minY && local.y <= aabb.maxY
                && local.z >= aabb.minZ && local.z <= aabb.maxZ;
    }

    public static boolean isPointOnScreen(Vec3 local) {
        return isPointOnScreen(local, 90.0);
    }

    public static AABB getScreenAABB() {
        return getBoneAabb("screen", 90.0);
    }
}

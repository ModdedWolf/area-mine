package net.xai.area_enchant;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.List;

public class MiningPattern {
    
    public static List<BlockPos> getBlocksToMine(String pattern, BlockPos center, Direction face, 
                                                  int horizontal, int vertical, int depth) {
        return switch (pattern.toLowerCase()) {
            case "sphere" -> {
                // Use the max dimension as radius (config already has correct values)
                int radius = Math.max(horizontal, Math.max(vertical, depth));
                yield getSpherePattern(center, radius);
            }
            case "tunnel" -> getTunnelPattern(center, face, horizontal, vertical, depth);
            case "cross" -> getCrossPattern(center, face, horizontal, vertical, depth);
            case "layer" -> getLayerPattern(center, horizontal, depth);
            case "vertical" -> getVerticalPattern(center, horizontal, vertical);
            default -> getCubePattern(center, face, horizontal, vertical, depth);
        };
    }
    
    // Original cube pattern (biased towards mining direction)
    public static List<BlockPos> getCubePattern(BlockPos center, Direction face, 
                                                 int horizontal, int vertical, int depth) {
        List<BlockPos> blocks = new ArrayList<>();
        
        Direction depthDir = face.getOpposite();
        Direction.Axis depthAxis = depthDir.getAxis();
        int depthOffset = switch (depthAxis) {
            case X -> depthDir.getOffsetX();
            case Y -> depthDir.getOffsetY();
            case Z -> depthDir.getOffsetZ();
        };
        
        int minX = center.getX();
        int maxX = center.getX();
        int minY = center.getY();
        int maxY = center.getY();
        int minZ = center.getZ();
        int maxZ = center.getZ();
        
        // Depth (biased extension into the block face)
        int depthComp = switch (depthAxis) {
            case X -> center.getX();
            case Y -> center.getY();
            case Z -> center.getZ();
        };
        int extend = depth - 1;
        int depthEnd = depthComp + extend * depthOffset;
        int depthMin = Math.min(depthComp, depthEnd);
        int depthMax = Math.max(depthComp, depthEnd);
        switch (depthAxis) {
            case X -> { minX = depthMin; maxX = depthMax; }
            case Y -> { minY = depthMin; maxY = depthMax; }
            case Z -> { minZ = depthMin; maxZ = depthMax; }
        }
        
        // Perpendicular axes (centered)
        for (Direction.Axis perpAxis : Direction.Axis.values()) {
            if (perpAxis == depthAxis) continue;
            int pSize = (perpAxis == Direction.Axis.Y) ? vertical : horizontal;
            int lower = getLower(pSize);
            int upper = getUpper(pSize);
            int pComp = switch (perpAxis) {
                case X -> center.getX();
                case Y -> center.getY();
                case Z -> center.getZ();
            };
            int pMin = pComp + lower;
            int pMax = pComp + upper;
            switch (perpAxis) {
                case X -> { minX = pMin; maxX = pMax; }
                case Y -> { minY = pMin; maxY = pMax; }
                case Z -> { minZ = pMin; maxZ = pMax; }
            }
        }
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!pos.equals(center)) {
                        blocks.add(pos);
                    }
                }
            }
        }
        
        return blocks;
    }
    
    // Sphere pattern - mines in a spherical radius
    public static List<BlockPos> getSpherePattern(BlockPos center, int radius) {
        List<BlockPos> blocks = new ArrayList<>();
        int radiusSquared = radius * radius;
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radiusSquared) {
                        BlockPos pos = center.add(x, y, z);
                        if (!pos.equals(center)) {
                            blocks.add(pos);
                        }
                    }
                }
            }
        }
        
        return blocks;
    }
    
    // Tunnel pattern - mines a long corridor forward
    public static List<BlockPos> getTunnelPattern(BlockPos center, Direction face, 
                                                   int width, int height, int length) {
        List<BlockPos> blocks = new ArrayList<>();
        Direction forward = face.getOpposite();
        
        // Get perpendicular directions
        Direction right, up;
        if (face.getAxis() == Direction.Axis.Y) {
            // Looking up/down
            right = Direction.EAST;
            up = Direction.NORTH;
        } else {
            right = face.rotateYClockwise();
            up = Direction.UP;
        }
        
        // Calculate centered ranges: width=3 means 3 blocks total (-1, 0, +1)
        int widthLower = getLower(width);
        int widthUpper = getUpper(width);
        int heightLower = getLower(height);
        int heightUpper = getUpper(height);
        
        for (int d = 0; d < length; d++) {
            for (int w = widthLower; w <= widthUpper; w++) {
                for (int h = heightLower; h <= heightUpper; h++) {
                    BlockPos pos = center
                        .offset(forward, d)
                        .offset(right, w)
                        .offset(up, h);
                    if (!pos.equals(center)) {
                        blocks.add(pos);
                    }
                }
            }
        }
        
        return blocks;
    }
    
    // Cross/Plus pattern - mines in a + shape (good for finding ores)
    public static List<BlockPos> getCrossPattern(BlockPos center, Direction face, 
                                                  int horizontal, int vertical, int depth) {
        List<BlockPos> blocks = new ArrayList<>();
        Direction forward = face.getOpposite();
        
        // Mine forward
        for (int d = 1; d <= depth; d++) {
            blocks.add(center.offset(forward, d));
        }
        
        // Mine horizontally (left/right)
        // Calculate how far to extend on each side: horizontal=7 means ~3 blocks each side
        Direction right = (face.getAxis() == Direction.Axis.Y) ? 
            Direction.EAST : face.rotateYClockwise();
        Direction left = right.getOpposite();
        
        int horizontalReach = (horizontal - 1) / 2; // e.g., 7 -> 3 blocks each side
        for (int h = 1; h <= horizontalReach; h++) {
            blocks.add(center.offset(right, h));
            blocks.add(center.offset(left, h));
        }
        
        // Mine vertically (up/down)
        int verticalReach = (vertical - 1) / 2; // e.g., 3 -> 1 block each direction
        for (int v = 1; v <= verticalReach; v++) {
            blocks.add(center.up(v));
            blocks.add(center.down(v));
        }
        
        return blocks;
    }
    
    // Layer pattern - mines a flat horizontal layer at the target block's Y level
    public static List<BlockPos> getLayerPattern(BlockPos center, int radius, int depth) {
        List<BlockPos> blocks = new ArrayList<>();
        
        // Calculate centered ranges for horizontal dimensions
        int radiusLower = getLower(radius);
        int radiusUpper = getUpper(radius);
        int depthLower = getLower(depth);
        int depthUpper = getUpper(depth);
        
        // Mine only at the target block's Y level (single flat layer)
        for (int x = radiusLower; x <= radiusUpper; x++) {
            for (int z = depthLower; z <= depthUpper; z++) {
                BlockPos pos = center.add(x, 0, z);  // Y stays at 0 (target level)
                if (!pos.equals(center)) {
                    blocks.add(pos);
                }
            }
        }
        
        return blocks;
    }
    
    // Vertical pattern - mines straight up and down
    public static List<BlockPos> getVerticalPattern(BlockPos center, int radius, int height) {
        List<BlockPos> blocks = new ArrayList<>();
        
        // Calculate centered ranges: radius=1 means 1 block total (0), height=12 means -6 to +5
        int radiusLower = getLower(radius);
        int radiusUpper = getUpper(radius);
        int heightLower = getLower(height);
        int heightUpper = getUpper(height);
        
        for (int x = radiusLower; x <= radiusUpper; x++) {
            for (int z = radiusLower; z <= radiusUpper; z++) {
                for (int y = heightLower; y <= heightUpper; y++) {
                    if (y == 0 && x == 0 && z == 0) continue;
                    blocks.add(center.add(x, y, z));
                }
            }
        }
        
        return blocks;
    }
    
    private static int getLower(int size) {
        return -((size - 1) / 2);
    }
    
    private static int getUpper(int size) {
        return size / 2;
    }
}


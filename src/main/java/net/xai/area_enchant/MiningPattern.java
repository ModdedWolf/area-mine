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
    
    // Cube pattern - true centered cube around the broken block
    // For 3x3x3: -1, 0, +1 in all directions (centered on broken block)
    public static List<BlockPos> getCubePattern(BlockPos center, Direction face, 
                                                 int horizontal, int vertical, int depth) {
        List<BlockPos> blocks = new ArrayList<>();
        
        // The mining face is the direction the player is looking (the face of the block being mined)
        Direction forward = face;
        
        // Get perpendicular directions for horizontal and vertical
        Direction right, up;
        if (face.getAxis() == Direction.Axis.Y) {
            // Looking up/down
            right = Direction.EAST;
            up = Direction.NORTH;
        } else {
            right = face.rotateYClockwise();
            up = Direction.UP;
        }
        
        // For a 3x3x3 cube: we need exactly 3 blocks in each dimension
        // The broken block is at the center of the first layer
        // Horizontal: -1, 0, +1 (3 blocks centered)
        // Vertical: -1, 0, +1 (3 blocks centered)
        // Depth: 0, 1, 2 (3 layers forward from the broken block)
        
        int hLower = getLower(horizontal);  // For 3: -1
        int hUpper = getUpper(horizontal);  // For 3: 1
        int vLower = getLower(vertical);    // For 3: -1
        int vUpper = getUpper(vertical);    // For 3: 1
        int dLower = 0;                      // Start at the broken block layer
        int dUpper = depth - 1;              // For 3: 0, 1, 2 (3 layers)
        
        // Create cube: exactly the specified dimensions
        // NOTE: We include the center block in the pattern (27 blocks for 3x3x3)
        // AreaMineHandler will skip the center block when breaking, so 26 blocks will be broken
        for (int d = dLower; d <= dUpper; d++) {
            for (int h = hLower; h <= hUpper; h++) {
                for (int v = vLower; v <= vUpper; v++) {
                    BlockPos pos = center
                        .offset(forward, d)  // Forward offset (0 = current layer, 1 = one forward, etc.)
                        .offset(right, h)    // Horizontal offset (-1, 0, +1)
                        .offset(up, v);       // Vertical offset (-1, 0, +1)
                    
                        blocks.add(pos);
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
        Direction forward = face; // Forward in the direction the player is looking
        
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
        
        // Tunnel extends forward: skip layer 0 (broken block), then forward for length-1 layers
        for (int d = 1; d < length; d++) {
            for (int w = widthLower; w <= widthUpper; w++) {
                for (int h = heightLower; h <= heightUpper; h++) {
                    BlockPos pos = center
                        .offset(forward, d)  // Forward d blocks
                        .offset(right, w)    // Horizontal offset
                        .offset(up, h);       // Vertical offset
                        blocks.add(pos);
                    }
                }
        }
        
        // Also add the width x height grid at layer 0 (the broken block's layer)
        // But skip the center block itself
        for (int w = widthLower; w <= widthUpper; w++) {
            for (int h = heightLower; h <= heightUpper; h++) {
                // Skip the center (0, 0)
                if (w == 0 && h == 0) {
                    continue;
                }
                BlockPos pos = center
                    .offset(right, w)
                    .offset(up, h);
                blocks.add(pos);
            }
        }
        
        return blocks;
    }
    
    // Cross/Plus pattern - mines in a + shape (good for finding ores)
    public static List<BlockPos> getCrossPattern(BlockPos center, Direction face, 
                                                  int horizontal, int vertical, int depth) {
        List<BlockPos> blocks = new ArrayList<>();
        Direction forward = face; // Forward in the direction the player is looking
        
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



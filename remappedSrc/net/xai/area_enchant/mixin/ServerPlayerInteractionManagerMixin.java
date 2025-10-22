package net.xai.area_enchant.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow public abstract boolean breakBlock(BlockPos pos);

    @Shadow public ServerWorld world;

    @Shadow public ServerPlayerEntity player;

    private Direction miningFace;
    private boolean isBreakingArea = false;

    @Inject(method = "processBlockBreakingAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;breakBlock(Lnet/minecraft/util/math/BlockPos;)Z", shift = At.Shift.BEFORE))
    private void captureMiningFace(BlockPos pos, PlayerActionC2SPacket.Action action, Direction face, int maxUpdateDepth, int sequence, CallbackInfo ci) {
        if (action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            miningFace = face;
        }
    }

    @Inject(method = "breakBlock", at = @At("RETURN"))
    private void onBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || isBreakingArea || miningFace == null) {
            miningFace = null;
            return;
        }

        var stack = player.getMainHandStack();
        RegistryEntry<net.minecraft.enchantment.Enchantment> entry = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(AreaEnchantMod.AREA_MINE).orElse(null);
        if (entry == null) {
            miningFace = null;
            return;
        }
        int level = EnchantmentHelper.getLevel(entry, stack);
        if (level <= 0) {
            miningFace = null;
            return;
        }

        var sizeConfig = AreaEnchantMod.config.levels.getOrDefault(level, new AreaEnchantMod.Size(level, level, level));
        int horizontalSize = sizeConfig.horizontal;
        int verticalSize = sizeConfig.vertical;
        int depthSize = sizeConfig.depth;

        Direction depthDir = miningFace.getOpposite();
        Direction.Axis depthAxis = depthDir.getAxis();
        int depthOffset = switch (depthAxis) {
            case X -> depthDir.getOffsetX();
            case Y -> depthDir.getOffsetY();
            case Z -> depthDir.getOffsetZ();
        };

        int minX = pos.getX();
        int maxX = pos.getX();
        int minY = pos.getY();
        int maxY = pos.getY();
        int minZ = pos.getZ();
        int maxZ = pos.getZ();

        // Depth (biased extension)
        int depthComp = switch (depthAxis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
        int extend = depthSize - 1;
        int depthEnd = depthComp + extend * depthOffset;
        int depthMin = Math.min(depthComp, depthEnd);
        int depthMax = Math.max(depthComp, depthEnd);
        switch (depthAxis) {
            case X -> { minX = depthMin; maxX = depthMax; }
            case Y -> { minY = depthMin; maxY = depthMax; }
            case Z -> { minZ = depthMin; maxZ = depthMax; }
        }

        // Perpendicular axes (centered-ish)
        for (var perpAxis : Direction.Axis.values()) {
            if (perpAxis == depthAxis) continue;
            int pSize = (perpAxis == Direction.Axis.Y) ? verticalSize : horizontalSize;
            int lower = getLower(pSize);
            int upper = getUpper(pSize);
            int pComp = switch (perpAxis) {
                case X -> pos.getX();
                case Y -> pos.getY();
                case Z -> pos.getZ();
            };
            int pMin = pComp + lower;
            int pMax = pComp + upper;
            switch (perpAxis) {
                case X -> { minX = pMin; maxX = pMax; }
                case Y -> { minY = pMin; maxY = pMax; }
                case Z -> { minZ = pMin; maxZ = pMax; }
            }
        }

        isBreakingArea = true;
        try {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos otherPos = new BlockPos(x, y, z);
                        if (otherPos.equals(pos)) continue;
                        if (breakBlock(otherPos)) {
                            stack.damage(1, player, EquipmentSlot.MAINHAND);
                        }
                    }
                }
            }
        } finally {
            isBreakingArea = false;
            miningFace = null;
        }
    }

    private int getLower(int size) {
        return -((size - 1) / 2);
    }

    private int getUpper(int size) {
        return size / 2;
    }
}
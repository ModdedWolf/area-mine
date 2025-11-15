package net.xai.area_enchant.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class TestMixin {
    static {
        System.out.println("[Area Mine] TestMixin class loaded!");
        System.out.println("[Area Mine] TestMixin Thread: " + Thread.currentThread().getName());
    }
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        // This should fire every tick if mixins work - but only log once per second to avoid spam
        long currentTime = System.currentTimeMillis();
        if (currentTime % 1000 < 50) { // Log roughly once per second
            System.out.println("[Area Mine] TestMixin.onTick called - MIXINS ARE WORKING! Thread: " + Thread.currentThread().getName());
        }
    }
}


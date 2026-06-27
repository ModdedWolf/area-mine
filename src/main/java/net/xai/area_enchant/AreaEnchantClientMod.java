package net.xai.area_enchant;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class AreaEnchantClientMod implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        System.out.println("[Area Mine] Client-side mod initialized");
        
        // Register client tick event for particle preview
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null) {
                // Show preview particles when looking at blocks while crouching
                MinecraftPreview.showPreviewParticles(client);
            }
        });
        
        net.xai.area_enchant.network.NetworkHandler.registerClientReceiver();
        
        // Simple mode: open upgrades UI when server sends upgrade list
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            net.xai.area_enchant.network.NetworkHandler.OpenUpgradesUiPayload.ID,
            (payload, context) -> context.client().execute(() -> 
                net.xai.area_enchant.client.UpgradesScreen.open(payload.entries()))
        );
        
        System.out.println("[Area Mine] Particle preview enabled!");
        System.out.println("[Area Mine] Client-server pattern sync enabled!");
    }
}

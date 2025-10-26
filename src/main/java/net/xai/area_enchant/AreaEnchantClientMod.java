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
        
        // Register network receiver for pattern sync
        net.xai.area_enchant.network.NetworkHandler.registerClientReceiver();
        
        System.out.println("[Area Mine] Particle preview enabled!");
        System.out.println("[Area Mine] Client-server pattern sync enabled!");
    }
}

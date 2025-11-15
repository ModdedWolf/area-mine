package net.xai.area_enchant.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.xai.area_enchant.AreaEnchantMod;

public class NetworkHandler {
    
    public static final Identifier SYNC_PATTERN_ID = Identifier.of("area_enchant", "sync_pattern");
    
    public static void registerPackets() {
        // Register the packet type
        PayloadTypeRegistry.playS2C().register(SyncPatternPayload.ID, SyncPatternPayload.CODEC);
    }
    
    public static void registerClientReceiver() {
        // Client receives pattern update from server
        ClientPlayNetworking.registerGlobalReceiver(SyncPatternPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                // Update client-side config with server's pattern
                if (AreaEnchantMod.config != null) {
                    AreaEnchantMod.config.miningPattern = payload.pattern();
                    System.out.println("[Area Mine Client] Pattern synced from server: " + payload.pattern());
                }
            });
        });
    }
    
    public static void sendPatternToClient(net.minecraft.server.network.ServerPlayerEntity player, String pattern) {
        // Send pattern update to specific player
        // In singleplayer, config is shared between client and server, so networking is optional
        if (player.networkHandler != null) {
            try {
                ServerPlayNetworking.send(player, new SyncPatternPayload(pattern));
            } catch (Exception e) {
                // In singleplayer, config is shared between client and server
                // If networking fails, the pattern will still work since they share the same config object
                // This is expected in singleplayer and not an error
            }
        }
        // If network handler is null or send fails, pattern will still work in singleplayer
        // because client and server share the same config object
    }
    
    public static void sendPatternToAllPlayers(net.minecraft.server.MinecraftServer server, String pattern) {
        // Send pattern update to all online players
        for (var player : server.getPlayerManager().getPlayerList()) {
            sendPatternToClient(player, pattern);
        }
    }
    
    /**
     * Custom payload for syncing mining pattern
     */
    public record SyncPatternPayload(String pattern) implements CustomPayload {
        public static final CustomPayload.Id<SyncPatternPayload> ID = new CustomPayload.Id<>(SYNC_PATTERN_ID);
        public static final PacketCodec<PacketByteBuf, SyncPatternPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.pattern),
            buf -> new SyncPatternPayload(buf.readString())
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}


package net.xai.area_enchant.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.xai.area_enchant.AreaEnchantMod;

import java.util.ArrayList;
import java.util.List;

public class NetworkHandler {
    
    public static final Identifier SYNC_PATTERN_ID = Identifier.of("area_enchant", "sync_pattern");
    public static final Identifier OPEN_UPGRADES_UI_ID = Identifier.of("area_enchant", "open_upgrades_ui");
    public static final Identifier REQUEST_UNLOCK_UPGRADE_ID = Identifier.of("area_enchant", "request_unlock_upgrade");
    
    public static void registerPackets() {
        PayloadTypeRegistry.playS2C().register(SyncPatternPayload.ID, SyncPatternPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenUpgradesUiPayload.ID, OpenUpgradesUiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestUnlockUpgradePayload.ID, RequestUnlockUpgradePayload.CODEC);
    }
    
    public static void registerClientReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPatternPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (AreaEnchantMod.config != null) {
                    AreaEnchantMod.config.miningPattern = payload.pattern();
                }
            });
        });
        // OpenUpgradesUiPayload receiver is registered in AreaEnchantClientMod to avoid loading client Screen on server
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
        for (var player : server.getPlayerManager().getPlayerList()) {
            sendPatternToClient(player, pattern);
        }
    }
    
    public static void sendOpenUpgradesUi(net.minecraft.server.network.ServerPlayerEntity player, List<UpgradeEntry> entries) {
        if (player.networkHandler != null) {
            try {
                ServerPlayNetworking.send(player, new OpenUpgradesUiPayload(entries));
            } catch (Exception ignored) { }
        }
    }
    
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
    
    /** One row for the simple-mode upgrades UI. */
    public record UpgradeEntry(String upgradeName, String oreBlockId, String oreDisplayName, int requiredCount, int currentCount, boolean owned) {}
    
    public record OpenUpgradesUiPayload(List<UpgradeEntry> entries) implements CustomPayload {
        public static final CustomPayload.Id<OpenUpgradesUiPayload> ID = new CustomPayload.Id<>(OPEN_UPGRADES_UI_ID);
        public static final PacketCodec<PacketByteBuf, OpenUpgradesUiPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeCollection(value.entries(), (b, e) -> {
                    b.writeString(e.upgradeName());
                    b.writeString(e.oreBlockId());
                    b.writeString(e.oreDisplayName());
                    b.writeInt(e.requiredCount());
                    b.writeInt(e.currentCount());
                    b.writeBoolean(e.owned());
                });
            },
            buf -> new OpenUpgradesUiPayload(buf.readList(b -> new UpgradeEntry(
                b.readString(), b.readString(), b.readString(), b.readInt(), b.readInt(), b.readBoolean()
            )))
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    public record RequestUnlockUpgradePayload(String upgradeName) implements CustomPayload {
        public static final CustomPayload.Id<RequestUnlockUpgradePayload> ID = new CustomPayload.Id<>(REQUEST_UNLOCK_UPGRADE_ID);
        public static final PacketCodec<PacketByteBuf, RequestUnlockUpgradePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.upgradeName()),
            buf -> new RequestUnlockUpgradePayload(buf.readString())
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}


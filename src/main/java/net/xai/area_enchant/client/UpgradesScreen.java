package net.xai.area_enchant.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.xai.area_enchant.network.NetworkHandler;
import org.joml.Vector2i;

import java.util.List;

/**
 * Client-only screen for simple-mode upgrades (ore-based).
 * Opened when the server sends OpenUpgradesUiPayload.
 */
public class UpgradesScreen extends Screen {

    private final List<NetworkHandler.UpgradeEntry> entries;
    private Text feedbackMessage;
    private long feedbackUntil;

    private UpgradesScreen(List<NetworkHandler.UpgradeEntry> entries) {
        super(Text.literal("Area Mine – Upgrades"));
        this.entries = entries;
    }

    public static void open(List<NetworkHandler.UpgradeEntry> entries) {
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen == null || !(net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof UpgradesScreen)) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new UpgradesScreen(entries));
        }
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int buttonWidth = 220;
        int buttonHeight = 22;
        int startY = 50;
        int gap = 26;
        for (int i = 0; i < entries.size(); i++) {
            NetworkHandler.UpgradeEntry e = entries.get(i);
            String label = formatButtonLabel(e);
            int y = startY + i * gap;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> onUpgradeClick(e))
                .dimensions(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight)
                .build();
            addDrawableChild(btn);
        }
        int doneY = startY + entries.size() * gap + 10;
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> closeScreen())
            .dimensions(centerX - 50, doneY, 100, 20)
            .build());
    }

    private static String formatButtonLabel(NetworkHandler.UpgradeEntry e) {
        String name = e.upgradeName().replace("_", " ");
        if (e.owned()) {
            return name + " - OWNED";
        }
        return name + " - Mined: " + e.currentCount() + " / " + e.requiredCount();
    }

    private void onUpgradeClick(NetworkHandler.UpgradeEntry e) {
        if (e.owned()) return;
        if (e.currentCount() >= e.requiredCount()) {
            ClientPlayNetworking.send(new NetworkHandler.RequestUnlockUpgradePayload(e.upgradeName()));
            closeScreen();
        } else {
            String oreName = e.oreDisplayName().startsWith("block.")
                ? Text.translatable(e.oreDisplayName()).getString()
                : e.oreDisplayName();
            int need = e.requiredCount() - e.currentCount();
            feedbackMessage = Text.literal("Requires " + e.requiredCount() + " " + oreName + " (you have " + e.currentCount() + "). Need " + need + " more.");
            feedbackUntil = System.currentTimeMillis() + 3000;
        }
    }

    private void closeScreen() {
        feedbackMessage = null;
        if (client != null) client.setScreen(null);
    }

    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 22;
    private static final int START_Y = 50;
    private static final int GAP = 26;
    private static final int ICON_SIZE = 16;
    private static final int ICON_LEFT_OFFSET = 20;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101010);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        // Draw feedback before icon/tooltip loop so it's in a clean state
        if (feedbackMessage != null && System.currentTimeMillis() < feedbackUntil) {
            int doneY = START_Y + entries.size() * GAP + 10;
            int feedbackY = doneY + 10 + 24;
            context.drawTooltip(
                textRenderer,
                List.of(feedbackMessage.asOrderedText()),
                (screenWidth, screenHeight, x, y, width, height) -> new Vector2i(x - width / 2, y - height / 2),
                centerX,
                feedbackY,
                false
            );
        }
        if (client != null && client.world != null) {
            var blockRegistry = client.world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK);
            for (int i = 0; i < entries.size(); i++) {
                NetworkHandler.UpgradeEntry e = entries.get(i);
                int iconX = centerX - BUTTON_WIDTH / 2 - ICON_LEFT_OFFSET;
                int iconY = START_Y + i * GAP + (BUTTON_HEIGHT - ICON_SIZE) / 2;
                Block block = null;
                try {
                    block = blockRegistry.get(Identifier.tryParse(e.oreBlockId()));
                } catch (Exception ignored) { }
                if (block != null && !block.asItem().getDefaultStack().isEmpty()) {
                    ItemStack stack = block.asItem().getDefaultStack();
                    context.drawItem(stack, iconX, iconY);
                }
                boolean hoverIcon = mouseX >= iconX && mouseX < iconX + ICON_SIZE && mouseY >= iconY && mouseY < iconY + ICON_SIZE;
                if (hoverIcon) {
                    Text tooltipText = e.oreDisplayName().startsWith("block.")
                        ? Text.translatable(e.oreDisplayName())
                        : Text.literal(e.oreDisplayName());
                    context.drawTooltip(textRenderer, tooltipText, mouseX, mouseY);
                }
            }
        }
    }

    /**
     * Draws the feedback overlay (bar + text) when active. Called from ScreenEvents.AFTER_RENDER
     * so the message appears on top of all screen content.
     */
    public void renderFeedbackOverlay(DrawContext context) {
        if (feedbackMessage == null || System.currentTimeMillis() >= feedbackUntil) return;
        int centerX = width / 2;
        int doneY = START_Y + entries.size() * GAP + 10;
        int feedbackY = doneY + 10 + 24;
        var ordered = feedbackMessage.asOrderedText();
        int textWidth = textRenderer.getWidth(ordered);
        int textX = centerX - textWidth / 2;
        context.drawText(textRenderer, ordered, textX, feedbackY, 0xFFFFFF, true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

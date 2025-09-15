package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Tiny text-only browser. Fetches HTML via HttpClient and strips tags for display.
 * Does not run JS/CSS.
 */
public class BrowserApp implements IApp {
    private DraggableWindow window;
    private EditBox addressBox;
    private String content = "Enter a URL (https://...) and press Go.";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    @Override public void onOpen(DraggableWindow window) { this.window = window; }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Address:"), cx + 6, cy + 8, 0xFFFFFFFF, false);
        if (addressBox == null) addressBox = new EditBox(Minecraft.getInstance().font, cx + 64, cy + 6, Math.max(80, cw - 160), 16, Component.literal("url"));
        // set position every frame so internal layout matches
        addressBox.setX(cx + 64);
        addressBox.setY(cy + 6);
        // render the address box — note: DesktopScreen delegates mouse/char/key to focused window -> this EditBox will receive input
        addressBox.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

        // "Go" button
        guiGraphics.fill(cx + cw - 86, cy + 6, cx + cw - 66, cy + 22, 0xFF666666);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Go"), cx + cw - 82, cy + 8, 0xFFFFFFFF, false);

        // content display
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(content), cx + 6, cy + 40, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;
        // Go button
        if (mouseRelX >= cx + cw - 86 && mouseRelX <= cx + cw - 66 && mouseRelY >= cy + 6 && mouseRelY <= cy + 22) {
            goTo(addressBox.getValue());
            return true;
        }
        // forward click to addressBox so it gets focused / cursor set
        if (addressBox != null) {
            // EditBox expects screen coordinates in many mappings; pass absolute values (DesktopScreen passes abs mouse)
            addressBox.mouseClicked(mouseRelX, mouseRelY, button);
        }
        return false;
    }

    @Override public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) { if (addressBox != null) addressBox.mouseReleased(mouseRelX, mouseRelY, button); }
    @Override public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) { return false; }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        if (addressBox != null && addressBox.isFocused()) return addressBox.charTyped(codePoint, modifiers);
        return false;
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (addressBox != null && addressBox.isFocused()) return addressBox.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    @Override public boolean onClose(DraggableWindow window) {
        // No special cleanup required; allow window to close
        return true;
    }

    private void goTo(String url) {
        if (url == null || url.isBlank()) return;
        String fixed = url.trim();
        if (!fixed.startsWith("http://") && !fixed.startsWith("https://")) fixed = "https://" + fixed;
        final String target = fixed;
        addressBox.setValue(target);

        asyncManager.submitIOTask(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(target)).GET().timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                String text = TAGS.matcher(body).replaceAll(" ");
                final String finalContent = text.length() > 4000 ? text.substring(0, 4000) + "\n\n[truncated]" : text;

                asyncManager.executeOnMainThread(() -> {
                    content = finalContent;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                });
            } catch (Exception e) {
                final String errorMsg = "Failed to load: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                asyncManager.executeOnMainThread(() -> content = errorMsg);
                e.printStackTrace();
            }
        });
    }
}
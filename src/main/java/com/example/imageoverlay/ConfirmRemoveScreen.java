package com.example.imageoverlay;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Are you sure?" shown before the menu's Remove button deletes an image for good.
 * Cancel is bold and focused by default, so Enter/Space cancels rather than deletes.
 */
public class ConfirmRemoveScreen extends Screen {
	private final Screen parent;
	private final ImageEntry entry;

	public ConfirmRemoveScreen(Screen parent, ImageEntry entry) {
		super(Component.literal("Remove image?"));
		this.parent = parent;
		this.entry = entry;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = this.height / 2 + 24;

		this.addRenderableWidget(Button.builder(Component.literal("Remove permanently"), b -> {
			ImageManager.remove(entry);
			Minecraft.getInstance().gui.setScreen(parent); // menu rebuilds its list when it reopens
		}).bounds(cx + 5, y, 150, 20).build());

		Button cancel = Button.builder(
				Component.literal("Cancel").withStyle(ChatFormatting.BOLD),
				b -> this.onClose()).bounds(cx - 155, y, 150, 20).build();
		this.addRenderableWidget(cancel);
		this.setInitialFocus(cancel); // the safe choice is the default
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		Font f = this.font;
		int cx = this.width / 2;
		int y = this.height / 2 - 40;

		center(graphics, f, "Remove this image permanently?", cx, y, 0xFFFF6060);
		center(graphics, f, f.plainSubstrByWidth(entry.url, 300), cx, y + 16, 0xFFDDDDDD);
		center(graphics, f, "This cannot be undone.", cx, y + 32, 0xFFFFFFFF);
		if (!ImageManager.isWebUrl(ImageManager.normalize(entry.url))) {
			center(graphics, f, "Your original file on this PC will not be deleted.", cx, y + 44, 0xFFAAAAAA);
		}
	}

	private static void center(GuiGraphicsExtractor g, Font f, String text, int cx, int y, int color) {
		g.text(f, text, cx - f.width(text) / 2, y, color);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent); // ESC / Cancel: nothing is removed
	}
}

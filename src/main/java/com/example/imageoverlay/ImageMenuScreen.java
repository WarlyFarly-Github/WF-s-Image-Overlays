package com.example.imageoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The URL menu: paste a URL, press Add (or Enter), manage the list. */
public class ImageMenuScreen extends Screen {
	private static final int ROW_H = 24;
	private static final int LIST_TOP = 98;

	private EditBox urlBox;
	private String savedText = "";
	private String feedback = "";
	private final List<ImageEntry> rowEntries = new ArrayList<>();

	public ImageMenuScreen() {
		super(Component.literal("Image Overlay"));
	}

	@Override
	protected void init() {
		int cx = this.width / 2;

		urlBox = new EditBox(this.font, cx - 150, 30, 200, 20, Component.literal("Image URL"));
		urlBox.setMaxLength(2048);
		urlBox.setHint(Component.literal("Paste image URL or file path..."));
		urlBox.setValue(savedText);
		this.addRenderableWidget(urlBox);
		this.setInitialFocus(urlBox);

		this.addRenderableWidget(Button.builder(Component.literal("Paste"), b -> {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (clip != null) urlBox.setValue(clip.trim());
		}).bounds(cx + 55, 30, 45, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Add"), b -> addUrl())
				.bounds(cx + 105, 30, 45, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Upload from computer..."), b -> {
			savedText = urlBox.getValue();
			feedback = "";
			FilePicker.pickImage(
					path -> {
						ImageManager.add(path.toAbsolutePath().toString());
						if (Minecraft.getInstance().gui.screen() == this) this.rebuildWidgets();
					},
					msg -> feedback = msg);
		}).bounds(cx - 150, 54, 300, 20).build());

		// Per-row "Remove" buttons
		rowEntries.clear();
		int maxRows = Math.max(0, (this.height - 36 - LIST_TOP) / ROW_H);
		List<ImageEntry> all = ImageManager.entries();
		for (int i = 0; i < Math.min(maxRows, all.size()); i++) {
			ImageEntry entry = all.get(i);
			rowEntries.add(entry);
			this.addRenderableWidget(Button.builder(Component.literal("Remove"), b -> {
				savedText = urlBox.getValue();
				Minecraft.getInstance().gui.setScreen(new ConfirmRemoveScreen(this, entry));
			}).bounds(cx + 90, LIST_TOP + i * ROW_H, 60, 20).build());
		}

		int by = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.literal("Move Images"), b ->
				Minecraft.getInstance().gui.setScreen(new MoveScreen()))
				.bounds(cx - 155, by, 100, 20).build());

		this.addRenderableWidget(Button.builder(overlayLabel(), b -> {
			ImageManager.setVisible(!ImageManager.isVisible());
			b.setMessage(overlayLabel());
		}).bounds(cx - 50, by, 100, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(cx + 55, by, 100, 20).build());
	}

	private static Component overlayLabel() {
		return Component.literal("Overlay: " + (ImageManager.isVisible() ? "ON" : "OFF"));
	}

	private void addUrl() {
		String src = ImageManager.normalize(urlBox.getValue());
		if (!ImageManager.isValidSource(src)) {
			feedback = "Enter an http(s) URL, or the full path of an image file on this PC";
			return;
		}
		feedback = "";
		savedText = "";
		ImageManager.add(src);
		this.rebuildWidgets();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		Font f = this.font;
		int cx = this.width / 2;

		graphics.text(f, "Image Overlay", cx - f.width("Image Overlay") / 2, 12, 0xFFFFFFFF);

		if (!feedback.isEmpty()) {
			graphics.text(f, f.plainSubstrByWidth(feedback, 330), cx - 150, 80, 0xFFFF6060);
		}

		for (int i = 0; i < rowEntries.size(); i++) {
			ImageEntry e = rowEntries.get(i);
			int y = LIST_TOP + i * ROW_H + 6;
			String tag;
			int color;
			switch (e.status) {
				case READY -> { tag = "[OK] "; color = 0xFF70FF70; }
				case FAILED -> { tag = "[ERR] "; color = 0xFFFF6060; }
				default -> { tag = "[...] "; color = 0xFFFFFF80; }
			}
			graphics.text(f, tag, cx - 150, y, color);
			int tagW = f.width(tag);
			String body = e.status == ImageEntry.Status.FAILED ? e.error + " - " + e.url : e.url;
			graphics.text(f, f.plainSubstrByWidth(body, 235 - tagW), cx - 150 + tagW, y, 0xFFDDDDDD);
		}

		int hidden = ImageManager.entries().size() - rowEntries.size();
		if (hidden > 0) {
			String more = "+" + hidden + " more (remove some to see them)";
			graphics.text(f, more, cx - 150, this.height - 42, 0xFFAAAAAA);
		} else if (ImageManager.entries().isEmpty()) {
			String none = "No images yet - paste a URL, or upload a file.";
			graphics.text(f, none, cx - f.width(none) / 2, LIST_TOP + 6, 0xFFAAAAAA);
		}
	}

	/** Dropping image files onto the game window while this menu is open adds them. (No @Override on purpose.) */
	public void onFilesDrop(List<Path> paths) {
		int added = 0;
		for (Path p : paths) {
			if (ImageManager.hasImageExtension(p)) {
				ImageManager.add(p.toAbsolutePath().toString());
				added++;
			}
		}
		feedback = added == 0 ? "Dropped files must be PNG, JPG, GIF or BMP" : "";
		savedText = urlBox.getValue();
		this.rebuildWidgets();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && urlBox.isFocused()) {
			addUrl();
			return true;
		}
		if (ImageOverlayClient.menuKey.matches(event) && !urlBox.isFocused()) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		ImageManager.save();
		super.onClose();
	}
}

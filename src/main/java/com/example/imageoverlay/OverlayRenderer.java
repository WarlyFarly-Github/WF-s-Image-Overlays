package com.example.imageoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

public final class OverlayRenderer {
	private OverlayRenderer() {}

	/**
	 * @param editMode when true: draws outlines + placeholders for images that are loading/failed
	 * @param active   the image currently being dragged / hovered (gets a highlighted outline)
	 */
	public static void draw(GuiGraphicsExtractor graphics, boolean editMode, ImageEntry active) {
		Font font = Minecraft.getInstance().font;

		for (ImageEntry e : ImageManager.entries()) {
			int w = e.drawW();
			int h = e.drawH();

			if (e.isReady()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, e.textureId,
						e.x, e.y, 0f, 0f, w, h, e.texW, e.texH, e.texW, e.texH);
			} else if (editMode) {
				graphics.fill(e.x, e.y, e.x + w, e.y + h, 0xAA202020);
				String label = e.status == ImageEntry.Status.FAILED ? "Failed: " + e.error : "Loading...";
				label = font.plainSubstrByWidth(label, w - 6);
				int color = e.status == ImageEntry.Status.FAILED ? 0xFFFF6060 : 0xFFFFFF80;
				graphics.text(font, label, e.x + 3, e.y + 6, color);
			}

			if (editMode) {
				int outline = (e == active) ? 0xFF55FF55 : 0x90FFFFFF;
				graphics.outline(e.x - 1, e.y - 1, w + 2, h + 2, outline);
			}
		}
	}
}

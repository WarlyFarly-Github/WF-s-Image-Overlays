package com.example.imageoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Drag mode: drag images with the left mouse button, resize with the scroll wheel. */
public class MoveScreen extends Screen {
	private static final Identifier TRASH_CLOSED = Identifier.fromNamespaceAndPath(
			ImageOverlayClient.MOD_ID, "textures/gui/trashcan.png");
	private static final Identifier TRASH_OPEN = Identifier.fromNamespaceAndPath(
			ImageOverlayClient.MOD_ID, "textures/gui/trashcanopen.png");
	private static final int TRASH_BOX = 44;   // clickable/drop area
	private static final int TRASH_ICON = 32;  // 16px texture drawn at 2x
	private static final int TRASH_MARGIN = 10;

	private ImageEntry dragging;
	private int grabX;
	private int grabY;

	public MoveScreen() {
		super(Component.literal("Move Images"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Deliberately not calling super: no blurred menu background, so you see the world as-is.
		graphics.fill(0, 0, this.width, this.height, 0x30000000);

		ImageEntry hover = dragging != null ? dragging : topAt(mouseX, mouseY);
		OverlayRenderer.draw(graphics, true, hover);

		Font f = this.font;
		String l1 = "Drag images with left mouse  |  Scroll over an image to resize";
		String l2 = "Drag an image onto the trash can to delete it  |  ESC or the Move key to finish";
		graphics.text(f, l1, (this.width - f.width(l1)) / 2, 8, 0xFFFFFFFF);
		graphics.text(f, l2, (this.width - f.width(l2)) / 2, 20, 0xFFAAAAAA);
		drawTrash(graphics, mouseX, mouseY);

		if (ImageManager.entries().isEmpty()) {
			String l3 = "No images yet. Open the image menu to add some.";
			graphics.text(f, l3, (this.width - f.width(l3)) / 2, this.height / 2, 0xFFFFFF80);
		}
	}

	private int trashX() {
		return this.width - TRASH_BOX - TRASH_MARGIN;
	}

	private int trashY() {
		return this.height - TRASH_BOX - TRASH_MARGIN;
	}

	private boolean overTrash(double mx, double my) {
		return mx >= trashX() && mx < trashX() + TRASH_BOX
				&& my >= trashY() && my < trashY() + TRASH_BOX;
	}

	private void drawTrash(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		boolean armed = dragging != null && overTrash(mouseX, mouseY);
		int x = trashX();
		int y = trashY();

		graphics.fill(x, y, x + TRASH_BOX, y + TRASH_BOX, armed ? 0xB0C03030 : 0x90000000);
		graphics.outline(x, y, TRASH_BOX, TRASH_BOX, armed ? 0xFFFF6060 : 0x90FFFFFF);

		int off = (TRASH_BOX - TRASH_ICON) / 2;
		// Scales the 16x16 icon up to 32x32 (u, v, drawW, drawH, regionW, regionH, texW, texH)
		graphics.blit(RenderPipelines.GUI_TEXTURED, armed ? TRASH_OPEN : TRASH_CLOSED,
				x + off, y + off, 0f, 0f, TRASH_ICON, TRASH_ICON, 16, 16, 16, 16);
	}

	private ImageEntry topAt(double mx, double my) {
		List<ImageEntry> list = ImageManager.entries();
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i).contains(mx, my)) return list.get(i);
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			ImageEntry hit = topAt(event.x(), event.y());
			if (hit != null) {
				dragging = hit;
				grabX = (int) event.x() - hit.x;
				grabY = (int) event.y() - hit.y;
				ImageManager.bringToFront(hit);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int nx = (int) event.x() - grabX;
			int ny = (int) event.y() - grabY;
			dragging.x = Math.max(-dragging.drawW() + 10, Math.min(this.width - 10, nx));
			dragging.y = Math.max(-dragging.drawH() + 10, Math.min(this.height - 10, ny));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			ImageEntry released = dragging;
			dragging = null;
			if (overTrash(event.x(), event.y())) {
				ImageManager.remove(released); // also frees its texture and saves
			} else {
				ImageManager.save();
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		ImageEntry e = dragging != null ? dragging : topAt(mouseX, mouseY);
		if (e != null && e.isReady() && scrollY != 0) {
			float factor = scrollY > 0 ? 1.1f : 1f / 1.1f;
			e.scale = Math.max(0.05f, Math.min(10f, e.scale * factor));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (ImageOverlayClient.moveKey.matches(event)) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		ImageManager.save();
		Minecraft.getInstance().gui.setScreen(null);
	}
}

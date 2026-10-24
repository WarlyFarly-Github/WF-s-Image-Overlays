package com.example.imageoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Drag mode:
 *  - drag an image to move it
 *  - drag a corner to resize it (free-form; hold SHIFT to keep its current shape)
 *  - drop an image on the trash can to delete it
 *  - press R over an image to reset its shape
 *
 * Because this is a Screen, Minecraft does not register movement keys while it is open,
 * so holding SHIFT here never makes the player crouch (and no sneak state is sent to servers).
 */
public class MoveScreen extends Screen {
	private static final Identifier TRASH_CLOSED = Identifier.fromNamespaceAndPath(
			ImageOverlayClient.MOD_ID, "textures/gui/trashcan.png");
	private static final Identifier TRASH_OPEN = Identifier.fromNamespaceAndPath(
			ImageOverlayClient.MOD_ID, "textures/gui/trashcanopen.png");
	private static final int TRASH_BOX = 44;   // clickable/drop area
	private static final int TRASH_ICON = 32;  // 16px texture drawn at 2x
	private static final int TRASH_MARGIN = 10;

	private static final int HANDLE = 8;       // drawn size of a corner handle
	private static final int HANDLE_HIT = 6;   // grab radius around a corner
	private static final int MIN_SIZE = 16;
	private static final int MAX_SIZE = 4000;

	// moving
	private ImageEntry dragging;
	private int grabX;
	private int grabY;

	// resizing
	private ImageEntry resizing;
	private int corner;            // 0 = top-left, 1 = top-right, 2 = bottom-left, 3 = bottom-right
	private int anchorX;           // the opposite corner stays fixed while resizing
	private int anchorY;
	private float startW;
	private float startH;

	private boolean shiftHeld;
	private int lastMouseX;
	private int lastMouseY;

	public MoveScreen() {
		super(Component.literal("Move Images"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;

		// Deliberately not calling super: no blurred menu background, so you see the world as-is.
		graphics.fill(0, 0, this.width, this.height, 0x30000000);

		ImageEntry active = resizing != null ? resizing : dragging != null ? dragging : topAt(mouseX, mouseY);
		OverlayRenderer.draw(graphics, true, active);
		drawHandles(graphics, mouseX, mouseY);
		drawTrash(graphics, mouseX, mouseY);

		Font f = this.font;
		String[] lines = {
				"Drag to move  |  Drag a corner to resize (hold SHIFT to keep the shape)",
				"Drop on the trash can to delete  |  R resets an image's shape  |  ESC or the Move key to finish"
		};
		for (int i = 0; i < lines.length; i++) {
			graphics.text(f, lines[i], (this.width - f.width(lines[i])) / 2, 8 + i * 12,
					i == 0 ? 0xFFFFFFFF : 0xFFAAAAAA);
		}
		if (ImageManager.entries().isEmpty()) {
			String l3 = "No images yet. Open the image menu to add some.";
			graphics.text(f, l3, (this.width - f.width(l3)) / 2, this.height / 2, 0xFFFFFF80);
		}
	}

	private void drawHandles(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		for (ImageEntry e : ImageManager.entries()) {
			if (!e.isReady()) continue;
			for (int c = 0; c < 4; c++) {
				int cx = cornerX(e, c);
				int cy = cornerY(e, c);
				boolean hot = (resizing == e && corner == c)
						|| (resizing == null && dragging == null && cornerAt(e, mouseX, mouseY) == c);
				int x0 = cx - HANDLE / 2;
				int y0 = cy - HANDLE / 2;
				graphics.fill(x0, y0, x0 + HANDLE, y0 + HANDLE, hot ? 0xFF55FF55 : 0xFFFFFFFF);
				graphics.outline(x0, y0, HANDLE, HANDLE, 0xFF000000);
			}
		}
	}

	// ------------------------------------------------------------------ trash can

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

	// ------------------------------------------------------------------ hit testing

	private static int cornerX(ImageEntry e, int c) {
		return (c == 0 || c == 2) ? e.x : e.x + e.drawW();
	}

	private static int cornerY(ImageEntry e, int c) {
		return (c == 0 || c == 1) ? e.y : e.y + e.drawH();
	}

	/** @return corner index under the point, or -1 */
	private static int cornerAt(ImageEntry e, double mx, double my) {
		if (!e.isReady()) return -1;
		for (int c = 0; c < 4; c++) {
			if (Math.abs(mx - cornerX(e, c)) <= HANDLE_HIT && Math.abs(my - cornerY(e, c)) <= HANDLE_HIT) {
				return c;
			}
		}
		return -1;
	}

	private ImageEntry topAt(double mx, double my) {
		List<ImageEntry> list = ImageManager.entries();
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i).contains(mx, my)) return list.get(i);
		}
		return null;
	}

	// ------------------------------------------------------------------ mouse

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			double mx = event.x();
			double my = event.y();
			List<ImageEntry> list = ImageManager.entries();

			// 1) corner handles (top-most image first)
			for (int i = list.size() - 1; i >= 0; i--) {
				ImageEntry e = list.get(i);
				int c = cornerAt(e, mx, my);
				if (c >= 0) {
					resizing = e;
					corner = c;
					// the opposite corner is the fixed anchor
					anchorX = cornerX(e, 3 - c);
					anchorY = cornerY(e, 3 - c);
					startW = e.drawW();
					startH = e.drawH();
					ImageManager.bringToFront(e);
					return true;
				}
			}

			// 2) image body -> move
			ImageEntry hit = topAt(mx, my);
			if (hit != null) {
				dragging = hit;
				grabX = (int) mx - hit.x;
				grabY = (int) my - hit.y;
				ImageManager.bringToFront(hit);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (resizing != null) {
				resizeTo(event.x(), event.y());
				return true;
			}
			if (dragging != null) {
				int nx = (int) event.x() - grabX;
				int ny = (int) event.y() - grabY;
				dragging.x = Math.max(-dragging.drawW() + 10, Math.min(this.width - 10, nx));
				dragging.y = Math.max(-dragging.drawH() + 10, Math.min(this.height - 10, ny));
				return true;
			}
		}
		return super.mouseDragged(event, dx, dy);
	}

	private void resizeTo(double mx, double my) {
		ImageEntry e = resizing;
		boolean left = (corner == 0 || corner == 2);
		boolean top = (corner == 0 || corner == 1);

		// distance from the fixed corner to the cursor (never flips past the anchor)
		float w = (float) (left ? anchorX - mx : mx - anchorX);
		float h = (float) (top ? anchorY - my : my - anchorY);
		w = Math.max(MIN_SIZE, Math.min(MAX_SIZE, w));
		h = Math.max(MIN_SIZE, Math.min(MAX_SIZE, h));

		if (shiftHeld) {
			// keep the shape the image had when the drag started; follow whichever axis moved more
			float s = Math.max(w / startW, h / startH);
			w = startW * s;
			h = startH * s;
		}

		e.x = Math.round(left ? anchorX - w : anchorX);
		e.y = Math.round(top ? anchorY - h : anchorY);
		e.scale = w / e.texW;
		e.stretchY = h / (e.texH * e.scale);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (resizing != null) {
			resizing = null;
			ImageManager.save();
			return true;
		}
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

	// ------------------------------------------------------------------ keyboard

	private static boolean isShift(KeyEvent event) {
		return event.key() == InputConstants.KEY_LSHIFT || event.key() == InputConstants.KEY_RSHIFT;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (isShift(event)) {
			shiftHeld = true;
			return true; // handled here; never reaches the player
		}
		if (ImageOverlayClient.moveKey.matches(event)) {
			this.onClose();
			return true;
		}
		if (event.key() == InputConstants.KEY_R) {
			ImageEntry e = topAt(lastMouseX, lastMouseY);
			if (e != null) {
				e.stretchY = 1f; // back to the image's original shape
				ImageManager.save();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (isShift(event)) {
			shiftHeld = false;
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public void onClose() {
		ImageManager.save();
		Minecraft.getInstance().gui.setScreen(null);
	}
}

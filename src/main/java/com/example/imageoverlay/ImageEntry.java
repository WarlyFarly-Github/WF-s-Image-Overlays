package com.example.imageoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** One overlay image. Persisted fields: url, x, y, scale. Everything else is runtime state. */
public class ImageEntry {
	public enum Status { IDLE, LOADING, READY, FAILED }

	public String url;
	public int x = 20;
	public int y = 20;
	/** 0 = "not set yet"; set automatically on first successful load. */
	public float scale = 0f;

	public transient volatile Status status = Status.IDLE;
	public transient volatile String error = "";
	public transient Identifier textureId;
	public transient int texW;
	public transient int texH;

	public ImageEntry() {}

	public ImageEntry(String url) {
		this.url = url;
	}

	public boolean isReady() {
		return status == Status.READY && textureId != null;
	}

	public int drawW() {
		return isReady() ? Math.max(8, Math.round(texW * scale)) : 130;
	}

	public int drawH() {
		return isReady() ? Math.max(8, Math.round(texH * scale)) : 20;
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + drawW() && my >= y && my < y + drawH();
	}

	public void release() {
		if (textureId != null) {
			Minecraft.getInstance().getTextureManager().release(textureId);
			textureId = null;
		}
		status = Status.IDLE;
	}
}

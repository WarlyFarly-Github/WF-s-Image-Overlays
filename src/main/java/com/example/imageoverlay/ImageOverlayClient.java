package com.example.imageoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class ImageOverlayClient implements ClientModInitializer {
	public static final String MOD_ID = "imageoverlay";

	public static KeyMapping menuKey;
	public static KeyMapping moveKey;

	@Override
	public void onInitializeClient() {
		ImageManager.loadConfig();

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.imageoverlay.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));
		moveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.imageoverlay.move", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, category));

		// Start downloading saved images once the client is up.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> ImageManager.loadAllTextures());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.consumeClick()) {
				if (client.gui.screen() == null && client.level != null) {
					client.gui.setScreen(new ImageMenuScreen());
				}
			}
			while (moveKey.consumeClick()) {
				if (client.gui.screen() == null && client.level != null) {
					client.gui.setScreen(new MoveScreen());
				}
			}
		});

		// The always-on overlay. (Hidden automatically with F1.)
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "overlay"), (graphics, tickCounter) -> {
			if (!ImageManager.isVisible()) return;
			// MoveScreen draws the images itself (with outlines), so don't draw twice.
			if (Minecraft.getInstance().gui.screen() instanceof MoveScreen) return;
			OverlayRenderer.draw(graphics, false, null);
		});
	}
}

package com.example.imageoverlay;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public class ImageOverlayClient implements ClientModInitializer {
	public static final String MOD_ID = "imageoverlay";

	public static KeyMapping menuKey;
	public static KeyMapping moveKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.imageoverlay.menu", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));
		moveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.imageoverlay.move", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, category));

		// Each world / server has its own overlay list.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				ImageManager.switchTo(worldKey(client)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ImageManager.unload());

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

	/** A stable id for the world/server you are in: the save folder name, or the server address. */
	private static String worldKey(Minecraft mc) {
		var singleplayer = mc.getSingleplayerServer();
		if (singleplayer != null) {
			Path root = singleplayer.getWorldPath(LevelResource.ROOT).normalize();
			Path folder = root.getFileName();
			return "singleplayer:" + (folder != null ? folder.toString() : "world");
		}
		var server = mc.getCurrentServer();
		if (server != null) {
			String address = (server.ip != null && !server.ip.isBlank()) ? server.ip : server.name;
			return "server:" + address.trim().toLowerCase();
		}
		return "unknown";
	}
}

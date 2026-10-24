package com.example.imageoverlay;

import net.minecraft.client.Minecraft;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Opens the operating system's "open file" dialog on a background thread
 * (a blocking native dialog on the game thread would freeze Minecraft).
 */
public final class FilePicker {
	private static volatile boolean open = false;

	private FilePicker() {}

	public static boolean isOpen() {
		return open;
	}

	/**
	 * @param onPick  called on the game thread with the chosen file
	 * @param onError called on the game thread with a short message if the dialog is unavailable
	 */
	public static void pickImage(Consumer<Path> onPick, Consumer<String> onError) {
		if (open) return;
		open = true;

		Thread t = new Thread(() -> {
			Path chosen = null;
			String error = null;
			try {
				if (GraphicsEnvironment.isHeadless()) {
					throw new IllegalStateException("file dialogs are unavailable here");
				}
				FileDialog fd = new FileDialog((Frame) null, "Choose an image", FileDialog.LOAD);
				fd.setFile("*.png;*.jpg;*.jpeg;*.gif;*.bmp"); // honoured on Windows
				fd.setFilenameFilter((dir, name) -> {
					String n = name.toLowerCase();
					return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
							|| n.endsWith(".gif") || n.endsWith(".bmp");
				});
				fd.setAlwaysOnTop(true);
				fd.setVisible(true); // blocks this thread until the user closes the dialog
				String dir = fd.getDirectory();
				String file = fd.getFile();
				fd.dispose();
				if (dir != null && file != null) chosen = Path.of(dir, file);
			} catch (Throwable ex) {
				error = "Could not open file dialog (" + ex.getClass().getSimpleName()
						+ "). Paste the file path instead, or drag the file onto this window.";
			} finally {
				open = false;
			}

			final Path result = chosen;
			final String err = error;
			Minecraft.getInstance().execute(() -> {
				if (err != null) onError.accept(err);
				else if (result != null) onPick.accept(result);
			});
		}, "ImageOverlay-FileDialog");
		t.setDaemon(true);
		t.start();
	}
}

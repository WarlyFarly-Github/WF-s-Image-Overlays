package com.example.imageoverlay;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Opens the operating system's native "open file" dialog by launching a small helper process
 * (PowerShell on Windows, osascript on macOS, zenity/kdialog on Linux).
 *
 * Java's own AWT dialogs can't be used: Minecraft runs Java in headless mode.
 * The dialog runs on a background thread so the game never freezes.
 */
public final class FilePicker {
	private static volatile boolean open = false;

	private FilePicker() {}

	public static boolean isOpen() {
		return open;
	}

	/**
	 * @param onPick  called on the game thread with the chosen file
	 * @param onError called on the game thread with a short message if no dialog could be shown
	 */
	public static void pickImage(Consumer<Path> onPick, Consumer<String> onError) {
		if (open) return;
		open = true;

		Thread t = new Thread(() -> {
			Path chosen = null;
			String error = null;
			try {
				String out = runDialog();
				if (out == null) {
					error = "No file dialog available - paste the file path instead";
				} else if (!out.isBlank()) {
					chosen = Path.of(out.strip());
				}
			} catch (Throwable ex) {
				error = "File dialog failed - paste the file path instead";
				System.err.println("[imageoverlay] File dialog error: " + ex);
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

	/** @return the chosen path, "" if the user cancelled, or null if no dialog program could be started. */
	private static String runDialog() throws InterruptedException {
		String os = System.getProperty("os.name", "").toLowerCase();

		if (os.contains("win")) {
			String script = String.join("; ",
					"[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
					"Add-Type -AssemblyName System.Windows.Forms",
					"$owner = New-Object System.Windows.Forms.Form -Property @{ TopMost = $true }",
					"$d = New-Object System.Windows.Forms.OpenFileDialog",
					"$d.Title = 'Choose an image'",
					"$d.Filter = 'Images (*.png;*.jpg;*.jpeg;*.gif;*.bmp)|*.png;*.jpg;*.jpeg;*.gif;*.bmp|All files (*.*)|*.*'",
					"if ($d.ShowDialog($owner) -eq [System.Windows.Forms.DialogResult]::OK) { [Console]::Out.Write($d.FileName) }");
			return run(List.of("powershell.exe", "-NoProfile", "-STA", "-NonInteractive", "-Command", script));
		}

		if (os.contains("mac")) {
			return run(List.of("osascript", "-e",
					"POSIX path of (choose file with prompt \"Choose an image\" of type {\"public.image\"})"));
		}

		// Linux / BSD: try zenity, then kdialog
		String filters = "*.png *.jpg *.jpeg *.gif *.bmp";
		String r = run(List.of("zenity", "--file-selection", "--title=Choose an image",
				"--file-filter=Images | " + filters));
		if (r != null) return r;
		return run(List.of("kdialog", "--getopenfilename", System.getProperty("user.home", "."),
				filters + "|Images"));
	}

	private static String run(List<String> command) throws InterruptedException {
		try {
			Process p = new ProcessBuilder(command)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			p.getOutputStream().close();
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			p.waitFor();
			return out; // empty when cancelled
		} catch (IOException notInstalled) {
			return null;
		}
	}
}

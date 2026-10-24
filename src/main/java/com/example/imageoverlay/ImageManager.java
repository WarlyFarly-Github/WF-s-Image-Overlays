package com.example.imageoverlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Holds the list of overlay images, saves/loads them, and downloads their textures. */
public final class ImageManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
	/** The old single global list (versions before per-world support). */
	private static final Path LEGACY_FILE = CONFIG_DIR.resolve("imageoverlay.json");
	/** One file per world/server lives here. */
	private static final Path WORLD_DIR = CONFIG_DIR.resolve("imageoverlay").resolve("worlds");
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private static final AtomicInteger IDS = new AtomicInteger();

	private static final int MAX_BYTES = 25 * 1024 * 1024;
	/** Images bigger than this are smoothly shrunk on load (keeps GUI rendering crisp and VRAM low). */
	private static final int MAX_DIM = 1024;

	private static final List<ImageEntry> ENTRIES = new ArrayList<>();
	private static boolean visible = true;
	/** Identifies the world/server whose overlays are currently loaded (null = none). */
	private static String currentKey;
	private static Path currentFile;

	private static class Config {
		String world;
		List<ImageEntry> images = new ArrayList<>();
		boolean visible = true;
	}

	private ImageManager() {}

	public static List<ImageEntry> entries() {
		return ENTRIES;
	}

	public static boolean isVisible() {
		return visible;
	}

	public static void setVisible(boolean v) {
		visible = v;
		save();
	}

	// ---------------------------------------------------------------- persistence (one file per world/server)

	/** Called when you join a world or server: loads that world's own overlay list. */
	public static void switchTo(String key) {
		if (key.equals(currentKey)) return;
		unload();

		currentKey = key;
		currentFile = WORLD_DIR.resolve(fileNameFor(key));
		visible = true;

		try {
			if (Files.exists(currentFile)) {
				readInto(currentFile);
			} else if (Files.exists(LEGACY_FILE)) {
				// One-time migration: the first world you join after updating inherits the old global list.
				readInto(LEGACY_FILE);
				Files.move(LEGACY_FILE, LEGACY_FILE.resolveSibling("imageoverlay.json.bak"),
						StandardCopyOption.REPLACE_EXISTING);
				save();
			}
		} catch (Exception ex) {
			System.err.println("[imageoverlay] Could not read overlays for " + key + ": " + ex);
		}

		for (ImageEntry e : ENTRIES) load(e);
	}

	/** Called when you leave a world/server: saves, frees the textures and empties the list. */
	public static void unload() {
		if (currentFile != null) save();
		for (ImageEntry e : ENTRIES) e.release();
		ENTRIES.clear();
		currentKey = null;
		currentFile = null;
	}

	private static void readInto(Path file) throws IOException {
		Config c = GSON.fromJson(Files.readString(file), Config.class);
		ENTRIES.clear();
		if (c != null) {
			if (c.images != null) {
				for (ImageEntry e : c.images) {
					if (e != null && e.url != null) ENTRIES.add(e);
				}
			}
			visible = c.visible;
		}
	}

	public static void save() {
		if (currentFile == null) return;
		try {
			Files.createDirectories(currentFile.getParent());
			Config c = new Config();
			c.world = currentKey;
			c.images = ENTRIES;
			c.visible = visible;
			Files.writeString(currentFile, GSON.toJson(c));
		} catch (IOException ex) {
			System.err.println("[imageoverlay] Could not save config: " + ex);
		}
	}

	/** Safe, unique file name for a world key (readable prefix + hash so different keys never collide). */
	private static String fileNameFor(String key) {
		String safe = key.toLowerCase().replaceAll("[^a-z0-9._-]+", "_");
		if (safe.length() > 60) safe = safe.substring(0, 60);
		return safe + "-" + Integer.toHexString(key.hashCode()) + ".json";
	}

	// ---------------------------------------------------------------- list ops

	public static ImageEntry add(String url) {
		ImageEntry e = new ImageEntry(url);
		int n = ENTRIES.size();
		e.x = 20 + (n % 8) * 24;
		e.y = 20 + (n % 8) * 24;
		ENTRIES.add(e);
		save();
		load(e);
		return e;
	}

	public static void remove(ImageEntry e) {
		ENTRIES.remove(e);
		e.release();
		save();
	}

	public static void bringToFront(ImageEntry e) {
		if (ENTRIES.remove(e)) ENTRIES.add(e);
	}

	// ---------------------------------------------------------------- downloading

	public static void load(ImageEntry e) {
		if (e.status == ImageEntry.Status.LOADING) return;
		e.status = ImageEntry.Status.LOADING;
		e.error = "";

		CompletableFuture.runAsync(() -> {
			try {
				byte[] data = fetch(e.url);

				BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
				if (img == null) {
					throw new IOException("Unsupported format (use PNG, JPG, GIF or BMP)");
				}
				img = toArgbAndShrink(img);

				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ImageIO.write(img, "png", bos);
				byte[] png = bos.toByteArray();
				final int w = img.getWidth();
				final int h = img.getHeight();

				Minecraft mc = Minecraft.getInstance();
				mc.execute(() -> {
					if (!ENTRIES.contains(e)) return; // world was left / image removed while downloading
					try {
						NativeImage ni = NativeImage.read(new ByteArrayInputStream(png));
						int id = IDS.incrementAndGet();
						Identifier texId = Identifier.fromNamespaceAndPath(ImageOverlayClient.MOD_ID, "dyn/img" + id);
						DynamicTexture tex = new DynamicTexture(() -> "imageoverlay_" + id, ni);

						if (e.textureId != null) mc.getTextureManager().release(e.textureId);
						mc.getTextureManager().register(texId, tex);

						e.textureId = texId;
						e.texW = w;
						e.texH = h;
						if (e.scale <= 0f) e.scale = Math.min(1f, 200f / w); // first load: ~200px wide
						e.status = ImageEntry.Status.READY;
						save();
					} catch (Exception ex) {
						fail(e, ex);
					}
				});
			} catch (Exception ex) {
				fail(e, ex);
			}
		});
	}

	// ---------------------------------------------------------------- sources (URL or local file)

	/** Strips surrounding quotes (Windows "Copy as path" adds them) and whitespace. */
	public static String normalize(String input) {
		String s = input == null ? "" : input.trim();
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
			s = s.substring(1, s.length() - 1).trim();
		}
		return s;
	}

	public static boolean isWebUrl(String s) {
		String l = s.toLowerCase();
		return l.startsWith("http://") || l.startsWith("https://");
	}

	private static Path toPath(String s) {
		return s.toLowerCase().startsWith("file:") ? Path.of(URI.create(s)) : Path.of(s);
	}

	/** True for an http(s) URL or a path to an existing file. */
	public static boolean isValidSource(String input) {
		String s = normalize(input);
		if (s.isEmpty()) return false;
		if (isWebUrl(s)) return true;
		try {
			return Files.isRegularFile(toPath(s));
		} catch (Exception ex) {
			return false;
		}
	}

	public static boolean hasImageExtension(Path p) {
		String n = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase();
		return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
				|| n.endsWith(".gif") || n.endsWith(".bmp");
	}

	private static byte[] fetch(String source) throws IOException, InterruptedException {
		String s = normalize(source);

		if (isWebUrl(s)) {
			HttpRequest req = HttpRequest.newBuilder(URI.create(s))
					.timeout(Duration.ofSeconds(20))
					.header("User-Agent", "Mozilla/5.0 (Minecraft ImageOverlay mod)")
					.GET()
					.build();
			HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
			if (resp.statusCode() / 100 != 2) {
				throw new IOException("HTTP " + resp.statusCode());
			}
			try (InputStream in = resp.body()) {
				return readLimited(in);
			}
		}

		Path p = toPath(s);
		if (!Files.isRegularFile(p)) throw new IOException("File not found");
		if (Files.size(p) > MAX_BYTES) throw new IOException("Image larger than 25 MB");
		return Files.readAllBytes(p);
	}

	private static void fail(ImageEntry e, Exception ex) {
		String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
		e.error = msg;
		e.status = ImageEntry.Status.FAILED;
		System.err.println("[imageoverlay] Failed to load " + e.url + ": " + msg);
	}

	private static byte[] readLimited(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[16384];
		int total = 0;
		int n;
		while ((n = in.read(buf)) != -1) {
			total += n;
			if (total > MAX_BYTES) throw new IOException("Image larger than 25 MB");
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	private static BufferedImage toArgbAndShrink(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		double f = Math.min(1.0, (double) MAX_DIM / Math.max(w, h));
		int nw = Math.max(1, (int) Math.round(w * f));
		int nh = Math.max(1, (int) Math.round(h * f));

		BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		if (f < 1.0) {
			Image scaled = src.getScaledInstance(nw, nh, Image.SCALE_AREA_AVERAGING);
			g.drawImage(scaled, 0, 0, null);
		} else {
			g.drawImage(src, 0, 0, null);
		}
		g.dispose();
		return out;
	}
}

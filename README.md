# WF's Image Overlays

A Claude-coded image overlay mod for Minecraft Fabric 26.3.

Paste image URLs into an in-game menu and show them as an on-screen overlay. Drag them wherever you like.

<p>
  <img width="300" alt="Image menu" src="https://github.com/user-attachments/assets/1bd33abb-63d8-4d12-a9f2-154a8872d1d3" />
  <img width="300" alt="Overlay in game" src="https://github.com/user-attachments/assets/6df6b4b5-e889-4cc5-8e99-016d26adf3cf" />
  <img width="300" alt="Drag mode" src="https://github.com/user-attachments/assets/9dcbcd45-e450-4eb8-b6d9-d9d1c2a5ec12" />
</p>

## Features

- Add images by pasting a direct URL (PNG, JPG, GIF, BMP)
- Drag images anywhere on screen and scroll to resize them
- Positions, sizes and URLs are saved and restored between sessions
- Toggle the whole overlay on or off from the menu
- Client-side only, so it works on any server

## Requirements

- Minecraft **26.3**
- [Fabric Loader](https://fabricmc.net/use/installer/) **0.19.5** or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3
- Java **25**

## Installation

1. Install Fabric for Minecraft 26.3.
2. Download `imageoverlay-1.1.0.jar` from the [Releases](../../releases) page.
3. Put the jar and Fabric API in your `mods` folder.
4. Launch the game and join a world.

## Controls

Both keys can be changed under **Options > Controls > Key Binds > Image Overlay**.

| Key   | Action                                                                    |
|-------|---------------------------------------------------------------------------|
| **J** | Open the image menu: paste a URL, press Add or Enter, remove images       |
| **K** | Drag mode: left-drag to move, scroll wheel to resize, ESC or K to finish  |

## Notes

- Use a direct link to the image file (usually ending in `.png` or `.jpg`), not a web page that contains the image.
- WebP is not supported, and GIFs show only the first frame.
- Images over 1024 px are shrunk on load.
- Your images are saved in `config/imageoverlay.json`.

## Building from source

### 1. Install JDK 25

Check which version you have:

```
java -version
```

It must say `25`. If it doesn't, install it. On Windows:

```
winget install EclipseAdoptium.Temurin.25.JDK
```

Then close and reopen your terminal and run `java -version` again.

If it still shows an older version, point the current terminal at JDK 25. Change the folder name to match what is in `C:\Program Files\Eclipse Adoptium\`:

```
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

### 2. Clone the repository

```
git clone https://github.com/WarlyFarly-Github/WF-s-Image-Overlays.git
cd WF-s-Image-Overlays
```

### 3. Build the jar

Always use the Gradle wrapper (`gradlew`), not a globally installed `gradle`. The project needs Gradle 9.5+, and the wrapper downloads the right version for you.

Windows (PowerShell):

```
.\gradlew.bat build
```

Windows (Command Prompt), macOS and Linux:

```
gradlew build
```

On macOS and Linux you may first need to make the script executable:

```
chmod +x gradlew
./gradlew build
```

The first build downloads Gradle, Minecraft and its libraries, so it can take several minutes.

### 4. Find the jar

The finished mod is here:

```
build/libs/imageoverlay-1.1.0.jar
```

Use that file. Ignore `imageoverlay-1.1.0-sources.jar`, which only contains the source code.

### Test in a development client

To try the mod without installing it:

```
.\gradlew.bat runClient
```

(Use `./gradlew runClient` on macOS and Linux.)

### Troubleshooting

| Problem | Fix |
|---------|-----|
| `No matching variant of net.fabricmc:fabric-loom` | You ran a global `gradle` that is too old. Use `gradlew` instead. |
| `java -version` shows 21 or older | Install JDK 25 and set `JAVA_HOME` as shown in step 1. |
| `package org.lwjgl.glfw does not exist` | You are building for the wrong Minecraft version. 26.3 uses SDL3, not GLFW. |
| `gradlew` is "not recognized" | Make sure your terminal is inside the project folder (the one containing `build.gradle`). |

## License

MIT

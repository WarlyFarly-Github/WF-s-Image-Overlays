# Image Overlay (Fabric, Minecraft 26.3)

Client-side mod. Paste image URLs in a menu; they show as an on-screen overlay you can drag around.

## Controls (rebindable under Options > Controls > Key Binds > Image Overlay)
- **J** - open the URL menu (paste URL, Add / Enter, Remove, toggle overlay)
- **K** - drag mode: left-drag an image to move it, scroll wheel over it to resize, ESC / K to finish

Positions, sizes and URLs are saved to `config/imageoverlay.json`.
Supports PNG, JPG, GIF (first frame) and BMP over http(s). WebP is not supported.

## Build
Requires JDK 25.
    gradle wrapper --gradle-version 9.5.1   # once, if gradlew is missing
    ./gradlew build                          # jar in build/libs/
    ./gradlew runClient                      # test in a dev client

Note: This was AI coded, I did not create any of this code myself.

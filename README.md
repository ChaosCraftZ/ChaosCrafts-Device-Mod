# CDM — Add-on Author Guide

This document explains how to create an add-on app for the ChaosCraft's Device's ("cdm" / chaoscrafts device mod / chaoscraft's device's). It shows the minimal steps to implement an app, register it with the runtime, supply a custom icon, and make it appear on the Desktop and Taskbar.

Audience: mod authors who want their app to integrate into the Desktop UI so players can install/open it like a built-in app.

Summary (very short)
- Implement `IApp` (your app behaviour + rendering).
- Register your app with `AppFactory.registerApp("internalName", YourApp::new)` on client setup.
- Optionally register a custom icon via `IconManager.registerCustomIcon("internalName", new ResourceLocation("yourmodid:..."))`.
- Make the app available to players with `AppRegistry.getInstance().installApp("internalName", "Display Name", "desc", "1.0")`.
- The Desktop and Taskbar will automatically show icons and allow opening via `DesktopScreen.openAppSingle(...)`.

Quick prerequisites
- Java 17 (matches the project's toolchain)
- Familiarity with Forge/Minecraft mod client lifecycle (registering client setup callbacks)
- Build with Gradle (this workspace uses Gradle wrapper)

Project layout (important paths)
- Java sources: src/main/java/net/chaoscraft/chaoscrafts_device_mod/client/
  - apps & UI helpers live under `client.app` and `client.screen` in this repo
- Icons (resources): src/main/resources/assets/chaoscrafts_device_mod/textures/gui/icons/
  - Include 32x32 PNGs; `default_icon.png` is used as a fallback

Contract: what your add-on needs to provide
- Input: none (the client will call your app when the user opens it)
- Output: a UI displayed inside a `DraggableWindow` created by the Desktop UI
- Error modes: creating an app may return null if registration failed; log and fail gracefully
- Success criteria: the app opens, renders, and cleans up; icon appears on Desktop/Taskbar when installed

Implementing an app (IApp)
- Implement `net.chaoscraft.chaoscrafts_device_mod.client.app.IApp`.
- Minimal example:

```java
public class MyExampleApp implements net.chaoscraft.chaoscrafts_device_mod.client.app.IApp {
    @Override
    public void onOpen(DraggableWindow window) {
        // Called once when the window opens; keep references here if needed
    }

    @Override
    public void onClose() {
        // Cleanup resources
    }

    @Override
    public void renderContent(GuiGraphics g, PoseStack poseStack, DraggableWindow w, int mouseX, int mouseY, float partialTick) {
        g.drawString(Minecraft.getInstance().font, Component.literal("Hello from MyApp"), 20, 40, 0xFFFFFFFF, false);
    }

    // Implement other IApp methods if needed (input handling, tick, resize, etc.)
}
```

Where to register your app
- Register at client initialization (e.g. FML client setup):

```java
// Called in your mod's client setup:
AppFactory.registerApp("myapp", MyExampleApp::new);
```

Important notes
- Use a stable, lowercase `internalName` (e.g. "myapp"). This is the identifier used by `AppRegistry` and `FilesManager`.
- Do the registration on client-side setup only (e.g. during the client setup lifecycle event). Do not call client-only APIs on the server thread.

Registering a custom icon
- Add a PNG to your mod resources: `src/main/resources/assets/yourmodid/textures/gui/icons/myapp_icon.png` (32×32)
- Register the icon during client setup so IconManager knows about it:

```java
IconManager.registerCustomIcon("myapp", new ResourceLocation("yourmodid:textures/gui/icons/myapp_icon.png"));
```

- If no custom icon is registered, the UI falls back to `default_icon.png` automatically.

Making the app available to players
- Optionally call `AppRegistry.installApp(...)` to mark the app installed and put it on the Desktop:

```java
AppRegistry.getInstance().installApp("myapp", "My App", "A short description", "1.0");
```

- `installApp` runs an IO task and will add the app to the installed set and create a desktop icon via `FilesManager`.

How the Desktop and Taskbar integrate your app
- `DesktopScreen.refreshDesktopIcons()` reads desktop entries from `FilesManager` and only adds icons where `AppRegistry.isInstalled(name)` is true.
- Desktop icons call `DesktopScreen.openAppSingle(name, width, height)` when the user activates them (double-click or click action).
- The `TaskBar` renders small icons for installed apps (uses `IconManager` to obtain the icon) and clicking one opens the app.

Debugging tips (if app does not open)
- Look for messages prefixed with `[DesktopScreen]` in the game console/logs (I added debug logs in the UI flow). They indicate resolution and creation steps.
- Common causes:
  - App not registered: make sure `AppFactory.registerApp("myapp", ...)` ran before trying to open.
  - Icon not found: IconManager falls back to default; ensure ResourceLocation is correct and file exists.
  - Registry not loaded yet: `AppRegistry` loads on a background task. The UI schedules operations to retry after registry load; check logs for scheduling messages.

Best practices
- Prefer `internalName` without spaces (e.g. "myapp"). Use display name via `installApp` if you need spaces/title-case.
- Register icons and app factories during client setup lifecycle.
- Keep rendering fast; offload heavy work to `AsyncTaskManager`.
- Use `AppRegistry` to persist installed/desktop state so users keep icons between sessions.

Example minimal add-on (flow)
1. Add Java class files: `MyExampleApp` implements `IApp`.
2. Add client setup class:

```java
public class MyAddonClient {
    public static void onClientSetup(final FMLClientSetupEvent event) {
        AppFactory.registerApp("myapp", MyExampleApp::new);
        IconManager.registerCustomIcon("myapp", new ResourceLocation("yourmodid:textures/gui/icons/myapp_icon.png"));
        AppRegistry.getInstance().installApp("myapp", "My App", "Example add-on", "1.0");
    }
}
```

3. Ensure resources are placed under `src/main/resources/assets/yourmodid/textures/gui/icons/`.
4. Build & run; the Desktop should eventually show the icon; clicking opens your app.

Advanced: runtime registration & add-on discovery
- `AppFactory.registerApp(...)` can be called by other mods at runtime (client setup) to register dynamic add-ons.
- If you want a discovery system, you can publish a small JSON descriptor in your mod jar and have this mod read it and call `registerApp`/`registerCustomIcon` automatically.

Troubleshooting checklist
- If the icon never appears: confirm `installApp` actually completed and `app_status.json` contains your app name.
- If `openAppSingle` prints "Failed to create app": either `AppFactory` does not have a supplier for that name, or `AppRegistry` does not mark the app installed.
- If you see resource/texture exceptions: verify the `ResourceLocation` string matches the file placement and the file is present in your mod jar.

License / attribution
- This README and the example code are provided to help add-on authors.

---
IMPORTANT NOTE: This Guide will be Redone in the Future


# Troubleshooting Guide

> 🔧 **Having issues?** This guide helps you resolve common problems with ChaosCrafts Device Mod.

**👥 Created by [ChaosCraft](https://github.com/ChaosCraftZ) & [Admany](https://github.com/Admany)**

## 📋 Table of Contents

- [Installation Issues](#installation-issues)
- [App Registration Problems](#app-registration-problems)
- [Icon Issues](#icon-issues)
- [Rendering Problems](#rendering-problems)
- [Performance Issues](#performance-issues)
- [Build Errors](#build-errors)
- [Crash Reports](#crash-reports)
- [Getting Help](#getting-help)

## Installation Issues

### Mod Not Loading

**Symptoms:**
- Mod doesn't appear in mod list
- "Mod file is missing" error

**Solutions:**
- ✅ Ensure JAR is in the correct `mods` folder
- ✅ Check Forge version compatibility (47.2.0+)
- ✅ Verify Minecraft version (1.20.1)
- ✅ Delete `mods.toml` cache if needed

**Debug Steps:**
```bash
# Check if mod JAR exists
ls ~/.minecraft/mods/ | grep chaoscrafts

# Check Forge version
java -jar forge-installer.jar --version
```

### Forge Installation Failed

**Symptoms:**
- Forge installer crashes
- "Installation failed" message

**Solutions:**
- ✅ Use official Forge installer from [files.minecraftforge.net](https://files.minecraftforge.net/)
- ✅ Run installer as administrator (Windows)
- ✅ Close Minecraft and launcher first
- ✅ Check Java version (17+ required)

### Conflicting Mods

**Symptoms:**
- Game crashes on startup
- Incompatible mod warnings

**Common Conflicts:**
- ❌ Other GUI mods
- ❌ Inventory management mods
- ❌ Screen overlay mods

**Solutions:**
- ✅ Test with only ChaosCrafts installed
- ✅ Gradually add mods back to identify conflicts
- ✅ Check mod compatibility on CurseForge

## App Registration Problems

### App Not Appearing

**Symptoms:**
- App not on desktop or taskbar
- Registration code runs but no effect

**Common Causes:**
- ❌ Registration in wrong event (`FMLServerSetupEvent` instead of `FMLClientSetupEvent`)
- ❌ Internal name not unique
- ❌ Mod not loaded properly
- ❌ Exception during registration

**Debug Code:**
```java
@SubscribeEvent
public static void onClientSetup(FMLClientSetupEvent event) {
    try {
        AppFactory.registerSimpleApp("myapp", MyApp::new, "My App", "Desc", "1.0");
        System.out.println("✅ App registered successfully!");
    } catch (Exception e) {
        System.err.println("❌ App registration failed: " + e.getMessage());
        e.printStackTrace();
    }
}
```

**Check Console:**
- Look for "App registered successfully!" message
- Check for exceptions during mod loading
- Verify client-side registration

### Duplicate App Names

**Symptoms:**
- "App already exists" error
- App overrides another mod's app

**Solutions:**
- ✅ Use unique internal names (include your mod ID)
- ✅ Check existing apps before registration
- ✅ Use namespacing: `"mymod_myapp"`

**Prevention:**
```java
// Check if app already exists
if (AppRegistry.getInstance().isInstalled("myapp")) {
    System.out.println("⚠️ App 'myapp' already exists, skipping registration");
    return;
}
```

## Icon Issues

### Icon Not Loading

**Symptoms:**
- Default icon shown instead of custom
- Missing texture errors in console

**Common Issues:**
- ❌ Wrong file path
- ❌ Incorrect ResourceLocation
- ❌ PNG not 32x32 pixels
- ❌ File not in correct assets folder

**Correct Structure:**
```
src/main/resources/
└── assets/
    └── yourmodid/
        └── textures/
            └── gui/
                └── icons/
                    └── your_icon.png
```

**ResourceLocation Format:**
```java
// ✅ Correct
new ResourceLocation("yourmodid", "textures/gui/icons/your_icon.png")

// ❌ Wrong
new ResourceLocation("yourmodid:textures/gui/icons/your_icon.png")
new ResourceLocation("textures/gui/icons/your_icon.png")
```

**Debug Commands:**
```java
// Test icon loading
ResourceLocation icon = new ResourceLocation("yourmodid", "textures/gui/icons/your_icon.png");
Minecraft mc = Minecraft.getInstance();
boolean exists = mc.getResourceManager().hasResource(icon);
System.out.println("Icon exists: " + exists);
```

### Icon Size Issues

**Symptoms:**
- Icon appears stretched or pixelated
- Parts of icon cut off

**Requirements:**
- 📏 **Size**: Exactly 32x32 pixels
- 🎨 **Format**: PNG with transparency
- 📁 **Location**: `assets/modid/textures/gui/icons/`

**Tools:**
- Use [GIMP](https://www.gimp.org/) or [Photoshop](https://www.adobe.com/products/photoshop.html)
- Online: [ResizePixel](https://www.resizepixel.com/) or [BulkResizePhotos](https://bulkresizephotos.com/)

## Rendering Problems

### Black/White Screens

**Symptoms:**
- App window is blank or shows wrong colors
- Text not rendering

**Common Causes:**
- ❌ Wrong coordinate system
- ❌ Missing PoseStack parameter
- ❌ Incorrect GuiGraphics usage

**Coordinate System:**
```java
@Override
public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack,
                        DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
    // ✅ mouseRelX/Y are relative to window top-left
    guiGraphics.drawString(Minecraft.getInstance().font,
        Component.literal("Text"), mouseRelX + 10, mouseRelY + 10, 0xFFFFFF, false);
}
```

### Mouse Input Not Working

**Symptoms:**
- Clicks not registering
- Wrong click positions

**Debug:**
```java
@Override
public boolean mouseClicked(DraggableWindow window, double mouseX, double mouseY, int button) {
    System.out.println("Click at: " + mouseX + ", " + mouseY + " (button: " + button + ")");

    // Check bounds correctly
    if (mouseX >= 10 && mouseX <= 50 && mouseY >= 10 && mouseY <= 30) {
        System.out.println("✅ Click inside button bounds");
        return true; // Consume the event
    }
    return false;
}
```

### Text Rendering Issues

**Symptoms:**
- Text not visible
- Wrong colors or fonts

**Correct Usage:**
```java
@Override
public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack,
                        DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
    // ✅ Correct text rendering
    guiGraphics.drawString(Minecraft.getInstance().font,
        Component.literal("Hello World"),
        10, 20,  // X, Y coordinates
        0xFFFFFF, // White color
        false     // No shadow
    );
}
```

## Performance Issues

### Lag When Opening Apps

**Symptoms:**
- Game freezes when opening app
- Low FPS in app windows

**Causes:**
- ❌ Heavy rendering in `renderContent()`
- ❌ Synchronous I/O operations
- ❌ Complex calculations every frame

**Solutions:**
- ✅ Use async operations for I/O
- ✅ Cache expensive calculations
- ✅ Only update when necessary

**Async Example:**
```java
@Override
public void onOpen(DraggableWindow window) {
    // ✅ Load data asynchronously
    AsyncRuntime.get().submitIo(() -> {
        // Heavy loading here
        return loadData();
    }).thenAccept(data -> {
        // ✅ Update UI on main thread
        this.data = data;
    });
}
```

### Memory Leaks

**Symptoms:**
- Increasing memory usage
- Game slower over time

**Prevention:**
- ✅ Clean up resources in `onClose()`
- ✅ Unregister listeners
- ✅ Dispose of heavy objects

## Build Errors

### Missing Dependencies

**Symptoms:**
- "Cannot resolve symbol" errors
- Import errors

**Solutions:**
- ✅ Add ChaosCrafts to `build.gradle`
- ✅ Check version compatibility
- ✅ Rebuild project

**build.gradle:**
```gradle
dependencies {
    implementation fg.deobf("curse.maven:chaoscrafts-device-mod:MOD_ID:VERSION")
    // Or local file
    implementation fg.deobf(files("libs/chaoscrafts_device_mod-1.0.0.jar"))
}
```

### Compilation Errors

**Symptoms:**
- Build fails with errors
- IDE shows red squiggly lines

**Common Issues:**
- ❌ Wrong Java version (need 17+)
- ❌ Missing ForgeGradle plugin
- ❌ Incorrect package structure

**Check Java Version:**
```bash
java -version  # Should show Java 17+
./gradlew --version  # Check Gradle
```

### IDE Issues

**IntelliJ IDEA:**
- ✅ Invalidate caches and restart
- ✅ Reimport Gradle project
- ✅ Check Project Structure → Project SDK

**Eclipse:**
- ✅ Clean and rebuild project
- ✅ Refresh Gradle dependencies
- ✅ Check Java Build Path

## Crash Reports

### Reading Crash Logs

**Key Information:**
- 📋 **Error Message**: What went wrong
- 📍 **Stack Trace**: Code path to error
- 🔧 **Mods Loaded**: Check for conflicts
- 💻 **Environment**: Java version, OS, etc.

**Common Crash Causes:**
- ❌ Null pointer exceptions
- ❌ Class loading issues
- ❌ OpenGL/rendering errors
- ❌ Memory issues

### Reporting Crashes

**Include:**
- 📄 **Full crash log** (latest.log or crash-report)
- 🏗️ **Mod list** (from mods folder)
- 🖥️ **System info** (OS, Java version, RAM)
- 📋 **Steps to reproduce**

**Example Report:**
```
**Crash Report**
- Mod Version: 1.0.0
- Minecraft: 1.20.1
- Forge: 47.2.0
- Java: 17.0.8
- OS: Windows 10

**Steps:**
1. Load world
2. Open ChaosCrafts device
3. Click custom app
4. Game crashes

**Crash Log:** [paste crash log here]
```

## Getting Help

### Quick Checks

**Before Asking:**
- ✅ Read this troubleshooting guide
- ✅ Check [CreatingApps.md](CreatingApps.md)
- ✅ Search existing [GitHub issues](../../issues)
- ✅ Test with only ChaosCrafts installed

### Where to Ask

**GitHub Issues:**
- 🐛 **Bug Reports**: Use bug report template
- 💡 **Feature Requests**: Use feature request template
- ❓ **Questions**: Check discussions first

**Discord:**
- 💬 **Real-time help**: Join our community
- 👥 **Other developers**: Share experiences
- 🔧 **Quick fixes**: Community solutions

### Information to Provide

**Always Include:**
- 📦 **Mod Version**
- 🎮 **Minecraft Version**
- 🔨 **Forge Version**
- 💻 **Java Version**
- 🖥️ **Operating System**
- 📋 **Steps to reproduce**
- 📄 **Crash logs** (if applicable)
- 🖼️ **Screenshots** (if visual issue)

**Example:**
```
**Environment:**
- ChaosCrafts Device Mod: 1.0.0
- Minecraft: 1.20.1
- Forge: 47.2.0
- Java: 17.0.8 (Eclipse Temurin Recommended)
- OS: Windows 11

**Issue:** App not appearing on desktop

**Steps:**
1. Added registration code to FMLClientSetupEvent
2. Built and ran mod
3. Opened ChaosCrafts device
4. App not visible on desktop

**Code:** [paste registration code]
```

## 🔧 Advanced Debugging

### Enable Debug Logging

**Add to `log4j2.xml`:**
```xml
<Logger name="net.chaoscraft.chaoscrafts_device_mod" level="DEBUG" additivity="false">
    <AppenderRef ref="Console"/>
    <AppenderRef ref="File"/>
</Logger>
```

### Debug Registration

```java
// Add debug logging
private static final Logger LOGGER = LogManager.getLogger();

@SubscribeEvent
public static void onClientSetup(FMLClientSetupEvent event) {
    LOGGER.info("🔧 Starting app registration...");

    try {
        AppFactory.registerSimpleApp("myapp", MyApp::new, "My App", "Desc", "1.0");
        LOGGER.info("✅ App registered successfully");

        // Verify registration
        boolean installed = AppRegistry.getInstance().isInstalled("myapp");
        LOGGER.info("📱 App installed: " + installed);

    } catch (Exception e) {
        LOGGER.error("❌ Registration failed", e);
    }
}
```

### Performance Profiling

**Use VisualVM or YourKit:**
- 📊 Monitor memory usage
- ⏱️ Profile method execution times
- 🧵 Check thread activity

**In-game Debug:**
```java
// Add FPS counter
@Override
public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack,
                        DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
    int fps = Minecraft.getInstance().fps;
    guiGraphics.drawString(Minecraft.getInstance().font,
        Component.literal("FPS: " + fps), 10, 10, 0xFFFFFF, false);
}
```

---

**Still having issues?** Create a [GitHub issue](../../issues) with all the details above!
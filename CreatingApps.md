# Creating Apps for ChaosCrafts Device Mod

> 🎮 **Add Your Own Apps to the PC Simulation!** This guide shows you how to create and integrate custom apps with minimal code.

**📚 Tutorial designed by [Admany](https://github.com/Admany)**  
**👥 Created by [ChaosCraft](https://github.com/ChaosCraftZ) & [Admany](https://github.com/Admany)**

## 🚀 Quick Start (5 Minutes)### 1. Create Your App Class

```java
package com.example.mymod.apps;

import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.PoseStack;

public class MyApp implements IApp {
    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack,
                            DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        guiGraphics.drawString(Minecraft.getInstance().font,
            Component.literal("Hello from My App!"), 20, 40, 0xFFFFFFFF, false);
    }

    // Required methods (can be empty)
    @Override public void onOpen(DraggableWindow window) {}
    @Override public boolean mouseClicked(DraggableWindow window, double x, double y, int button) { return false; }
    @Override public void mouseReleased(DraggableWindow window, double x, double y, int button) {}
    @Override public boolean mouseDragged(DraggableWindow window, double x, double y, double dx, double dy) { return false; }
    @Override public boolean charTyped(DraggableWindow window, char c, int modifiers) { return false; }
    @Override public boolean keyPressed(DraggableWindow window, int key, int scan, int modifiers) { return false; }
    @Override public boolean onClose(DraggableWindow window) { return true; }
}
```

### 2. Register Your App

```java
// In your mod's client setup (FMLClientSetupEvent)
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.AppFactory;

@Mod.EventBusSubscriber(modid = "mymod", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MyModClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // One line to add your app!
        AppFactory.registerSimpleApp(
            "myapp",           // Internal name (unique, lowercase)
            MyApp::new,        // Your app class
            "My App",          // Display name
            "Does cool stuff", // Description
            "1.0"              // Version
        );
    }
}
```

**Done!** Your app appears on desktop and taskbar instantly! 🖥️

## 📚 Core Concepts

### The App System
- **`IApp`**: Interface your app implements
- **`AppFactory`**: Registers and creates apps
- **`AppRegistry`**: Manages installed apps
- **`IconManager`**: Handles custom icons
- **`DraggableWindow`**: Your app's window container

### App Lifecycle
1. **Registration**: Tell mod about your app
2. **Installation**: Make it available to players
3. **Usage**: Players open and use your app

## 🛠️ Registration Methods

### `registerSimpleApp()` - Recommended
```java
static void registerSimpleApp(String internalName, Supplier<IApp> supplier,
                            String displayName, String description, String version)
```
- Uses default icon
- One-line setup
- Perfect for most apps

### `registerAndInstallApp()` - With Custom Icon
```java
static void registerAndInstallApp(String internalName, Supplier<IApp> supplier,
                                String displayName, String description, String version,
                                ResourceLocation iconResource)
```
- Supports custom 32x32 PNG icons
- Pass `null` for default icon

### Manual Registration - Advanced
```java
// Separate steps for full control
AppFactory.registerApp("myapp", MyApp::new);
IconManager.registerCustomIcon("myapp", new ResourceLocation("mymod", "textures/gui/icons/myapp.png"));
AppRegistry.getInstance().installApp("myapp", "My App", "Description", "1.0");
```

## 🎨 Custom Icons

1. **Create**: 32x32 PNG file
2. **Location**: `src/main/resources/assets/yourmodid/textures/gui/icons/`
3. **Register**: Use `registerAndInstallApp` with ResourceLocation

```java
AppFactory.registerAndInstallApp(
    "myapp",
    MyApp::new,
    "My App",
    "Description",
    "1.0",
    new ResourceLocation("mymod", "textures/gui/icons/myapp.png")
);
```

## 🔧 Advanced Features

### Persistent Data
```java
import net.chaoscraft.chaoscrafts_device_mod.Client.Files.FilesManager;

@Override
public void onOpen(DraggableWindow window) {
    Path dataDir = FilesManager.getPlayerDataDir();
    // Load/save your app data here
}
```

### Async Operations
```java
import net.chaoscraft.chaoscrafts_device_mod.Client.Async.AsyncRuntime;

AsyncRuntime.get().submitIo(() -> {
    // Heavy I/O here
    return result;
}).thenAccept(result -> {
    // Back on main thread
});
```

### Window Management
```java
@Override
public void onOpen(DraggableWindow window) {
    window.setTitle("My Custom Title");
    // Window size set when opening
}
```

## 🏆 Best Practices

- **🔒 Client-Safe**: Register only in `FMLClientSetupEvent`
- **📏 Icons**: 32x32 PNG, simple design
- **⚡ Performance**: Use async for heavy operations
- **🛡️ Error Handling**: Wrap risky code in try-catch
- **🔄 Updates**: Bump version for app updates
- **📝 Naming**: Lowercase internal names, unique across mods

## 📖 API Reference

### IApp Interface
```java
void onOpen(DraggableWindow window);
void renderContent(GuiGraphics gui, PoseStack pose, DraggableWindow window,
                  int mouseRelX, int mouseRelY, float partialTick);
boolean mouseClicked(DraggableWindow window, double x, double y, int button);
void mouseReleased(DraggableWindow window, double x, double y, int button);
boolean mouseDragged(DraggableWindow window, double x, double y, double dx, double dy);
boolean charTyped(DraggableWindow window, char c, int modifiers);
boolean keyPressed(DraggableWindow window, int key, int scan, int modifiers);
boolean onClose(DraggableWindow window);
```

### AppFactory
```java
// Simple registration
static void registerSimpleApp(String name, Supplier<IApp> supplier,
                            String displayName, String desc, String version)

// With custom icon
static void registerAndInstallApp(String name, Supplier<IApp> supplier,
                                String displayName, String desc, String version,
                                ResourceLocation icon)

// Advanced
static int registerApp(String name, Supplier<IApp> supplier)
static IApp create(String name)
```

### Utility Classes
```java
// File system
class FilesManager {
    static Path getPlayerDataDir()
    static Path getWorldDataDir()
}

// Async operations
class AsyncRuntime {
    static AsyncRuntime get()
    CompletableFuture<Void> submitIo(Supplier<?> task)
}

// Icons
class IconManager {
    static void registerCustomIcon(String appName, ResourceLocation icon)
    static ResourceLocation getIconResource(String appName)
}

// Registry
class AppRegistry {
    static AppRegistry getInstance()
    void installApp(String name, String display, String desc, String version)
    boolean isInstalled(String name)
    List<String> getInstalledAppNames()
}
```

## 💡 Complete Example

```java
package com.example.mymod;

import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.AppFactory;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "mymod", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MyModClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        AppFactory.registerSimpleApp(
            "mycalculator",
            CalculatorApp::new,
            "Calculator",
            "Basic calculator app",
            "1.0"
        );
    }

    public static class CalculatorApp implements IApp {
        private String display = "0";

        @Override
        public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack,
                                DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
            // Draw display
            guiGraphics.fill(10, 10, 140, 30, 0xFF333333);
            guiGraphics.drawString(Minecraft.getInstance().font,
                Component.literal(display), 15, 18, 0xFFFFFF, false);

            // Draw buttons (simplified)
            drawButton(guiGraphics, "1", 10, 40);
            drawButton(guiGraphics, "2", 40, 40);
            drawButton(guiGraphics, "3", 70, 40);
            drawButton(guiGraphics, "+", 100, 40);
        }

        private void drawButton(GuiGraphics guiGraphics, String text, int x, int y) {
            guiGraphics.fill(x, y, x + 25, y + 25, 0xFF666666);
            guiGraphics.drawString(Minecraft.getInstance().font,
                Component.literal(text), x + 8, y + 8, 0xFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(DraggableWindow window, double mouseX, double mouseY, int button) {
            // Handle button clicks (simplified)
            if (mouseX >= 10 && mouseX <= 35 && mouseY >= 40 && mouseY <= 65) {
                display = "1";
                return true;
            }
            return false;
        }

        // Required empty implementations
        @Override public void onOpen(DraggableWindow window) {}
        @Override public void mouseReleased(DraggableWindow window, double x, double y, int button) {}
        @Override public boolean mouseDragged(DraggableWindow window, double x, double y, double dx, double dy) { return false; }
        @Override public boolean charTyped(DraggableWindow window, char c, int modifiers) { return false; }
        @Override public boolean keyPressed(DraggableWindow window, int key, int scan, int modifiers) { return false; }
        @Override public boolean onClose(DraggableWindow window) { return true; }
    }
}
```

## 🎉 Happy Modding!

**Need Help?**
- Check `ExampleAddOn.java` in the mod source
- Join our Discord community
- Create GitHub issues
- Share your apps with the community!

*Built with ❤️ for Minecraft modders*
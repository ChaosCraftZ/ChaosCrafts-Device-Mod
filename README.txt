# ChaosCrafts Device Mod

> 🎮 **Bringing Desktop Computing to Minecraft!** A fully functional PC simulation mod with desktop, apps, and file system.

**👥 Created by [ChaosCraft](https://github.com/ChaosCraft) & [Admany](https://github.com/Admany)**

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-orange)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.2.0-red)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

## ✨ Features

- 🖥️ **Full Desktop Environment**: Complete PC simulation with windows, desktop, and taskbar
- 📱 **Extensible App System**: Easy-to-use API for adding custom apps
- 🎨 **Custom Icons**: Support for custom app icons with automatic fallbacks
- 💾 **Persistent Storage**: File system integration for app data
- 🚀 **Async Operations**: Background task support for heavy operations
- 🔧 **Developer Friendly**: Simple registration system for modders

## 🎯 What You Get

### Built-in Apps
- 🖥️ **Browser**: Web surfing in Minecraft
- 🧮 **Calculator**: Math operations
- 🎨 **Paint**: Drawing and creativity
- 📁 **Files**: File management
- ⚙️ **Settings**: Customization options
- 📺 **YouTube**: Video watching
- 🛒 **Marketplace**: Download more apps
- 🎮 **Geometry Dash**: Mini-games
- 🏠 **Home Security**: Base monitoring
- 📝 **Notepad**: Text editing
- 🎵 **Audio Player**: Music playback
- 🎬 **Video Player**: Movie watching

### For Developers
- 📚 **Simple API**: Add apps with just one line of code
- 🔌 **Plug-and-Play**: Automatic desktop and taskbar integration
- 🎨 **Icon Support**: Custom icons with smart fallbacks
- 💾 **Data Persistence**: Built-in save/load system
- 🚀 **Performance**: Async support for heavy operations

## 🚀 Quick Start

1. **Install the mod** in your Minecraft instance
2. **Launch Minecraft** with the mod loaded
3. **Load/create a world**
4. **Find and interact** with ChaosCrafts devices
5. **Open the desktop** to access apps

## 📖 Documentation

- **[Creating Apps](CreatingApps.md)**: Guide for developers to add custom apps
- **[Troubleshooting](Troubleshooting.md)**: Common issues and solutions

## 🛠️ Installation

### For Players
1. Install [Minecraft Forge](https://files.minecraftforge.net/) 47.2.0+
2. Download the mod JAR from [releases](../../releases)
3. Place in your `mods` folder
4. Launch Minecraft

### For Developers
1. Clone this repository
2. Run `./gradlew build`
3. Use the built JAR in your development environment

## 🔧 Requirements

- **Minecraft**: 1.20.1
- **Forge**: 47.2.0 or higher
- **Java**: 17 or higher
- **RAM**: 4GB recommended

## 🤝 Contributing

We love contributions! Here's how you can help:

### For Players
- 🐛 **Report Bugs**: [GitHub Issues](../../issues)
- 💡 **Suggest Features**: [GitHub Discussions](../../discussions)
- 📖 **Improve Docs**: Edit our documentation

### For Developers
- 🚀 **Add Apps**: Create new apps using our API
- 🔧 **Improve Code**: Submit pull requests
- 🧪 **Test**: Help test new features

### Development Setup
```bash
git clone https://github.com/yourusername/ChaosCrafts-Device-Mod.git
cd ChaosCrafts-Device-Mod
./gradlew build
./gradlew runClient  # For testing
```

## 📋 API Overview

### Simple App Registration
```java
AppFactory.registerSimpleApp(
    "myapp",           // Internal name
    MyApp::new,        // App supplier
    "My App",          // Display name
    "Description",     // Description
    "1.0"              // Version
);
```

### With Custom Icon
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

## 📞 Support

- 📧 **Issues**: [GitHub Issues](../../issues)
- 💬 **Discord**: [Join our community](https://discord.gg/UPZ8BAJYvB)
- 🐛 **Bug Reports**: Include logs and steps to reproduce

## 📄 License

This project is licensed under the MIT License.

## 🙏 Credits

- **Lead Developer**: ChaosCraft and Admany

## 🎯 Roadmap

- [ ] Mobile app support
- [ ] Cloud sync features
- [ ] Advanced file operations
- [ ] Plugin marketplace
- [ ] Multiplayer collaboration
- [ ] Custom themes
- [ ] App store integration

---
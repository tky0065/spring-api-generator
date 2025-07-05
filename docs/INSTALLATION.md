# Installation Guide

This guide provides detailed instructions for installing the Spring Boot Code Generator plugin in your IntelliJ IDEA IDE.

## Prerequisites

- IntelliJ IDEA (Community or Ultimate) 2023.1 or later
- Java Development Kit (JDK) 11 or later

## Installation Methods

### Method 1: Install from JetBrains Marketplace (Recommended)

1. Open IntelliJ IDEA
2. Navigate to **Settings** (or **Preferences** on macOS)
   - Windows/Linux: File → Settings
   - macOS: IntelliJ IDEA → Preferences
3. Select **Plugins** from the left sidebar
4. Click on the **Marketplace** tab at the top
5. In the search box, type "Spring Boot Code Generator"
6. Click on the **Install** button next to the plugin
7. After installation, click the **Restart IDE** button to apply changes
8. After IDE restarts, the plugin is ready to use

### Method 2: Install from Disk

1. Download the latest plugin JAR file from [GitHub Releases](https://github.com/enokdev/spring-api-generator/releases)
2. Open IntelliJ IDEA
3. Navigate to **Settings** (or **Preferences** on macOS)
   - Windows/Linux: File → Settings
   - macOS: IntelliJ IDEA → Preferences
4. Select **Plugins** from the left sidebar
5. Click on the gear icon (⚙️) in the upper right corner
6. Select **Install Plugin from Disk...**
7. Navigate to the downloaded JAR file and select it
8. Click **OK** to install the plugin
9. Click the **Restart IDE** button when prompted
10. After IDE restarts, the plugin is ready to use

## Verifying the Installation

To verify that the plugin has been installed correctly:

1. Open a Java project with JPA entities (or create a new Spring Boot project)
2. Open any Java file containing a JPA entity class (a class with `@Entity` annotation)
3. Right-click in the editor
4. In the context menu, you should see the option "Generate Spring REST Code"

If you can see this menu item, the plugin has been successfully installed.

## Troubleshooting

### Plugin Not Visible in Context Menu

- Make sure you're right-clicking on a valid JPA entity class (with `@Entity` annotation)
- Check if the plugin is enabled in Settings → Plugins
- Try restarting IntelliJ IDEA

### Installation Fails

- Ensure your IntelliJ IDEA version is 2023.1 or later
- Check your internet connection (for marketplace installation)
- Make sure you have sufficient permissions to install plugins

### Plugin Conflicts

If you experience any conflicts with other plugins:

1. Go to Help → Show Log in Explorer/Finder
2. Check the log file for any plugin-related errors
3. If you identify conflicts, try disabling potentially conflicting plugins

## Updating the Plugin

To update to a newer version of the plugin:

1. Go to Settings → Plugins
2. Select the "Installed" tab
3. If an update is available, you'll see an "Update" button next to the plugin
4. Click "Update" and restart the IDE when prompted

## Uninstalling the Plugin

If you need to uninstall the plugin:

1. Go to Settings → Plugins
2. Select the "Installed" tab
3. Find "Spring Boot Code Generator" in the list
4. Click the gear icon next to it and select "Uninstall"
5. Restart IntelliJ IDEA when prompted

## Getting Help

If you encounter any issues with installation, please:

- Check the [FAQ section](./FAQ.md) for common issues
- Visit our [GitHub Issues](https://github.com/enokdev/spring-api-generator/issues) page
- Contact support at support@enokdev.com

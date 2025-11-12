# RetroPlay-Android

## Project Overview

RetroPlay-Android is a standalone Android application for emulating retro games. It's a feature-rich emulator that supports a wide range of consoles, from the NES to the PlayStation. The application is built with a modern tech stack, including Kotlin, Jetpack Compose, and Material 3.

The core of the application is its hybrid emulation system, which combines native emulation using **LibretroDroid** with web-based emulation using **EmulatorJS**. This allows the application to support a vast library of games and provides a flexible and powerful emulation experience.

The application is a standalone version of a larger project, `ChatAI-Android`, and shares some of its components and data. It runs a local web server on port 7777 to serve the EmulatorJS files.

## Building and Running

### Prerequisites

*   Android 7.0+ (API 24)
*   Android Studio or Gradle command line
*   Android device or emulator

### Build

To build the application, run the following command from the project's root directory:

```bash
.\gradlew clean assembleDebug
```

### Install

To install the application on a connected device or emulator, run the following command:

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Development Conventions

### Technologies

*   **Kotlin** and **Java**
*   **Jetpack Compose** for the UI
*   **Material 3** for the design system
*   **LibretroDroid** for native emulation
*   **EmulatorJS** for web-based emulation
*   **OkHttp** for HTTP requests
*   **Glide** for image loading
*   **Gson** for JSON parsing

### Project Structure

The project is organized into the following modules:

*   `app`: The main application module, containing the activities, services, and UI.
*   `lemuroid-touchinput`: A module for the virtual gamepads.
*   `libretrodroid`: A module for the native emulation core.
*   `retrograde-util`: A module for Libretro utilities.

### Code Style

The project follows the standard Android and Kotlin coding conventions. The code is well-documented, with clear and concise comments.

### Testing

The project does not have a dedicated testing suite. However, the `README.md` file provides instructions for manual testing and debugging.

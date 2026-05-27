# Demo Notes & Tasks App

A full-stack Kotlin Multiplatform application for managing notes and tasks, featuring a shared UI across multiple platforms and a centralized Ktor backend.

## Features

- **User Authentication**: Secure login and session management using JWT.
- **Note Management**: Create, read, update, and delete notes.
- **Task Tracking**: Organize and manage your tasks.
- **Cross-Platform**: One codebase for Android, iOS, Desktop (JVM), and Web (WasmJs).
- **Real-time Sync**: Data is synchronized via a Ktor backend.

## Tech Stack

- **Frontend**: [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- **Backend**: [Ktor](https://ktor.io/)
- **Language**: [Kotlin](https://kotlinlang.org/)
- **Architecture**: MVVM (Model-View-ViewModel) with Repository pattern.

## Project Structure

- [/composeApp](./composeApp/src): UI and platform-specific logic for the client apps.
  - [commonMain](./composeApp/src/commonMain/kotlin): Shared UI and business logic.
  - Other folders (`androidMain`, `iosMain`, `jvmMain`, `wasmJsMain`) contain platform-specific implementations.
- [/iosApp](./iosApp/iosApp): iOS application entry point and SwiftUI code.
- [/server](./server/src/main/kotlin): Ktor backend application.
- [/shared](./shared/src): Shared logic and data models between platforms.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run Server

To build and run the development version of the server, use the run configuration from the run widget in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :server:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :server:run
  ```

### Build and Run Web Application

To build and run the development version of the web app, use the run configuration from the run widget in your IDE's toolbar or run it directly from the terminal:
- for the Wasm target (faster, modern browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
    ```
- for the JS target (slower, supports older browsers):
  - on macOS/Linux
    ```shell
    ./gradlew :composeApp:jsBrowserDevelopmentRun
    ```
  - on Windows
    ```shell
    .\gradlew.bat :composeApp:jsBrowserDevelopmentRun
    ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform), [Kotlin/Wasm](https://kotl.in/wasm/)…
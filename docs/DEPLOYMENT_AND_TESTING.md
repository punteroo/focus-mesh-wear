# First-Time Wear OS Setup & Galaxy Watch Ultra Deployment

If this is your first time developing and testing an app on a **Samsung Galaxy Watch Ultra**, follow this step-by-step guide to build, install, and test FocusMesh Wear directly on your wrist.

## 1. Prerequisites on Your Computer

1. Download and install **Android Studio** (Koala / Ladybug or newer).
2. During setup, ensure the following SDK components are installed via **Settings -> Languages & Frameworks -> Android SDK**:
   - **Android 14.0 ("UpsideDownCake", API Level 34)** SDK Platform.
   - **Android SDK Platform-Tools** (includes `adb`).
   - Optional for emulator testing: **Wear OS 5 Round System Image (API 34)**.

## 2. Enabling Developer Options & Wireless Debugging on Galaxy Watch Ultra

Samsung watches deploy apps over Wi-Fi using Wireless ADB (since the watch charges wirelessly and has no USB port):

1. Open **Settings** on your Galaxy Watch Ultra.
2. Scroll to the bottom and tap **About watch** -> **Software information**.
3. Tap **Software version** rapidly **7 times** until a toast message says *"Developer mode turned on"*.
4. Swipe back to the main **Settings** menu; you will now see **Developer options** near the bottom.
5. Connect your Galaxy Watch Ultra to the **exact same Wi-Fi network** as your computer (Settings -> Connections -> Wi-Fi).
6. Open **Settings -> Developer options** on the watch:
   - Enable **ADB debugging**.
   - Tap **Wireless debugging** and toggle it **On**.
7. Inside **Wireless debugging**, tap **Pair new device**. The watch will display:
   - A **Wi-Fi pairing code** (6 digits).
   - An **IP address and Pairing Port** (for example, `192.168.1.45:37123`).

## 3. Pairing Your Computer to the Watch

Open a terminal on your computer (or the Terminal tab inside Android Studio) and run:

```bash
adb pair <WATCH_IP>:<PAIRING_PORT>
```

Enter the 6-digit pairing code shown on your watch when prompted. Once paired, look at the main **Wireless debugging** screen on your watch (one screen back from the pairing dialog) to see the connection port (which is different from the pairing port), and run:

```bash
adb connect <WATCH_IP>:<CONNECTION_PORT>
```

Your Samsung Galaxy Watch Ultra will now appear in Android Studio's device dropdown at the top toolbar.

## 4. Building and Installing the App

1. Open this repository folder in Android Studio.
2. Wait for Gradle sync to finish.
3. Select your **Samsung SM-L705U / Galaxy Watch Ultra** in the target device dropdown.
4. Click the green **Run ('app')** button (`Shift + F10`).
5. On first launch on the watch, grant the **Notifications** permission when prompted so the Ongoing Activity watch face indicator can render.

## 5. Testing Key Features on the Watch

- **Phase Transitions**: Long-press the center timer digits to skip directly from **Work (Red)** -> **Break (Light Blue)** -> **Readiness (Yellow)** without waiting 60 minutes.
- **Touch Wave Physics**: Drag your finger across the mesh background to watch the 3D surface ripple and rebound.
- **Left-Swipe Settings Drawer**: Swipe from the left edge of the screen toward the right to open the Configuration UI. Test switching between 3D and 2D mesh modes, changing Google Fonts, toggling the Readiness grace timer, and previewing acoustic chimes.
- **Do Not Disturb Bypass**: Enable **Do Not Disturb** from the watch Quick Panel, start a timer, and advance/complete a phase to verify the alarm stream chime and haptic waveform still fire.
- **One UI Watch Island**: Start the Work timer and press the Home button to return to your watch face. You will see the floating Ongoing Activity indicator at the bottom of the watch face; tap it to jump straight back into FocusMesh Wear.

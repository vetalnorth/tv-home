# TV Home — Liquid Glass Edition

A lightweight, ultra-smooth, and distraction-free Android TV launcher inspired by Apple tvOS and modern glassmorphism design.

Forked from [mihirgupta0900/tv-home](https://github.com/mihirgupta0900/tv-home) and completely rewritten to eliminate UI stuttering, input lag, and heavy background rendering overhead.

![Before and After](before-after.png?v=2)

---

## ⚠️ Important Note: What This Project Is (and Isn't)

> ### 📌 Just a Launcher, Not a Custom Firmware
> **This project is strictly a standalone user interface (UI Launcher).**  
> It replaces the stock home screen on your TV, but it does **not** modify your system firmware, root your device, or automatically remove Google telemetry, analytics, or background manufacturer daemons.
>
> ### 🛑 No Debloating Guides Provided Here
> Every TV brand (Xiaomi, TCL, Sony, Philips, Realme, Hisense, etc.) uses entirely different underlying system package names, framework dependencies, and power-management rules. Executing generic debloat scripts found online can easily soft-brick your TV into an unrecoverable bootloop.
>
> **If you want to disable background Google telemetry, stock launchers, or analytics services:**  
> Use Google search, specialized Android modding forums (such as XDA Developers or 4PDA), or consult an AI assistant (like ChatGPT, Claude, etc.) **specifically for your exact TV brand and model number**. You are solely responsible for managing your device's internal packages.

---

## ✨ Features

- **Liquid Glass Aesthetic:** Sleek frosted glass dock capsules, ambient gradients, and crisp high-contrast focus rings.
- **Adaptive TV Layout:** Built with density-independent units (`dp`) and responsive grids, engineered to scale cleanly across 1080p and 4K panels.
- **Centered Smooth Dock:** The horizontal Favorites row smoothly glides and auto-centers the focused tile as you browse with your D-pad.
- **Remote-First Dock Management:** **Long-press `OK`** on any app tile in the grid to pin it to the Favorites bar. Long-press again in the Dock to remove it.
- **Hardware-Accelerated Fluidity:** Heavy dynamic gradient engines and continuous UI-thread shaders were completely removed, delivering an instant 60–120Hz response rate even on budget smart TVs.
- **All Apps Accessible:** Seamlessly displays both native Android TV / Leanback applications and sideloaded APKs.

---

## 🎮 Navigation & Remote Shortcuts

| Action | Remote Button |
| :--- | :--- |
| **Launch App** | Press `OK` / `Enter` |
| **Pin / Unpin to Dock** | **Long-press `OK`** on any app tile |
| **Navigate Grid / Dock** | D-pad `Left` / `Right` / `Up` / `Down` |
| **Quick Settings & Inputs** | Press `Up` from the top row |

---

## 📺 Tested Hardware & Compatibility

* **Tested & Verified (60 FPS Fluid):**
  * **Xiaomi TV A 43" 4K (2026 Edition)** — Google TV, 4K Ultra HD panel, instant D-pad response, zero UI lag.
* **Target Requirements:** Android TV / Google TV 9.0 (Pie) or newer with standard Leanback framework.
* **Community Feedback:** Tested on other models? Feel free to [open an issue](https://github.com/vetalnorth/tv-home/issues) with your TV model and impressions!

---

## 📦 Quick Start & Setup

1. Download the latest **[tvhome.apk](../../releases/latest/download/tvhome.apk)** (or check the [Releases](../../releases) section).
2. Transfer and install it via flash drive, local network, or via ADB: `adb install tvhome.apk`
3. Press the **Home** button on your TV remote, choose **TV Home**, and select **"Always"**.

---

## 👥 Credits & Contributors

<table>
  <tr>
    <td align="center" valign="top" width="160">
      <a href="https://github.com/mihirgupta0900">
        <img src="https://github.com/mihirgupta0900.png" width="90" style="border-radius: 50%;" alt="Mihir Gupta"/><br />
        <sub><b>Mihir Gupta</b></sub>
      </a><br />
      <sub>💻 Original Core</sub>
    </td>
    <td align="center" valign="top" width="160">
      <a href="https://github.com/vetalnorth">
        <img src="https://github.com/vetalnorth.png" width="90" style="border-radius: 50%;" alt="vetalnorth"/><br />
        <sub><b>vetalnorth</b></sub>
      </a><br />
      <sub>🎯 Direction & AI Prompting</sub>
    </td>
    <td align="center" valign="top" width="160">
      <a href="https://gemini.google.com/">
        <img src="https://www.gstatic.com/lamda/images/gemini_sparkle_v002_d4735304ff6292a690345.svg" width="90" style="border-radius: 50%; padding: 10px; background: #1e1f20;" alt="Google Gemini"/><br />
        <sub><b>Google Gemini</b></sub>
      </a><br />
      <sub>🤖 Code & UI Execution</sub>
    </td>
  </tr>
</table>

---

## ☕ Support the Project

If you enjoy this launcher and want to support further development and optimization:

- **[Donate via DonationAlerts](https://www.donationalerts.com/r/izwi)**

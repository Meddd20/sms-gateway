# SMSGateway

Turn a dedicated Android phone into an SMS gateway for institutional transaction notifications.

## About

SMSGateway lets an institution send transaction notifications by SMS to its customers or members, using nothing more than an Android phone with an active SIM card. Staff install the app on a phone that stays at the office, sign in once, and the phone quietly sends messages on the institution's behalf from then on. No technical background is needed to operate it — the app walks the user through every required step.

## Key features

- **Dual SIM support** — on phones with two SIM cards, outgoing and incoming messages can be enabled or disabled independently for SIM 1 and SIM 2.
- **API key login** — sign in with the API key issued by the CoreSystem web dashboard.
- **QR code login** — alternatively, scan the QR code from CoreSystem instead of typing the key.
- **Background SMS listener** — once signed in, a listener service keeps running so sent and received messages are monitored without the app being open.
- **Heartbeat / connectivity check** — a "Send heartbeat" button pings the server to confirm the device is online and reachable.
- **Guided required setup** — the app detects missing SMS permissions and battery optimization and walks the user through fixing them.

## Screenshots

<!-- Replace the placeholder paths below with real screenshots, e.g. docs/screenshots/login.png -->

| Login | SIM selection | Main screen |
|---|---|---|
| ![Login screen](docs/screenshots/login.png) | ![SIM selection](docs/screenshots/sim-selection.png) | ![Main screen](docs/screenshots/main.png) |

## Requirements

- **Android 9 (API 28) or newer.**
- **Google Play Services** — required for push notifications (FCM). The app cannot sign in without a notification token.
- **An active SIM card** able to send SMS, with sufficient credit or SMS package. A dual SIM phone is fully supported.
- **Internet connection** — mobile data or Wi-Fi.

Permissions the app requests:

| Permission | Why |
|---|---|
| Send, receive and read SMS (plus receive MMS) | To send notifications and detect incoming replies |
| Read phone state | To detect the SIM cards and their phone numbers |
| Notifications | To show the ongoing listener notification |
| Foreground service, wake lock, boot completed | To keep the listener running in the background |
| Ignore battery optimization | So the system does not stop the app when the screen is off |
| Internet and network state | To talk to the server |

## Installation and setup

1. **Install the application** on the phone that will act as the gateway device.
2. **Get the API key from CoreSystem.** Open the CoreSystem dashboard, go to the **Gateway Settings** menu, and copy the API key shown there. If you prefer, display the QR code on the same screen and scan it from the app later.
3. **Open the app and grant permissions.** Allow SMS, phone and notification access when prompted. The app cannot work without SMS permission.
4. **Sign in.** Paste the API key into the field (it is labelled **"Kode Pairing"** in the app), or tap the QR icon to scan it. Then tap **Masuk**.
5. **Complete the required setup.** The main screen checks each requirement and shows one step at a time: disable battery optimization, then grant SMS permission, then enable autostart. Follow the button in each dialog until no more appear.
6. **Send a test heartbeat.** Tap **Kirim Sinyal Status** to confirm the device can reach the server.

## Usage flow

1. **Login** — enter the API key from CoreSystem (or scan its QR code) and tap **Masuk**. A successful login validates the key and registers the phone with the server.
2. **SIM selection** — the app lists the SIM cards it detects. Pick the SIM that should send the notifications. If a SIM cannot report its number, enter it manually when prompted. The chosen line stays in use until you log out.
3. **Enable permissions** — work through the required setup dialogs: battery optimization off, SMS permission granted, autostart enabled. These keep the background listener alive.
4. **Send a test heartbeat** — tap **Kirim Sinyal Status**. A message reading *"Sinyal status berhasil dikirim"* confirms the device is online.
5. **Leave it running** — the app now listens in the background and sends messages as instructed by the server. A permanent notification, *"Pemantau SMSGateway"*, indicates the listener is active.

## Project structure and architecture

Kotlin application with a Jetpack Compose UI.

<!-- Navigation approach: the app currently uses Activity-based navigation. Each Activity hosts its Compose content via setContent. Update this section if that changes. -->

```
app/src/main/java/com/httpsms/
├── LoginActivity.kt / MainActivity.kt / SettingsActivity.kt   Screens and navigation hosts
├── core/          App configuration and device information (settings, device status, OEM settings)
├── data/
│   ├── api/       HTTP client and backend endpoints
│   ├── io/        Attachment download for MMS
│   └── model/     Request and response models
├── push/          Firebase Cloud Messaging (FCM) integration
├── sms/           Sending, receiving and status reporting for SMS/MMS
├── background/    Boot receiver, heartbeat worker, foreground listener service
├── ui/            Compose screens and shared components
└── util/          Helpers (phone number validation)
```

Navigation is **Activity-based**: the flow is Login → Main, with each screen hosted in its own Activity. The backend contract is documented in [`docs/API-CONTRACT.md`](docs/API-CONTRACT.md).

## Troubleshooting

**"Nomor untuk SIM 1 tidak dapat dibaca dari perangkat. Lepas dan pasang kembali kartu SIM Anda, lalu coba lagi."**
The phone cannot read the SIM's phone number. Take the SIM out and put it back in, then retry. If the SIM still hides its number, enter the number manually in the SIM picker — the app accepts a hand-typed number in international format (for example `+6281234567890`).

**"Token notifikasi (FCM) tidak ditemukan. Pastikan Google Play Services sudah terpasang di perangkat Anda"**
The app has no push notification token. Make sure Google Play Services is installed and up to date, that the device has an internet connection, and try again. If it persists, reinstall the app.

**Heartbeat fails ("Gagal")**
A failed heartbeat opens a dialog titled *"Gagal"* containing the message returned by the request. Most often this is a connectivity problem: check that the phone has internet, then tap **Kirim Sinyal Status** again. If the device is not signed in, sign in first.

**"Tidak ada kartu SIM yang terdeteksi. Masukkan kartu SIM untuk melanjutkan."**
No SIM card is visible to the app. Insert a SIM and make sure it is active.

**The app stops sending after a while**
Android may be putting the app to sleep. Open the app and complete the required setup dialogs — battery optimization off and autostart enabled. On some manufacturers (Xiaomi, Oppo, Vivo, Huawei), the autostart setting must be switched on manually; the dialog explains how.

## Acknowledgements

This application is a modified fork of [httpSMS](https://github.com/NdoleStudio/httpsms) by [NdoleStudio](https://github.com/NdoleStudio), and builds on its Android client. Thanks to the httpSMS authors and contributors.

See [NOTICE](NOTICE) for the attribution and a summary of the changes made in this fork.

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)** — see the [LICENSE](LICENSE) file for the full text.

Because it is derived from httpSMS, which is also AGPL-3.0, the same licence applies to this work: anyone who receives this application is entitled to its complete corresponding source.

# API & FCM Contract — Android SMS Gateway

Kontrak backend ↔ aplikasi Android (diambil langsung dari kode app saat ini).
Update file ini setiap payload/protocol berubah.

---

## 1. Konfigurasi Umum

| Item | Nilai |
|---|---|
| Base URL | **Statis** di `BuildConfig.SERVER_URL` (build-time, placeholder `https://api.httpsms.com` — ganti dengan backend sendiri sebelum build). |
| Header semua request | `x-api-key: <API key user>` dan `X-Client-Version: <versi app>` (contoh `1.0.0`). |
| Envelope response standar | `{ "data": ..., "message": "...", "status": "..." }` (diparse oleh app; `data` bisa berisi object). |
| Format timestamp | ISO-8601 UTC: `yyyy-MM-dd'T'HH:mm:ss.SSS'000000'Z` (mis. `2026-09-09T03:00:00.000000Z`). |

Kode pemanggil: `app/src/main/java/com/httpsms/HttpSmsApiService.kt`.
Model: `app/src/main/java/com/httpsms/Models.kt`.

---

## 2. Registrasi line & validasi API key

**`PUT /v1/phones/fcm-token`** — dipakai saat login (per SIM), FCM `onNewToken`, dan refresh token 24 jam.

Request:
```json
{
  "fcm_token": "<firebase cloud messaging token>",
  "phone_number": "+60123456789",
  "sim": "SIM1"
}
```

Response sukses (2xx):
```json
{
  "data": { "id": "phone-id", "user_id": "account-user-id" },
  "message": "ok",
  "status": "success"
}
```
Catatan penting:
- Ini **satu-satunya mekanisme "login"** — sukses berarti API key valid.
- `user_id` disimpan app (dipakai identitas account).
- **`401` = API key salah** → app menampilkan pesan "Cannot validate the API key…".
- Selain 2xx → login gagal.
- `sim` bernilai `"SIM1"` atau `"SIM2"` (label posisi subscription di device).
- Backend harus menyimpan mapping `phone_number + sim → fcm_token` untuk routing FCM.

---

## 3. Ambil pesan untuk dikirim

**`GET /v1/messages/outstanding?message_id=<messageId>`** — dipanggil worker setelah menerima FCM `KEY_MESSAGE_ID`.

Response envelope dengan `data` berupa object `Message`:
```json
{
  "id": "message-id",
  "contact": "+628123456",
  "content": "isi sms / pdu",
  "sim": "SIM1",
  "owner": "+60123456789",
  "encrypted": false,
  "status": "outstanding",
  "type": "sms",
  "created_at": "...",
  "order_timestamp": "...",
  "request_received_at": "...",
  "updated_at": "...",
  "failure_reason": null,
  "last_attempted_at": null,
  "received_at": null,
  "sent_at": null,
  "send_time": null,
  "attachments": ["https://cdn.example/file1.jpg"]
}
```
Catatan:
- `sim` = `"SIM1"` / `"SIM2"` — instruksi SIM fisik mana yang dipakai mengirim.
- `attachments` = **array URL** (untuk MMS). App mengunduh tiap URL; batas maks 1.5 MB per file.
- Response tanpa `data` / bukan 2xx → worker gagal.
- `encrypted: true` → app mendekripsi konten sebelum kirim (butuh encryption key yang diset user).

---

## 4. Teruskan SMS/MMS masuk (device → server)

**`POST /v1/messages/receive`** — dipanggil receiver SMS/MMS masuk.

Request:
```json
{
  "sim": "SIM1",
  "from": "+628123456",
  "to": "+60123456789",
  "content": "isi pesan",
  "encrypted": false,
  "timestamp": "2026-09-09T03:00:00.000000Z",
  "attachments": [
    { "name": "photo.jpg", "content_type": "image/jpeg", "content": "<base64>" }
  ]
}
```
Catatan:
- `encrypted: true` → `content` terenkripsi AES oleh app (saat user menyalakan "encrypt received messages"). `attachments` selalu base64 & tidak dienkripsi.
- `attachments` opsional (null kalau SMS biasa).
- Response **4xx = berhenti** (anggap permanen), selain itu app **retry otomatis** (WorkManager) sampai sukses.

---

## 5. Laporan status pengiriman (device → server)

**`POST /v1/messages/{messageId}/events`**

Event sukses:
```json
{ "event_name": "SENT",     "timestamp": "..." }
{ "event_name": "DELIVERED", "timestamp": "..." }
```
Event gagal:
```json
{ "event_name": "FAILED", "reason": "NO_SERVICE", "timestamp": "..." }
```
Nilai `reason` (dari Android result code / logika app):
`GENERIC_FAILURE`, `NO_SERVICE`, `NULL_PDU`, `RADIO_OFF`, `LIMIT_EXCEEDED`, `FDN_CHECK_FAILURE`, `SHORT_CODE_NOT_ALLOWED`, `SHORT_CODE_NEVER_ALLOWED`, `UNKNOWN:<code>`, `CANNOT BE DELIVERED`, atau teks bebas (mis. SIM nonaktif, kontak kosong, gagal dekripsi).

Jika perangkat menyertakan cause code dari modem, nilainya ditambahkan dalam tanda kurung, contoh: `GENERIC_FAILURE (errorCode=21)`. Android tidak punya kode khusus "pulsa habis" — kasus saldo/kredit habis biasanya muncul sebagai `GENERIC_FAILURE`, kadang dengan `errorCode` spesifik per operator. Backend sebaiknya menyimpan `reason` mentah ini untuk klasifikasi sendiri.

Catatan:
- **`404` dianggap sukses** oleh app ("pesan sudah dihapus server") — jangan bikin app retry terus.
- Multipart SMS: hanya bagian terakhir yang melapor (app sudah menangani itu).

---

## 6. Heartbeat (device → server)

**`POST /v1/heartbeats`** — dikirim periodik tiap ~15 menit (WorkManager), saat FCM ping `KEY_HEARTBEAT_ID`, dan tombol manual "Send Heartbeat" di Settings.

Request (payload lengkap saat ini):
```json
{
  "device_id": "3f9c…-uuid-acak",
  "app_version": "1.0.0",
  "timestamp": 1773124514,
  "sms_permission": true,
  "battery_optimization_disabled": true,
  "battery_level": 85,
  "is_charging": true,
  "active_subscription_id": 1,
  "sim_carrier": "Telkomsel",
  "network_type": "WIFI",
  "phone_numbers": ["+60123456789"]
}
```

Detail tiap field:

| Field | Tipe | Sumber / arti |
|---|---|---|
| `device_id` | string (UUID) | Dibuat sekali oleh app, disimpan SharedPreferences. Stabil selama data app tidak di-clear. |
| `app_version` | string | `BuildConfig.VERSION_NAME`. |
| `timestamp` | integer (epoch detik) | `System.currentTimeMillis() / 1000`. |
| `sms_permission` | boolean | `true` bila SEND/RECEIVE/READ SMS semua granted. |
| `battery_optimization_disabled` | boolean | `true` bila app bebas dari penghemat baterai. |
| `battery_level` | integer (0–100, bisa -1) | Level baterai `BATTERY_PROPERTY_CAPACITY`. |
| `is_charging` | boolean | Status charge. |
| `active_subscription_id` | integer | Default SMS subscription id; `-1` bila tidak ada SIM. |
| `sim_carrier` | string | Carrier dari subscription aktif (bisa kosong). |
| `network_type` | string | `"WIFI"` / `"CELLULAR"` / `"NONE"`. |
| `phone_numbers` | string[] | Nomor SIM yang aktif (tergantung status aktif per SIM). |

Catatan: `phone_numbers` masih dipertahankan untuk kompatibilitas routing lama.

---

## 8. FCM (server → device)

- App menerima **data message tanpa notification**. Hanya **dua key** yang dikenali:
  - `KEY_MESSAGE_ID` → memicu alur kirim:
    1. app enqueue worker → `GET /v1/messages/outstanding?message_id=<id>`
    2. kirim SMS/MMS lewat SIM sesuai field `sim`
    3. lapor `POST /v1/messages/{id}/events` (`SENT`/`DELIVERED`/`FAILED`)
  - `KEY_HEARTBEAT_ID` → app langsung `POST /v1/heartbeats`.
- **Tidak ada topik** — backend harus kirim **per-token** ke device yang mapping `phone_number + sim`-nya sudah diregistrasi lewat `PUT /v1/phones/fcm-token`.

---

## 9. Alur End-to-End (detail untuk backend)

### 9.1 Registrasi & otentikasi line (device → server)

User memasukkan **API key** (dikirim sebagai header `x-api-key` di semua request) dan memilih nomor dari SIM yang terdeteksi.

`PUT /v1/phones/fcm-token` dipanggil oleh device pada saat:
1. **Login** (SIM1 wajib; SIM2 bila terdeteksi) — sekaligus validasi API key: **401 = API key salah, proses berhenti**.
2. **Firebase `onNewToken`** (token FCM berubah) — registrasi ulang nomor tersimpan.
3. **Refresh otomatis ±24 jam** di `MainActivity` — memakai nomor tersimpan.
4. **User mengganti SIM/nomor di layar Home** (`SimCardSelector`) — langsung nge-PUT nomor + SIM baru yang dipilih, tanpa login ulang.

Backend menyimpan mapping `phone_number + sim → fcm_token`. Jika nanti user "ganti nomor", backend cukup menerima PUT baru untuk nomor tsb. Nomor lama tidak di-unregister oleh app — di sisi device SIM lama dinonaktifkan, sehingga jika ada pesan yang menyasar nomor lama, device menolaknya (lapor `FAILED`).

### 9.2 Alur kirim pesan (server → FCM → device → SMS → event)

1. API consumer minta kirim SMS ke `contact` dari salah satu nomor terdaftar (`owner`).
2. Backend simpan pesan sebagai **outstanding** (status awal) → kirim FCM **data-only**:
   ```json
   { "KEY_MESSAGE_ID": "<messageId>" }
   ```
3. Device menerima FCM → worker menjalankan:
   - `GET /v1/messages/outstanding?message_id=<messageId>` (tanpa body) → ambil `Message` lengkap (`contact`, `content`, `sim`, `attachments`, dst.).
   - Kirim SMS/MMS via SIM sesuai field `sim` (attachment diunduh dari URL, maks 1.5 MB).
   - Lapor hasil ke backend.
4. Device lapor **event**:
   - `POST /v1/messages/{messageId}/events` → `{ "event_name": "SENT" | "DELIVERED", "timestamp": "..." }`
   - Gagal: `{ "event_name": "FAILED", "reason": "...", "timestamp": "..." }`
   - **`404` dianggap sukses** oleh app (pesan sudah dihapus server) → jangan sampai app retry terus.
5. Backend update status pesan (dan bisa lanjut kirim webhook/response ke pemanggil API).

> Catatan serialisasi: protokol **tidak mewajibkan** backend menunggu event sebelum mengirim FCM berikutnya, tapi tiap SIM device hanya punya satu slot kirim. **Disarankan** backend men-serialize pengiriman per device/SIM (kirim FCM berikutnya setelah menerima event `SENT`/`DELIVERED`/`FAILED`) untuk menghindari antrean ganda di satu SIM.

### 9.3 Heartbeat (device → server) — alur terpisah dari event

Heartbeat **tidak** dikirim setelah setiap event kirim. `POST /v1/heartbeats` dipicu oleh:
1. **Timer internal ±15 menit** (WorkManager `HeartbeatWorker`).
2. **FCM ping** dari backend:
   ```json
   { "KEY_HEARTBEAT_ID": "<apa saja>" }
   ```
   → device membalas `POST /v1/heartbeats` dengan payload status lengkap (§6).
3. **Tombol manual "Send Heartbeat"** di Settings (ditekan user sendiri).

Backend bisa memakai heartbeat untuk update `last_seen`/`status device`, memantau SIM aktif, level baterai, izin, dan koneksi (§6).

### 9.4 Pesan masuk (device → server)

Saat ada SMS/MMS yang diterima **dan** fitur incoming SIM itu aktif, device mengirim `POST /v1/messages/receive` (payload §4). Tidak ada FCM yang terlibat di arah ini — murni REST dari device saat pesan tiba.

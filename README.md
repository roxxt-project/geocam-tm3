# GeoCam TM-3

Kamera Android yang mencetak waktu, tanggal, alamat, dan koordinat **TM-3** (BPN) langsung di foto.

- Tanpa login, tanpa iklan, **tanpa izin internet**.
- Zona TM-3 (46.2–54.1) dipilih otomatis dari GPS; akurasi konversi diuji terhadap PROJ (< 1 mm).
- Foto tersimpan di `DCIM/GeoCam`, lengkap dengan EXIF GPS.
- Kotlin + Jetpack Compose + CameraX. Android 8.0 ke atas.

## Contoh stamp

```
Sen, 26 Okt 2026  19:18:42 WIB
TM-3 Zona 48.2
X (E): 236.205,15 m
Y (N): 817.191,18 m
Lat/Long: -6.175392, 106.827153
Akurasi: ±3,2 m
Gambir, Jakarta Pusat
Catatan: BT-01
```

## Build APK

### Cara 1: GitHub Actions (tanpa install apa pun)

1. Upload folder ini ke repo GitHub baru.
2. Buka tab **Actions** → workflow **Build APK** berjalan otomatis.
3. Unduh `GeoCam-TM3-apk` dari hasil run (berisi APK + checksum SHA-256).
4. Untuk rilis publik: buat tag `v1.0.0` → APK otomatis terlampir di **Releases**.

### Cara 2: Komputer sendiri

Butuh JDK 17 dan Android SDK (cara termudah: install Android Studio).

```
./gradlew testReleaseUnitTest assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Kunci rilis (wajib sebelum dibagikan ke publik)

Tanpa kunci rilis, APK ditandatangani kunci debug: bisa diinstal, tapi update berikutnya akan ditolak HP.

```
keytool -genkeypair -v -keystore release.jks -alias geocam -keyalg RSA -keysize 4096 -validity 10000
```

- Lokal: buat `keystore.properties` di root proyek:
  ```
  storeFile=release.jks
  storePassword=...
  keyAlias=geocam
  keyPassword=...
  ```
- GitHub: tambahkan secrets `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Simpan `release.jks` dan passwordnya di tempat aman. Hilang = tidak bisa merilis update.

## Instal di HP

1. Kirim file APK ke HP.
2. Buka APK, izinkan "Instal aplikasi tidak dikenal" bila diminta.
3. Sejak 30 Sep 2026, HP Android di Indonesia hanya menerima APK dari developer terdaftar.
   Daftarkan akun di Android Developer Console (akun penuh USD 25, atau distribusi terbatas gratis untuk ≤ 20 HP),
   lalu daftarkan nama paket `id.geocam.tm3` (ganti dulu jika perlu, di `app/build.gradle.kts`).

## Struktur

| File | Isi |
| --- | --- |
| `geo/Tm3.kt` | Konversi WGS84 → TM-3 (deret Krüger orde 6) |
| `location/LocationTracker.kt` | GPS (Fused, fallback GNSS tanpa Play Services) |
| `stamp/StampText.kt` | Isi baris stamp |
| `stamp/StampRenderer.kt` | Menggambar panel stamp di foto |
| `stamp/PhotoSaver.kt` | Rotasi, EXIF, simpan ke galeri |
| `ui/CameraScreen.kt` | Layar kamera |

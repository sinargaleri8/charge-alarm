# 🔋 Charge Alarm 1.1.0

Aplikasi Android native untuk memantau charging dan memberi peringatan ketika baterai mencapai target.

## Fitur
- Target baterai 50–100%.
- Alarm suara looping + getar.
- 🗣️ Text-to-Speech dapat diatur dan diulang 5–30 detik.
- Placeholder TTS: `{percent}` dan `{target}`.
- 🌡️ Temperature warning dengan batas 40–55°C.
- 📊 Riwayat sesi charging: mulai, target, durasi, kecepatan, selesai.
- ⚡ Estimasi charging speed (%/jam) dan ETA menuju target.
- Auto-start saat charger dicolok.
- Auto-stop saat charger dicabut.
- Foreground service dan recovery setelah reboot.
- UI dashboard baru.

## Build
Workflow GitHub Actions membangun **Release APK** dengan:

`gradle assembleRelease`

Jalankan workflow melalui **GitHub → Actions → Build Charge Alarm Release → Run workflow**.

# Range Voice Messenger

Do phones ek dusre ke Bluetooth range me aayein to saved text-message TTS (Text-to-Speech) se bar-bar (har 1 second) bola jata hai, jab tak range me hain ya service band na karo.

## Setup Steps

1. Android Studio me `File > Open` karke ye poora `RangeVoiceMessenger` folder open karo.
2. Gradle sync hone do.
3. Dono phones me pehle **Settings > Bluetooth** se ek dusre ko pair karo.
4. App ko dono phones me install/run karo.
5. Har phone me app open karo: paired device select karo, apna message likho, "Save Karo" dabao, "Service Start Karo" dabao.
6. Jab dono phones Bluetooth range me aayenge, peer phone message repeat bolega jab tak range me ho.

## GitHub Actions se automatic APK

`.github/workflows/build-apk.yml` already add hai:

1. GitHub par naya repo banao.
2. Push karo:
   ```
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<username>/<repo>.git
   git push -u origin main
   ```
3. Repo ke **Actions** tab me jao, "Build APK" workflow ko **Run workflow** se manually chalao (ya push par automatic).
4. Run complete hone par **Artifacts** section me se APK download karo.

## Notes

- Bluetooth Classic (RFCOMM) use hota hai, range ~10-30m.
- Dono phones me pehle se Bluetooth pairing zaroori hai.
- Android 12+ me runtime Bluetooth permissions allow karni padegi.
- Battery optimization me app ko "unrestricted" set karo, warna background service band ho sakti hai.

# BoxingFitness Android

This folder contains the native Android app project for BoxingFitness.

Current recognition method:

- Smart boxing gloves connect through Bluetooth Low Energy.
- Punches are detected from glove sensor packets.
- The app does not use microphone-based punch recognition and does not collect audio samples.

Main features:

- Dual-glove Bluetooth connection for devices named `BOXING#R######` and `BOXING#L######`.
- Emotion relief modes: Color Graffiti, Emotion Champ, Free Boxing.
- Fitness fat-burning modes: Rapid Fat Burn, Fat-Burn Challenge, Fat Burn Sparring.
- Sensitivity setting from 0 to 100, saved locally and written to the gloves when connected.
- Workout results, leaderboard, profile, achievements, privacy policy, and user agreement.

Notes for opening/building:

- Open the `boxingfitness-android` folder in Android Studio.
- Let Android Studio sync the project and download the required Android/Gradle components.
- Run the app on an Android phone or tablet and grant Bluetooth permissions.

Related project docs:

- User guide: `D:\2026\202604\0425\boxingfitness-docs\BoxingFitness_用户使用说明.md`

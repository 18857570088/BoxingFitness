# BoxingFitness Android

This folder contains a native Android app project for the boxing reflex-ball trainer.

Current short-term recognition scope:

- single punch
- slower double punch
- medium-speed triple punch (up to about 3 punches per second)

Not a short-term acceptance target:

- 4-hit chains
- high-speed bursts above 3 punches per second

Implemented features:

- 3, 2, 1 countdown before training starts
- 30-second and 60-second training modes
- Live large-number hit counter
- Automatic post-session report:
  - total hit count
  - average frequency (hits per second)
  - best 3-second burst
- Microphone-based hit detection adapted from the tuned desktop Python version
- Bundled light-hit template based on `hit_template_light.npz`

Notes for opening/building:

- Open the `boxingfitness-android` folder in Android Studio.
- Let Android Studio sync the project and download the required Android/Gradle components.
- After sync, run the app on an Android phone and grant microphone permission.

Related project docs:

- See the copied project documents under `D:\2026\202604\0425\boxingfitness-docs`.

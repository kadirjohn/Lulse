# first_prompt.md

## AI Coding Agent First Prompt

Aşağıdaki promptu Android Studio / Cursor / Claude Code / başka bir coding agent’a verebilirsin.

---

You are a senior Android engineer, signal-processing-aware product engineer, and premium mobile UI designer.

I want you to build the first version of an Android app called **HeartStill**.

## Product goal

This app is a **single-screen Android app** that tries to estimate a user's heart rate by using the phone’s motion sensors while the phone is placed on the user’s chest/heart area when lying down.

The app should feel extremely modern, minimal, premium, and calm.  
Visually, it should be inspired by the feeling of Apple’s Find My / AirTag style interfaces, but **do not copy it directly**. Create an original dark, elegant, minimal interface with animated gradients, subtle glow, and smooth state transitions.

The app is **not a medical device**.  
The first goal is to create a robust foundation with:
1. high-quality UI,
2. sensor acquisition,
3. motion detection,
4. state-driven screen transitions,
5. recording/debug infrastructure.

Do **not** jump straight into a full ML-based solution.  
Start with clean architecture and prepare the app for DSP-based heart detection later.

---

## Tech requirements

- Platform: Android
- Language: Kotlin
- UI: Jetpack Compose
- Architecture: MVVM
- State management: StateFlow / Flow
- Concurrency: Kotlin Coroutines
- Keep the code modular and production-style
- Create clear package separation for UI, sensors, motion analysis, and future signal processing

Suggested package structure:

```text
com.heartstill.app
├── data
│   ├── sensor
│   ├── recording
│   └── model
├── domain
│   ├── motion
│   ├── signal
│   ├── measurement
│   └── confidence
├── ui
│   ├── screen
│   ├── components
│   ├── animation
│   └── theme
└── debug
```

---

## Build the app in phases, but in this first implementation I want you to complete Phase 1 and Phase 2 foundations, with some Phase 3 UI polish.

### Phase 1 — Sensor foundation
Implement:
- SensorManager-based access to:
  - `TYPE_ACCELEROMETER`
  - `TYPE_LINEAR_ACCELERATION`
  - `TYPE_GYROSCOPE`
- Collect:
  - timestamp
  - x
  - y
  - z
  - sensor type
- Estimate effective sample rate for each sensor
- Expose sensor stream to ViewModel cleanly
- Create an in-memory ring buffer or bounded buffer for recent samples
- Add start/stop recording support
- Add CSV export capability for recorded sessions

### Phase 2 — Motion analysis
Implement a `MotionAnalyzer` that computes a simple motion score from recent sensor windows.

Use a practical heuristic such as:
- accelerometer variance
- gyroscope energy
- orientation change
- sudden spikes / jerk

From that motion score derive these UI states:
- `HIGH_MOTION`
- `SETTLING`
- `STILL`

Then derive measurement states:
- `IDLE`
- `WAITING_FOR_STILLNESS`
- `SEARCHING_PULSE`
- `NO_PULSE`
- `PULSE_DETECTED`
- `LOW_CONFIDENCE`

At this first stage, `PULSE_DETECTED` can be placeholder or mocked if necessary, but the app structure must be ready for real signal processing later.

---

## Single-screen UX requirements

The app must use **only one main screen**.

### UI State A — High motion
When the phone is moving too much:
- show a dark background with red / crimson animated gradient
- show centered main text:
  - “Yatar pozisyona geçin”
- show secondary text:
  - “Telefonu kalbinizin üzerine koyun”
- show tertiary helper text:
  - “Hareket azalınca ölçüm başlayacak”

This state should feel active, slightly unstable, and guiding.

### UI State B — Settling
When motion is decreasing:
- gradually reduce the red intensity
- transition the screen toward black
- animate the gradient to become slower and calmer
- main text:
  - “Sabit kalın”
- secondary text:
  - “Ölçüm için hazırlanıyor”

### UI State C — Still / Ready
When the device is stable:
- background should become nearly pure black
- show a subtle glow
- show a heart icon in the center
- animate the heart gently with a breathing/pulsing effect
- main text:
  - “Hazır”
- secondary text:
  - “Nabız aranıyor”

### UI State D — No pulse detected
If the device is stable for enough time but no reliable pulse is found:
- keep a dark minimal background
- show a dim heart icon
- main text:
  - “Nabız algılanmadı”
- secondary text:
  - “Telefonu biraz daha sola kaydırmayı deneyin”
  - or “Birkaç saniye daha sabit kalın”

### UI State E — Pulse detected
When pulse becomes available:
- show a large BPM value in the center, e.g. `72 BPM`
- show label:
  - “Tahmini nabız”
- show small signal quality text:
  - “Sinyal kalitesi: Yüksek”
- add a subtle heart pulse animation synced visually

### UI State F — Low confidence
If pulse is estimated but confidence is weak:
- show BPM
- show small label:
  - “Ölçüm kararsız”
- show secondary text:
  - “Biraz daha sabit kalın”

---

## UI design requirements

Make the UI visually impressive.

Use:
- deep black background
- animated mesh / radial / flowing gradient
- subtle blur and glow
- elegant typography
- minimal layout
- smooth animated transitions between states
- no clutter
- no ugly debug-heavy main interface

The app should feel:
- premium
- calm
- futuristic
- original
- sleep/night-friendly

### Important
Do not build a generic Android template UI.  
I want something polished and design-forward.

---

## Debug mode

Include a hidden or optional debug panel that can be toggled.

It may show:
- raw sensor availability
- sampling rate
- motion score
- current motion state
- current measurement state
- selected sensor values
- recording status

This debug panel should not pollute the main UI.

---

## Architecture requirements

Please implement:
- a `SensorRepository` or similar abstraction
- `MotionAnalyzer`
- `MainViewModel`
- clear UI state models
- reusable Compose components
- custom app theme
- animation helpers
- CSV export utility

Please keep sensor callbacks lightweight and avoid doing heavy processing directly in the callback.

---

## Deliverables I want from you

1. A clean Android project structure
2. Main screen implemented
3. State-driven animations implemented
4. Sensor acquisition implemented
5. Motion detection implemented
6. CSV recording/export implemented
7. A clear README explaining the structure
8. Notes in code about where future signal-processing heart detection will be added

---

## Important product notes

- This app is for chest placement in a lying/resting scenario
- Do not frame it as a medical diagnostic tool
- Design the code so future DSP can be added:
  - respiration band
  - cardiac band
  - envelope detection
  - beat detection
  - confidence scoring
- Leave placeholders/TODOs for these next phases

---

## Output format

Please:
1. first give me a short implementation plan,
2. then generate the code structure,
3. then generate the key files step by step,
4. explain any important architectural decisions,
5. do not oversimplify the UI,
6. prioritize code quality and maintainability.

If something is uncertain, choose the most reasonable production-style solution and continue.

---

## Optional extra request
If possible, also create:
- a `README.md`
- a simple `AppState` diagram in text
- TODO markers for future DSP and ML phases

End goal for this first pass:
A beautiful, single-screen Android app foundation with real sensor integration and motion-aware UI transitions, ready for future heart-rate detection logic.

---

## Kısa kullanım notu

Eğer istersen bu promptu tek seferde değil, parçalı da kullanabilirsin.

### Parçalı kullanım önerisi

#### Prompt 1
- proje iskeleti
- tema
- tek ekran
- state yapısı

#### Prompt 2
- sensör altyapısı
- motion analyzer
- debug panel

#### Prompt 3
- animasyonlar
- premium UI polish
- CSV export

#### Prompt 4
- sonraki aşamada gerçek signal processing

Bu parçalama, coding agent’ın daha kontrollü ve temiz ilerlemesini sağlar.
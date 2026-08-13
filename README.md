# Lulse

Lulse is an experimental Android project that estimates heart rate (BPM) from the tiny mechanical vibrations produced by the heartbeat using only a phone's accelerometer and gyroscope.

No camera. No flash. No phone side PPG sensor.

Place the phone flat on the center of your chest and keep still. Lulse analyzes the resulting seismocardiography like motion signal, searches for a stable periodic cardiac pattern, and only exposes a BPM reading after the signal has passed motion, confidence, harmonic, and temporal-lock checks.

An optional Wear OS / Galaxy Watch companion app can stream a reference heart rate for comparison and validation. The watch is never used to override the phone's own estimate.

---

## Why Lulse?

Most phone heart-rate apps rely on the camera and flash to perform a form of photoplethysmography (PPG). Lulse explores a different idea:

> Can the mechanical motion of the heart be recovered from the motion sensors already inside a phone?

A heartbeat produces small chest-wall vibrations. When the phone is resting on the chest, those vibrations appear in the accelerometer and gyroscope signals. The challenge is not simply detecting peaks — breathing, body motion, clothing, placement, and multiple mechanical events inside a single cardiac cycle can all create misleading periodic patterns.

Lulse is built around that signal-processing problem.

---

## Features

### Camera-free and PPG-free heart-rate estimation
- Uses the phone's built-in accelerometer and gyroscope.
- No camera permission and no flashlight required.

### SCG-style mechanical pulse sensing
- Primary cardiac signal from accelerometer data.
- Gyroscope data provides secondary evidence and cross-channel validation.

### Fresh, timestamp-bounded analysis windows
- Only recent data collected **after** the phone becomes still is sent to the cardiac estimator.
- Prevents old movement / placement noise from contaminating a new measurement.

### Motion and orientation gating
- Measurement pauses while the phone is moving excessively.
- Upright / invalid placement is rejected.
- The UI guides the user back into a measurable state.

### Autocorrelation-first signal processing
- Designed to detect the **repetition period** of the complete mechanical heartbeat pattern rather than blindly counting individual peaks.
- Helps reduce classic SCG harmonic errors such as interpreting two mechanical events inside one heartbeat as two separate beats.

### Harmonic disambiguation
- Evaluates fundamental, half-rate, and double-rate hypotheses.
- Includes explicit protection against common `0.5×` and `2×` BPM errors.

### Pulse lock state machine
- `SEARCHING → ACQUIRING → LOCKED`
- Avoids immediately showing every unstable candidate.
- Uses temporal consistency and hysteresis to keep the displayed BPM stable.

### Beat-event deduplication
- Candidate mechanical beat timestamps pass through a refractory gate before triggering UI pulse feedback.
- Prevents the same cardiac cycle from producing repeated flashes when multiple SCG peaks are detected.

### Single-screen Jetpack Compose UI
- Dark, minimal measurement interface.
- State-driven gradients and pulse feedback.
- Keeps the main interaction intentionally simple: place the phone, stay still, wait for lock.

### Hidden debug tools
- Long-press the main screen to open the debug panel.
- Live sensor rates, motion telemetry, signal confidence, lock state, raw candidates, buffer state, and watch status.

### CSV session recording
- Raw sensor samples and analysis frames can be exported for offline evaluation.
- Useful for comparing algorithm revisions, studying harmonic errors, and validating measurements against an external reference.

### Optional Galaxy Watch reference
- Samsung Health Sensor SDK on the Wear OS companion.
- Phone ↔ watch communication through the Wear OS Data Layer.
- The phone algorithm remains completely standalone when no watch is connected.

---

## How It Works

The simplified phone-side pipeline is:

```
Accelerometer + Gyroscope
          │
          ▼
 Motion / orientation gating
          │
          ▼
 Fresh STILL-only time window
          │
          ▼
 Per-sensor sample-rate estimation
          │
          ▼
 Band-pass filtering
          │
          ▼
 Rectification + envelope
          │
          ▼
 Autocorrelation (ACF)
          │
          ▼
 Candidate periods / harmonic hypotheses
          │
          ▼
 ACC + GYRO evidence
          │
          ▼
 SEARCHING → ACQUIRING → LOCKED
          │
          ▼
 Stable displayed BPM
          │
          ▼
 BeatEventGate → UI pulse / flash
```

### Signal Processing

The current signal processor is autocorrelation-first.

The phone pipeline uses approximately:

```
ACCELEROMETER.z
    ↓
5–30 Hz cardiac band-pass
    ↓
absolute value / envelope
    ↓
short smoothing
    ↓
ACF search across a physiological BPM range
    ↓
harmonic disambiguation
```

Gyroscope data is processed separately using its own measured sample rate and is used as secondary evidence.

This matters because Android sensors do not necessarily run at identical effective rates. For example, accelerometer and gyroscope streams may arrive much faster than linear acceleration. Lulse therefore avoids treating every channel as if it had one shared global sampling frequency.

### Why Autocorrelation?

A mechanical heartbeat is not a single clean spike.

One cardiac cycle can contain several strong mechanical events. A naive peak counter can therefore interpret:

```
real HR:      80 BPM
detected:    160 BPM
```

Autocorrelation instead asks:

> How often does the overall mechanical pattern repeat?

The estimator then evaluates competing harmonic hypotheses before the temporal lock layer decides what should actually be shown to the user.

### Pulse Lock

Lulse separates finding a candidate from trusting a candidate.

```
SEARCHING
   │
   ▼
ACQUIRING
   │   candidate evidence must remain consistent
   ▼
LOCKED
```

While locked, nearby candidates can update the estimate gradually, while likely `2×` and `0.5×` harmonics are rejected rather than immediately replacing the current BPM.

If the signal drifts far enough for several consecutive analysis frames, the lock is released and acquisition starts again.

---

## Galaxy Watch Companion

Lulse includes an optional Wear OS companion intended for reference measurements and algorithm validation.

The watch side can obtain heart-rate data through the Samsung Health Sensor SDK and send reference samples to the phone through the Wear OS Data Layer.

```
Galaxy Watch
    │
Samsung Health Sensor SDK
    │
HR / IBI reference
    │
Wear OS Data Layer
    │
Pixel / Android phone
    │
CSV + debug comparison
```

The separation is intentional:

```
Phone estimate ───────────────► displayed BPM
       │
       └────────► offline comparison

Watch reference ──────────────► validation only
```

The phone does not read the watch BPM and then adjust its own answer to match it.

---

## Project Structure

Lulse is a standard multi-module Android Gradle project.

```
Lulse/
├── app/       # Phone application
├── wear/      # Wear OS / Galaxy Watch companion
├── shared/    # Shared phone ↔ watch protocol and models
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

### `app`

Contains the phone-side application:

- Android sensor collection
- motion / orientation analysis
- sensor ring buffer
- SCG signal processing
- harmonic hypothesis generation
- pulse lock tracking
- beat-event validation
- Jetpack Compose UI
- CSV recording / export
- Wear OS connection handling

### `wear`

Contains the Wear OS companion:

- Samsung Health Sensor SDK integration
- continuous heart-rate reference collection
- IBI / status handling
- phone communication
- watch-side measurement UI

### `shared`

Contains data structures and protocol definitions shared by the phone and watch modules.

---

## Tech Stack

| Area | Technology |
|------|-----------|
| Language | Kotlin |
| Phone UI | Jetpack Compose |
| Watch UI | Compose for Wear OS |
| Architecture | ViewModel + state-driven UI |
| Async / state | Kotlin Coroutines, Flow, StateFlow |
| Signal processing | Custom Kotlin DSP / autocorrelation |
| Serialization | kotlinx.serialization / JSON |
| Phone ↔ Watch | Google Play Services Wearable / Wear OS Data Layer |
| Watch sensors | Samsung Health Sensor SDK |
| Build | Gradle Kotlin DSL |
| Android SDK | compileSdk / targetSdk 36 |
| Minimum SDK | app: 31 · wear: 30 |

---

## Getting Started

### Requirements

- Android Studio
- Android SDK 36
- A physical Android phone with accelerometer and gyroscope sensors
- Optional: a compatible Wear OS Galaxy Watch for reference measurements

Sensor-based heart-rate estimation should be tested on a real device. Emulator motion data is not representative of a phone resting on the chest.

### Clone

```bash
git clone https://github.com/kadirjohn/Lulse.git
cd Lulse
```

### Build the phone app

```bash
./gradlew :app:assembleDebug
```

### Build the Wear OS companion

```bash
./gradlew :wear:assembleDebug
```

### Run tests

```bash
./gradlew test
```

In Android Studio, use the `app` run configuration for the phone and the `wear` configuration for the watch.

---

## Basic Usage

1. Launch Lulse on the phone.
2. Lie down or remain comfortably still.
3. Place the phone flat on the center of the chest.
4. Wait while Lulse moves through the searching / acquisition phase.
5. Once a stable periodic pattern is found, the UI enters the locked state and displays the estimated BPM.
6. If the phone moves significantly, the measurement is gated until the signal becomes usable again.

For experimentation, long-press the main screen to open the hidden debug panel.

---

## Debugging & Research Data

Lulse can record sessions to CSV for offline analysis.

A session may include:

- raw accelerometer samples
- raw gyroscope samples
- linear-acceleration samples
- per-sensor sample-rate estimates
- motion scores
- estimator BPM and confidence
- ACF candidate / harmonic evidence
- pulse-lock state and locked BPM
- watch reference BPM
- buffer diagnostics
- analysis-frame timestamps

These recordings are useful for examining cases such as:

- normal breathing vs. breath holding
- clothing vs. direct phone-to-chest contact
- incorrect `2×` / `0.5×` harmonic locks
- movement contamination
- cold-start acquisition
- recovery after signal drift
- phone estimate vs. wearable reference

---

## Current Research Focus

Lulse is still under active experimentation. Current areas of interest include:

- faster and safer cold-start acquisition
- better accelerometer / gyroscope evidence fusion
- robust rejection of breathing and external mechanical artifacts
- confidence calibration for the locked BPM rather than only the underlying raw estimate
- improved beat timing and UI pulse synchronization
- richer beat-event logging for offline analysis
- validation across different phones, body positions, clothing, and heart-rate ranges

The goal is not merely to produce a number quickly, but to avoid displaying a confident BPM when the underlying mechanical evidence is ambiguous.

---

## Important Limitations

Mechanical heart-rate sensing from a phone is sensitive to conditions such as:

- body movement
- breathing
- phone placement and orientation
- clothing and phone cases
- mattress / surface vibration
- sensor hardware and sampling behavior
- individual differences in chest-wall mechanics
- heart-rate range and rhythm

A stable reading from Lulse should therefore be treated as an experimental sensor estimate. For research validation, compare against an independent reference sensor rather than assuming the phone estimate is ground truth.

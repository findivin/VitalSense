# CHS — Pulse & Blood Oxygen

An Android app (Jetpack Compose, Romanian UI) that measures **heart rate** and **blood oxygen saturation (SpO₂)** using the built-in heart-rate sensor of the **Samsung Galaxy S10**, and keeps a per-profile history with charts.

> Measurements are indicative only and are **not** a medical diagnosis. The SpO₂ value is an uncalibrated estimate.

## Features

- **Home screen** with two menus, **Puls** (pulse) and **Oxigen (SpO₂)**, each showing the latest reading of the selected profile.
- **Profiles**: a personal profile, 5 demo profiles with a year of simulated data for both metrics, and the option to add new profiles (name, age, description). The selected profile is shared by both menus.
- **History** for each metric: *Zile / Săptămâni / Luni* (7 days / 8 weeks / 12 months) tabs, navigation back in time, period average and a chart (pulse 0–200+ bpm, SpO₂ 80–100 %). Weeks run Monday–Sunday.
- **Pulse measurement** (Samsung Health style): a single progress bar for the whole measurement (detection phase up to ~30 %, then 10 s of stable readings), animated heart that beats at the measured rate, live bpm trend for the last 30 s, result = median of the readings, resting-range hint (60–100 bpm), *Salvează* / *Repetă* buttons.
- **SpO₂ measurement**: reads raw **red and infrared** light from the S10's PPG sensor, shows the live pulse waveform and a progress bar (15 s of valid signal), result = median of the estimates, *Salvează* / *Repetă*. A *Senzori detectați* section lists the phone's sensors for troubleshooting.
- Both measurements: finger lifted → progress restarts; 45 s without a usable signal → timeout message; leaving the app stops the sensor and offers *Încearcă din nou*.

## How it works

### Pulse
Uses the standard Android `Sensor.TYPE_HEART_RATE` (Samsung's own algorithm). Values outside 30–220 bpm or marked `SENSOR_STATUS_UNRELIABLE` are ignored; `SENSOR_STATUS_NO_CONTACT` means no finger.

### SpO₂
Android has no public SpO₂ sensor. On the Galaxy S10 (SM-G973F) the MAX86915 chip exposes its raw LED channels to any app holding `BODY_SENSORS`:

| Sensor | String type | Used for |
|---|---|---|
| `MAX86915 IR` | `com.samsung.sensor.hrm_led_ir` | infrared light |
| `MAX86915 RED` | `com.samsung.sensor.hrm_led_red` | red light |

Both stream at ~100 Hz. Other LED sensors on the chip (e.g. `rear_led_red`) need Samsung-only permissions and are deliberately not used.

The app uses the classic **ratio-of-ratios** method (`Spo2Calculator.kt`):

1. Keep the last 8 s of IR and RED samples; red is interpolated at the IR timestamps.
2. Ignore zero readings and the first second after the LEDs start (the S10 reports zeros and a short plateau for ~220 ms).
3. DC = mean, AC = RMS after removing a 1 s moving average.
4. `R = (AC_red / DC_red) / (AC_ir / DC_ir)`, `SpO₂ ≈ 110 − 25·R`, clamped to 70–100 %.
5. Quality gates (no value if any fails): at least 4 s of data at ≥ 20 Hz, perfusion index 0.05–20 %, no jump > 5 % between consecutive samples, IR/RED correlation ≥ 0.6.

The 110/25 coefficients are the textbook approximation and are **not calibrated** for this phone; compare with Samsung Health or a finger oximeter. The small grey line under the waveform (`R = … • PI = …%`) helps with calibration.

## Project structure

```
app/src/main/java/com/example/chs/
├── MainActivity.kt            # navigation (Home / Pulse / Oxygen), home screen, history screen
├── PulseMeasurementScreen.kt  # pulse measurement (TYPE_HEART_RATE), progress logic
├── OxygenScreens.kt           # SpO₂ measurement screen, waveform chart
├── OxygenSensors.kt           # finds the raw IR/RED PPG sensors on the device
├── Spo2Calculator.kt          # PPG buffer, SpO₂ algorithm, measurement session state machine
├── PulseStore.kt              # SQLite storage, Metric enum, profiles
├── PulseStatistics.kt         # day/week/month buckets and averages
├── PulseCharts.kt             # history chart and live bpm chart
├── ProfilePicker.kt           # profile dropdown and "add profile" dialog
└── SampleProfiles.kt          # demo profiles and simulated history
```

### Data
SQLite database `pulse.db`, schema version 3:

- `profiles(id, name, age, description, is_sample)`
- `readings(id, time, bpm, profile_id)` — pulse
- `oxygen_readings(id, time, spo2, profile_id)` — SpO₂

Upgrades from versions 1 and 2 keep existing readings and seed the demo data.

## Requirements

- Android 12+ (`minSdk 31`, `targetSdk 37`), Android Studio with AGP 9.5, Kotlin 2.2.
- A phone with a heart-rate sensor. SpO₂ needs raw IR/RED PPG access (tested on Galaxy S10 SM-G973F, Android 12). The Galaxy S10e has no heart-rate sensor.
- Permission: `BODY_SENSORS`. On Android 16+ heart rate moves to `android.permission.health.READ_HEART_RATE`, which this app does not request yet.

## Build & run

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

```bash
./gradlew testDebugUnitTest      # 51 JVM tests
./gradlew lintDebug
```

Unit tests cover the SpO₂ algorithm (synthetic signals, noise, drift, jumps, rate limits), the measurement session, sensor matching (including the S10's real sensor list), statistics, chart scales, demo data, and a **real S10 recording** with no finger on the sensor (`app/src/test/resources/ppg/s10_no_finger.csv`), which must never produce a value.

Instrumented tests (`app/src/androidTest`): database migrations, navigation UI, oxygen screen, and `PpgSensorProbeTest`, which reads the real sensors. **Do not use `connectedAndroidTest` on a phone with real data** — it uninstalls the app afterwards. Install both APKs and run with `am instrument` instead:

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.example.chs.test/androidx.test.runner.AndroidJUnitRunner

# record raw PPG for 30 s to a CSV on the phone
adb shell am instrument -w -e class com.example.chs.PpgSensorProbeTest \
  -e probeSeconds 30 -e probeFile finger.csv com.example.chs.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s PpgProbe:I
```

## Further development

### External sensor module: ESP32-C3 Super Mini over Bluetooth

Planned request:

> am nevoie ca la fiecare din cele 2 optiuni puls, oxigen sa imi faci un nou mod langa + prin care pot sa masor pulsul, oxigenarea prin intermediul senzorilor mpu6500, max30102, ds18b20 de la un esp32-c3 super mini. semnalul va fi transmis prin bluetooth. intreaba inainte de a implementa

*(In each of the two menus, pulse and oxygen, add a new mode next to the **+** button for measuring pulse and oxygen with an ESP32-C3 Super Mini carrying MPU6500, MAX30102 and DS18B20 sensors, transmitted over Bluetooth. Ask before implementing.)*

Already established:

- The ESP32-C3 supports only **Bluetooth Low Energy (BLE)**, so the app will use BLE.
- On Android 12+ the app needs the `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` permissions.
- MAX30102 (I²C 0x57) and MPU6500 (I²C 0x68) can share the same I²C bus; DS18B20 uses a 1-Wire pin with a 4.7 kΩ pull-up.

Sensor roles:

| Sensor | Role |
|---|---|
| MAX30102 | red + infrared PPG → pulse and SpO₂ (same principle as the S10's MAX86915) |
| MPU6500 | motion detection, to warn "nu vă mișcați" and reject readings taken while moving |
| DS18B20 | temperature |

Open questions to settle before implementing:

1. **Firmware**: write a new Arduino/PlatformIO firmware for the ESP32-C3 (and get the wiring pins), or adapt to existing firmware (device name, BLE UUIDs, data format)?
2. **Where to compute**: stream raw red/IR (~100 Hz) to the phone and reuse `Spo2Calculator` plus a new beat detector for pulse (one algorithm, live waveform), or compute pulse/SpO₂ on the ESP32 and send only the values?
3. **Temperature**: show it during measurement only, add a third *Temperatură* menu with history, store it alongside each reading, or ignore it?
4. **Connection**: remember the board and reconnect automatically, or scan and pick each time?

### Other ideas

- Calibrate the SpO₂ coefficients against a reference finger oximeter.
- Request `android.permission.health.READ_HEART_RATE` for Android 16+.
- Move database access off the main thread.

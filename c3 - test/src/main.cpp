#include <Arduino.h>
#include <Wire.h>
#include "MAX30105.h"
#include "heartRate.h"

constexpr uint8_t PIN_SDA = 6;
constexpr uint8_t PIN_SCL = 8;
constexpr uint32_t PRAG_CONTACT = 50000;   // valoare din exemplele pentru deget

MAX30105 senzor;
byte curentLed = 0x1F;

// --- puls ---
constexpr byte DIM_MEDIE = 4;
float bpmRecente[DIM_MEDIE];
byte nrBpm = 0;
byte pozBpm = 0;
unsigned long ultimaBataie = 0;
float bpmCurent = 0;
float bpmMediu = 0;

// --- statistici pe fereastra de 1 secunda ---
uint32_t irMin = UINT32_MAX;
uint32_t irMax = 0;
double irSuma = 0;
uint32_t nrEsantioane = 0;
unsigned long ultimaAfisare = 0;

void reseteazaPuls() {
  nrBpm = 0;
  pozBpm = 0;
  ultimaBataie = 0;
  bpmCurent = 0;
  bpmMediu = 0;
}

void reseteazaFereastra() {
  irMin = UINT32_MAX;
  irMax = 0;
  irSuma = 0;
  nrEsantioane = 0;
}

void configureazaSenzor() {
  // curent LED, medie 4 esantioane, mod 2 (rosu + IR), 400 Hz, puls 411 us, 4096 nA
  senzor.setup(curentLed, 4, 2, 400, 411, 4096);
  reseteazaPuls();
  reseteazaFereastra();
}

void setup() {
  Serial.begin(115200);
  delay(2000);

  Wire.begin(PIN_SDA, PIN_SCL);
  if (!senzor.begin(Wire, I2C_SPEED_FAST)) {
    Serial.println("EROARE: MAX30102 negasit. Verifica cablarea.");
    while (true) delay(1000);
  }

  configureazaSenzor();
  Serial.println("Taste: '+' mareste curentul LED, '-' il micsoreaza.");
  Serial.println("Pune senzorul pe piele cu presiune usoara si constanta.");
}

void gestioneazaTastatura() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '+') {
      int n = curentLed + 16;
      curentLed = n > 255 ? 255 : n;
    } else if (c == '-') {
      curentLed = curentLed > 32 ? curentLed - 16 : 16;
    } else {
      continue;
    }
    configureazaSenzor();
    Serial.printf(">>> curent LED = 0x%02X\n", curentLed);
  }
}

void loop() {
  gestioneazaTastatura();

  uint32_t ir = senzor.getIR();
  bool contact = ir > PRAG_CONTACT;

  if (ir < irMin) irMin = ir;
  if (ir > irMax) irMax = ir;
  irSuma += ir;
  nrEsantioane++;

  if (contact) {
    if (checkForBeat(ir)) {
      unsigned long acum = millis();
      unsigned long delta = acum - ultimaBataie;
      ultimaBataie = acum;
      float bpm = 60000.0f / (float)delta;

      if (bpm > 40 && bpm < 200) {
        bpmCurent = bpm;
        bpmRecente[pozBpm] = bpm;
        pozBpm = (pozBpm + 1) % DIM_MEDIE;
        if (nrBpm < DIM_MEDIE) nrBpm++;

        float suma = 0;
        for (byte i = 0; i < nrBpm; i++) suma += bpmRecente[i];
        bpmMediu = suma / nrBpm;
      }
    }
  } else {
    reseteazaPuls();
  }

  if (millis() - ultimaAfisare >= 1000 && nrEsantioane > 0) {
    ultimaAfisare = millis();
    double medie = irSuma / nrEsantioane;
    double variatie = (double)irMax - (double)irMin;
    double acPct = variatie * 100.0 / medie;

    Serial.printf("LED=0x%02X | IR medie=%.0f | variatie=%.0f (%.2f%%) | contact=%s | BPM=%.0f | medie=%.0f%s\n",
                  curentLed, medie, variatie, acPct, contact ? "DA" : "NU",
                  bpmCurent, bpmMediu,
                  irMax > 250000 ? " | POSIBIL SATURAT" : "");
    reseteazaFereastra();
  }
}
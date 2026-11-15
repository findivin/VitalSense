#include <Arduino.h>
#include <Wire.h>
#include <math.h>

constexpr uint8_t PIN_SDA = 4;
constexpr uint8_t PIN_SCL = 5;
constexpr uint8_t ADDR_MPU = 0x68;

constexpr uint8_t REG_PWR_MGMT_1 = 0x6B;
constexpr uint8_t REG_WHO_AM_I = 0x75;
constexpr uint8_t REG_ACCEL_XOUT_H = 0x3B;

constexpr float SENS_ACCEL = 16384.0f;  // LSB/g, domeniu implicit +-2 g
constexpr float SENS_GYRO = 131.0f;     // LSB/(grad/s), domeniu implicit +-250

constexpr uint32_t PERIOADA_MS = 20;    // 50 de citiri pe secunda
constexpr uint32_t AFISARE_MS = 500;
constexpr int NR_CALIBRARE = 100;

struct Esantion {
  float ax, ay, az;
  float gx, gy, gz;
};

float gx0 = 0, gy0 = 0, gz0 = 0;         // offset giroscop, masurat la pornire
uint32_t citiri = 0, esecuri = 0;

double sumaMiscare = 0, sumaGiro = 0;
uint32_t nrFereastra = 0;
unsigned long ultimaCitire = 0, ultimaAfisare = 0;

bool scrieReg(uint8_t reg, uint8_t val) {
  Wire.beginTransmission(ADDR_MPU);
  Wire.write(reg);
  Wire.write(val);
  return Wire.endTransmission() == 0;
}

bool citesteReg(uint8_t reg, uint8_t &val) {
  Wire.beginTransmission(ADDR_MPU);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(ADDR_MPU, (uint8_t)1) != 1) return false;
  val = Wire.read();
  return true;
}

bool citesteEsantion(Esantion &e) {
  Wire.beginTransmission(ADDR_MPU);
  Wire.write(REG_ACCEL_XOUT_H);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(ADDR_MPU, (uint8_t)14) != 14) return false;

  uint8_t b[14];
  for (int i = 0; i < 14; i++) b[i] = Wire.read();

  int16_t ax = (b[0] << 8) | b[1];
  int16_t ay = (b[2] << 8) | b[3];
  int16_t az = (b[4] << 8) | b[5];
  // b[6], b[7] = temperatura interna a chipului (nu o folosim)
  int16_t gx = (b[8] << 8) | b[9];
  int16_t gy = (b[10] << 8) | b[11];
  int16_t gz = (b[12] << 8) | b[13];

  e.ax = ax / SENS_ACCEL;
  e.ay = ay / SENS_ACCEL;
  e.az = az / SENS_ACCEL;
  e.gx = gx / SENS_GYRO;
  e.gy = gy / SENS_GYRO;
  e.gz = gz / SENS_GYRO;
  return true;
}

void calibreazaGiroscop() {
  Serial.println("Calibrare giroscop: tine modulul NEMISCAT 2 secunde...");
  double sx = 0, sy = 0, sz = 0;
  int valide = 0;
  for (int i = 0; i < NR_CALIBRARE; i++) {
    Esantion e;
    if (citesteEsantion(e)) {
      sx += e.gx; sy += e.gy; sz += e.gz;
      valide++;
    }
    delay(PERIOADA_MS);
  }
  if (valide < NR_CALIBRARE / 2) {
    Serial.println("EROARE: prea putine citiri valide la calibrare.");
    return;
  }
  gx0 = sx / valide; gy0 = sy / valide; gz0 = sz / valide;
  Serial.printf("Offset giroscop [dps]: %.2f %.2f %.2f\n", gx0, gy0, gz0);
  if (fabsf(gx0) > 20 || fabsf(gy0) > 20 || fabsf(gz0) > 20) {
    Serial.println("ATENTIE: offset mare, probabil s-a miscat modulul. Reseteaza placa.");
  }
}

void setup() {
  Serial.begin(115200);
  delay(2000);
  Wire.begin(PIN_SDA, PIN_SCL);
  Wire.setClock(100000);

  uint8_t who = 0;
  if (!citesteReg(REG_WHO_AM_I, who)) {
    Serial.println("EROARE: MPU nu raspunde la 0x68");
    while (true) delay(1000);
  }
  Serial.printf("WHO_AM_I = 0x%02X (0x70 = MPU6500)\n", who);

  scrieReg(REG_PWR_MGMT_1, 0x00);   // scoate chipul din sleep
  delay(100);

  calibreazaGiroscop();
  ultimaCitire = millis();
  ultimaAfisare = millis();
}

void loop() {
  if (millis() - ultimaCitire < PERIOADA_MS) return;
  ultimaCitire = millis();

  Esantion e;
  citiri++;
  if (!citesteEsantion(e)) {
    esecuri++;
    return;
  }

  e.gx -= gx0; e.gy -= gy0; e.gz -= gz0;

  float mag = sqrtf(e.ax * e.ax + e.ay * e.ay + e.az * e.az);
  float miscareMg = fabsf(mag - 1.0f) * 1000.0f;   // abatere de la 1 g, in mg
  float giro = sqrtf(e.gx * e.gx + e.gy * e.gy + e.gz * e.gz);

  sumaMiscare += miscareMg;
  sumaGiro += giro;
  nrFereastra++;

  if (millis() - ultimaAfisare >= AFISARE_MS && nrFereastra > 0) {
    ultimaAfisare = millis();
    Serial.printf("A[g] %6.3f %6.3f %6.3f |A|=%5.3f | G[dps] %7.2f %7.2f %7.2f | "
                  "miscare=%6.1f mg | giro=%6.1f dps | esecuri=%lu/%lu\n",
                  e.ax, e.ay, e.az, mag, e.gx, e.gy, e.gz,
                  sumaMiscare / nrFereastra, sumaGiro / nrFereastra,
                  (unsigned long)esecuri, (unsigned long)citiri);
    sumaMiscare = 0; sumaGiro = 0; nrFereastra = 0;
  }
}
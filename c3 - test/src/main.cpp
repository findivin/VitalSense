#include <Arduino.h>
#include <OneWire.h>
#include <DallasTemperature.h>

constexpr uint8_t PIN_DS18B20 = 10;

OneWire oneWire(PIN_DS18B20);
DallasTemperature sensors(&oneWire);

void setup() {
  Serial.begin(115200);
  delay(2000);

  sensors.begin();
  Serial.print("Senzori DS18B20 gasiti: ");
  Serial.println(sensors.getDeviceCount());
  sensors.setResolution(12);
}

void loop() {
  sensors.requestTemperatures();
  float t = sensors.getTempCByIndex(0);

  if (t == DEVICE_DISCONNECTED_C) {
    Serial.println("EROARE: senzor negasit (-127). Verifica firele.");
  } else if (t == 85.0f) {
    Serial.println("ATENTIE: 85.00 C este valoarea de pornire, citire invalida.");
  } else {
    Serial.print("Temperatura: ");
    Serial.print(t, 2);
    Serial.println(" C");
  }
  delay(1000);
}
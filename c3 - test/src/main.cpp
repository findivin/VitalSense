#include <Arduino.h>

void setup() {
  Serial.begin(115200);
  delay(2000);
  Serial.println("VitalSense: test compilare OK");
}

void loop() {
  Serial.println("Placa functioneaza");
  delay(1000);
}
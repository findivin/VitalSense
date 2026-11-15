// ==================================================
// VITALSENSE SMARTWATCH v1.0
// Toate dimensiunile sunt in mm
// ==================================================

// --- COMPONENTE ---

esp     = [25, 18, 3];
mpu     = [26, 15, 3];
max_    = [19, 14, 3];

ds      = [21.5, 10, 9];

baterie = [30, 20, 4];
switch_ = [8.5, 3.7, 3.9];

// --- PARAMETRI MAX30102 ---

max_optic_x = 6;
max_optic_y = 4;

// Pozitia centrului elementului optic
max_center_x = 6;
max_center_y = 7;

// Fereastra optica
max_window_x = 7;
max_window_y = 5;

// --- PARAMETRI CARCASA ---

perete  = 2;
podea   = 2;
capac_g = 2;

toler   = 1;

// --- PARAMETRI SWITCH ---

switch_decupaj_lat = 3;
switch_decupaj_inalt = 1.5;

// --- PARAMETRI USB-C ---

usb_lat = 10;
usb_inl = 4;

// --- AFISARE ---

arata_capac = false;
arata_componente = true;
// ============================================================
// VITALSENSE SMARTWATCH v1.0
// Prototip carcasa pentru verificarea incadrarii componentelor
// Toate dimensiunile sunt in mm
//
// Z = 0 reprezinta suprafata exterioara orientata spre piele
// ============================================================

$fn = 48;

// ============================================================
// 1. DIMENSIUNI COMPONENTE
// [lungime, latime, inaltime]
// ============================================================

esp     = [25, 18, 3];
mpu     = [26, 15, 3];
max_    = [19, 14, 3];
ds      = [21.5, 10, 9];

baterie = [30, 20, 4];
switch_ = [8.5, 3.7, 3.9];


// ============================================================
// 2. PARAMETRI CARCASA
// ============================================================

perete = 2;
podea = 2;
capac_g = 2;

toler = 1;

raza_colt = 4;


// Dimensiuni exterioare carcasa
ext_x = 70;
ext_y = 45;
ext_z = 22;


// Dimensiuni interioare
int_x = ext_x - 2*perete;
int_y = ext_y - 2*perete;


// ============================================================
// 3. PARAMETRI MAX30102
// ============================================================

// Pozitia modulului in carcasa
max_x = 5;
max_y = 5;

// Pozitia centrului senzorului optic fata de placa
max_center_x = 6;
max_center_y = 7;

// Dimensiunea elementului optic
max_optic_x = 6;
max_optic_y = 4;

// Dimensiunea ferestrei din carcasa
max_window_x = 7;
max_window_y = 5;


// Pozitia calculata a ferestrei
max_hole_x = max_x + max_center_x - max_window_x/2;
max_hole_y = max_y + max_center_y - max_window_y/2;


// ============================================================
// 4. PARAMETRI DS18B20
// ============================================================

// Pozitia placii DS18B20
ds_x = 30;
ds_y = 5;

// Orificiu pentru capsula neagra
ds_hole_d = 6;

// Proeminenta capsulei spre piele
ds_protrusion = 1.5;


// Pozitia aproximativa a capsulei pe modul
ds_sensor_x = 13;
ds_sensor_y = 5;


// ============================================================
// 5. PARAMETRI ESP32
// ============================================================

esp_x = 5;
esp_y = 23;

usb_lat = 10;
usb_inl = 4;


// ============================================================
// 6. PARAMETRI MPU6500
// ============================================================

mpu_x = 34;
mpu_y = 23;


// ============================================================
// 7. PARAMETRI BATERIE
// ============================================================

bat_x = 19;
bat_y = 8;


// ============================================================
// 8. PARAMETRI SWITCH
// ============================================================

switch_decupaj_lat = 3.2;
switch_decupaj_inalt = 1.8;


// ============================================================
// 9. PARAMETRI CAPAC SI AFISARE
// ============================================================

arata_capac = true;
arata_componente = true;
arata_detalii = true;


// ============================================================
// 10. FUNCTIE CARCASA CU COLTURI ROTUNJITE
// ============================================================

module rounded_box(x, y, z, r) {

    hull() {

        translate([r, r, 0])
            cylinder(h=z, r=r);

        translate([x-r, r, 0])
            cylinder(h=z, r=r);

        translate([r, y-r, 0])
            cylinder(h=z, r=r);

        translate([x-r, y-r, 0])
            cylinder(h=z, r=r);
    }
}


// ============================================================
// 11. CORPUL PRINCIPAL AL CARCASEI
// ============================================================

module carcasa() {

    difference() {

        // Corp exterior
        rounded_box(
            ext_x,
            ext_y,
            ext_z,
            raza_colt
        );


        // Cavitate interioara
        translate([
            perete,
            perete,
            podea
        ])

        rounded_box(
            int_x,
            int_y,
            ext_z,
            raza_colt/2
        );


        // ----------------------------------------------------
        // FEREASTRA OPTICA MAX30102
        // ----------------------------------------------------

        translate([
            max_hole_x,
            max_hole_y,
            -0.1
        ])

        cube([
            max_window_x,
            max_window_y,
            podea + 0.2
        ]);


        // ----------------------------------------------------
        // ORIFICIU DS18B20
        // ----------------------------------------------------

        translate([
            ds_x + ds_sensor_x,
            ds_y + ds_sensor_y,
            -0.1
        ])

        cylinder(
            h=podea + 0.2,
            d=ds_hole_d
        );


        // ----------------------------------------------------
        // DECUPAJ USB-C
        // ESP32 orientat cu USB spre peretele stang
        // ----------------------------------------------------

        translate([
            -0.1,
            esp_y + esp[1]/2 - usb_lat/2,
            5
        ])

        cube([
            perete + 0.2,
            usb_lat,
            usb_inl
        ]);


        // ----------------------------------------------------
        // DECUPAJ SWITCH PE PERETELE DREPT
        // ----------------------------------------------------

        translate([
            ext_x - perete - 0.1,
            ext_y/2 - switch_decupaj_lat/2,
            5
        ])

        cube([
            perete + 0.2,
            switch_decupaj_lat,
            switch_decupaj_inalt
        ]);


        // ----------------------------------------------------
        // GAURI PENTRU FIXAREA CAPACULUI
        // ----------------------------------------------------

        for (x = [5, ext_x-5]) {

            for (y = [5, ext_y-5]) {

                translate([x, y, ext_z-3])

                    cylinder(
                        h=3.2,
                        d=2
                    );
            }
        }
    }
}


// ============================================================
// 12. SUPORTURI PENTRU CUREA
// ============================================================


// ============================================================
// 13. CAPACUL
// ============================================================

module capac() {

    difference() {

        rounded_box(
            ext_x,
            ext_y,
            capac_g,
            raza_colt
        );


        // Gauri de prindere
        for (x = [5, ext_x-5]) {

            for (y = [5, ext_y-5]) {

                translate([x,y,-0.1])

                    cylinder(
                        h=capac_g+0.2,
                        d=2.2
                    );
            }
        }
    }
}


// ============================================================
// 14. REPREZENTAREA COMPONENTELOR
// Acestea sunt modele aproximative pentru verificare.
// Nu reprezinta geometrii exacte ale modulelor.
// ============================================================

module componente() {

    // --------------------------------------------------------
    // MAX30102
    // --------------------------------------------------------

    color("green")

    translate([
        max_x,
        max_y,
        podea+0.5
    ])

    cube(max_);


    // Element optic MAX30102
    color("white")

    translate([
        max_hole_x,
        max_hole_y,
        podea+0.3
    ])

    cube([
        max_window_x,
        max_window_y,
        1
    ]);


    // --------------------------------------------------------
    // DS18B20 - placa albastra
    // --------------------------------------------------------

    color("blue")

    translate([
        ds_x,
        ds_y,
        podea+1
    ])

    cube([
        ds[0],
        ds[1],
        1.6
    ]);


    // Capsula neagra DS18B20
    // Orientata spre piele

    color("black")

    translate([
        ds_x+ds_sensor_x,
        ds_y+ds_sensor_y,
        podea+1
    ])

    rotate([180,0,0])

        cylinder(
            h=ds[2]-1.5,
            d1=5,
            d2=4.5
        );


    // --------------------------------------------------------
    // ESP32-C3 SUPER MINI
    // --------------------------------------------------------

    color("darkgreen")

    translate([
        esp_x,
        esp_y,
        11
    ])

    cube(esp);


    // Conector USB-C aproximativ
    color("silver")

    translate([
        esp_x-2,
        esp_y+esp[1]/2-4,
        11.5
    ])

    cube([3,8,3]);


    // --------------------------------------------------------
    // MPU6500
    // --------------------------------------------------------

    color("darkblue")

    translate([
        mpu_x,
        mpu_y,
        11
    ])

    cube(mpu);


    // --------------------------------------------------------
    // BATERIE LI-PO
    // --------------------------------------------------------

    color("silver")

    translate([
        bat_x,
        bat_y,
        16
    ])

    cube(baterie);


    // --------------------------------------------------------
    // SWITCH SLIDE
    // --------------------------------------------------------

    color("gray")

    translate([
        ext_x-12,
        ext_y/2-switch_[1]/2,
        6
    ])

    cube(switch_);
}


// ============================================================
// 15. AFISARE SUPORTURI INTERIOARE
// ============================================================

module suporturi_interioare() {

    // Suporturi MAX30102
    for (x=[max_x, max_x+max_[0]-2]) {

        translate([x,max_y,5])

            cube([2, max_[1], 2]);
    }


    // Suporturi DS18B20
    for (x=[ds_x, ds_x+ds[0]-2]) {

        translate([x,ds_y,5])

            cube([2, ds[1], 2]);
    }


    // Suporturi baterie
    translate([bat_x-1,bat_y-1,15])

        cube([baterie[0]+2,2,1]);

    translate([bat_x-1,bat_y+baterie[1]-1,15])

        cube([baterie[0]+2,2,1]);
}


// ============================================================
// 16. ASAMBLAREA FINALA
// ============================================================


// Corp principal
color("lightgray")
carcasa();


// Suporturi curea
color("gray")
suport_curea();


// Suporturi componente
color("gray")
suporturi_interioare();


// Componente transparente / colorate
if (arata_componente) {

    componente();
}


// Capac - afisat separat langa carcasa
if (arata_capac) {

    color("silver")

    translate([
        0,
        ext_y+8,
        ext_z
    ])

    capac();
}


// ============================================================
// 17. INFORMATII DIMENSIONALE
// ============================================================

echo("====================================");
echo("VITALSENSE SMARTWATCH v1.0");
echo("====================================");

echo(str(
    "Dimensiuni carcasa: ",
    ext_x,
    " x ",
    ext_y,
    " x ",
    ext_z,
    " mm"
));

echo(str(
    "Dimensiune cu capac: ",
    ext_x,
    " x ",
    ext_y,
    " x ",
    ext_z+capac_g,
    " mm"
));

echo(str(
    "Fereastra MAX30102: ",
    max_window_x,
    " x ",
    max_window_y,
    " mm"
));

echo(str(
    "Proeminenta DS18B20: ",
    ds_protrusion,
    " mm"
));

echo("Model conceptual - verificati dimensiunile fizice inainte de imprimare.");

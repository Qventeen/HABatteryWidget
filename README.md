# HA Battery Widget (Android)

Нативний Android-додаток із віджетом на базі **Jetpack Glance** для моніторингу акумулятора JK-BMS (`EVE 8S 314Ah`) з **Home Assistant**.

## Можливості
- **Заряд батареї (`%`)** та адаптивний прогрес-бар (`sensor.espjkbms_espjkbms_state_of_charge`)
- **Загальна напруга (`V`) та струм (`A`)** (`sensor.espjkbms_espjkbms_total_voltage`, `sensor.espjkbms_espjkbms_current`)
- **Потужність (`W` / `kW`) і залишок часу** (`sensor.espjkbms_espjkbms_power` з резервом `sensor.aninerel_anl_4200t_24l_w_pro_battery_power` + `sensor.battery_time_status`)
- **Напруги комірок `01–08` у 2 колонки та дельта (`Δ mV`)** з підсвічуванням мінімальної та максимальної комірок
- **Сповіщення про зарядку до 100%**: вмикається/вимикається чекбоксом у додатку; надсилає одне сповіщення при досягненні 100% (з повторним спрацюванням після зниження заряду `≤ 98%`)
- **Режим оновлення**: вибір між **Автооновленням** (ручне задання інтервалу `1–1440 хв` через `WorkManager` / `AlarmManager`) та **Ручним** оновленням по `↻` на віджеті
- **Світла / темна тема**: автоматично адаптується під тему Android
- **Гнучке підключення та сутності**: основний локальний URL + резервний / зовнішній URL, токен доступу HA та ручне редагування всіх сутностей (`entity_id`) показників і комірок у налаштуваннях додатку

## Збірка з командного рядка
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

Готовий підписаний APK: [`HA-Battery-Widget.apk`](HA-Battery-Widget.apk)

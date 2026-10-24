# Diagnóstico: los botones del control no andaban

Notas de cómo se encontró y arregló un problema de la TV (Noblex, MediaTek, Android 11) antes de
escribir el launcher. Sirve de referencia si vuelve a pasar.

## Síntoma

Los cuatro botones dedicados del control (Netflix, Prime, YouTube y Play Store) no hacían nada.

## Hipótesis descartada

Primero se sospechó que el launcher de Google (`com.google.android.tvlauncher`), que estaba
deshabilitado, era el que atendía esas teclas. **No era eso.**

## Cómo se encontró

Se dejó `adb logcat` capturando mientras se apretaban los botones. En el log se veía que
`com.mediatek.tv.service` (el servicio persistente que despacha las teclas dedicadas mediante
`HotKeyDispatcher`) se **caía y se reiniciaba unas 85 veces en 50 segundos**:

```
FATAL EXCEPTION: ConnectivityThread
Process: com.mediatek.tv.service
java.lang.SecurityException: Failed to find provider tvlauncher.widget for user 0
    at ...MyNetworkCallback.notifyChange(MyStartUp.kt:40)
```

Al arrancar, el servicio avisa a un `ContentProvider` con la autoridad `tvlauncher.widget`. Ese provider
lo aporta el paquete `com.smartdevice.leanbacklauncher.customizer`, que figuraba con `installed=false`
para el usuario 0 (alguien lo había desinstalado con `pm uninstall --user 0`). Sin provider, el servicio
reventaba al iniciar y Android lo reintentaba en bucle: las teclas nunca llegaban a despacharse y, de
yapa, se gastaba CPU y RAM todo el tiempo.

## Arreglo

El APK sigue en la partición de sistema, así que alcanza con reinstalarlo para el usuario:

```sh
adb shell pm install-existing --user 0 com.smartdevice.leanbacklauncher.customizer
adb reboot
```

El reinicio hace falta porque, tras tantas caídas seguidas, Android deja el proceso persistente en
espera ("Restarting PERS"). Después del reinicio el servicio queda vivo y los botones andan.

Es reversible con `adb shell pm uninstall --user 0 com.smartdevice.leanbacklauncher.customizer`.

## Moraleja

- Al "limpiar bloatware" con `pm uninstall --user 0`, conviene anotar qué se saca: algunos paquetes
  de fabricante son dependencias de servicios del sistema aunque no tengan interfaz.
- Un proceso persistente en crash loop se nota en `logcat` y en la cantidad de `Start proc` repetidos.

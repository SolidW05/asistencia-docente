# Geocercas y ubicación

La jerarquía tiene dos niveles: el polígono principal de un campus y sus módulos hijos. No existen geocercas de aula. El administrador dibuja o edita polígonos y selecciona campus y padre; el servidor comprueba la geometría antes de guardarla. No hay límites ni polígonos precargados: deben configurarse explícitamente.

GeoJSON usa coordenadas `[longitud, latitud]`; Leaflet suele recibir `[latitud, longitud]`. Confundirlas coloca los polígonos en otra región. Cada anillo debe cerrarse repitiendo el primer punto:

```json
{"type":"Polygon","coordinates":[[[-103.21,20.56],[-103.20,20.56],[-103.20,20.57],[-103.21,20.57],[-103.21,20.56]]]}
```

El SRID 4326 describe coordenadas WGS84 en grados. El esquema consolidado `schema.sql` habilita PostGIS, define una columna `geometry(Polygon,4326)` y crea un índice GiST. El GeoJSON se conserva para intercambio y snapshots; la clasificación opera sobre geometrías espaciales reales.

Se rechazan geometrías vacías, tipos incompatibles, coordenadas fuera de rango y polígonos autointersectados. `ST_IsValid` verifica la geometría. `ST_Covers(campus, modulo)` comprueba que el hijo esté cubierto por su padre; también se preserva esta relación al editar el campus. `ST_Covers(poligono, punto)` incluye los puntos del borde.

Las distancias se calculan sobre `geography`, en metros. No se interpretan grados como metros. Si la posición está a una distancia del límite menor o igual que su precisión, se considera incierta. En un borde exacto y con precisión cero, la prueba matemática de cobertura sigue incluyendo el borde; una lectura de dispositivo con radio positivo se trata de manera conservadora.

Política inicial configurable:

| Configuración | Valor | Propósito |
|---|---:|---|
| MAX_ACCURACY_METERS | 50 m | Evitar confirmar pertenencia con lecturas muy amplias |
| MAX_AGE_SECONDS | 120 s | Exigir una captura reciente antes de enviar |
| EARLY_MINUTES | 15 min | Ventana anterior al inicio de la clase |
| LATE_MINUTES | 15 min | Ventana posterior al final de la clase |
| TIME_ZONE | America/Mexico_City | Resolver fechas y horarios locales |

Son parámetros técnicos para pruebas, no reglas institucionales. Cincuenta metros tampoco garantiza detectar correctamente módulos pequeños: el control de distancia al límite puede clasificar una lectura como incierta aunque pase el umbral general. El servidor usa su reloj de recepción y mantiene separada la comprobación temporal. El algoritmo admite hasta cinco segundos de adelanto del reloj de captura para pequeñas diferencias de reloj; un adelanto mayor se clasifica como posición antigua/no reciente (STALE) y exige revisión.

| Situación | Tratamiento |
|---|---|
| Módulo permitido y ventana válida | Confirmado sin justificación |
| Otro espacio del campus, incluido otro módulo | Motivo y texto; pendiente de revisión |
| Fuera del campus | Motivo y texto; pendiente de revisión |
| Sin ubicación, antigua, imprecisa o incierta | Reintento o reporte explícito justificado; pendiente |
| Fuera de horario | Excepción justificada; pendiente |
| Sin configuración aplicable | Nunca confirmar automáticamente |

Permiso denegado, tiempo agotado y ubicación no disponible se distinguen en la interfaz y se conservan como incidencia. No se convierten en coordenadas artificiales ni en “fuera del campus”. El backend vuelve a evaluar cada registro; una comprobación previa en pantalla no constituye autorización definitiva.

Cada edición incrementa una versión; las versiones/snapshots usados quedan asociados a la evidencia de asistencia. Consultar el historial no vuelve a clasificar con el polígono actual. No hay borrado físico de geocercas utilizadas.

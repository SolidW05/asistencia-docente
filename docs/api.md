# API

El contrato completo se encuentra en [contrato.md](contrato.md). Los endpoints se sirven bajo `/api`, con JSON y errores `{code,message}` en español. Swagger está en [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html); el esquema OpenAPI en `/v3/api-docs`. Requiere la sesión según la configuración de seguridad. La interfaz accede mediante el proxy Nginx `/api`, sin nombres internos de Docker en el navegador.

La identidad se toma de la sesión; las escrituras de asistencia no aceptan un código docente como autoridad. Consultar otra ocurrencia o clave de solicitud no permite acceder a datos de otro docente. Las rutas `/admin/**` exigen rol administrador en el backend.

`GET /api/chain` entrega la cabecera global, el génesis común y los bloques de asistencia propios. Los índices pueden tener saltos: todos los docentes comparten una secuencia, pero no los payloads de sus asistencias. `GET /api/chain/verify` comprueba la secuencia global completa en el servidor y devuelve solamente el resultado de integridad y el primer error, sin exponer identidades ajenas. El bloque 0 se deriva de `And they say chivalry is dead`; el primer registro de asistencia tiene índice 1.

Secuencia de autenticación y registro:

1. GET `/api/auth/csrf`, conservar cookie y `{token,headerName}`.
2. POST `/api/auth/login` con el encabezado CSRF y `{code,password}`. El código es el elegido por el usuario, con siete dígitos; el rol administrador depende de `DEMO_ADMIN_CODES`. En el perfil local `demo`, la contraseña no se valida.
3. Renovar CSRF tras el login; consultar `/api/auth/me`, `/api/classes` y `/api/geofences`.
4. Seleccionar una ocurrencia y solicitar ubicación al navegador.
5. POST `/api/attendance/check`; mostrar resultados y justificación requerida.
6. POST `/api/attendance` con `Idempotency-Key` UUID y token CSRF.
7. Ante incertidumbre de recepción, GET `/api/attendance/requests/{key}`. Si aún no existe, reintentar con la misma clave y el mismo cuerpo.
8. Consultar `/api/attendance`, `/api/chain` y `/api/chain/verify`.

Ejemplo de cuerpo de comprobación (sustituir el UUID y la fecha por valores reales recientes):

```json
{
  "occurrenceId": "00000000-0000-0000-0000-000000000001",
  "location": {
    "latitude": 20.565,
    "longitude": -103.21,
    "accuracyMeters": 8,
    "capturedAt": "2026-10-13T01:00:00Z"
  },
  "locationIssue": null
}
```

Ejemplo de respuesta de un caso ordinario:

```json
{"spatialResult":"ALLOWED_MODULE","temporalResult":"IN_WINDOW","status":"CONFIRMED","requiresJustification":false,"message":"..."}
```

Una incidencia explícita puede enviarse sin coordenadas:

```json
{"occurrenceId":"00000000-0000-0000-0000-000000000001","location":null,"locationIssue":"PERMISSION_DENIED","reason":"Otro","justification":"No pude otorgar permiso de ubicación en el dispositivo."}
```

No enviar justificación vacía en un caso pendiente. No asumir que un HTTP fallido significa que la transacción nunca llegó: consultar la clave. Un 409 indica un conflicto, como una asistencia ya registrada o una versión desactualizada.

Las escrituras administrativas envían polígonos GeoJSON y una versión al editar. Una clase configura docente, campus, módulos permitidos, día de la semana (1=lunes, 7=domingo), inicio/fin y estado. Los horarios del MVP no atraviesan medianoche; crear sesiones separadas si se necesita ese caso.

El tipo/campus/padre de una geocerca quedan fijos al crearla. Para cambiar su jerarquía, crear otra y desactivar la anterior conservando evidencia. El docente/campus de una clase no se reasignan si ya existen ocurrencias. Los cambios de horario actualizan ocurrencias actuales/futuras sin asistencia; las ya registradas preservan su horario histórico.

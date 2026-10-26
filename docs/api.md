# API

El contrato completo está en [contrato.md](contrato.md). Las rutas usan `/api`, JSON y errores `{code,message}` en español. Swagger está en `http://localhost:8080/swagger-ui.html`; OpenAPI en `/v3/api-docs`.

La identidad procede de la sesión. Las escrituras conservan CSRF. Las rutas administrativas distinguen ADMIN y JEFE: campus/geocercas y altas de jefes son exclusivas de ADMIN; clases y revisión admiten JEFE con alcance limitado al creador de la clase. ADMIN consulta todas las asistencias.

## Recorrido de administración

1. Iniciar sesión con un código principal de `DEMO_ADMIN_CODES`.
2. Renovar CSRF y crear campus y geocercas.
3. POST `/api/admin/teachers` con `{"code":"1234567"}` para crear/promover un JEFE.
4. Iniciar sesión como JEFE y crear clases en `/api/admin/classes`. Se puede asignar por `teacherId` o por `teacherCode`; en este último caso se crea el docente si falta.
5. Consultar `/api/admin/attendance`. El servidor filtra las clases del JEFE; ADMIN obtiene todos los registros.
6. POST `/api/admin/attendance/{id}/review` con `{"status":"CONFIRMED","comment":"Justificación revisada."}`.

Una revisión confirma un pendiente y agrega un evento. Repetirla sobre una asistencia confirmada devuelve el registro actual sin otro bloque. La evidencia del registro original permanece guardada.

## Recorrido docente

1. GET `/api/auth/csrf`; conservar cookie y encabezado indicado.
2. POST `/api/auth/login` con `{code,password}` y CSRF; renovar el token después.
3. Consultar `/api/classes?date=YYYY-MM-DD` y `/api/geofences`.
4. Seleccionar una ocurrencia y solicitar ubicación al navegador.
5. POST `/api/attendance/check`; leer resultados y justificación requerida.
6. POST `/api/attendance` con `Idempotency-Key` UUID y CSRF.
7. Tras un resultado incierto, GET `/api/attendance/requests/{key}` y reutilizar la misma clave con el mismo cuerpo.
8. Consultar `/api/attendance` y `/api/attendance/{id}/history` para ver estados y comentarios de revisión.

Una incidencia sin coordenadas puede usar este cuerpo, reemplazando el UUID por una ocurrencia real:

```json
{"occurrenceId":"00000000-0000-0000-0000-000000000001","location":null,"locationIssue":"PERMISSION_DENIED","reason":"Otro","justification":"No pude otorgar permiso de ubicación en el dispositivo."}
```

## Cadena y verificación

GET `/api/chain` y GET `/api/chain/verify` son exclusivos de ADMIN. La primera entrega todos los bloques y sus datos; la segunda reconstruye y verifica toda la secuencia, incluidos los cambios de estado. Un docente recibe solo su registro e historial funcional.

El génesis tiene índice 0; el registro inicial usa ATTENDANCE_RECORDED y la revisión ATTENDANCE_STATUS_CHANGED. Consultar o repetir una operación ya aplicada no agrega bloques. Los errores 409 indican conflictos de idempotencia, duplicados o versión desactualizada, según la operación.

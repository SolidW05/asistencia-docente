# Contrato de integración

Prototipo local. Todos los identificadores son UUID en cadenas. API JSON bajo `/api`; fechas ISO-8601, UTC para instantes y America/Mexico_City para horarios. Los errores tienen forma `{code,message}` en español. Se usan sesiones HTTP y CSRF: GET `/auth/csrf` devuelve `{token,headerName}`; enviar ese encabezado en escrituras y renovar después de login/logout. El frontend usa credenciales del mismo origen.

## Identidad y permisos

- GET `/auth/csrf` es público.
- POST `/auth/login` recibe `{code,password}` y devuelve `{id,code,name,role}`. Los roles son `TEACHER`, `JEFE` y `ADMIN`. El perfil `demo` exige siete dígitos e ignora la contraseña, sin persistirla.
- GET `/auth/me` devuelve la identidad vigente; POST `/auth/logout` cierra la sesión.
- Los códigos principales se configuran mediante `DEMO_ADMIN_CODES`. ADMIN da de alta jefes; la API de alta no acepta asignar ADMIN.
- POST `/admin/teachers`, solo ADMIN, recibe `{"code":"1234567"}`. Si no existe, crea la cuenta con rol JEFE; si ya es docente, conserva su identidad y la promueve. El siguiente login respeta JEFE.
- GET `/admin/teachers`, ADMIN o JEFE, devuelve `[{id,code,name,role}]` para asignar clases.
- Un jefe administra y revisa únicamente las clases que creó. ADMIN tiene alcance global. Las clases anteriores sin creador quedan disponibles solo para ADMIN.
- GET `/config` autenticado devuelve `{maxAccuracyMeters,maxAgeSeconds,earlyMinutes,lateMinutes,timeZone}`.

## Campus, módulos y clases

- GET `/campuses` devuelve `[{id,name,active}]`.
- GET `/geofences` devuelve `[{id,campusId,name,type,parentId,geometry,active,version}]`; type es CAMPUS/MODULE y geometry es GeoJSON Polygon.
- POST/PUT `/admin/campuses[/{id}]`, solo ADMIN, recibe `{name,active}`.
- POST/PUT `/admin/geofences[/{id}]`, solo ADMIN, recibe `{campusId,name,type,parentId,geometry,active}` y al editar también `version`.
- GET `/classes?date=YYYY-MM-DD` devuelve clases propias con ocurrencia de esa fecha: `[{id,name,teacherId,campusId,allowedModuleIds,dayOfWeek,startTime,endTime,active,version,occurrence:{id,date,startAt,endAt}}]`. Si no aplica ese día, occurrence es null.
- GET `/admin/classes`, ADMIN o JEFE, devuelve clases de su alcance, sin ocurrencia.
- POST `/admin/classes` recibe `{name,teacherId,campusId,allowedModuleIds,dayOfWeek,startTime,endTime,active}`. Como alternativa a `teacherId`, acepta `teacherCode` de siete dígitos y crea el docente si falta. Se envía solo una de las dos referencias.
- PUT `/admin/classes/{id}` recibe el mismo cuerpo y `version`; exige autorización sobre la clase y conserva al creador original.

El día usa 1=lunes y 7=domingo. Los horarios no cruzan medianoche. No se reasignan docente/campus cuando ya existen ocurrencias. Cambiar un horario solo reconcilia ocurrencias actuales o futuras sin asistencia. Una cuenta nueva no recibe clases inventadas.

## Registro de asistencia

`location` es null cuando no hay coordenadas o `{latitude,longitude,accuracyMeters,capturedAt}`. `locationIssue` es null o PERMISSION_DENIED/TIMEOUT/UNAVAILABLE; el backend también calcula STALE/INACCURATE/UNCERTAIN.

- POST `/attendance/check` recibe `{occurrenceId,location,locationIssue}` y devuelve `{spatialResult,temporalResult,status,requiresJustification,message}`. Consultar no crea bloques.
- POST `/attendance` recibe `{occurrenceId,location,locationIssue,reason,justification}`, con encabezado `Idempotency-Key` UUID. Devuelve AttendanceView. Las excepciones requieren motivo y justificación.
- GET `/attendance` devuelve registros propios: `[{id,className,occurrenceId,receivedAt,status,spatialResult,temporalResult,reason,justification,location}]`.
- GET `/attendance/requests/{key}` devuelve el registro propio o 404. Tras un fallo de red se reutiliza la clave y el mismo cuerpo hasta conocer el resultado.
- GET `/attendance/{id}/history` devuelve solo el historial propio, sin hashes ni payloads: `[{id,previousStatus,status,changedAt,reviewerName,comment}]`. El registro inicial tiene previousStatus null. Una asistencia ajena no se entrega.

Los estados disponibles son CONFIRMED y PENDING_REVIEW. El resultado temporal es IN_WINDOW/OUTSIDE_WINDOW; el espacial es ALLOWED_MODULE/OTHER_CAMPUS_SPACE/OUTSIDE_CAMPUS/NO_LOCATION/STALE/INACCURATE/UNCERTAIN/UNCONFIGURED. La falta de configuración nunca confirma automáticamente.

## Revisión administrativa

- GET `/admin/attendance`, ADMIN o JEFE, devuelve registros de su alcance. Cada elemento agrega `classId`, `teacherName` y `teacherCode` a los datos de AttendanceView.
- POST `/admin/attendance/{id}/review` recibe `{"status":"CONFIRMED","comment":"Se revisó la justificación del docente."}` y devuelve AttendanceView actualizado. El comentario es obligatorio.
- Solo se permite PENDING_REVIEW → CONFIRMED. Repetir una confirmación ya aplicada devuelve el registro confirmado y no crea otro bloque.
- La revisión conserva la evidencia original de ubicación, horario y justificación; agrega un evento con autor, fecha, comentario y estados anterior/nuevo. El docente puede consultar ese cambio.

## Cadena global: solo ADMIN

- GET `/chain` devuelve `{chainId,teacherId,lastIndex,lastHash,totalBlocks,genesisPhrase,genesisHash,blocks:[{id,index,eventType,teacherId,attendanceId,serverAt,payload,previousHash,hash,formatVersion}]}`. Incluye todos los bloques de todos los docentes. `teacherId` superior identifica al administrador consultante; en cada bloque identifica al dueño de la asistencia.
- GET `/chain/verify` devuelve `{valid,checkedBlocks,firstError}` y comprueba toda la cadena global. firstError es null o texto. JEFE y TEACHER no pueden acceder a ninguna de estas dos rutas.
- `chainId` es GLOBAL_ATTENDANCE. El bloque 0 es GENESIS, con docente/asistencia/fecha null, hash anterior de 64 ceros y payload `{"phrase":"And they say chivalry is dead"}`. Su hash es SHA-256 de esa frase UTF-8 sin comillas ni salto final.
- Desde el índice 1 se guardan ATTENDANCE_RECORDED y ATTENDANCE_STATUS_CHANGED, formato 2. El registro agrega un bloque; cada transición efectiva agrega otro. Login, lectura, comprobación, alta de clases/jefes y reintentos no agregan bloques.

No hay endpoints para editar o borrar bloques. La asistencia actual y la evidencia de cada evento se conservan en la misma transacción. La verificación reconstruye los estados por evento y coteja el estado final con el registro actual. Véase [cadena-de-bloques.md](cadena-de-bloques.md).

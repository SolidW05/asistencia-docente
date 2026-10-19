# Contrato de integración

Prototipo local. Todos los identificadores son UUID en cadenas. API JSON bajo `/api`; fechas ISO-8601, UTC para instantes y America/Mexico_City para horarios. Errores `{code,message}` en español, HTTP 400/401/403/404/409/422. Cookies de sesión y CSRF: GET `/auth/csrf` devuelve `{token,headerName}`; enviar el encabezado indicado en escrituras. Renovar tras login/logout. El frontend usa credenciales same-origin.

## Autenticación
- GET `/auth/csrf` público.
- POST `/auth/login` `{code,password}` -> `{id,code,name,role}`; rol `TEACHER` o `ADMIN`. En perfil `demo` el código debe tener siete dígitos y la contraseña se ignora, sin persistirla. La identidad se guarda solo tras este acceso manual, con nombre genérico según rol y código. Los administradores se definen en `DEMO_ADMIN_CODES`.
- GET `/auth/me` -> identidad (401 sin sesión).
- POST `/auth/logout` -> 204.
- GET `/config` autenticado -> `{maxAccuracyMeters,maxAgeSeconds,earlyMinutes,lateMinutes,timeZone}`.

## Mapa y clases
- GET `/campuses` -> `[{id,name,active}]`.
- GET `/geofences` -> `[{id,campusId,name,type,parentId,geometry,active,version}]`; `type` CAMPUS/MODULE, geometry GeoJSON Polygon.
- GET `/classes?date=YYYY-MM-DD` -> clases propias con ocurrencia concreta: `[{id,name,teacherId,campusId,allowedModuleIds,dayOfWeek,startTime,endTime,active,version,occurrence:{id,date,startAt,endAt}}]`. dayOfWeek 1=lunes…7=domingo. Sin clase ese día -> `occurrence:null`. Los códigos nuevos no reciben asignaciones inventadas.

## Asistencia
`location` es null para incidencia sin coordenadas o `{latitude,longitude,accuracyMeters,capturedAt}`; `locationIssue` null o PERMISSION_DENIED/TIMEOUT/UNAVAILABLE. El backend calcula STALE/INACCURATE/UNCERTAIN.
- POST `/attendance/check` `{occurrenceId,location,locationIssue}` -> `{spatialResult,temporalResult,status,requiresJustification,message}`. Resultados espaciales ALLOWED_MODULE/OTHER_CAMPUS_SPACE/OUTSIDE_CAMPUS/NO_LOCATION/STALE/INACCURATE/UNCERTAIN/UNCONFIGURED; temporal IN_WINDOW/OUTSIDE_WINDOW; status CONFIRMED/PENDING_REVIEW. Sin configuración jamás confirma.
- POST `/attendance` `{occurrenceId,location,locationIssue,reason,justification}` con `Idempotency-Key` UUID -> registro. Las incidencias y excepciones exigen motivo y texto.
- GET `/attendance` -> registros propios: `[{id,className,occurrenceId,receivedAt,status,spatialResult,temporalResult,reason,justification,location}]`.
- GET `/attendance/requests/{key}` -> registro propio o 404; reutilizar key tras fallos hasta resolución.
- GET `/chain` -> `{chainId,teacherId,lastIndex,lastHash,totalBlocks,genesisPhrase,genesisHash,blocks:[{id,index,eventType,teacherId,attendanceId,serverAt,payload,previousHash,hash,formatVersion}]}`. `chainId` es `GLOBAL_ATTENDANCE`; la cabecera y total incluyen toda la cadena. `teacherId` superior es la identidad consultante. `blocks` contiene el génesis y únicamente sus asistencias; puede tener índices discontinuos.
- Génesis: índice 0, evento `GENESIS`, docente/asistencia/fecha null, payload `{phrase:"And they say chivalry is dead"}`, hash anterior de 64 ceros y hash SHA-256 de la frase UTF-8 sin comillas ni salto final. Asistencias: índices desde 1, evento `ATTENDANCE_RECORDED`, docente/asistencia/fecha no null. Formato actual 2.
- GET `/chain/verify` -> `{valid,checkedBlocks,firstError}`; verifica toda la cadena global, incluidos bloques de otros docentes, sin divulgar sus datos. `checkedBlocks` cuenta los bloques examinados, incluido el génesis; firstError null o texto.

## Administración (solo ADMIN)
- GET `/admin/teachers` -> `[{id,code,name,role}]`.
- POST `/admin/campuses` `{name,active}` -> campus; PUT `/admin/campuses/{id}` mismo cuerpo.
- POST `/admin/geofences` `{campusId,name,type,parentId,geometry,active}` -> geocerca; PUT `/admin/geofences/{id}` mismo cuerpo más `version` para evitar actualización perdida.
- GET `/admin/classes` -> clases con contrato anterior (sin ocurrencia o null).
- POST `/admin/classes` `{name,teacherId,campusId,allowedModuleIds,dayOfWeek,startTime,endTime,active}` -> clase; PUT `/admin/classes/{id}` mismo cuerpo más `version`.

No hay eliminación física ni modificación de bloques desde la API. La base nueva solo contiene el Génesis y la cabecera; no incluye docentes, campus, módulos, clases ni asistencias semilla. El acceso manual no crea asignaciones. La política configurable inicial permite precisión de 50 m, antigüedad de 120 s y ventana de 15 min antes y después; existe incertidumbre si el círculo de precisión alcanza un límite relevante. Estos parámetros no son reglas institucionales.

Las versiones utilizadas están fijadas en `backend/pom.xml`, `frontend/package-lock.json` y las imágenes de Compose. El backend usa Java target 21. La reorganización por capas conserva este contrato HTTP.

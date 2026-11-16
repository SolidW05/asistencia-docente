# Guía para explicar el backend

El proyecto registra asistencias como eventos. Una asistencia nueva produce un bloque; confirmar una asistencia pendiente produce otro. El objetivo de la cadena es detectar cambios en la evidencia que ya se guardó.

## Qué datos forman un bloque

La tabla `global_chain_blocks` y la entidad [ChainBlock](../backend/src/main/java/mx/udg/cut/asistencia/models/chain/ChainBlock.java) contienen:

| Consigna | Campo del proyecto | Propósito |
|---|---|---|
| ID / número | `id` y `block_index` | UUID único e índice consecutivo para ordenar. |
| Timestamp | `server_at` | Instante del servidor del evento, persistido en UTC. |
| Datos | `payload` | JSON con la asistencia o el cambio de estado. |
| `prev_hash` | `previous_hash` | Hash almacenado del bloque anterior. |
| `hash_actual` | `hash` | SHA-256 de la envoltura del bloque. |

También se protegen `chain_id`, `event_type`, `teacher_id`, `attendance_id` y `format_version`. El génesis de índice 0 es el ancla que proporciona el hash anterior al primer evento.

## Cómo se calcula el hash

[CanonicalJson](../backend/src/main/java/mx/udg/cut/asistencia/utils/CanonicalJson.java) construye una envoltura que incluye los campos anteriores. Ordena las claves del JSON, normaliza números y obtiene los bytes UTF-8. Luego utiliza `MessageDigest.getInstance("SHA-256")` y representa el resultado con 64 caracteres hexadecimales.

```text
hash = SHA256(UTF8(JSON_canónico(
    blockId, index, serverAt, payload, previousHash,
    chainId, eventType, teacherId, attendanceId, formatVersion
)))
```

La envoltura es una manera inequívoca de combinar los campos de `H(ID || timestamp || datos || prev_hash)`. Evita ambigüedades de concatenar texto sin separadores. Cambiar el orden de las claves o escribir `1.00` en vez de `1` no cambia el significado canónico; cambiar un dato sí lo cambia. Un hash no cifra ni oculta el payload.

## Recorrido de una escritura

1. Un controlador recibe HTTP, toma la identidad de la sesión y valida el DTO. El cliente no decide la identidad ni el hash.
2. [AttendanceService](../backend/src/main/java/mx/udg/cut/asistencia/services/attendance/AttendanceService.java) valida la ocurrencia de clase, ubicación, horario y justificación. Comprueba duplicados y la clave de idempotencia.
3. La transacción bloquea la fila del docente. Guarda la asistencia y su snapshot original.
4. [ChainAppendService](../backend/src/main/java/mx/udg/cut/asistencia/services/chain/ChainAppendService.java) bloquea la cabecera con `SELECT ... FOR UPDATE`. Eso evita que dos solicitudes asignen el mismo índice o se enlacen en paralelo al mismo final.
5. Comprueba la evidencia criptográfica existente. Calcula el índice siguiente, toma el hash anterior, genera el hash propio e inserta el bloque.
6. Actualiza `global_chain_head`, que guarda el último índice, hash y número de bloques para localizar el final. Esos valores se contrastan contra los bloques al verificar.
7. Se confirma todo junto. Si falla el encadenamiento o cualquier operación, se revierte también la asistencia. No queda una asistencia nueva sin su bloque.

Una revisión conserva el bloque del registro inicial y agrega un evento `ATTENDANCE_STATUS_CHANGED`. El estado actual de la asistencia puede cambiar; la evidencia histórica original permanece disponible.

## Cómo se detecta una alteración

[ChainService.verify](../backend/src/main/java/mx/udg/cut/asistencia/services/chain/ChainService.java) lee los bloques en orden y con una vista consistente de la base. Comprueba el génesis, índices consecutivos, campos de cada evento y enlaces. Recalcula cada hash y lo compara con el almacenado.

Después contrasta la evidencia con la asistencia y sus versiones de configuración, reconstruye los cambios de estado y comprueba la cabecera. Devuelve `valid`, `checkedBlocks` y `firstError`; se detiene en el primer error para indicar dónde empezar a revisar. La comprobación previa a agregar se limita a los bloques y sus enlaces porque la asistencia de la transacción actual todavía está en proceso de guardarse.

Si se cambia el payload del bloque 1, su hash recalculado ya no coincide con su hash almacenado. Si alguien recalcula también ese hash, el `previous_hash` del bloque 2 dejará de coincidir. Quien controle toda la base podría modificar todos los enlaces y la cabecera; este sistema no tiene una referencia externa que impida esa reescritura completa.

## Demostración para clase

En **Cadena global**, verificar una cadena con movimientos, seleccionar un bloque anterior y pulsar **Alterar bloque**. [ChainAttackService](../backend/src/main/java/mx/udg/cut/asistencia/services/chain/ChainAttackService.java) añade una marca al payload guardado sin actualizar hashes. La página muestra el error del verificador. **Restaurar contenido original** repone el respaldo exacto y permite repetir la práctica. Esta acción requiere ADMIN, perfil demo y configuración explícita.

Para la revisión oral conviene poder explicar: qué campos entran al hash; por qué se ordena el JSON; qué ocurre al cambiar un dato pasado; por qué una revisión crea otro bloque; qué conserva PostgreSQL al reiniciar; y por qué un administrador que reescriba toda la evidencia puede superar esta comprobación local. Los comentarios del código acompañan estas decisiones; no sustituyen leer y comprender los métodos.

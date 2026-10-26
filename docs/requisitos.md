# Requisitos vigentes

El proyecto es un prototipo académico de asistencia docente para CUTonalá. Usa Java 21, Spring Boot, PostgreSQL/PostGIS y React/Vite/TypeScript. La autenticación es simulada y la cadena es centralizada.

## Roles y revisión

- ADMIN se configura mediante `DEMO_ADMIN_CODES`, registra jefes y consulta todas las asistencias.
- JEFE se da de alta por código docente, aunque la persona todavía no exista en la base. Su rol se conserva al iniciar sesión.
- JEFE crea clases, administra las que creó y revisa sus asistencias. No puede acceder a las de otro jefe ni a clases anteriores sin creador.
- Al asignar una clase por código, se crea automáticamente el docente si falta. El alta no crea clases ni asistencias adicionales.
- El docente consulta solo sus asistencias y sus cambios. No tiene acceso a la cadena.
- Confirmar un pendiente requiere comentario y conserva la evidencia original. Repetir una confirmación no agrega otro evento.

## Cadena global

Todos comparten GLOBAL_ATTENDANCE. El génesis tiene índice 0 y usa SHA-256 de la frase UTF-8 exacta `And they say chivalry is dead`, sin comillas ni salto final. No se duplica al reiniciar.

Cada registro crea un bloque ATTENDANCE_RECORDED y cada transición efectiva crea un ATTENDANCE_STATUS_CHANGED. No se crean bloques por login, comprobación, lectura, alta de clases/jefes o reintentos. Estado actual, evento y cabecera se confirman juntos bajo bloqueo del docente y de la cabecera global.

Solo ADMIN puede consultar todos los bloques, sus datos y verificar la cadena completa. El verificador conserva el formato 2, revisa los hashes y enlaces, reconstruye las transiciones y coteja el estado final con la asistencia actual.

## Configuración, identidad y datos

Se conserva la organización backend por capas y DTO públicos separados. La sesión HTTP determina identidad; se mantienen CSRF, cookies HttpOnly/SameSite y protección de rutas. Otro perfil sin proveedor real falla explícitamente. El código docente usa siete dígitos como String; la contraseña demo no se valida ni se guarda.

Una base nueva contiene solo génesis y cabecera, sin cuentas ni clases de ejemplo. El esquema consolidado sigue en `schema.sql`; no se introduce Flyway. La actualización de esta entrega se aplica antes de Hibernate bajo bloqueo transaccional, conserva datos/bloques y agrega permisos de creador y soporte para varios eventos por asistencia. No reiniciar ni borrar el volumen para actualizar.

Se conservan geocercas GeoJSON Polygon WGS84, validación PostGIS, versiones y snapshots. La ubicación se solicita puntualmente, sin seguimiento continuo. Las incidencias requieren motivo y justificación y quedan pendientes; la falta de configuración nunca confirma automáticamente. Horarios en America/Mexico_City e instantes UTC.

## Interfaz y validación

Conservar estados de carga, vacíos y errores comprensibles en español. Distinguir fallos de mapa, ubicación y envío. La interfaz debe mostrar únicamente acciones por rol y esperar confirmación del servidor antes de representar una escritura como exitosa.

Docker Compose conserva frontend, backend y PostgreSQL/PostGIS con volumen persistente y controles de salud. El proxy público es `/api`; geolocalización fuera de localhost requiere HTTPS.

La solicitud actual incorpora pruebas de roles y eventos, además de implementación y auditoría por agentes separados. Las pruebas no deben modificar los datos del entorno existente. Documentar comandos ejecutados, resultados reales y limitaciones; no afirmar pruebas de concurrencia o integración si no se ejecutaron.

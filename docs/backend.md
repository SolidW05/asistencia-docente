# Backend para un equipo Java

El código está en `backend/src/main/java/mx/udg/cut/asistencia`. Se organiza por capas: `models/{dominio}`, `dto/{dominio}`, `services/{dominio}`, `controllers`, `repositories/{dominio}`, `config`, `exceptions` y `utils`. `src/main/resources` contiene configuración, mensajes y el esquema consolidado `schema.sql`. Las pruebas de roles y movimientos están en `src/test/java`; no se usa Flyway.

Java 21 es el destino de compilación. Las versiones de Spring Boot, springdoc y las dependencias están fijadas en `pom.xml`.

## Capas

| Concepto | Responsabilidad | Ejemplo |
|---|---|---|
| `controllers` | Recibir HTTP, validar DTO y devolver respuestas | AuthController, GeofenceController |
| `dto/{dominio}` | Records de entrada/salida, sin lógica de persistencia | LoginRequest, ClassInput, ClassView |
| `services/{dominio}` | Aplicar reglas y coordinar transacciones | GeofenceService, ClassService, GenesisService |
| `repositories/{dominio}` | Consultar y guardar entidades | TeacherRepository, GeofenceRepository |
| `models/{dominio}` | Entidades y tipos del dominio | Teacher, Geofence, ClassOccurrence |
| `config` | Configurar seguridad, reloj e inicialización | DatabaseInitializationConfig |
| `exceptions` / `utils` | Errores de API y serialización canónica | ApiExceptionHandler, CanonicalJson |

Spring crea estos objetos e inyecta dependencias mediante constructores. `@Transactional` define el límite atómico de una operación. `@Valid` y las restricciones de Bean Validation verifican entradas; las comprobaciones de dominio y geometría viven en los servicios.

## Autenticación y sesión

`IdentityProvider` encapsula el proveedor; `DemoIdentityProvider` existe únicamente en perfil `demo`. El código debe tener exactamente siete dígitos y se conserva como String. La contraseña se ignora: no se almacena ni se registra. No hay identidades precargadas. El login crea una identidad local si falta. ADMIN también puede crear o promover un JEFE por código, y una clase puede precrear al docente asignado. El rol JEFE se conserva al iniciar sesión. Ninguna de estas altas crea asistencias. Los códigos principales siguen definidos mediante DEMO_ADMIN_CODES.

El backend crea y guarda un `SecurityContext` en la sesión HTTP. Cambia el identificador de sesión al autenticar para evitar fijación de sesión. La cookie de sesión es HttpOnly, SameSite=Lax y configurable como Secure para HTTPS. El cliente usa `credentials: same-origin`; no mantiene una identidad autorizadora en un campo oculto del formulario.

CSRF permanece activo, incluido el login. GET `/api/auth/csrf` devuelve el token y nombre del encabezado que debe acompañar las escrituras. El frontend renueva el token tras login/logout. No se habilita CORS permisivo: Vite en desarrollo y Nginx en Docker proporcionan un proxy del mismo origen. Una integración real debe definir su proveedor, autenticación y política de sesión; iniciar otro perfil sin proveedor real falla explícitamente.

Los códigos administradores se definen en `DEMO_ADMIN_CODES`; un código arbitrario no obtiene ese rol. Los endpoints administrativos verifican el rol y los de asistencia restringen datos a la identidad de la sesión. La protección visual de React acompaña a la autorización Java, sin sustituirla.

## Registro

El servidor valida ocurrencia, identidad, configuración activa, horario y calidad de ubicación. Luego clasifica mediante PostGIS. Las excepciones requieren motivo y texto y conservan estado pendiente. La hora del cliente describe la captura; el reloj del servidor decide recepción y ventana. Los horarios se resuelven en America/Mexico_City y los instantes se guardan en UTC.

La clave de idempotencia identifica un envío concreto, y su huella detecta reutilización con un cuerpo distinto. Un reintento idéntico recupera el registro; una asistencia ya existente no agrega un bloque. Dentro de la transacción se bloquea primero el docente para proteger esos controles y después la cabecera global para asignar un índice único y enlazar con la última asistencia, cualquiera que sea su docente. No se depende de `synchronized` ni del número de instancias Java.

`ChainService` verifica toda la cadena en una lectura REPEATABLE_READ. Tanto consulta como verificación son exclusivas de ADMIN y entregan la cadena global completa. El docente consulta su registro e historial sin datos criptográficos. La revisión confirma un pendiente con comentario, conserva el snapshot inicial y agrega un ATTENDANCE_STATUS_CHANGED. Un reintento sobre una asistencia ya confirmada no crea otro bloque.

`config/DatabaseInitializationConfig.java` se ejecuta antes de la validación de Hibernate. Bajo un bloqueo transaccional de PostgreSQL, aplica `schema.sql` únicamente si falta `global_chain_head` y llama a `services/chain/GenesisService.java`. Este servicio crea el Génesis y la cabecera una sola vez; posteriores arranques no agregan bloques. No se importan tablas ni registros antiguos. Véase [cadena-de-bloques.md](cadena-de-bloques.md).

`ApiExceptionHandler` convierte errores de entrada y dominio a respuestas uniformes en español. `ValidationMessages.properties` fija los mensajes de Bean Validation en español. Spring Security devuelve 401/403 JSON para autenticación/autorización. La información de geometría inválida se presenta como error comprensible y no como un stack trace para el usuario.

## Permisos y pruebas de esta entrega

El control de acceso consulta el rol persistido. ADMIN configura campus/geocercas y jefes; JEFE administra clases filtradas por createdBy y revisa sus asistencias. ADMIN tiene alcance global. El propietario de una clase es su creador, no necesariamente el docente asignado. Las clases heredadas sin creador quedan reservadas a ADMIN.

El inicializador también aplica una actualización idempotente para conservar datos existentes: incorpora JEFE y el creador de clase, permite eventos de revisión y elimina la unicidad global por attendanceId de los bloques. No elimina registros ni reescribe hashes.

Las pruebas unitarias y de autorización están en src/test/java. Usan dobles de repositorios y no modifican la base del usuario. La evidencia de ejecución y los límites del entorno se registran en [cambios-roles-revision.md](cambios-roles-revision.md).

Surefire precarga Mockito como javaagent únicamente durante tests. Esto permite ejecutar la suite con Java 25 sin intentar adjuntar instrumentación en ejecución. La versión procede del BOM de Spring Boot y no modifica el arranque de producción.

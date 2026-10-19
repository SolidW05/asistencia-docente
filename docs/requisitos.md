# Requisitos vigentes

El proyecto es un prototipo académico de asistencia docente para CUTonalá. No es un sistema oficial ni integra plataformas institucionales.

## Estructura

- Java 21, Spring Boot, Spring Data JPA, Spring Security, Bean Validation y OpenAPI.
- PostgreSQL/PostGIS como servicio independiente.
- React, Vite y TypeScript; React Router, Leaflet y herramientas de edición de polígonos.
- Backend por capas: `models/{dominio}`, `dto/{dominio}`, `services/{dominio}`, `controllers`, `repositories/{dominio}`, `config`, `exceptions` y `utils`.
- DTO públicos como records separados de controladores y servicios.
- Versiones fijadas en los archivos de dependencias e imágenes.

## Inicio limpio

La base nueva no debe contener docentes, campus, geocercas, clases, horarios ni asistencias de ejemplo. Deben eliminarse las suites automatizadas anteriores, los scripts de datos sintéticos y sus evidencias/resultados guardados. No añadir otras pruebas ni funcionalidades.

No usar Flyway ni conservar carpetas `db/migration`. El único esquema consolidado es `backend/src/main/resources/schema.sql`, sin tablas antiguas ni semillas.

`config/DatabaseInitializationConfig.java` debe coordinar el arranque mediante un bloqueo transaccional PostgreSQL. Si falta `global_chain_head`, inicializa el esquema antes de la validación de Hibernate. `services/chain/GenesisService.java` crea el Génesis y su cabecera una sola vez. Los arranques siguientes deben conservar los datos y no duplicar el bloque inicial.

El reinicio local solicitado elimina el volumen anterior y empieza desde cero. No se requiere importar o migrar la evidencia antigua. Los reinicios normales posteriores deben preservar los datos.

## Autenticación local

El perfil `demo` conserva autenticación simulada con código de exactamente siete dígitos, almacenado como String. La contraseña se ignora, no se guarda ni se registra, y el formulario la elimina después del envío.

No hay códigos docentes semilla, UUID reservados para ellos ni nombres especiales. Solo un login manual persiste una identidad local con nombre genérico `Docente <código>` o `Administrador <código>`. Los administradores dependen exclusivamente de `DEMO_ADMIN_CODES`. Un login no crea clases, geocercas ni asistencias.

La sesión HTTP determina la identidad y los permisos. Mantener CSRF, cookies HttpOnly y SameSite, renovación de sesión y autorización de rutas administrativas. Un perfil sin proveedor real debe fallar explícitamente. Informar en la interfaz que la autenticación es simulada.

## Configuración y asistencia

El administrador configura campus, polígono principal, módulos hijos y clases con docentes existentes, horarios semanales y módulos permitidos. No inventar límites oficiales ni reglas institucionales. Si falta configuración, mostrar un estado comprensible y nunca confirmar automáticamente.

Usar GeoJSON Polygon con coordenadas longitud/latitud y SRID 4326. PostGIS debe validar geometría, contención de módulos, bordes y distancias en metros. Conservar versiones de geocercas y snapshots históricos.

El docente solicita ubicación puntualmente y registra asistencia explícitamente. No hay seguimiento continuo. El servidor evalúa la ocurrencia, el horario, la pertenencia espacial y la precisión/antigüedad de la ubicación. Las excepciones e incidencias requieren motivo y justificación y quedan pendientes.

La hora de recepción corresponde al servidor. Resolver horarios en America/Mexico_City y guardar instantes en UTC. Preservar idempotencia, control de duplicados, concurrencia y aislamiento entre docentes.

## Cadena global

Todos los docentes comparten `GLOBAL_ATTENDANCE`. El Génesis tiene índice 0, docente/asistencia/fecha null, hash anterior de 64 ceros y payload con la frase exacta `And they say chivalry is dead`. Su hash es SHA-256 de los bytes UTF-8 de esa frase, sin comillas ni salto final.

Cada asistencia agrega un bloque desde el índice 1 enlazado con el último bloque global. Asistencia, bloque y cabecera se confirman en una transacción. No crear bloques por login, consulta o reintento idempotente.

La consulta entrega solo el Génesis y bloques propios, con cabecera y conteo globales. La verificación comprueba toda la cadena en el servidor sin divulgar payloads ajenos. Mantener el formato y contrato HTTP documentados en [contrato.md](contrato.md).

## Interfaz y ejecución

Conservar las pantallas de acceso, registro, administración e historial/cadena, con estados vacíos claros y textos en español. Los fallos de mapa, ubicación y envío deben distinguirse. Un fallo de envío no autoriza mostrar un registro como confirmado; permitir consultar o reintentar mediante la misma clave de idempotencia.

Docker Compose debe ejecutar frontend, backend y PostgreSQL/PostGIS con volumen persistente y controles de salud. La interfaz usa un proxy `/api`; no expone nombres internos de Docker al navegador. Documentar ejecución local, configuración inicial y reinicio completo del volumen.

La geolocalización requiere localhost o HTTPS en otros dispositivos. No desactivar protecciones del navegador.

## Comprobación y entrega

Compilar backend y frontend, comprobar el arranque y verificar que la base nueva contenga solamente el Génesis y su cabecera. Comprobar que un reinicio no los duplique. No crear datos de demostración para estas comprobaciones ni guardar nuevas suites de pruebas.

Documentar brevemente qué cambió, qué se comprobó y cualquier impedimento real, sin declarar verificaciones que no se hayan ejecutado. La autenticación sigue siendo simulada y la cadena centralizada solo demuestra consistencia interna, no presencia física ni inmutabilidad absoluta.

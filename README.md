# Asistencia docente mediante geocercas

Prototipo académico para el equipo de CUTonalá, Universidad de Guadalajara. Permite registrar asistencia con ubicación, conservar excepciones justificadas pendientes, administrar campus/módulos/clases e inspeccionar una cadena criptográfica global. La autenticación es local y simulada; no hay conexión con plataformas institucionales.

Java 21, Spring Boot, PostgreSQL/PostGIS y React con Vite/TypeScript. Las versiones están fijadas en `backend/pom.xml`, `frontend/package-lock.json` y las imágenes de Docker.

## Arranque con Docker

Requisitos: Docker Desktop con contenedores Linux y Docker Compose. Desde esta carpeta:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

Abrir [la interfaz](http://localhost:8088), [la API](http://localhost:8080) o [Swagger](http://localhost:8080/swagger-ui.html). Compose espera los controles de salud de PostgreSQL y del backend. Los puertos se pueden cambiar en `.env`.

La base nueva arranca sin docentes, campus, geocercas, clases ni asistencias. El backend inicializa una sola vez el esquema desde `backend/src/main/resources/schema.sql` y crea únicamente el bloque Génesis y su cabecera. No utiliza migraciones Flyway ni carga datos de ejemplo. Los reinicios conservan los datos existentes y no duplican el Génesis.

## Configuración inicial

1. Definir los códigos administradores en `DEMO_ADMIN_CODES` antes de arrancar.
2. Ingresar con un código ADMIN configurado y crear campus, geocerca principal y módulos.
3. En Jefes, dar de alta por código de siete dígitos. Si no existe, se crea la cuenta con rol JEFE; al iniciar sesión abrirá su administración.
4. ADMIN o JEFE crea clases asignando un docente existente o un código; el código permite crear al docente automáticamente. Cada JEFE administra solo las clases que creó.
5. El docente registra asistencia o reporta una incidencia justificada. En Mis asistencias ve su estado y sus cambios.
6. En Asistencias, JEFE revisa pendientes de sus clases y ADMIN consulta todos. Confirmar requiere comentario y agrega un nuevo bloque.
7. Solo ADMIN abre Cadena para consultar todos los bloques y verificarla. El índice 0 es el génesis; los registros y revisiones empiezan desde el índice 1.
8. Con `APP_CHAIN_ATTACK_ENABLED=true`, la misma pantalla permite seleccionar un bloque, **Alterar bloque**, verificar la inconsistencia y **Restaurar contenido original**. Esta opción requiere el perfil `demo`.

El perfil `demo` permite entrar con un código de siete dígitos. El campo de contraseña se conserva editable y es opcional: puede quedar vacío o contener cualquier texto, sin validarlo, almacenarlo ni registrarlo. Los nombres son etiquetas locales como `Docente <código>` y `Administrador <código>`. Los códigos configurados no son cuentas precargadas. El rol JEFE se asigna en la base y se conserva al entrar. Otro perfil sin proveedor real configurado falla explícitamente.

## Persistencia y reinicio

```powershell
docker compose logs --tail=100 backend
docker compose restart
docker compose down
```

Estos comandos conservan el volumen PostgreSQL. Para un reinicio completo y deliberado del entorno local:

```powershell
docker compose down -v
docker compose up --build -d
```

Este último procedimiento elimina todos los datos del proyecto y crea una base vacía con su Génesis. La actualización de roles y eventos conserva la base existente y sus hashes. No es necesario eliminar el volumen para aplicarla.

## Desarrollo fuera de Docker

Requisitos adicionales: JDK 21, Node.js compatible con Vite y npm. Mantener PostgreSQL/PostGIS en Docker:

```powershell
docker compose up -d db
$env:SPRING_PROFILES_ACTIVE='demo'
$env:DATABASE_URL='jdbc:postgresql://localhost:5433/asistencia'
$env:DATABASE_USER='asistencia'
$env:DATABASE_PASSWORD='demo-change-me'
$env:APP_CHAIN_ATTACK_ENABLED='true'
Set-Location backend
.\mvnw.cmd spring-boot:run
```

En otra terminal, desde la raíz:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

Vite hace proxy al backend local. La geolocalización funciona en localhost; para otros dispositivos se necesita HTTPS y `COOKIE_SECURE=true`.

## Carpetas

- `backend/`: API organizada por `models`, `dto`, `services`, `controllers`, `repositories`, `config`, `exceptions` y `utils`.
- `frontend/`: páginas React, mapa, formularios y cliente HTTP.
- `docs/`: [arquitectura](docs/arquitectura.md), [backend](docs/backend.md), [frontend](docs/frontend.md), [base de datos](docs/base-de-datos.md), [geocercas](docs/geocercas.md), [cadena](docs/cadena-de-bloques.md), [API](docs/api.md), [contrato](docs/contrato.md) y [limitaciones](docs/limitaciones.md).

Se incluyen pruebas de roles, autorización y eventos en `backend/src/test/java`. Ejecutar `.\mvnw.cmd test` dentro de `backend/` y `npm.cmd run build` dentro de `frontend/`. Véase [el historial de cambios y validaciones](docs/cambios-roles-revision.md) para la evidencia y las limitaciones.

La cadena es centralizada: los hashes permiten comprobar consistencia interna, pero no demuestran presencia física ni impiden que quien controle toda la base reescriba la evidencia.

## Demostración de integridad y ataque

1. Registrar al menos dos asistencias (o registrar una y confirmar su revisión) para tener movimientos encadenados. La configuración inicial anterior permite crear los registros desde la aplicación.
2. Como ADMIN, abrir **Cadena global** y verificar que aparezca **Cadena global íntegra**. Expandir los bloques muestra ID, fecha, datos, hash anterior y hash actual completos.
3. En **Simular alteración**, seleccionar un movimiento anterior y pulsar **Alterar bloque**. El backend añade `_simulacionAtaque` al payload guardado en PostgreSQL y conserva el hash almacenado y los enlaces. La pantalla vuelve a verificar e identifica el bloque cuyo hash ya no coincide.
4. Pulsar **Verificar de nuevo** para repetir la comprobación. Reiniciar la aplicación conserva tanto la alteración como su respaldo; la inconsistencia sigue siendo detectable.
5. Pulsar **Restaurar contenido original** para recuperar exactamente el payload respaldado y comprobar de nuevo la integridad. La restauración no recalcula hashes ni corrige otras alteraciones.

Mientras una cadena tiene hashes o enlaces corruptos, las nuevas escrituras de asistencia se rechazan y su transacción se revierte. El génesis se conserva como ancla inicial para el primer movimiento. Desactivar la simulación con `APP_CHAIN_ATTACK_ENABLED=false` oculta sus controles y rechaza sus escrituras en la API.

El payload contiene los datos del evento; el hash protege además ID, índice, fecha y hash anterior, dentro de una envoltura JSON canónica. Basta cambiar uno de esos valores sin actualizar el hash para detectar una inconsistencia. Véanse la [explicación del algoritmo](docs/cadena-de-bloques.md), la [auditoría contra la consigna](docs/auditoria-cadena.md) y la [guía para explicar el backend](docs/guia-backend.md). El informe técnico en PDF queda fuera de esta revisión.

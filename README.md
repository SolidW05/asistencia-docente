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

El perfil `demo` acepta códigos de siete dígitos y no valida la contraseña. Los nombres son etiquetas locales como `Docente <código>` y `Administrador <código>`. Los códigos configurados no son cuentas precargadas. El rol JEFE se asigna en la base y se conserva al entrar. Otro perfil sin proveedor real configurado falla explícitamente.

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

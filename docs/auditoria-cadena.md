# Auditoría del encadenamiento criptográfico

Revisión del 9 de noviembre de 2026. Alcance: backend, comentarios didácticos y controles de simulación de ataque en la pantalla de cadena. El génesis se conserva como ancla inicial por indicación del usuario. El informe técnico en PDF de 4–6 páginas se excluyó expresamente de este trabajo.

## Resultado contra la consigna

| Requisito | Resultado y evidencia |
|---|---|
| Hash criptográfico | SHA-256 real mediante `MessageDigest`, UTF-8 y salida hexadecimal de 64 caracteres. Fixture conocido y pruebas de normalización y sensibilidad. |
| Estructura de datos | Los eventos tienen UUID, índice, fecha del servidor, payload, hash anterior y hash propio; además se protegen los identificadores y el tipo de evento. `ChainBlock`, `BlockView` y `schema.sql` describen el almacenamiento. |
| Fórmula de encadenamiento | La envoltura JSON canónica incluye ID, índice, fecha, datos y hash anterior; se evita la concatenación ambigua de texto. Se conserva el formato 2 existente. |
| Agregar al final | Registrar asistencia o confirmar un pendiente agrega un evento. El bloqueo transaccional de la cabecera determina índice y enlace; un fallo revierte toda la escritura. |
| Mostrar registros | ADMIN consulta todos los bloques ordenados, incluidos los hashes completos y el payload expandible. |
| Verificar integridad | Recalcula hashes, comprueba índices/enlaces, génesis y cabecera; también compara snapshots y estados de asistencia. Reporta el primer bloque o comprobación inconsistente. |
| Persistencia | PostgreSQL y volumen Docker conservan los eventos. La prueba integrada reinició el backend y verificó que datos, alteración y respaldo siguieran presentes. |
| Prueba de alteración | ADMIN puede seleccionar un bloque desde el frontend. El backend modifica su payload persistido sin actualizar el hash; la verificación detecta la inconsistencia. La restauración recupera el contenido exacto. |
| Ejecución y código fuente | README con requisitos, Docker, desarrollo local y recorrido de demostración; código organizado por capas. |
| Supuestos, limitaciones y mejoras | Documentados en la guía, la explicación de la cadena y `limitaciones.md`. No hay consenso, minería ni P2P; se proponen firmas, referencias externas y réplicas. |
| Comentarios y comprensión | Comentarios en clases del backend y en los procedimientos de validación, persistencia, hash, concurrencia y ataque. Guía para explicar el flujo y preparar la revisión oral. |
| Informe PDF y sus capturas | Pendiente por decisión del usuario; no se elaboró ni se presenta esta bitácora como sustituto. Hay capturas de QA independientes disponibles. |

Esta revisión acredita comportamientos comprobados; la calificación corresponde al docente y el entregable PDF permanece pendiente.

## Hallazgos corregidos

1. **Faltaba la simulación desde la interfaz.** Se incorporaron los endpoints de estado, ataque y restauración, restringidos a ADMIN, perfil demo, configuración explícita y CSRF. El cambio se realiza en PostgreSQL y no solo en el estado visual de React.
2. **Era posible agregar después de corromper la cadena.** Antes de insertar se verifican los bloques persistidos bajo el bloqueo de cabecera. Un conflicto devuelve `409 / CHAIN_INVALID` y revierte la asistencia de esa transacción.
3. **Faltaba explicación suficiente del backend.** Se añadieron comentarios sobre propósito, datos y decisiones en las capas Java y en los algoritmos principales. El frontend se modificó únicamente para la simulación.
4. **Una impresión previa exponía el payload completo.** Se retiró el `printf` del cálculo del hash. Se conserva el algoritmo y el resto del cambio previo de ese archivo.
5. **La evidencia automatizada de alteraciones era limitada.** Se ampliaron los casos de datos, metadatos, enlaces, cabecera, génesis y JSON, además de ejecutar una prueba HTTP con PostgreSQL/PostGIS y otra con la interfaz real.

## Evidencia ejecutada

- Suite Maven del backend: **39 pruebas, cero fallos, cero errores y cero omitidas**, sobre roles, alcance, revisión, serialización, hash, verificación y restricciones de ataque. Resultado confirmado en los XML de `backend/target/surefire-reports/` y empaquetado final correcto.
- `npm.cmd run build`: TypeScript y empaquetado Vite correctos.
- `python scripts/probar_cadena.py --reiniciar`: recorrido completo correcto en el proyecto Docker independiente `metodologia-cadena-auditoria`, con PostgreSQL/PostGIS y Java 21.
- Prueba integrada: génesis protegido; dos registros consecutivos; enlaces válidos; rechazo de ataque por docente; payload alterado y hash conservado; detección del bloque 1; rechazo de nueva asistencia y rollback; persistencia de ataque y respaldo tras reiniciar; restauración exacta; inserción posterior y revisión. Finalizó con cinco bloques y cadena íntegra.
- Prueba de navegador: **Alterar bloque** mostró «El hash no coincide en el bloque 1»; **Restaurar contenido original** volvió a mostrar cinco bloques verificados y cadena íntegra.

Capturas reales del entorno aislado: [cadena válida](evidencias-cadena/01-cadena-valida.png) y [ataque detectado](evidencias-cadena/02-ataque-detectado.png). Los datos de esas capturas son ficticios. Las pruebas no alteraron la base habitual del proyecto.

## Repetir las comprobaciones

Pruebas de código y compilación, desde la raíz, usando JDK 21 o superior y Node/npm:

```powershell
Set-Location backend
.\mvnw.cmd test package
Set-Location ../frontend
npm.cmd ci
npm.cmd run build
Set-Location ..
```

El wrapper requiere acceso a Maven Central si aún no está descargado. En esta máquina se usó Maven 3.9.14 local y el repositorio `backend/.m2/repository` en modo offline; no se modificó la configuración global de Maven.

Para la prueba integrada se requieren Docker, Python 3 y las imágenes locales `metodologia-geocercas-backend:latest`, `metodologia-geocercas-frontend:latest` y `postgis/postgis:16-3.5`. Pueden obtenerse al construir el proyecto habitual; la configuración de auditoría usa esas imágenes como entorno de ejecución, montando el JAR y `frontend/dist` recién compilados:

```powershell
docker compose build
docker compose -f scripts/compose.auditoria.yaml up -d --wait
python scripts/probar_cadena.py --reiniciar
```

La prueba exige una base aislada con solo el génesis y se detiene si encuentra movimientos previos. Para repetirla desde cero, este comando elimina exclusivamente el entorno y volumen de auditoría:

```powershell
docker compose -f scripts/compose.auditoria.yaml down -v
docker compose -f scripts/compose.auditoria.yaml up -d --wait
python scripts/probar_cadena.py --reiniciar
```

Para detenerlo conservando su evidencia, usar `docker compose -f scripts/compose.auditoria.yaml down`. El entorno habitual utiliza otro proyecto, volumen y puertos; no ejecutar `down -v` sobre él para aplicar esta revisión.

## Límites de lo comprobado

No se ejecutó una prueba de carga ni de concurrencia entre varios procesos de aplicación. La serialización se implementa con bloqueos de PostgreSQL y se explica en el código, pero no se atribuye una validación de carga que no se realizó. Tampoco se demuestra presencia física ni identidad institucional: la autenticación es de demostración y la ubicación puede ser manipulada. Una reescritura completa de datos, hashes y cabecera exige una referencia externa confiable para poder detectarse.

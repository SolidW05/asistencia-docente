# Cambios de roles y revisión de asistencias

Fecha de trabajo: 25 de octubre de 2026. Esta entrega incorpora la jerarquía ADMIN/JEFE/TEACHER y el historial de movimientos de asistencia. Conserva el estilo de documentación técnica del proyecto: comportamiento, responsables, datos y verificaciones concretas.

## Resultado funcional

| Acción | ADMIN | JEFE | TEACHER |
|---|---|---|---|
| Dar de alta jefes por código | Sí | No | No |
| Configurar campus y geocercas | Sí | No | No |
| Crear clases y asignar docente por código | Sí | Sí | No |
| Consultar y editar clases | Todas | Las que creó | Sus asignaciones, solo consulta |
| Consultar asistencias para revisión | Todas | Las de sus clases | Sus propios registros |
| Confirmar un pendiente con comentario | Sí | Solo de sus clases | No |
| Consultar cambios del registro propio | Sí, si es propietario | Sí, si es propietario | Sí |
| Inspeccionar todos los bloques y verificar | Sí | No | No |

ADMIN crea/promueve JEFE sin exigir un login previo. Si una clase se asigna a un código inexistente, el docente se crea automáticamente. El rol JEFE se conserva al entrar; ADMIN depende de la configuración DEMO_ADMIN_CODES. Las clases anteriores sin creador quedan bajo ADMIN.

Una asistencia pendiente genera su bloque de registro. Confirmarla genera otro bloque, ligado a la misma asistencia, con responsable, fecha, comentario y estados anterior/nuevo. La consulta docente presenta ese cambio sin hashes ni payloads. Repetir la confirmación no genera otro bloque.

## Organización del trabajo

Se utilizaron seis agentes gpt-6-luna con razonamiento max, distribuidos en dos grupos de implementación, auditoría y pruebas. El agente principal coordinó contratos, resolvió observaciones y realizó integración, frontend y documentación. Todos trabajaron en el mismo árbol; no se crearon commits ni se publicaron cambios.

| Área | Implementación | Auditoría | Pruebas |
|---|---|---|---|
| Roles y clases | roles_implementacion | roles_auditoria | roles_pruebas |
| Revisión y cadena | cadena_implementacion | cadena_auditoria | cadena_pruebas |

## Historial de decisiones y correcciones

1. Se separó al creador de clase del docente asignado. created_by determina el alcance JEFE y no cambia al editar.
2. Se habilitó el alta por código con conservación del UUID existente. El login respeta JEFE persistido; un código retirado de DEMO_ADMIN_CODES deja de obtener ADMIN al entrar.
3. Se añadió autorización basada en el rol guardado para no depender de autoridades antiguas de una sesión. Las escrituras de campus/geocercas siguen reservadas a ADMIN.
4. Se incorporó actualización compatible de base: roles, creador e índices de clase y soporte para múltiples eventos de una asistencia. No se borran registros ni se recalculan bloques anteriores.
5. Se cambió la verificación: compara la evidencia inicial, reconstruye las transiciones y coteja el estado final. Contar bloques ya no equivale a contar asistencias.
6. La auditoría encontró una lectura gestionada antes del bloqueo que podía quedar obsoleta. Se reemplazó por una consulta escalar del propietario y se carga la asistencia después de bloquear al docente.
7. Se reforzó la validación del evento: IDs, fecha, autor existente y creador de clase cuando el autor actuó como JEFE. El rol/nombre histórico no se compara contra su valor actual, que puede cambiar legítimamente.
8. Se agregó bloqueo por código al login y altas/promociones para serializar cambios de rol de cuentas existentes. Dos primeras altas simultáneas de un código ausente se resuelven por la restricción única con 409; se puede reintentar.
9. No se revocó la consulta o registro propio por API a una cuenta promovida a JEFE: las ocurrencias y registros siguen vinculados a su identidad. Esto no permite leer ni registrar por otro docente. La interfaz de JEFE abre su administración.
10. Se separaron las pantallas: Mis asistencias para docente; Asistencias para revisión; Jefes y Cadena exclusivas de ADMIN. Se conservaron mapa, clases, sesión y CSRF.

## Archivos y contrato

- Roles, identidad y altas: Role, TeacherRepository, TeacherService, AuthService, RoleAccessService, AuthController y AdminTeacherController.
- Clases y alcance: TeachingClass, ClassScopeService, ClassService, TeachingClassRepository y DTO de creación/edición.
- Revisión: AttendanceService, AttendanceRepository, AdminAttendanceController y DTO de revisión, consulta administrativa e historial.
- Evidencia: ChainAppendService, ChainService y ChainController.
- Datos: DatabaseInitializationConfig y schema.sql.
- Interfaz: App, AppShell, api/types, AdminPage, HeadsPage, ReviewPage, HistoryPage, ChainPage y estilos compartidos.
- Documentación: README y documentos de API, contrato, arquitectura, backend, frontend, base de datos, cadena, requisitos y limitaciones.

El detalle HTTP se mantiene en [contrato.md](contrato.md) y el formato criptográfico en [cadena-de-bloques.md](cadena-de-bloques.md).

## Resultado de auditoría

Los dos auditores cerraron sin bloqueos pendientes dentro del alcance solicitado. Se resolvieron la pérdida de JEFE al login, el alcance por creador, el bloqueo de cadena para otros roles, la lectura anterior al lock y la comparación incorrecta entre estado inicial y estado actual. La actualización del esquema fue revisada en código, pero no aplicada contra PostgreSQL en este entorno.

## Comprobaciones

La ejecución final terminó con BUILD SUCCESS: 22 pruebas, 0 fallos, 0 errores y 0 omitidas. Se usaron Java 25, Maven 3.9.14 y compilación Java target 21. Mockito y MockMvc no se conectan a la base del proyecto.

| Suite | Pruebas | Resultado |
|---|---:|---|
| AdminTeacherControllerSecurityTest | 2 | Pasaron |
| AuthControllerRolePersistenceTest | 1 | Pasó |
| ChainAndReviewControllerSecurityTest | 3 | Pasaron |
| AttendanceServiceReviewTest | 4 | Pasaron |
| ChainServiceVerificationTest | 1 | Pasó |
| ClassScopeServiceTest | 3 | Pasaron |
| ClassServiceRoleScopeTest | 4 | Pasaron |
| TeacherRoleFlowsTest | 4 | Pasaron |

El wrapper no pudo descargar su distribución por DNS restringido. Se utilizó Maven 3.9.14 ya disponible en la caché local y el repositorio de dependencias de backend/.m2/repository. Desde backend se ejecutó:

```powershell
mvn.cmd --no-transfer-progress -o '-Dmaven.repo.local=C:\Users\PC\Desktop\Metodologia\backend\.m2\repository' test
```

El ejecutable usado está en la distribución local `C:/Users/PC/.m2/wrapper/dists/apache-maven-3.9.14/ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d/bin/mvn.cmd`. En un entorno con el wrapper disponible puede usarse `.\mvnw.cmd test`.

La primera corrida se bloqueó al adjuntar Mockito dinámicamente a Java 25. El agente principal confirmó la causa y ejecutó los tests con Mockito precargado. Después se dejó la carga mediante javaagent en Surefire/pom.xml y se repitió la ejecución normal con JVM de pruebas separada: las 22 pruebas pasaron de nuevo. Los reportes locales quedan en backend/target/surefire-reports. No se cambió la configuración JVM de producción.

La compilación del frontend `npm.cmd run build --prefix frontend` pasó con TypeScript y Vite. También pasó `git diff --check` y la comprobación de enlaces internos de la documentación. No se hizo validación visual en navegador.

Docker no permitió acceder al daemon en este entorno. No se ejecutó el arranque con PostgreSQL/PostGIS, la actualización de una base existente ni una prueba concurrente entre procesos. La corrección de locks se revisó en código; las pruebas de servicio comprueban la confirmación y el reintento, pero requieren comprobación concurrente de integración con PostgreSQL.

## Comprobación de integración pendiente

Estos pasos describen una comprobación adicional en una base de prueba independiente; no se ejecutaron contra los datos del proyecto:

1. Arrancar la versión anterior con una asistencia pendiente; conservar su UUID, payload y hash inicial.
2. Arrancar la versión actual sobre esa base; comprobar que agrega columnas/restricciones y conserva el génesis y el bloque anterior.
3. Con ADMIN crear dos jefes y con cada uno crear una clase. Probar que cada JEFE consulta/revisa únicamente la suya y ADMIN ve ambas.
4. Asignar una clase por código inexistente; entrar como ese docente y comprobar que recibe su clase. Dar de alta otro código como JEFE y verificar su acceso sin login previo.
5. Confirmar el pendiente y repetir la solicitud: debe existir un solo evento nuevo, estado CONFIRMED y snapshot inicial sin cambios.
6. Hacer dos confirmaciones simultáneas del mismo pendiente: debe haber una sola transición y un solo bloque adicional.
7. Verificar la cadena con ADMIN; intentar consulta y verificación como JEFE/TEACHER y comprobar 403. Como docente, leer únicamente el historial propio.
8. Reiniciar y comprobar que no se repite el génesis ni se pierden roles, revisiones o asignaciones.

Para actualizar el entorno existente, basta reconstruir y arrancar los servicios; no usar `docker compose down -v`. Ese comando elimina el volumen y no forma parte de esta actualización.

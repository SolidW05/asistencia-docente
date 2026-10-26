# Frontend para un equipo Java

`frontend/` contiene React, Vite y TypeScript. `App.tsx` define rutas y sesión; `api.ts` concentra HTTP y CSRF; `types.ts` describe los DTO. Los componentes de `components/` conservan el mapa, avisos y controles compartidos. El servidor decide los permisos y la interfaz presenta las acciones correspondientes a cada rol.

## Pantallas por rol

| Rol | Pantallas | Alcance |
|---|---|---|
| TEACHER | Registro y Mis asistencias | Sus clases, registros y cambios de estado |
| JEFE | Administración y Asistencias | Clases creadas por él y asistencias de esas clases |
| ADMIN | Administración, Asistencias, Jefes y Cadena | Configuración y consulta global |

`AdminPage` recibe la identidad. ADMIN configura campus, geocercas y clases; JEFE abre directamente clases y horarios. El formulario permite elegir un docente existente o escribir su código de siete dígitos. Si no existe, el servidor lo crea al guardar la clase.

`HeadsPage` permite a ADMIN dar acceso como JEFE por código. No necesita que esa persona haya iniciado sesión antes. Al entrar con ese código, su rol persistido abre el espacio administrativo.

`ReviewPage` consulta las asistencias autorizadas y permite filtrar pendientes, confirmadas o todas. Para confirmar un pendiente solicita un comentario, muestra motivo y justificación originales y espera la respuesta del servidor. El botón queda deshabilitado mientras se guarda. Un reintento no duplica la revisión.

`HistoryPage` muestra al docente el estado actual y, al solicitarlo, los cambios de cada registro con fecha, responsable y comentario. No llama a endpoints de cadena ni muestra hashes, bloques o payloads. Actualizar asistencias descarta el historial local para que la siguiente consulta vuelva a leer los cambios.

`ChainPage`, exclusiva de ADMIN, presenta la cabecera, génesis, todos los eventos y sus datos completos. Permite verificar la cadena global. Una cantidad de bloques no equivale a cantidad de asistencias: una revisión también agrega un bloque.

## Registro y errores

El docente elige una ocurrencia, solicita ubicación puntualmente, consulta la comprobación y registra explícitamente. No se usa seguimiento continuo. Las excepciones requieren motivo y justificación. La falta de configuración no confirma la asistencia.

Si falla el envío, se conservan los datos y la clave UUID para consultar el resultado o reintentar el mismo cuerpo. Solo una respuesta confirmada permite mostrar que se guardó. Los errores de mapa, ubicación y API se presentan por separado.

La aplicación consulta `/api/auth/me` al cargar y redirige según TEACHER/JEFE/ADMIN. La autorización visual acompaña las restricciones del backend. Vite y Nginx usan proxy `/api` del mismo origen; el cliente conserva cookies y renueva CSRF después de login/logout. La autenticación sigue siendo simulada.

Leaflet y Geoman mantienen la edición de polígonos para ADMIN. La geolocalización requiere localhost o HTTPS en otros dispositivos. La compilación se comprueba con `npm.cmd run build` dentro de `frontend/`.

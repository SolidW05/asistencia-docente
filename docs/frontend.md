# Frontend para un equipo Java

`frontend/` contiene una aplicación React con Vite y TypeScript. `src/main.tsx` monta la aplicación; `App.tsx` configura navegación y estado de sesión; `pages/` contiene login, registro, historial/cadena y administración; `components/` reúne mapa, avisos y controles compartidos. `api.ts` centraliza HTTP y CSRF, `types.ts` describe los DTO y `format.ts` traduce estados/fechas para la interfaz.

Un componente funcional es una función que produce elementos de interfaz. Las **props** son parámetros enviados por el componente padre, por ejemplo la identidad o una lista de geocercas. `useState` conserva estado local y vuelve a dibujar la vista al cambiarlo; `useEffect` sincroniza con operaciones externas y debe limpiar sus efectos; `useMemo` evita recomputar valores derivados innecesarios; `useCallback` mantiene callbacks estables cuando otro componente los utiliza como dependencia. No hace falta introducir una capa compleja de gestión de estado para este MVP.

React Router decide qué página se muestra según la URL. La aplicación consulta `/api/auth/me` al cargar, protege pantallas y limita la navegación administrativa según el rol. El servidor vuelve a validar los permisos. El login informa que la autenticación es simulada y borra el campo contraseña al enviar. No hay cuentas, campus ni clases precargados; el inicio de sesión manual crea únicamente la identidad local. Un administrador configura después campus, módulos y clases para docentes existentes.

React Leaflet integra Leaflet con los componentes de React. La [documentación oficial](https://react-leaflet.js.org/docs/start-introduction/) explica que algunas propiedades de las capas se fijan al crearlas; cambiar datos de una geocerca puede requerir recrear la capa. Geoman permite dibujar/editar polígonos. El frontend convierte el dibujo a GeoJSON y el servidor comprueba validez y contención.

## Recorrido docente

1. Seleccionar una clase con ocurrencia para la fecha local.
2. Pulsar “Actualizar ubicación” y otorgar consentimiento al navegador.
3. Consultar la comprobación en el servidor; leer por separado espacio y horario.
4. Registrar explícitamente o elegir el reporte de incidencia y aportar motivo/texto.
5. Esperar confirmación HTTP antes de mostrar que la asistencia se guardó.
6. Abrir historial, inspeccionar payloads propios y verificar integridad de la cadena global.

El historial distingue total global de bloques y asistencias visibles de la sesión. Muestra el génesis común, su frase y hash, y explica que los saltos de índice corresponden a registros de otros docentes. El génesis tiene docente, asistencia y fecha null; se identifica como bloque inicial. La verificación se realiza sobre toda la cadena en el servidor sin entregar payloads ajenos al navegador.

La Geolocation API usa una consulta puntual, sin `watchPosition`. Se informa precisión y antigüedad. Puede consultarse el mapa sin clase aplicable. La falta de geocercas o asignaciones genera un estado comprensible, nunca aprobación automática.

Ante un fallo de envío, el formulario conserva los datos y la clave UUID. Si se desconoce el resultado, permite consultarlo antes de reintentar el mismo cuerpo con la misma clave. Un cuerpo editado constituye otro intento; nunca se representa un envío offline como confirmado.

El error de teselas del proveedor cartográfico se muestra por separado. Ubicación, polígonos y registro pueden funcionar aunque falten imágenes del mapa. Los controles y paneles se adaptan a celular y escritorio; las etiquetas y estados usan español.

Vite hace proxy de `/api` al backend local en desarrollo. Nginx hace el mismo proxy al servicio `backend:8080` dentro de Docker; el navegador siempre solicita la dirección pública del frontend.

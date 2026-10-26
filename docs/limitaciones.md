# Limitaciones del prototipo

- La autenticación está simulada. Cualquier código de siete dígitos entra en el perfil demo; la contraseña se ignora. Los administradores provienen exclusivamente de la lista configurada. No exponer esta demostración como un sistema institucional.
- No hay identidades, límites ni horarios precargados. Los nombres genéricos generados tras el login no acreditan identidad institucional. Los códigos sin asignaciones deben solicitar configuración al administrador; no se aprueba asistencia sin ella.
- La geolocalización del navegador depende del dispositivo, permisos y entorno. Puede ser imprecisa o manipulada. No identifica pisos ni salones, ni prueba infaliblemente presencia física.
- El radio de precisión que informa el navegador es una estimación, no una garantía matemática. Tratar el contacto con un límite como incertidumbre reduce confirmaciones injustificadas, pero no elimina errores.
- Los registros con excepción espacial, temporal o incidencia requieren justificación y permanecen pendientes. La declaración de una clase virtual no constituye aprobación. La consola permite confirmar pendientes con comentario; no incluye rechazo, reapertura ni apelación. La revisión agrega un evento y conserva el original.
- La cadena es un encadenamiento criptográfico centralizado, sin consenso ni red distribuida. Un operador con control total de la base puede reescribir registros, hashes y cabecera. La eliminación del final puede escapar a la detección si no existe una referencia externa confiable. Los hashes tampoco prueban que la ubicación reportada sea verdadera.
- No hay seguimiento continuo, aprobación offline, dependencia de Wi-Fi institucional, QR probatorio, criptomonedas ni aplicación nativa. Una ampliación offline podría conservar una captura local pendiente y sincronizarla después; debe diferenciarla siempre de una asistencia confirmada por el servidor.
- Geolocalización requiere contexto seguro: localhost en desarrollo, HTTPS para otros dispositivos. No desactivar protecciones del navegador. Configurar cookies seguras al publicar mediante HTTPS.
- Las imágenes del mapa dependen de OpenStreetMap y conectividad; el mensaje de error de teselas no cambia la clasificación ni el estado de registro.
- El volumen de PostgreSQL conserva los datos entre reinicios. Una copia de seguridad y una política de retención quedan fuera de esta demostración y son necesarias antes de cualquier uso real.
- El inicializador crea una base nueva o aplica la actualización compatible de roles, creador de clase y eventos. No importa estructuras de otras versiones. El reinicio completo del volumen elimina los datos guardados.
- Las sesiones HTTP se conservan en memoria del backend. Reiniciarlo requiere iniciar sesión de nuevo; las asistencias, geocercas, asignaciones y cadenas permanecen en PostgreSQL.

- Las clases anteriores sin creador no se asignan automáticamente a un JEFE: ADMIN conserva su acceso. No se infiere al responsable por el docente asignado.
- Las pruebas con dobles no sustituyen una prueba PostgreSQL/PostGIS ni una prueba concurrente entre procesos. La bitácora distingue lo ejecutado de lo pendiente.

package mx.udg.cut.asistencia.services.auth;

import mx.udg.cut.asistencia.models.auth.DemoIdentity;

/**
 * Define el límite entre autenticación y el resto de la aplicación.
 * AuthService solicita una identidad a partir del código recibido.
 * La implementación activa determina el origen de la identidad y su rol inicial.
 * El contrato no recibe una contraseña.
 */
public interface IdentityProvider {
    /**
     * Recibe el código usado para identificar al usuario.
     * Devuelve la identidad que AuthService reconciliará con la fila persistida.
     */
    DemoIdentity authenticate(String code);
}

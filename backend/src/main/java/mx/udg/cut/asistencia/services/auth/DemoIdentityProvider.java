package mx.udg.cut.asistencia.services.auth;

import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.models.teacher.Role;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Implementa el proveedor de identidad exclusivo del perfil demo.
 * Deriva un UUID estable a partir del código y asigna ADMIN según la configuración.
 * Los demás códigos reciben identidad TEACHER con nombre simulado.
 * No comprueba contraseñas ni consulta una institución externa.
 */
@Component
@Profile("demo")
public class DemoIdentityProvider implements IdentityProvider {
    private final Set<String> administratorCodes;

    /**
     * Separa la lista configurada de códigos administradores y descarta entradas vacías.
     * Rechaza una lista vacía o códigos que no tengan exactamente siete dígitos.
     */
    public DemoIdentityProvider(@Value("${app.demo.admin-codes}") String administratorCodes) {
        this.administratorCodes =
                Arrays.stream(administratorCodes.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        if (this.administratorCodes.isEmpty()
                || this.administratorCodes.stream().anyMatch(c -> !c.matches("[0-9]{7}")))
            throw new IllegalStateException(
                    "DEMO_ADMIN_CODES debe contener códigos explícitos de siete dígitos.");
    }

    /**
     * Deriva UUID determinista del código y construye nombre y rol simulados.
     * Asigna ADMIN a los códigos configurados y TEACHER al resto; no valida contraseña.
     */
    @Override
    public DemoIdentity authenticate(String code) {
        boolean admin = administratorCodes.contains(code);
        Role role = admin ? Role.ADMIN : Role.TEACHER;
        String name = (admin ? "Administrador " : "Docente ") + code;
        UUID id =
                UUID.nameUUIDFromBytes(
                        ("asistencia-demo:" + code).getBytes(StandardCharsets.UTF_8));
        return new DemoIdentity(id, code, name, role);
    }
}

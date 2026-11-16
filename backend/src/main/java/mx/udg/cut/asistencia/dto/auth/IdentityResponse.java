package mx.udg.cut.asistencia.dto.auth;

/** Expone los datos de identidad y rol de la sesión autenticada. */
public record IdentityResponse(
        java.util.UUID id,
        String code,
        String name,
        mx.udg.cut.asistencia.models.teacher.Role role
) {
}

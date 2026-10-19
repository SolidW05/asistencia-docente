package mx.udg.cut.asistencia.dto.auth;

public record IdentityResponse(
        java.util.UUID id,
        String code,
        String name,
        mx.udg.cut.asistencia.models.teacher.Role role
) {
}

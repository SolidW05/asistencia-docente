package mx.udg.cut.asistencia.dto.attendance;

import mx.udg.cut.asistencia.models.attendance.AttendanceStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Recibe la decisión y el comentario de una revisión administrativa. */
public record AttendanceReviewRequest(
        @NotNull AttendanceStatus status,
        @NotBlank @Size(max = 2000) String comment
) {
}
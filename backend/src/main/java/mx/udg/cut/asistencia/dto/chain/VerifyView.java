package mx.udg.cut.asistencia.dto.chain;

/** Comunica si la cadena es válida, cuántos bloques se revisaron y el primer error. */
public record VerifyView(
        boolean valid,
        int checkedBlocks,
        String firstError
) {
}

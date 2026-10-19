package mx.udg.cut.asistencia.dto.chain;

public record VerifyView(
        boolean valid,
        int checkedBlocks,
        String firstError
) {
}

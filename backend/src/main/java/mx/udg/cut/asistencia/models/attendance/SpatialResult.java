package mx.udg.cut.asistencia.models.attendance;

/** Resultados posibles de la evaluación de ubicación. */
public enum SpatialResult {
    ALLOWED_MODULE,
    OTHER_CAMPUS_SPACE,
    OUTSIDE_CAMPUS,
    NO_LOCATION,
    STALE,
    INACCURATE,
    UNCERTAIN,
    UNCONFIGURED
}

package mx.udg.cut.asistencia.services.auth;

import mx.udg.cut.asistencia.models.auth.DemoIdentity;

public interface IdentityProvider {
    DemoIdentity authenticate(String code);
}

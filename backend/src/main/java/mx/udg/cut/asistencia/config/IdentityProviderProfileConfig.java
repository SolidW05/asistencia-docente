package mx.udg.cut.asistencia.config;

import mx.udg.cut.asistencia.services.auth.IdentityProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!demo")
public class IdentityProviderProfileConfig {
    @Bean
    @ConditionalOnMissingBean(IdentityProvider.class)
    IdentityProvider missingIdentityProvider() {
        throw new IllegalStateException(
                "El perfil activo no es demo y no hay un IdentityProvider institucional configurado.");
    }
}

package mx.udg.cut.asistencia.controllers;

import mx.udg.cut.asistencia.dto.auth.IdentityResponse;
import mx.udg.cut.asistencia.dto.auth.LoginRequest;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.services.auth.AuthService;
import mx.udg.cut.asistencia.services.auth.RoleAccessService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestiona los extremos HTTP de sesión, identidad y token CSRF.
 * El proveedor de identidad se invoca mediante AuthService y devuelve el rol persistido.
 * El inicio renueva la sesión y guarda el contexto de seguridad para solicitudes posteriores.
 * La contraseña opcional del DTO no se entrega al servicio ni se valida en este perfil.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final RoleAccessService roleAccess;
    private final SecurityContextRepository contextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    /**
     * Recibe servicios de autenticación y rol junto con componentes de sesión de Spring.
     * Estas dependencias permiten guardar y recuperar el contexto de seguridad.
     */
    public AuthController(
            AuthService service,
            RoleAccessService roleAccess,
            SecurityContextRepository contextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.service = service;
        this.roleAccess = roleAccess;
        this.contextRepository = contextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    /**
     * Devuelve un mapa con el valor del token CSRF y el nombre de la cabecera.
     * El cliente usa ambos en solicitudes de escritura protegidas por Spring Security.
     * Esta lectura no autentica por sí sola al solicitante.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    /**
     * Recibe un código validado de siete dígitos y una contraseña opcional ignorada.
     * El proveedor demo construye identidad simulada; no autentica contra una institución.
     * Con el rol persistido construye la autenticación y aplica la estrategia de sesión.
     * Guarda el contexto de seguridad y devuelve identificador, código, nombre y rol.
     */
    @PostMapping("/login")
    public IdentityResponse login(
            @Valid
            @RequestBody
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse response) {
        DemoIdentity identity = service.login(request.code());
        var authentication =
                new UsernamePasswordAuthenticationToken(
                        identity,
                        null,
                        java.util.List.of(
                                new SimpleGrantedAuthority("ROLE_" + identity.role().name())));
        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, servletRequest, response);
        return response(identity);
    }

    /**
     * Recibe la identidad autenticada desde el principal de sesión.
     * Reconsulta la fila para reflejar los datos y rol vigentes cuando existe.
     * Devuelve el DTO público de identidad sin campos de contraseña.
     */
    @GetMapping("/me")
    public IdentityResponse me(@AuthenticationPrincipal DemoIdentity identity) {
        return response(roleAccess.currentIdentity(identity));
    }

    /**
     * Limpia el contexto de seguridad del hilo y busca la sesión sin crear otra.
     * Si existe, la invalida para cerrar el acceso asociado.
     * Devuelve una respuesta sin contenido también cuando la sesión ya no existía.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null)
            session.invalidate();
        return ResponseEntity.noContent().build();
    }

    /**
     * Convierte la identidad interna en el DTO público de sesión.
     * Expone identificador, código, nombre y rol, sin datos adicionales.
     */
    private IdentityResponse response(DemoIdentity identity) {
        return new IdentityResponse(
                identity.id(), identity.code(), identity.name(), identity.role());
    }
}
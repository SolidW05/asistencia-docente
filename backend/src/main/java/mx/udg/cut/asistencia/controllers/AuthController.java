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

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final RoleAccessService roleAccess;
    private final SecurityContextRepository contextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

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

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

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

    @GetMapping("/me")
    public IdentityResponse me(@AuthenticationPrincipal DemoIdentity identity) {
        return response(roleAccess.currentIdentity(identity));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null)
            session.invalidate();
        return ResponseEntity.noContent().build();
    }

    private IdentityResponse response(DemoIdentity identity) {
        return new IdentityResponse(
                identity.id(), identity.code(), identity.name(), identity.role());
    }
}
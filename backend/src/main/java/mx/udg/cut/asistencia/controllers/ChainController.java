package mx.udg.cut.asistencia.controllers;

import mx.udg.cut.asistencia.dto.chain.ChainView;
import mx.udg.cut.asistencia.dto.chain.VerifyView;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.services.chain.ChainService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chain")
public class ChainController {
    private final ChainService chain;

    public ChainController(ChainService chain) {
        this.chain = chain;
    }

    @GetMapping
    public ChainView chain(@AuthenticationPrincipal DemoIdentity identity) {
        return chain.chain(identity.id());
    }

    @GetMapping("/verify")
    public VerifyView verify(@AuthenticationPrincipal DemoIdentity identity) {
        return chain.verify(identity.id());
    }
}

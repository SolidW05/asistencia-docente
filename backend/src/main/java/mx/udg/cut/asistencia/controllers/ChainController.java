package mx.udg.cut.asistencia.controllers;
import mx.udg.cut.asistencia.dto.chain.ChainView;
import mx.udg.cut.asistencia.dto.chain.VerifyView;
import mx.udg.cut.asistencia.models.auth.DemoIdentity;
import mx.udg.cut.asistencia.services.chain.ChainService;
import mx.udg.cut.asistencia.services.chain.ChainAttackService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * Restringe las rutas de cadena al rol ADMIN consultado por RoleAccessService.
 * Delegan la lectura y verificación a ChainService y la simulación a ChainAttackService.
 * El ataque requiere habilitación adicional y la restauración usa respaldo del servidor.
 * La respuesta de verificación comunica el primer fallo detectado.
 */
@RestController
@RequestMapping("/api/chain")
@PreAuthorize("@roleAccess.hasRole(authentication, 'ADMIN')")
public class ChainController {
    private final ChainService chain;
    private final ChainAttackService attack;
    /**
     * Recibe el servicio de lectura y verificación y el de simulación reversible.
     * Ambos se exponen bajo el control de rol ADMIN de la clase.
     */
    public ChainController(ChainService chain, ChainAttackService attack) {
        this.chain = chain;
        this.attack = attack;
    }
    /**
     * Recibe la identidad administrativa de la sesión y solicita la cadena global.
     * Devuelve la cabecera y todos los bloques ordenados por índice.
     * El UUID del solicitante es metadato y no filtra los bloques por docente.
     */
    @GetMapping
    public ChainView chain(@AuthenticationPrincipal DemoIdentity identity) {
        return chain.chain(identity.id());
    }
    /**
     * Solicita la verificación de todos los bloques y registros relacionados.
     * Devuelve validez, número revisado y primer error detectado, si lo hay.
     * La consulta no modifica ni repara el contenido persistido.
     */
    @GetMapping("/verify")
    public VerifyView verify(@AuthenticationPrincipal DemoIdentity identity) {
        return chain.verify(identity.id());
    }
    /**
     * Devuelve si la función de ataque está habilitada y el UUID del bloque pendiente.
     * La función exige perfil demo y bandera explícita además del rol ADMIN.
     * Esta consulta no modifica la cadena ni el respaldo.
     */
    @GetMapping("/attack")
    public ChainAttackService.AttackStatus attackStatus() { return attack.status(); }
    /**
     * Recibe el UUID de un bloque de asistencia y exige ADMIN y protección CSRF.
     * La función solo opera con perfil demo y bandera explícita habilitada.
     * Respalda y altera el payload persistido sin modificar hashes ni cabecera.
     * Devuelve el estado de la simulación y el identificador afectado.
     */
    @PostMapping("/attack/{blockId}")
    public ChainAttackService.AttackStatus attack(@PathVariable UUID blockId) { return attack.attack(blockId); }
    /**
     * Solicita restaurar el payload original desde el respaldo persistido del servidor.
     * Exige ADMIN, CSRF y simulación habilitada; no acepta contenido del navegador.
     * Si el bloque cambió después del ataque se devuelve conflicto sin sobrescribirlo.
     */
    @PostMapping("/attack/restore")
    public ChainAttackService.AttackStatus restore() { return attack.restore(); }
}

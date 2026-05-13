package com.notificacao_api.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.common.TipoGlobal;
import com.notificacao_api.dto.LoginRequestDTO;
import com.notificacao_api.dto.LoginResponseDTO;
import com.notificacao_api.dto.MeResponseDTO;
import com.notificacao_api.dto.OrganizacaoLoginDTO;
import com.notificacao_api.dto.SelecionarOrganizacaoResponseDTO;
import com.notificacao_api.model.Usuario;
import com.notificacao_api.model.UsuarioOrganizacao;
import com.notificacao_api.repository.UsuarioOrganizacaoRepository;
import com.notificacao_api.repository.UsuarioRepository;
import com.notificacao_api.security.JwtAuthentication;
import com.notificacao_api.security.JwtService;

@Service
public class AuthService {

    private static final String CREDENCIAIS_INVALIDAS = "CPF ou senha invalidos";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioOrganizacaoRepository usuarioOrganizacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TenantContextService tenantContextService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            UsuarioOrganizacaoRepository usuarioOrganizacaoRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            TenantContextService tenantContextService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioOrganizacaoRepository = usuarioOrganizacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tenantContextService = tenantContextService;
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByNuCpf(request.nuCpf())
                .filter(Usuario::getFlAtivo)
                .filter(u -> passwordEncoder.matches(request.dsSenha(), u.getDsSenha()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, CREDENCIAIS_INVALIDAS));

        String token = jwtService.gerarTokenSemTenant(usuario.getIdUsuario(), usuario.getTpGlobal().name());

        if (usuario.getTpGlobal() == TipoGlobal.SUPER_ADMIN) {
            return new LoginResponseDTO(token, "SUPER_ADMIN", false, List.of());
        }

        List<OrganizacaoLoginDTO> organizacoes = usuarioOrganizacaoRepository
                .findByUsuarioIdUsuarioAndFlAtivoTrueAndOrganizacaoFlAtivoTrue(usuario.getIdUsuario())
                .stream()
                .map(vinculo -> new OrganizacaoLoginDTO(
                        vinculo.getOrganizacao().getIdOrganizacao(),
                        vinculo.getOrganizacao().getNmOrganizacao(),
                        vinculo.getDsRole()))
                .toList();

        return new LoginResponseDTO(token, "DEFAULT", true, organizacoes);
    }

    @Transactional(readOnly = true)
    public SelecionarOrganizacaoResponseDTO selecionarOrganizacao(Long idOrganizacao) {
        JwtAuthentication atual = tenantContextService.atual();
        if (!"DEFAULT".equals(atual.getTipoGlobal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SUPER_ADMIN nao seleciona organizacao");
        }

        UsuarioOrganizacao vinculo = usuarioOrganizacaoRepository
                .findByUsuarioIdUsuarioAndOrganizacaoIdOrganizacaoAndFlAtivoTrueAndOrganizacaoFlAtivoTrue(
                        atual.getIdUsuario(),
                        idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario nao possui vinculo ativo com a organizacao"));

        String token = jwtService.gerarTokenComTenant(
                atual.getIdUsuario(),
                idOrganizacao,
                vinculo.getDsRole());

        return new SelecionarOrganizacaoResponseDTO(token, idOrganizacao, vinculo.getDsRole());
    }

    @Transactional(readOnly = true)
    public MeResponseDTO me() {
        JwtAuthentication atual = tenantContextService.atual();
        Usuario usuario = usuarioRepository.findById(atual.getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Usuario autenticado nao encontrado"));

        return new MeResponseDTO(
                atual.getIdUsuario(),
                atual.getTipoGlobal(),
                atual.getIdOrganizacao(),
                atual.getRole(),
                usuario.getNmUsuario(),
                usuario.getNmEmail());
    }
}

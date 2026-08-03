package com.notificacao_api.service;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notificacao_api.common.TipoGlobal;
import com.notificacao_api.dto.admin.CriarOrganizacaoRequestDTO;
import com.notificacao_api.dto.admin.CriarUsuarioOrganizacaoRequestDTO;
import com.notificacao_api.dto.admin.OrganizacaoResponseDTO;
import com.notificacao_api.dto.admin.UsuarioOrganizacaoResponseDTO;
import com.notificacao_api.model.Organizacao;
import com.notificacao_api.model.Usuario;
import com.notificacao_api.model.UsuarioOrganizacao;
import com.notificacao_api.repository.OrganizacaoRepository;
import com.notificacao_api.repository.UsuarioOrganizacaoRepository;
import com.notificacao_api.repository.UsuarioRepository;

@Service
public class AdminService {

    private final OrganizacaoRepository organizacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioOrganizacaoRepository usuarioOrganizacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizacaoConfiguracaoService organizacaoConfiguracaoService;
    private final PlanoLimiteService planoLimiteService;
    private final AdminOrganizacaoRemocaoService adminOrganizacaoRemocaoService;

    public AdminService(
            OrganizacaoRepository organizacaoRepository,
            UsuarioRepository usuarioRepository,
            UsuarioOrganizacaoRepository usuarioOrganizacaoRepository,
            PasswordEncoder passwordEncoder,
            OrganizacaoConfiguracaoService organizacaoConfiguracaoService,
            PlanoLimiteService planoLimiteService,
            AdminOrganizacaoRemocaoService adminOrganizacaoRemocaoService) {
        this.organizacaoRepository = organizacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioOrganizacaoRepository = usuarioOrganizacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.organizacaoConfiguracaoService = organizacaoConfiguracaoService;
        this.planoLimiteService = planoLimiteService;
        this.adminOrganizacaoRemocaoService = adminOrganizacaoRemocaoService;
    }

    @Transactional
    public OrganizacaoResponseDTO criarOrganizacao(CriarOrganizacaoRequestDTO request) {
        Organizacao organizacao = new Organizacao();
        organizacao.setNmOrganizacao(request.nmOrganizacao());
        organizacao.setDsDocumento(request.dsDocumento());
        organizacao.setFlAtivo(true);

        organizacao = organizacaoRepository.save(organizacao);
        organizacaoConfiguracaoService.criarPadrao(organizacao.getIdOrganizacao(), organizacao.getNmOrganizacao());
        return toResponse(organizacao);
    }

    @Transactional(readOnly = true)
    public List<OrganizacaoResponseDTO> listarOrganizacoes() {
        return organizacaoRepository.findAllByOrderByNmOrganizacaoAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioOrganizacaoResponseDTO> listarUsuariosDaOrganizacao(Long idOrganizacao) {
        Organizacao organizacao = buscarOrganizacao(idOrganizacao);

        return usuarioOrganizacaoRepository.findByOrganizacaoIdOrganizacaoOrderByUsuarioNmUsuarioAsc(idOrganizacao)
                .stream()
                .map(vinculo -> toResponse(vinculo.getUsuario(), organizacao, vinculo))
                .toList();
    }

    @Transactional
    public UsuarioOrganizacaoResponseDTO criarUsuarioDaOrganizacao(
            Long idOrganizacao,
            CriarUsuarioOrganizacaoRequestDTO request) {
        Organizacao organizacao = buscarOrganizacaoAtiva(idOrganizacao);
        String emailNormalizado = normalizarEmail(request.nmEmail());

        Optional<Usuario> usuarioExistente = usuarioRepository.findByNuCpf(request.nuCpf());
        if (usuarioExistente.isPresent()) {
            return vincularUsuarioExistente(organizacao, usuarioExistente.get(), request, emailNormalizado);
        }

        if (emailNormalizado != null && usuarioRepository.existsByNmEmail(emailNormalizado)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "E-mail ja cadastrado para outro usuario");
        }

        if (request.senha() == null || request.senha().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Senha obrigatoria para cadastrar novo usuario");
        }

        planoLimiteService.validarCriacaoUsuario(idOrganizacao);

        Usuario usuario = new Usuario();
        usuario.setNuCpf(request.nuCpf());
        usuario.setNmUsuario(request.nmUsuario());
        usuario.setNmEmail(emailNormalizado);
        usuario.setDsSenha(passwordEncoder.encode(request.senha()));
        usuario.setTpGlobal(TipoGlobal.DEFAULT);
        usuario.setFlAtivo(true);
        usuario = usuarioRepository.save(usuario);

        UsuarioOrganizacao vinculo = criarVinculo(organizacao, usuario, request.role());
        return toResponse(usuario, organizacao, vinculo);
    }

    private UsuarioOrganizacaoResponseDTO vincularUsuarioExistente(
            Organizacao organizacao,
            Usuario usuario,
            CriarUsuarioOrganizacaoRequestDTO request,
            String emailNormalizado) {

        if (usuario.getTpGlobal() == TipoGlobal.SUPER_ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nao e permitido vincular usuario SUPER_ADMIN a organizacao");
        }

        if (emailNormalizado != null
                && usuario.getNmEmail() != null
                && !emailNormalizado.equalsIgnoreCase(usuario.getNmEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "CPF ja cadastrado com outro e-mail");
        }

        Optional<UsuarioOrganizacao> vinculoExistente = usuarioOrganizacaoRepository
                .findByUsuarioIdUsuarioAndOrganizacaoIdOrganizacao(
                        usuario.getIdUsuario(),
                        organizacao.getIdOrganizacao());

        if (vinculoExistente.isPresent()) {
            UsuarioOrganizacao vinculo = vinculoExistente.get();

            if (Boolean.TRUE.equals(vinculo.getFlAtivo())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Usuario ja vinculado a esta organizacao");
            }

            planoLimiteService.validarCriacaoUsuario(organizacao.getIdOrganizacao());
            aplicarAtualizacaoOpcionalUsuario(usuario, request, emailNormalizado);
            vinculo.setDsRole(request.role());
            vinculo.setFlAtivo(true);
            usuario.setFlAtivo(true);
            usuarioRepository.save(usuario);
            usuarioOrganizacaoRepository.save(vinculo);
            return toResponse(usuario, organizacao, vinculo);
        }

        planoLimiteService.validarCriacaoUsuario(organizacao.getIdOrganizacao());
        aplicarAtualizacaoOpcionalUsuario(usuario, request, emailNormalizado);
        usuario.setFlAtivo(true);
        usuarioRepository.save(usuario);

        UsuarioOrganizacao vinculo = criarVinculo(organizacao, usuario, request.role());
        return toResponse(usuario, organizacao, vinculo);
    }

    private UsuarioOrganizacao criarVinculo(Organizacao organizacao, Usuario usuario, String role) {
        UsuarioOrganizacao vinculo = new UsuarioOrganizacao();
        vinculo.setUsuario(usuario);
        vinculo.setOrganizacao(organizacao);
        vinculo.setDsRole(role);
        vinculo.setFlAtivo(true);
        return usuarioOrganizacaoRepository.save(vinculo);
    }

    private void aplicarAtualizacaoOpcionalUsuario(
            Usuario usuario,
            CriarUsuarioOrganizacaoRequestDTO request,
            String emailNormalizado) {

        if (request.nmUsuario() != null && !request.nmUsuario().isBlank()) {
            usuario.setNmUsuario(request.nmUsuario());
        }

        if (emailNormalizado != null
                && (usuario.getNmEmail() == null || usuario.getNmEmail().isBlank())) {
            usuario.setNmEmail(emailNormalizado);
        }

        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setDsSenha(passwordEncoder.encode(request.senha()));
        }
    }

    private Organizacao buscarOrganizacaoAtiva(Long idOrganizacao) {
        return organizacaoRepository.findById(idOrganizacao)
                .filter(Organizacao::getFlAtivo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organizacao ativa nao encontrada"));
    }

    private Organizacao buscarOrganizacao(Long idOrganizacao) {
        return organizacaoRepository.findById(idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organizacao nao encontrada"));
    }

    private String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private OrganizacaoResponseDTO toResponse(Organizacao organizacao) {
        return new OrganizacaoResponseDTO(
                organizacao.getIdOrganizacao(),
                organizacao.getNmOrganizacao(),
                organizacao.getDsDocumento(),
                organizacao.getFlAtivo());
    }

    private UsuarioOrganizacaoResponseDTO toResponse(
            Usuario usuario,
            Organizacao organizacao,
            UsuarioOrganizacao vinculo) {
        return new UsuarioOrganizacaoResponseDTO(
                usuario.getIdUsuario(),
                usuario.getNuCpf(),
                usuario.getNmUsuario(),
                usuario.getNmEmail(),
                organizacao.getIdOrganizacao(),
                organizacao.getNmOrganizacao(),
                vinculo.getDsRole(),
                vinculo.getFlAtivo());
    }

    @Transactional
    public OrganizacaoResponseDTO editarOrganizacao(
            Long idOrganizacao,
            CriarOrganizacaoRequestDTO request) {

        Organizacao organizacao = buscarOrganizacaoAtiva(idOrganizacao);

        organizacao.setNmOrganizacao(request.nmOrganizacao());
        organizacao.setDsDocumento(request.dsDocumento());

        return toResponse(organizacaoRepository.save(organizacao));
    }

    @Transactional
    public UsuarioOrganizacaoResponseDTO editarUsuarioDaOrganizacao(
            Long idOrganizacao,
            Long idUsuario,
            CriarUsuarioOrganizacaoRequestDTO request) {

        Organizacao organizacao = buscarOrganizacaoAtiva(idOrganizacao);

        UsuarioOrganizacao vinculo = usuarioOrganizacaoRepository
                .findByOrganizacaoIdOrganizacaoOrderByUsuarioNmUsuarioAsc(idOrganizacao)
                .stream()
                .filter(item -> item.getUsuario().getIdUsuario().equals(idUsuario))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuario da organizacao nao encontrado"));

        Usuario usuario = vinculo.getUsuario();

        String emailNormalizado = normalizarEmail(request.nmEmail());

        if (!usuario.getNuCpf().equals(request.nuCpf())
                && usuarioRepository.existsByNuCpf(request.nuCpf())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "CPF ja cadastrado");
        }

        if (emailNormalizado != null
                && !emailNormalizado.equals(usuario.getNmEmail())
                && usuarioRepository.existsByNmEmail(emailNormalizado)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "E-mail ja cadastrado");
        }

        usuario.setNuCpf(request.nuCpf());
        usuario.setNmUsuario(request.nmUsuario());
        usuario.setNmEmail(emailNormalizado);

        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setDsSenha(passwordEncoder.encode(request.senha()));
        }

        vinculo.setDsRole(request.role());

        usuarioRepository.save(usuario);
        usuarioOrganizacaoRepository.save(vinculo);

        return toResponse(usuario, organizacao, vinculo);
    }

    @Transactional
    public OrganizacaoResponseDTO excluirOrganizacao(Long idOrganizacao) {
        return inativarOrganizacao(idOrganizacao);
    }

    @Transactional
    public OrganizacaoResponseDTO inativarOrganizacao(Long idOrganizacao) {
        Organizacao organizacao = buscarOrganizacao(idOrganizacao);

        if (!Boolean.TRUE.equals(organizacao.getFlAtivo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Organizacao ja esta inativa");
        }

        organizacao.setFlAtivo(false);
        organizacaoRepository.save(organizacao);

        usuarioOrganizacaoRepository.findByOrganizacaoIdOrganizacaoOrderByUsuarioNmUsuarioAsc(idOrganizacao)
                .stream()
                .filter(vinculo -> Boolean.TRUE.equals(vinculo.getFlAtivo()))
                .forEach(vinculo -> inativarVinculo(vinculo));

        return toResponse(organizacao);
    }

    @Transactional
    public OrganizacaoResponseDTO ativarOrganizacao(Long idOrganizacao) {
        Organizacao organizacao = buscarOrganizacao(idOrganizacao);

        if (Boolean.TRUE.equals(organizacao.getFlAtivo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Organizacao ja esta ativa");
        }

        organizacao.setFlAtivo(true);
        return toResponse(organizacaoRepository.save(organizacao));
    }

    @Transactional
    public void excluirOrganizacaoPermanentemente(Long idOrganizacao) {
        adminOrganizacaoRemocaoService.removerOrganizacaoPermanentemente(idOrganizacao);
    }

    @Transactional
    public void excluirUsuarioDaOrganizacao(Long idOrganizacao, Long idUsuario) {
        inativarUsuarioDaOrganizacao(idOrganizacao, idUsuario);
    }

    @Transactional
    public UsuarioOrganizacaoResponseDTO ativarUsuarioDaOrganizacao(Long idOrganizacao, Long idUsuario) {
        Organizacao organizacao = buscarOrganizacaoAtiva(idOrganizacao);

        UsuarioOrganizacao vinculo = usuarioOrganizacaoRepository
                .findByUsuarioIdUsuarioAndOrganizacaoIdOrganizacao(idUsuario, idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuario da organizacao nao encontrado"));

        if (Boolean.TRUE.equals(vinculo.getFlAtivo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Usuario ja esta ativo nesta organizacao");
        }

        Usuario usuario = vinculo.getUsuario();

        if (usuario.getTpGlobal() == TipoGlobal.SUPER_ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nao e permitido alterar usuario SUPER_ADMIN");
        }

        vinculo.setFlAtivo(true);
        usuario.setFlAtivo(true);
        usuarioRepository.save(usuario);
        usuarioOrganizacaoRepository.save(vinculo);

        return toResponse(usuario, organizacao, vinculo);
    }

    @Transactional
    public void inativarUsuarioDaOrganizacao(Long idOrganizacao, Long idUsuario) {
        buscarOrganizacao(idOrganizacao);

        UsuarioOrganizacao vinculo = usuarioOrganizacaoRepository
                .findByUsuarioIdUsuarioAndOrganizacaoIdOrganizacao(idUsuario, idOrganizacao)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Usuario da organizacao nao encontrado"));

        Usuario usuario = vinculo.getUsuario();

        if (usuario.getTpGlobal() == TipoGlobal.SUPER_ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Nao e permitido excluir usuario SUPER_ADMIN");
        }

        if (!Boolean.TRUE.equals(vinculo.getFlAtivo())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Usuario ja esta inativo nesta organizacao");
        }

        inativarVinculo(vinculo);
    }

    @Transactional
    public void excluirUsuarioPermanentemente(Long idOrganizacao, Long idUsuario) {
        adminOrganizacaoRemocaoService.removerUsuarioPermanentemente(idOrganizacao, idUsuario);
    }

    private void inativarVinculo(UsuarioOrganizacao vinculo) {
        vinculo.setFlAtivo(false);
        usuarioOrganizacaoRepository.save(vinculo);

        Usuario usuario = vinculo.getUsuario();
        Long idUsuario = usuario.getIdUsuario();

        if (Boolean.TRUE.equals(usuario.getFlAtivo())
                && usuarioOrganizacaoRepository.countByUsuarioIdUsuarioAndFlAtivoTrue(idUsuario) == 0) {
            usuario.setFlAtivo(false);
            usuarioRepository.save(usuario);
        }
    }
}

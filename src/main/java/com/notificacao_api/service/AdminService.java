package com.notificacao_api.service;

import java.util.List;

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

    public AdminService(
            OrganizacaoRepository organizacaoRepository,
            UsuarioRepository usuarioRepository,
            UsuarioOrganizacaoRepository usuarioOrganizacaoRepository,
            PasswordEncoder passwordEncoder) {
        this.organizacaoRepository = organizacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioOrganizacaoRepository = usuarioOrganizacaoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public OrganizacaoResponseDTO criarOrganizacao(CriarOrganizacaoRequestDTO request) {
        Organizacao organizacao = new Organizacao();
        organizacao.setNmOrganizacao(request.nmOrganizacao());
        organizacao.setDsDocumento(request.dsDocumento());
        organizacao.setFlAtivo(true);

        return toResponse(organizacaoRepository.save(organizacao));
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
        Organizacao organizacao = buscarOrganizacaoAtiva(idOrganizacao);

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

        validarUsuarioNovo(request);

        Usuario usuario = new Usuario();
        usuario.setNuCpf(request.nuCpf());
        usuario.setNmUsuario(request.nmUsuario());
        usuario.setNmEmail(normalizarEmail(request.nmEmail()));
        usuario.setDsSenha(passwordEncoder.encode(request.senha()));
        usuario.setTpGlobal(TipoGlobal.DEFAULT);
        usuario.setFlAtivo(true);
        usuario = usuarioRepository.save(usuario);

        UsuarioOrganizacao vinculo = new UsuarioOrganizacao();
        vinculo.setUsuario(usuario);
        vinculo.setOrganizacao(organizacao);
        vinculo.setDsRole(request.role());
        vinculo.setFlAtivo(true);
        usuarioOrganizacaoRepository.save(vinculo);

        return toResponse(usuario, organizacao, vinculo);
    }

    private Organizacao buscarOrganizacaoAtiva(Long idOrganizacao) {
        return organizacaoRepository.findById(idOrganizacao)
                .filter(Organizacao::getFlAtivo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organizacao ativa nao encontrada"));
    }

    private void validarUsuarioNovo(CriarUsuarioOrganizacaoRequestDTO request) {
        if (usuarioRepository.existsByNuCpf(request.nuCpf())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF ja cadastrado");
        }

        String email = normalizarEmail(request.nmEmail());
        if (email != null && usuarioRepository.existsByNmEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail ja cadastrado");
        }
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
}

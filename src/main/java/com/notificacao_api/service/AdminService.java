package com.notificacao_api.service;

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

    @Transactional
    public UsuarioOrganizacaoResponseDTO criarUsuarioDaOrganizacao(
            Long idOrganizacao,
            CriarUsuarioOrganizacaoRequestDTO request) {
        Organizacao organizacao = organizacaoRepository.findById(idOrganizacao)
                .filter(Organizacao::getFlAtivo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organizacao ativa nao encontrada"));

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
}

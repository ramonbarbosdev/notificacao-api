package com.notificacao_api.config;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.notificacao_api.security.JwtAuthentication;
import com.notificacao_api.security.JwtService;

import io.jsonwebtoken.Claims;

@Component
public class WebSocketStompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TOPICO_ORGANIZACAO = Pattern.compile(
            "^/topic/(?:notificacoes|whatsapp)/organizacao/(\\d+)$");

    private final JwtService jwtService;

    public WebSocketStompAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            JwtAuthentication autenticacao = autenticarConnect(accessor);
            accessor.setUser(autenticacao);
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validarSubscribe(accessor);
        }

        return message;
    }

    private JwtAuthentication autenticarConnect(StompHeaderAccessor accessor) {
        String authorization = primeiroHeader(accessor, "Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("Token JWT obrigatorio para conexao WebSocket.");
        }

        try {
            Claims claims = jwtService.parse(authorization.substring(7));
            Long idUsuario = Long.valueOf(claims.getSubject());
            String tipoGlobal = claims.get("tipoGlobal", String.class);
            Long idOrganizacao = extrairLong(claims, "idOrganizacao");
            String role = claims.get("role", String.class);

            if (idOrganizacao == null) {
                throw new AccessDeniedException("Token sem organizacao nao pode assinar topicos da fila.");
            }

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("GLOBAL_" + tipoGlobal),
                    new SimpleGrantedAuthority("TENANT_ACCESS"),
                    new SimpleGrantedAuthority("ROLE_" + role));

            return new JwtAuthentication(idUsuario, tipoGlobal, idOrganizacao, role, authorities);
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AccessDeniedException("Token JWT invalido para WebSocket.");
        }
    }

    private void validarSubscribe(StompHeaderAccessor accessor) {
        String destino = accessor.getDestination();
        if (destino == null || !destino.startsWith("/topic/")) {
            return;
        }

        Matcher matcher = TOPICO_ORGANIZACAO.matcher(destino);
        if (!matcher.matches()) {
            throw new AccessDeniedException("Topico nao permitido: " + destino);
        }

        Long idOrganizacaoDestino = Long.valueOf(matcher.group(1));
        Long idOrganizacaoToken = extrairOrganizacaoDoUsuario(accessor.getUser());

        if (idOrganizacaoToken == null || !idOrganizacaoToken.equals(idOrganizacaoDestino)) {
            throw new AccessDeniedException("Assinatura negada para organizacao " + idOrganizacaoDestino);
        }
    }

    private Long extrairOrganizacaoDoUsuario(java.security.Principal principal) {
        if (principal instanceof JwtAuthentication jwtAuthentication) {
            return jwtAuthentication.getIdOrganizacao();
        }
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof JwtAuthentication jwtAuthentication) {
            return jwtAuthentication.getIdOrganizacao();
        }
        return null;
    }

    private String primeiroHeader(StompHeaderAccessor accessor, String nome) {
        List<String> valores = accessor.getNativeHeader(nome);
        if (valores == null || valores.isEmpty()) {
            return null;
        }
        return valores.get(0);
    }

    private Long extrairLong(Claims claims, String chave) {
        Object valor = claims.get(chave);
        if (valor instanceof Number number) {
            return number.longValue();
        }
        if (valor instanceof String texto && !texto.isBlank()) {
            return Long.valueOf(texto);
        }
        return null;
    }
}

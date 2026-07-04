package com.notificacao_api.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.notificacao_api.model.ConfiguracaoGlobal;
import com.notificacao_api.repository.ConfiguracaoGlobalRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailEnvioService {

    private static final Logger log = LoggerFactory.getLogger(EmailEnvioService.class);

    private final ConfiguracaoGlobalRepository configuracaoGlobalRepository;

    public EmailEnvioService(ConfiguracaoGlobalRepository configuracaoGlobalRepository) {
        this.configuracaoGlobalRepository = configuracaoGlobalRepository;
    }

    public boolean enviarAlerta(List<String> destinatarios, String assunto, String corpoTexto) {
        List<String> emails = destinatarios.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (emails.isEmpty()) {
            log.warn("Nenhum e-mail de alerta configurado para envio.");
            return false;
        }

        ConfiguracaoGlobal global = configuracaoGlobalRepository.findAll().stream().findFirst().orElse(null);
        if (global == null || !StringUtils.hasText(global.getDsSmtpHost())) {
            log.warn("SMTP global nao configurado. Alerta registrado sem envio de e-mail.");
            return false;
        }

        String senha = global.getDsSmtpSenhaCriptografada();
        if (!StringUtils.hasText(senha) || senha.startsWith("$2a$") || senha.startsWith("$2b$")) {
            log.warn(
                    "Senha SMTP invalida ou legada (BCrypt). Reconfigure a senha SMTP em Configuracoes Globais.");
            return false;
        }

        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(global.getDsSmtpHost());
            mailSender.setPort(global.getNuSmtpPorta() != null ? global.getNuSmtpPorta() : 587);
            mailSender.setUsername(global.getNmSmtpUsuario());
            mailSender.setPassword(senha);
            mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            String remetente = StringUtils.hasText(global.getNmSmtpUsuario())
                    ? global.getNmSmtpUsuario()
                    : (StringUtils.hasText(global.getNmEmailSuporte())
                            ? global.getNmEmailSuporte()
                            : "alertas@notificacao.local");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(remetente);
            helper.setTo(emails.toArray(String[]::new));
            helper.setSubject(assunto);
            helper.setText(corpoTexto, false);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.error("Falha ao enviar e-mail de alerta operacional: {}", ex.getMessage());
            return false;
        }
    }
}

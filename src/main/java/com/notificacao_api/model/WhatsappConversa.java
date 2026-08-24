package com.notificacao_api.model;

import java.time.LocalDateTime;

import com.notificacao_api.enums.WhatsappMensagemDirecao;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "whatsapp_conversa")
public class WhatsappConversa {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_whatsapp_conversa")
    @SequenceGenerator(
            name = "seq_whatsapp_conversa",
            sequenceName = "seq_whatsapp_conversa",
            allocationSize = 1)
    @Column(name = "id_conversa")
    private Long idConversa;

    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Column(name = "ds_telefone", nullable = false, length = 32)
    private String telefone;

    @Column(name = "nm_contato")
    private String nmContato;

    @Column(name = "ds_ultima_mensagem", columnDefinition = "text")
    private String ultimaMensagem;

    @Column(name = "tp_ultima_mensagem", length = 32)
    private String tipoUltimaMensagem;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_ultima_direcao", length = 16)
    private WhatsappMensagemDirecao ultimaDirecaoMensagem;

    @Column(name = "ds_jid", length = 128)
    private String jid;

    @Column(name = "fl_nao_lida", nullable = false)
    private Boolean naoLida = true;

    @Column(name = "dt_ultima_mensagem", nullable = false)
    private LocalDateTime dtUltimaMensagem;

    @Column(name = "dt_criacao", nullable = false, updatable = false)
    private LocalDateTime dtCriacao;

    @Column(name = "dt_atualizacao", nullable = false)
    private LocalDateTime dtAtualizacao;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (dtUltimaMensagem == null) {
            dtUltimaMensagem = agora;
        }
        dtCriacao = agora;
        dtAtualizacao = agora;
    }

    @PreUpdate
    void preUpdate() {
        dtAtualizacao = LocalDateTime.now();
    }
}

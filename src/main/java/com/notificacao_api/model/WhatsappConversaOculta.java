package com.notificacao_api.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "whatsapp_conversa_oculta")
@IdClass(WhatsappConversaOculta.WhatsappConversaOcultaId.class)
public class WhatsappConversaOculta {

    @Id
    @Column(name = "id_organizacao", nullable = false)
    private Long idOrganizacao;

    @Id
    @Column(name = "ds_telefone", nullable = false, length = 32)
    private String telefone;

    @Column(name = "dt_oculta", nullable = false)
    private LocalDateTime dtOculta;

    @PrePersist
    void prePersist() {
        if (dtOculta == null) {
            dtOculta = LocalDateTime.now();
        }
    }

    @Getter
    @Setter
    public static class WhatsappConversaOcultaId implements Serializable {
        private Long idOrganizacao;
        private String telefone;
    }
}

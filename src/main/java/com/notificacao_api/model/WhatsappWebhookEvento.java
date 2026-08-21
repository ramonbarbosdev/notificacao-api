package com.notificacao_api.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "whatsapp_webhook_evento")
public class WhatsappWebhookEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_whatsapp_webhook_evento")
    @SequenceGenerator(
            name = "seq_whatsapp_webhook_evento",
            sequenceName = "seq_whatsapp_webhook_evento",
            allocationSize = 1)
    @Column(name = "id_evento")
    private Long idEvento;

    @Column(name = "ds_id_evento_meta", nullable = false)
    private String idEventoMeta;

    @Column(name = "dt_processado", nullable = false)
    private LocalDateTime dtProcessado;

    @PrePersist
    void prePersist() {
        dtProcessado = LocalDateTime.now();
    }
}

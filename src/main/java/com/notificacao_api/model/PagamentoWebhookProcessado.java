package com.notificacao_api.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "pagamento_webhook_processado")
public class PagamentoWebhookProcessado {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pagamento_webhook_processado")
    @SequenceGenerator(name = "seq_pagamento_webhook_processado", sequenceName = "seq_pagamento_webhook_processado", allocationSize = 1)
    @Column(name = "id_pagamento_webhook_processado")
    private Long idPagamentoWebhookProcessado;

    @Column(name = "id_evento_asaas", nullable = false, length = 100)
    private String idEventoAsaas;

    @Column(name = "ds_tipo_evento", nullable = false, length = 60)
    private String dsTipoEvento;

    @Column(name = "dt_processamento", nullable = false)
    private LocalDateTime dtProcessamento = LocalDateTime.now();
}

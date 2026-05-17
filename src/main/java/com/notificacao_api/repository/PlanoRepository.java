package com.notificacao_api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.notificacao_api.model.Plano;

public interface PlanoRepository extends JpaRepository<Plano, Long> {

    List<Plano> findAllByOrderByNmPlanoAsc();
}

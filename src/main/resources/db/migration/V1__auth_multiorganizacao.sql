
create schema if not exists public;
set search_path to public;

create sequence if not exists seq_usuario start with 100 increment by 1;
create sequence if not exists seq_organizacao start with 100 increment by 1;
create sequence if not exists seq_usuario_organizacao start with 100 increment by 1;

create table if not exists usuario (
    id_usuario bigint primary key,
    nu_cpf varchar(11) not null,
    nm_usuario varchar(255),
    nm_email varchar(255),
    ds_senha varchar(255) not null,
    tp_global varchar(30) not null check (tp_global in ('SUPER_ADMIN', 'DEFAULT')),
    fl_ativo boolean not null default true,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create unique index if not exists ux_usuario_nu_cpf on usuario (nu_cpf);
create unique index if not exists ux_usuario_nm_email on usuario (nm_email);

create table if not exists organizacao (
    id_organizacao bigint primary key,
    nm_organizacao varchar(255) not null,
    ds_documento varchar(50),
    fl_ativo boolean not null default true,
    dt_criacao timestamp not null default current_timestamp,
    dt_atualizacao timestamp not null default current_timestamp
);

create table if not exists usuario_organizacao (
    id_usuario_organizacao bigint primary key,
    id_usuario bigint not null references usuario (id_usuario),
    id_organizacao bigint not null references organizacao (id_organizacao),
    ds_role varchar(30) not null check (ds_role in ('ADMIN', 'USER')),
    fl_ativo boolean not null default true,
    dt_criacao timestamp not null default current_timestamp
);

create unique index if not exists ux_usuario_organizacao_vinculo
    on usuario_organizacao (id_usuario, id_organizacao);

insert into usuario (id_usuario, nu_cpf, nm_usuario, nm_email, ds_senha, tp_global, fl_ativo, dt_criacao, dt_atualizacao)
select 1, '85778905548', 'Super Admin', 'superadmin@notificacao.local', '$2a$10$ZWSelZuae0XqUhu.kX5JIeSvkaG7tO5YhufCDGgQeQiyJ7yBZnH0G', 'SUPER_ADMIN', true, current_timestamp, current_timestamp
where not exists (select 1 from usuario where nu_cpf = '85778905548');

insert into usuario (id_usuario, nu_cpf, nm_usuario, nm_email, ds_senha, tp_global, fl_ativo, dt_criacao, dt_atualizacao)
select 2, '55308042098', 'Admin Demo', 'admin@notificacao.local', '$2a$10$ZWSelZuae0XqUhu.kX5JIeSvkaG7tO5YhufCDGgQeQiyJ7yBZnH0G', 'DEFAULT', true, current_timestamp, current_timestamp
where not exists (select 1 from usuario where nu_cpf = '55308042098');

insert into usuario (id_usuario, nu_cpf, nm_usuario, nm_email, ds_senha, tp_global, fl_ativo, dt_criacao, dt_atualizacao)
select 3, '98765432100', 'Usuario Demo', 'user@notificacao.local', '$2a$10$ZWSelZuae0XqUhu.kX5JIeSvkaG7tO5YhufCDGgQeQiyJ7yBZnH0G', 'DEFAULT', true, current_timestamp, current_timestamp
where not exists (select 1 from usuario where nu_cpf = '98765432100');

insert into organizacao (id_organizacao, nm_organizacao, ds_documento, fl_ativo, dt_criacao, dt_atualizacao)
select 1, 'Organizacao Demo', '00000000000191', true, current_timestamp, current_timestamp
where not exists (select 1 from organizacao where id_organizacao = 1);

insert into usuario_organizacao (id_usuario_organizacao, id_usuario, id_organizacao, ds_role, fl_ativo, dt_criacao)
select 1, 2, 1, 'ADMIN', true, current_timestamp
where not exists (select 1 from usuario_organizacao where id_usuario = 2 and id_organizacao = 1);

insert into usuario_organizacao (id_usuario_organizacao, id_usuario, id_organizacao, ds_role, fl_ativo, dt_criacao)
select 2, 3, 1, 'USER', true, current_timestamp
where not exists (select 1 from usuario_organizacao where id_usuario = 3 and id_organizacao = 1);

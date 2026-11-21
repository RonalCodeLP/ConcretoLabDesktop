-- V2: bitacora de cambios importantes (proyectos, importaciones, copias de seguridad).
CREATE TABLE IF NOT EXISTS bitacora (id integer, fecha varchar(30) not null, accion varchar(40) not null, detalle varchar(400), primary key (id));

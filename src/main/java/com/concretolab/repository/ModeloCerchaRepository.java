package com.concretolab.repository;
import com.concretolab.model.ModeloCercha;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ModeloCerchaRepository extends JpaRepository<ModeloCercha,Long>{List<ModeloCercha> findByProyectoIdOrderByNombreAsc(Long proyectoId);}

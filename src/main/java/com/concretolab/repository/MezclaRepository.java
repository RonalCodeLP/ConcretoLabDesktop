package com.concretolab.repository;
import com.concretolab.model.Mezcla;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MezclaRepository extends JpaRepository<Mezcla,Long>{List<Mezcla> findByProyectoIdOrderByIdDesc(Long proyectoId);}

package com.concretolab.repository;
import com.concretolab.model.Elemento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ElementoRepository extends JpaRepository<Elemento,Long> { List<Elemento> findByProyectoIdOrderById(Long id); }

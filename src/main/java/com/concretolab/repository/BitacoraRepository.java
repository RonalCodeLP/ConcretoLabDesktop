package com.concretolab.repository;

import com.concretolab.model.RegistroBitacora;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BitacoraRepository extends JpaRepository<RegistroBitacora, Long> {
    List<RegistroBitacora> findTop200ByOrderByIdDesc();
}

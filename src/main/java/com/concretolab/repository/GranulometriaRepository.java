package com.concretolab.repository;
import com.concretolab.model.RegistroGranulometria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface GranulometriaRepository extends JpaRepository<RegistroGranulometria,Long>{List<RegistroGranulometria> findByMaterialIdOrderByIdDesc(Long materialId);}

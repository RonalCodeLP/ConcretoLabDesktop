package com.concretolab.repository;
import com.concretolab.model.Material;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MaterialRepository extends JpaRepository<Material,Long>{
 List<Material> findAllByOrderByNombreAsc();
 List<Material> findByTipoOrderByNombreAsc(String tipo);
}

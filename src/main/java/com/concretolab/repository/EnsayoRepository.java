package com.concretolab.repository;
import com.concretolab.model.Ensayo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EnsayoRepository extends JpaRepository<Ensayo,Long>{List<Ensayo> findByMezclaIdOrderByIdDesc(Long mezclaId);}

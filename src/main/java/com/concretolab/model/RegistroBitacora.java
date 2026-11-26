package com.concretolab.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bitacora")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString @EqualsAndHashCode(of = "id")
public class RegistroBitacora {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String fecha;

    @Column(nullable = false, length = 40)
    private String accion;

    @Column(length = 400)
    private String detalle;
}

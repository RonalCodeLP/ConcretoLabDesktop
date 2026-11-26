# Equipo 8 · Redes Informáticas I · Unidad 3

Configuración de referencia para el diagrama entregado. Se conserva el direccionamiento del adjunto. La guía adjunta solo contiene la topología, por lo que rutas estáticas, DHCP, NAT y las direcciones de los servidores son decisiones para este laboratorio.

## Montaje en Packet Tracer

| Origen | Destino | Conexión |
|---|---|---|
| CENTRAL G0/0 | Switch del primer piso | Ethernet |
| CENTRAL G0/1 | ISP G0/0 | Ethernet |
| CENTRAL S0/0/0 (DCE) | NORTE S0/0/0 (DTE) | Serial |
| CENTRAL S0/0/1 (DCE) | SUR S0/0/0 (DTE) | Serial |
| NORTE G0/0 | Switch Norte | Ethernet |
| SUR Fa0/0 | Switch Sur | Ethernet |
| Switch primer piso | Switch segundo piso | Cascada Ethernet |
| Switch segundo piso | Switch tercer piso | Cascada Ethernet |

Usa Cisco 1941 para Central y Norte y Cisco 1841 para Sur, según el diagrama. Con los routers apagados instala un módulo serial de dos puertos en la ranura 0 de cada router. Comprueba la numeración con `show ip interface brief`. Usa un switch de 24 puertos por piso y otro en Norte. En Sur puedes usar un switch de 24 puertos si el simulador no ofrece uno de 16. Los switches funcionan en su VLAN predeterminada, como equivalentes de los no administrables del diagrama. Al conectar los seriales DCE, selecciona primero Central para que ese lado suministre el reloj.

Pega cada archivo `.ios.txt` en la CLI de su router correspondiente. Para ISP se presupone otro Cisco 1941. Su loopback simula un destino externo y ambas redes del ISP usan rangos reservados para documentación.

## Estaciones y servicios

| Segmento | Equipos del diagrama | Dirección fija o puerta de enlace |
|---|---|---|
| Central, piso 1 | 15 PCs, 1 impresora, 2 servidores | Impresora 192.168.1.20 |
| Central, piso 2 | 16 PCs, 1 impresora, 1 AP | Impresora 192.168.1.21, AP 192.168.1.30 |
| Central, piso 3 | 14 PCs, 1 impresora | Impresora 192.168.1.22 |
| Norte | 22 PCs, 1 impresora | Impresora 192.168.2.20 |
| Sur | 8 PCs | Puerta de enlace 192.168.3.1 |
| Servidor de archivos | Central, piso 1 | 192.168.1.10 |
| Servidor web y DNS | Central, piso 1 | 192.168.1.11 |

Todas las LAN usan máscara 255.255.255.0. Las direcciones fijas usan como puerta de enlace la .1 de su sede y como DNS 192.168.1.11. Configura las 75 PCs en DHCP. Los tres rangos dinámicos van de .50 a .199.

En el servidor 192.168.1.11, activa HTTP y DNS bajo Services. Agrega un registro A para `www.equipo8.test` que apunte a 192.168.1.11. Para representar el servicio de archivos con Server-PT, habilita FTP en 192.168.1.10 y crea una cuenta exclusiva de laboratorio con permisos de lectura y listado. En el DNS agrega `archivos.equipo8.test` como registro A hacia 192.168.1.10.

El AP se conecta al switch del segundo piso. El diagrama comparte la LAN entre personal e invitados. La configuración reproduce esa red plana. Aislar invitados exigiría otra interfaz o una topología con VLAN y un AP compatible.

## Comprobación en IOS y Packet Tracer

1. Ejecuta `show ip interface brief`, `show ip route` y `show ip dhcp binding` en cada router de sede. Las interfaces cableadas deben estar `up/up` y es normal que las concesiones estén vacías hasta activar DHCP en las PCs.
2. Desde una PC de cada sede, ejecuta `ipconfig /all` y haz ping a su puerta de enlace.
3. Desde una PC de Norte y otra de Sur, haz ping a 192.168.1.11. Haz también ping entre dos PCs de sucursales usando sus IP asignadas por DHCP.
4. Abre `http://www.equipo8.test` desde cada sede. Prueba el acceso al servidor de archivos con `ftp archivos.equipo8.test` y la cuenta de laboratorio.
5. Haz ping a 198.51.100.1 desde las tres sedes y ejecuta `show ip nat translations` inmediatamente en Central. Deben aparecer traducciones ICMP durante la prueba.
6. Guarda el proyecto resultante como `Equipo_8_U3.pkt`.

Los comandos IOS se entregan listos para pegar. Las pruebas en Packet Tracer quedan pendientes.

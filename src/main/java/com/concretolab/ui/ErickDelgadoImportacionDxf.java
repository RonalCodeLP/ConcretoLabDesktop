package com.concretolab.ui;

import com.concretolab.model.Proyecto;
import com.concretolab.service.FernandoHuancaPlanoDxf;
import com.concretolab.service.FernandoHuancaPlanoDxf.Datos;
import com.concretolab.service.YefriLermaProyectoService;
import java.util.*;
import java.util.stream.Collectors;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class ErickDelgadoImportacionDxf {
    private final FernandoHuancaPlanoDxf erickDelgadoLector;
    private final YefriLermaProyectoService proyectos;

    public ErickDelgadoImportacionDxf(FernandoHuancaPlanoDxf lector, YefriLermaProyectoService proyectos) {
        this.erickDelgadoLector = lector;
        this.proyectos = proyectos;
    }

    public Optional<Proyecto> erickDelgadoAbrir(Window ventana, Proyecto activo) {
        var selector = new FileChooser();
        selector.setTitle("Seleccionar archivo CAD");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("AutoCAD o Revit", "*.dxf", "*.dxf.txt", "*.dwg", "*.rvt"));
        var archivo = selector.showOpenDialog(ventana);
        if (archivo == null) return Optional.empty();
        return erickDelgadoAbrirArchivo(ventana, activo, archivo);
    }

    public Optional<Proyecto> erickDelgadoAbrirArchivo(Window ventana, Proyecto activo, java.io.File archivo) {
        FernandoHuancaPlanoDxf.Plano plano;
        try {
            String extension = archivo.getName().toLowerCase(Locale.ROOT);
            if (extension.endsWith(".rvt")) {
                var aviso = new Alert(Alert.AlertType.INFORMATION,
                        "Abra el RVT en Revit y use Archivo → Exportar → Formatos CAD → DXF. "
                        + "Prepare los centros de columnas y los ejes de vigas en capas separadas e importe el DXF.");
                aviso.initOwner(ventana);
                aviso.showAndWait();
                return Optional.empty();
            }
            if (extension.endsWith(".dwg")) {
                var convertido = erickDelgadoConvertirDwg(ventana, archivo);
                if (convertido.isEmpty()) return Optional.empty();
                plano = convertido.orElseThrow();
            } else {
                plano = erickDelgadoLector.fernandoHuancaLeer(archivo.toPath());
            }
        } catch (Exception ex) {
            var aviso = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            aviso.initOwner(ventana);
            aviso.showAndWait();
            return Optional.empty();
        }

        var inventario = erickDelgadoInventario(plano);
        var capasPuntos = erickDelgadoCapasCompatibles(plano,Set.of("POINT","BLOQUE","RECTÁNGULO","CÍRCULO"));
        var capasVigas = erickDelgadoCapasCompatibles(plano,Set.of("LINE"));
        boolean modeloCompleto = !plano.modelo3d().isEmpty();
        boolean planoVial = plano.entidades().stream().anyMatch(e->e.capa().equalsIgnoreCase("C-ROAD-CORR"))
                && plano.entidades().stream().anyMatch(e->e.capa().equalsIgnoreCase("C-ROAD-SECT-GRID"));
        boolean capasEstructurales = capasPuntos.stream().anyMatch(c->c.equalsIgnoreCase("COLUMNAS") || c.equalsIgnoreCase("COLUMNS"))
                && capasVigas.stream().anyMatch(c->c.equalsIgnoreCase("VIGAS") || c.equalsIgnoreCase("BEAMS"));
        if (!modeloCompleto && ((planoVial && !capasEstructurales) || capasPuntos.isEmpty() || capasVigas.isEmpty())) {
            erickDelgadoMostrarInventario(ventana,archivo.getName(),inventario,planoVial && !capasEstructurales);
            return Optional.empty();
        }

        var dialogo = new Dialog<Proyecto>();
        dialogo.initOwner(ventana);
        dialogo.setTitle("Importar planta estructural");
        var guardar = new ButtonType("Guardar en proyecto", ButtonBar.ButtonData.OK_DONE);
        var revisar = new ButtonType("Revisar modelo");
        dialogo.getDialogPane().getButtonTypes().addAll(revisar, guardar, ButtonType.CANCEL);
        var sugerido = com.concretolab.service.DeteccionCapasDxf.erickDelgadoSugerir(plano);
        var columnas = erickDelgadoCapa(capasPuntos, sugerido.de(com.concretolab.service.DeteccionCapasDxf.Rol.COLUMNAS), "COLUMNAS","COLUMNS");
        var vigas = erickDelgadoCapa(capasVigas, sugerido.de(com.concretolab.service.DeteccionCapasDxf.Rol.VIGAS), "VIGAS","BEAMS");
        var zapatas = erickDelgadoCapa(capasPuntos, sugerido.de(com.concretolab.service.DeteccionCapasDxf.Rol.ZAPATAS), "ZAPATAS","FOOTINGS");
        zapatas.getItems().add(0,"");
        if (zapatas.getValue()==null) zapatas.setValue("");
        var losas = erickDelgadoCapa(erickDelgadoCapasCompatibles(plano,Set.of("RECTÁNGULO")), sugerido.de(com.concretolab.service.DeteccionCapasDxf.Rol.LOSAS));
        losas.getItems().add(0,"");
        if (losas.getValue()==null) losas.setValue("");
        var muros = erickDelgadoCapa(erickDelgadoCapasCompatibles(plano,Set.of("LINE")), sugerido.de(com.concretolab.service.DeteccionCapasDxf.Rol.MUROS));
        muros.getItems().add(0,"");
        if (muros.getValue()==null) muros.setValue("");
        var espesorLosa = new TextField();
        var espesorMuro = new TextField();
        espesorLosa.disableProperty().bind(losas.valueProperty().isEqualTo(""));
        espesorMuro.disableProperty().bind(muros.valueProperty().isEqualTo(""));

        var disponibles = new ComboBox<Proyecto>();
        disponibles.getItems().setAll(proyectos.proyectos());
        disponibles.setPromptText("Crear proyecto nuevo");
        var usarExistente = new CheckBox("Guardar en un proyecto existente vacío");
        disponibles.disableProperty().bind(usarExistente.selectedProperty().not());
        if(activo != null) disponibles.getItems().stream().filter(p->Objects.equals(p.getId(),activo.getId()))
                .findFirst().ifPresent(disponibles::setValue);
        var nombre = new TextField(archivo.getName().replaceFirst("(?i)\\.(dxf(?:\\.txt)?|dwg)$", ""));
        nombre.disableProperty().bind(usarExistente.selectedProperty());
        double factorUnidad = FernandoHuancaPlanoDxf.fernandoHuancaMetrosPorUnidad(plano.unidad());
        double estimada = com.concretolab.service.DeteccionCapasDxf.erickDelgadoUnidad(plano).metrosPorUnidad();
        double factorSugerido = Double.isNaN(factorUnidad) ? estimada : factorUnidad;
        var escala = new TextField(Double.isNaN(factorSugerido) ? "" : java.math.BigDecimal.valueOf(factorSugerido).stripTrailingZeros().toPlainString());
        var grilla = new GridPane();
        grilla.setHgap(10);
        grilla.setVgap(7);
        var campos = new ArrayList<TextField>();
        String[] etiquetas = {"Cantidad de pisos","Altura por piso (m)","Lado de columna si hay puntos (m)",
                "Ancho de viga (m)","Peralte de viga (m)","Lado de zapata si hay puntos (m)","Espesor de zapata (m)"};
        grilla.addRow(0,new Label("Columnas · rectángulos, círculos, bloques o puntos"),columnas);
        grilla.addRow(1,new Label("Vigas · líneas o polilíneas de 2 puntos"),vigas);
        grilla.addRow(2,new Label("Zapatas · rectángulos o puntos"),zapatas);
        grilla.addRow(3,new Label("Metros por unidad DXF"),escala);
        for (int fila=0;fila<etiquetas.length;fila++) {
            var campo = new TextField();
            grilla.addRow(4+fila,new Label(etiquetas[fila]),campo);
            campos.add(campo);
        }
        grilla.addRow(4+etiquetas.length,new Label("Losas · rectángulos (opcional)"),losas);
        grilla.addRow(5+etiquetas.length,new Label("Espesor de losa (m)"),espesorLosa);
        grilla.addRow(6+etiquetas.length,new Label("Muros · líneas (opcional)"),muros);
        grilla.addRow(7+etiquetas.length,new Label("Espesor de muro (m)"),espesorMuro);
        campos.get(2).disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                ()->!erickDelgadoTienePuntos(plano,columnas.getValue()),columnas.valueProperty()));
        campos.get(5).disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                ()->!erickDelgadoTienePuntos(plano,zapatas.getValue()),zapatas.valueProperty()));
        campos.get(6).disableProperty().bind(zapatas.valueProperty().isEqualTo(""));
        inventario.setPrefRowCount(8);
        inventario.setMinHeight(150);
        var estado = new Label(modeloCompleto
                ? "Modelo 3D recuperado del DXF (unidad: " + FernandoHuancaPlanoDxf.fernandoHuancaNombreUnidad(plano.unidad()) + (Double.isNaN(factorUnidad) ? "; se asumen metros" : "; escala a metros ×" + java.math.BigDecimal.valueOf(factorUnidad).stripTrailingZeros().toPlainString()) + "). Revise el conteo y las dimensiones antes de guardar."
                : "Revise las capas y complete las dimensiones de la planta que se repetirá en altura.");
        estado.setWrapText(true);
        estado.setMinHeight(Region.USE_PREF_SIZE);
        var contenido = new VBox(10,new Label("Capas y entidades del archivo"),inventario,usarExistente,
                disponibles,new Label("Nombre del proyecto nuevo"),nombre,grilla);
        contenido.setPadding(new Insets(12));
        if (modeloCompleto) contenido.getChildren().remove(grilla);
        if ("planta-estructural-ejemplo.dxf".equalsIgnoreCase(archivo.getName())) {
            var completar = new Button("Completar valores del ejemplo");
            completar.setOnAction(evento -> {
                String[] valores = {"3","3","0.40","0.30","0.50","1.50","0.50"};
                escala.setText("0.001");
                for (int i=0;i<valores.length;i++) campos.get(i).setText(valores[i]);
                estado.setText("Valores de demostración cargados. Pulse «Revisar modelo».");
            });
            contenido.getChildren().add(2, completar);
        }
        var scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(495);
        var panel = new VBox(10,scroll,estado);
        VBox.setVgrow(scroll,Priority.ALWAYS);
        dialogo.getDialogPane().setContent(panel);
        dialogo.getDialogPane().setPrefWidth(650);
        dialogo.setResizable(true);
        Proyecto[] resultado = new Proyecto[1];
        for (var boton:List.of(revisar,guardar)) dialogo.getDialogPane().lookupButton(boton)
                .addEventFilter(ActionEvent.ACTION,evento->{
                    try {
                        if (!modeloCompleto && (columnas.getValue()==null || vigas.getValue()==null))
                            throw new IllegalArgumentException("Seleccione las capas de columnas y vigas.");
                        String z = zapatas.getValue();
                        var candidatos = modeloCompleto ? FernandoHuancaPlanoDxf.fernandoHuancaEscalar(plano.modelo3d(), Double.isNaN(factorUnidad) ? 1.0 : factorUnidad) : erickDelgadoLector.fernandoHuancaConvertir(plano,
                                new Datos(columnas.getValue(),vigas.getValue(),z,erickDelgadoNumero(escala),
                                Integer.parseInt(campos.get(0).getText().strip()),erickDelgadoNumero(campos.get(1)),
                                campos.get(2).isDisabled()?Double.NaN:erickDelgadoNumero(campos.get(2)),erickDelgadoNumero(campos.get(3)),erickDelgadoNumero(campos.get(4)),
                                campos.get(5).isDisabled()?Double.NaN:erickDelgadoNumero(campos.get(5)),z.isBlank()?1:erickDelgadoNumero(campos.get(6))),
                                new FernandoHuancaPlanoDxf.Extra(losas.getValue()==null?"":losas.getValue(),muros.getValue()==null?"":muros.getValue(),
                                espesorLosa.isDisabled()?Double.NaN:erickDelgadoNumero(espesorLosa),espesorMuro.isDisabled()?Double.NaN:erickDelgadoNumero(espesorMuro)));
                        if (boton == revisar) {
                            var conteo = candidatos.stream().collect(Collectors.groupingBy(
                                    com.concretolab.model.Elemento::getTipo,TreeMap::new,Collectors.counting()));
                            var metrado = com.concretolab.service.MetradoNeto.yefriLermaCalcular(candidatos);
                            estado.setText("Geometría propuesta · "+conteo+String.format(Locale.ROOT," · concreto neto %.2f m³ (bruto %.2f)",metrado.volumenNeto(),metrado.volumenBruto())
                                    +". Revise dimensiones y unidades antes de guardar.");
                            evento.consume();
                        } else {
                            if (usarExistente.isSelected() && disponibles.getValue()==null)
                                throw new IllegalArgumentException("Seleccione un proyecto existente.");
                            resultado[0] = proyectos.yefriLermaGuardarPlano(
                                    usarExistente.isSelected()?disponibles.getValue().getId():null,
                                    nombre.getText(), candidatos);
                        }
                    } catch (NumberFormatException ex) {
                        estado.setText("Complete los campos con números válidos. La cantidad de pisos debe ser entera.");
                        evento.consume();
                    } catch (RuntimeException ex) {
                        estado.setText(ex.getMessage());
                        evento.consume();
                    }
                });
        dialogo.setResultConverter(b->b==guardar?resultado[0]:null);
        return dialogo.showAndWait();
    }

    private Optional<FernandoHuancaPlanoDxf.Plano> erickDelgadoConvertirDwg(Window ventana, java.io.File archivo) {
        var encontrado = FernandoHuancaPlanoDxf.fernandoHuancaLocalizarDwgread();
        if (encontrado.isPresent()) return erickDelgadoConvertirDwg(ventana,archivo,encontrado.get());
        var instrucciones = new Alert(Alert.AlertType.CONFIRMATION,
                "No se encontró el conversor DWG (dwgread.exe de LibreDWG). Colóquelo en la carpeta tools junto a la aplicación o selecciónelo ahora. También puede guardar una copia DXF desde AutoCAD, que es la vía más confiable.",
                ButtonType.OK, ButtonType.CANCEL);
        instrucciones.initOwner(ventana);
        if (instrucciones.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return Optional.empty();
        var selector = new FileChooser();
        selector.setTitle("Seleccionar dwgread.exe de LibreDWG");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("LibreDWG", "dwgread.exe"));
        var conversor = selector.showOpenDialog(ventana);
        if (conversor == null) return Optional.empty();
        return erickDelgadoConvertirDwg(ventana,archivo,conversor.toPath());
    }

    private Optional<FernandoHuancaPlanoDxf.Plano> erickDelgadoConvertirDwg(Window ventana, java.io.File archivo, java.nio.file.Path conversor) {
        var conversion = new javafx.concurrent.Task<FernandoHuancaPlanoDxf.Plano>() {
            @Override protected FernandoHuancaPlanoDxf.Plano call() throws Exception {
                return erickDelgadoLector.fernandoHuancaLeerDwg(archivo.toPath(), conversor);
            }
        };
        var espera = new Dialog<FernandoHuancaPlanoDxf.Plano>();
        espera.initOwner(ventana);
        espera.setTitle("Convertir DWG");
        espera.getDialogPane().setContent(new HBox(12,new ProgressIndicator(),new Label("Convirtiendo a DXF localmente…")));
        espera.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        espera.setOnShown(evento -> {
            var hilo = new Thread(conversion, "concretolab-dwg");
            hilo.setDaemon(true);
            hilo.start();
        });
        espera.setOnHidden(evento -> { if (conversion.isRunning()) conversion.cancel(); });
        conversion.setOnSucceeded(evento -> {
            espera.setResult(conversion.getValue());
            espera.close();
        });
        conversion.setOnFailed(evento -> {
            espera.close();
            var aviso = new Alert(Alert.AlertType.ERROR, conversion.getException().getMessage());
            aviso.initOwner(ventana);
            aviso.showAndWait();
        });
        return espera.showAndWait();
    }

    private static boolean erickDelgadoTienePuntos(FernandoHuancaPlanoDxf.Plano plano,String capa) {
        return plano.entidades().stream().anyMatch(e->e.capa().equals(capa) && ("POINT".equals(e.tipo()) || "BLOQUE".equals(e.tipo())));
    }

    private static List<String> erickDelgadoCapasCompatibles(FernandoHuancaPlanoDxf.Plano plano,Set<String> tipos) {
        return plano.entidades().stream().collect(Collectors.groupingBy(FernandoHuancaPlanoDxf.Entidad::capa,TreeMap::new,Collectors.toList()))
                .entrySet().stream().filter(e->e.getValue().size()<=200 && e.getValue().stream().allMatch(x->tipos.contains(x.tipo())))
                .map(Map.Entry::getKey).toList();
    }

    private static TextArea erickDelgadoInventario(FernandoHuancaPlanoDxf.Plano plano) {
        var area = new TextArea(com.concretolab.service.DeteccionCapasDxf.erickDelgadoDiagnosticar(plano).texto());
        area.setEditable(false);
        return area;
    }

    private static void erickDelgadoMostrarInventario(Window ventana,String nombre,TextArea inventario,boolean vial) {
        var aviso = new Dialog<Void>();
        aviso.initOwner(ventana);
        aviso.setTitle("Capas del archivo CAD");
        aviso.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        var mensaje = new Label(vial
                ? "Se detectaron capas de corredores y secciones viales. Este archivo se abrió para consultar sus capas."
                : "El archivo se abrió, pero faltan capas compatibles de columnas o vigas para generar un edificio.");
        mensaje.setWrapText(true);
        aviso.getDialogPane().setContent(new VBox(10,new Label(nombre),mensaje,inventario));
        aviso.getDialogPane().setPrefWidth(650);
        aviso.setResizable(true);
        aviso.showAndWait();
    }

    private static ComboBox<String> erickDelgadoCapa(List<String> capas,String detectada,String... sugeridas) {
        var combo = new ComboBox<String>();
        combo.getItems().setAll(capas);
        combo.setPromptText("Seleccione una capa");
        if (detectada != null && capas.contains(detectada)) { combo.setValue(detectada); return combo; }
        capas.stream().filter(c->Arrays.stream(sugeridas).anyMatch(c::equalsIgnoreCase)).findFirst().ifPresent(combo::setValue);
        return combo;
    }
    private static double erickDelgadoNumero(TextField campo) {
        return Double.parseDouble(campo.getText().strip().replace(',','.'));
    }
}

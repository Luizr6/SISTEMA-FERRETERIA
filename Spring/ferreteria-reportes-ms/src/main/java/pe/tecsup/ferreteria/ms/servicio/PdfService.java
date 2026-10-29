package pe.tecsup.ferreteria.ms.servicio;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.web.dto.ReporteStockCritico;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia;

/** Exporta los reportes a PDF (A4) con OpenPDF. */
@Service
public class PdfService {

    private static final Color AZUL = new Color(37, 99, 235);
    private static final Color GRIS_TEXTO = new Color(75, 85, 99);
    private static final Color GRIS_FONDO = new Color(243, 244, 246);
    private static final Color GRIS_BORDE = new Color(229, 231, 235);
    private static final Color ROJO = new Color(220, 38, 38);
    private static final Color AMBAR = new Color(180, 83, 9);

    private static final Font TITULO = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(17, 24, 39));
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS_TEXTO);
    private static final Font SECCION = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(17, 24, 39));
    private static final Font CABECERA = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
    private static final Font NORMAL = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, new Color(31, 41, 55));
    private static final Font CIFRA_GRANDE = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(17, 24, 39));
    private static final java.util.Set<String> COLUMNAS_NUMERICAS =
            java.util.Set.of("Ventas", "Total", "Unidades", "Importe", "Stock", "Mínimo", "Pedir", "Costo");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FerreteriaProperties props;
    private final ZoneId zona;

    public PdfService(FerreteriaProperties props) {
        this.props = props;
        this.zona = props.zona();
    }

    public byte[] ventasDelDia(ReporteVentasDia r) {
        return generar(doc -> {
            encabezado(doc, "Reporte de ventas del día " + FECHA.format(r.fecha()),
                    "Generado el " + FECHA_HORA.format(r.generadoEn().atZone(zona)));

            PdfPTable kpis = new PdfPTable(4);
            kpis.setWidthPercentage(100);
            kpi(kpis, "Total vendido (con IGV)", soles(r.total()));
            kpi(kpis, "Ventas completadas", String.valueOf(r.ventasCompletadas()));
            kpi(kpis, "Ticket promedio", soles(r.ticketPromedio()));
            kpi(kpis, "Anuladas", r.ventasAnuladas() + " (" + soles(r.montoAnulado()) + ")");
            doc.add(kpis);

            Paragraph igv = new Paragraph("Op. gravada " + soles(r.subtotal()) + "    IGV " + soles(r.igv()), SUBTITULO);
            igv.setSpacingBefore(4);
            doc.add(igv);

            seccion(doc, "Por forma de pago");
            doc.add(tablaFilas("Forma de pago", r.porMetodoPago()));

            seccion(doc, "Por vendedor");
            doc.add(tablaFilas("Vendedor", r.porVendedor()));

            seccion(doc, "Por canal de venta");
            doc.add(tablaFilas("Canal", r.porOrigen()));

            seccion(doc, "Productos más vendidos");
            PdfPTable top = tabla(new float[] {1.3f, 4, 1.2f, 1.6f}, "Código", "Producto", "Unidades", "Importe");
            for (ReporteVentasDia.ProductoVendido p : r.topProductos()) {
                celda(top, p.codigo(), Element.ALIGN_LEFT, null);
                celda(top, p.nombre(), Element.ALIGN_LEFT, null);
                celda(top, String.valueOf(p.unidades()), Element.ALIGN_RIGHT, null);
                celda(top, soles(p.importe()), Element.ALIGN_RIGHT, null);
            }
            vacioSiCorresponde(top, r.topProductos().isEmpty(), 4);
            doc.add(top);

            seccion(doc, "Ventas por hora");
            PdfPTable horas = tabla(new float[] {2, 1, 2}, "Hora", "Ventas", "Total");
            r.porHora().stream().filter(h -> h.cantidad() > 0).forEach(h -> {
                celda(horas, String.format("%02d:00 - %02d:59", h.hora(), h.hora()), Element.ALIGN_LEFT, null);
                celda(horas, String.valueOf(h.cantidad()), Element.ALIGN_RIGHT, null);
                celda(horas, soles(h.total()), Element.ALIGN_RIGHT, null);
            });
            vacioSiCorresponde(horas, r.ventasCompletadas() == 0, 3);
            doc.add(horas);
        });
    }

    public byte[] stockCritico(ReporteStockCritico r) {
        return generar(doc -> {
            encabezado(doc, "Reporte de stock crítico",
                    "Generado el " + FECHA_HORA.format(r.generadoEn().atZone(zona)));

            PdfPTable kpis = new PdfPTable(4);
            kpis.setWidthPercentage(100);
            kpi(kpis, "Productos por reponer", String.valueOf(r.totalCriticos()));
            kpi(kpis, "Agotados", String.valueOf(r.agotados()));
            kpi(kpis, "Productos activos", String.valueOf(r.productosActivos()));
            kpi(kpis, "Costo de reposición", soles(r.costoReposicionEstimado()));
            doc.add(kpis);

            seccion(doc, "Detalle (cantidad sugerida: hasta el doble del stock mínimo)");
            PdfPTable t = tabla(new float[] {1.2f, 3.4f, 2.4f, 1, 1, 1.1f, 1.1f, 1.5f},
                    "Código", "Producto", "Proveedor", "Stock", "Mínimo", "Estado", "Pedir", "Costo");
            for (ReporteStockCritico.Item i : r.productos()) {
                Color color = "AGOTADO".equals(i.estado()) ? ROJO : AMBAR;
                celda(t, i.codigo(), Element.ALIGN_LEFT, null);
                celda(t, i.nombre(), Element.ALIGN_LEFT, null);
                celda(t, i.proveedor() == null ? "-" : i.proveedor(), Element.ALIGN_LEFT, null);
                celda(t, i.stockActual() + " " + i.unidadMedida().toLowerCase(Locale.ROOT), Element.ALIGN_RIGHT, color);
                celda(t, String.valueOf(i.stockMinimo()), Element.ALIGN_RIGHT, null);
                celda(t, "AGOTADO".equals(i.estado()) ? "Agotado" : "Bajo", Element.ALIGN_LEFT, color);
                celda(t, String.valueOf(i.cantidadSugerida()), Element.ALIGN_RIGHT, null);
                celda(t, soles(i.costoReposicion()), Element.ALIGN_RIGHT, null);
            }
            vacioSiCorresponde(t, r.productos().isEmpty(), 8);
            doc.add(t);
        });
    }

    // ------------------------------------------------------------------ bloques reutilizables

    @FunctionalInterface
    private interface Contenido {
        void escribir(Document doc) throws Exception;
    }

    private byte[] generar(Contenido contenido) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.open();
            contenido.escribir(doc);
            Paragraph pie = new Paragraph(
                    "Reporte interno generado por el microservicio de reportes del SISTEMA-FERRETERIA.", SUBTITULO);
            pie.setSpacingBefore(16);
            doc.add(pie);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return salida.toByteArray();
    }

    private void encabezado(Document doc, String titulo, String subtitulo) throws Exception {
        Paragraph negocio = new Paragraph(props.negocio().nombre() + "  -  RUC " + props.negocio().ruc(),
                new Font(Font.HELVETICA, 9, Font.BOLD, AZUL));
        doc.add(negocio);
        Paragraph t = new Paragraph(titulo, TITULO);
        t.setSpacingBefore(2);
        doc.add(t);
        Paragraph s = new Paragraph(subtitulo, SUBTITULO);
        s.setSpacingAfter(12);
        doc.add(s);
    }

    private static void seccion(Document doc, String texto) throws Exception {
        Paragraph p = new Paragraph(texto, SECCION);
        p.setSpacingBefore(14);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private static void kpi(PdfPTable t, String etiqueta, String valor) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(GRIS_BORDE);
        c.setPadding(8);
        c.addElement(new Phrase(etiqueta, SUBTITULO));
        c.addElement(new Phrase(valor, CIFRA_GRANDE));
        t.addCell(c);
    }

    private static PdfPTable tabla(float[] anchos, String... cabeceras) {
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String c : cabeceras) {
            PdfPCell celda = new PdfPCell(new Phrase(c, CABECERA));
            if (COLUMNAS_NUMERICAS.contains(c)) {
                celda.setHorizontalAlignment(Element.ALIGN_RIGHT); // alineado con los valores
            }
            celda.setBackgroundColor(AZUL);
            celda.setBorderColor(AZUL);
            celda.setPadding(5);
            t.addCell(celda);
        }
        return t;
    }

    private static PdfPTable tablaFilas(String titulo, java.util.List<ReporteVentasDia.Fila> filas) {
        PdfPTable t = tabla(new float[] {4, 1.2f, 1.8f}, titulo, "Ventas", "Total");
        for (ReporteVentasDia.Fila f : filas) {
            celda(t, legible(f.clave()), Element.ALIGN_LEFT, null);
            celda(t, String.valueOf(f.cantidad()), Element.ALIGN_RIGHT, null);
            celda(t, soles(f.total()), Element.ALIGN_RIGHT, null);
        }
        vacioSiCorresponde(t, filas.isEmpty(), 3);
        return t;
    }

    private static void celda(PdfPTable t, String texto, int alineacion, Color color) {
        Font f = color == null ? NORMAL : new Font(Font.HELVETICA, 8.5f, Font.BOLD, color);
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto, f));
        c.setHorizontalAlignment(alineacion);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(GRIS_BORDE);
        c.setPadding(5);
        t.addCell(c);
    }

    private static void vacioSiCorresponde(PdfPTable t, boolean vacio, int columnas) {
        if (vacio) {
            PdfPCell c = new PdfPCell(new Phrase("Sin datos para este periodo.", SUBTITULO));
            c.setColspan(columnas);
            c.setBackgroundColor(GRIS_FONDO);
            c.setBorderColor(GRIS_BORDE);
            c.setPadding(8);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(c);
        }
    }

    /** EFECTIVO -> Efectivo, WHATSAPP -> Whatsapp (claves de Django en mayúsculas) */
    private static String legible(String clave) {
        if (clave == null || clave.isEmpty() || !clave.equals(clave.toUpperCase(Locale.ROOT))) {
            return clave;
        }
        String s = clave.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String soles(BigDecimal monto) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "S/ " + f.format(monto == null ? BigDecimal.ZERO : monto);
    }
}

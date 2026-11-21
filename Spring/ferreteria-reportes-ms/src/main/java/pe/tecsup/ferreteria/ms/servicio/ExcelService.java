package pe.tecsup.ferreteria.ms.servicio;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.web.dto.Kardex;
import pe.tecsup.ferreteria.ms.web.dto.ReporteCajas;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia.Fila;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia.ProductoVendido;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasPeriodo;

/** Exporta los reportes a Excel (.xlsx) con Apache POI: una hoja por sección. */
@Service
public class ExcelService {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FerreteriaProperties props;
    private final ZoneId zona;

    public ExcelService(FerreteriaProperties props) {
        this.props = props;
        this.zona = props.zona();
    }

    public byte[] ventas(ReporteVentasPeriodo r) {
        return libro(l -> {
            l.hoja("Resumen", List.of("Indicador", "Valor"), List.of(
                    fila("Negocio", props.negocio().nombre()),
                    fila("Desde", r.desde()),
                    fila("Hasta", r.hasta()),
                    fila("Ventas completadas", r.ventasCompletadas()),
                    fila("Ventas anuladas", r.ventasAnuladas()),
                    fila("Op. gravada (sin IGV)", r.subtotal()),
                    fila("IGV", r.igv()),
                    fila("Total vendido (con IGV)", r.total()),
                    fila("Ticket promedio", r.ticketPromedio()),
                    fila("Monto anulado", r.montoAnulado())));
            l.hoja("Por día", List.of("Fecha", "Ventas", "Total"), filas(r.porDia()));
            l.hoja("Por vendedor", List.of("Vendedor", "Ventas", "Total"), filas(r.porVendedor()));
            l.hoja("Por categoría", List.of("Categoría", "Unidades", "Valor de venta (sin IGV)"), filas(r.porCategoria()));
            l.hoja("Por forma de pago", List.of("Forma de pago", "Ventas", "Total"), filas(r.porMetodoPago()));
            l.hoja("Por canal", List.of("Canal", "Ventas", "Total"), filas(r.porOrigen()));
            List<Object[]> top = new ArrayList<>();
            for (ProductoVendido p : r.topProductos()) {
                top.add(fila(p.codigo(), p.nombre(), p.unidades(), p.importe()));
            }
            l.hoja("Productos más vendidos", List.of("Código", "Producto", "Unidades", "Importe (sin IGV)"), top);
            List<Object[]> ventas = new ArrayList<>();
            if (r.ventas() != null) {
                for (ReporteVentasPeriodo.VentaFila v : r.ventas()) {
                    ventas.add(fila(v.numero(), v.fecha(), v.cliente(), v.vendedor(), v.metodoPago(), v.origen(),
                            v.estado(), v.subtotal(), v.igv(), v.total()));
                }
            }
            l.hoja("Ventas", List.of("Comprobante", "Fecha", "Cliente", "Vendedor", "Pago", "Canal", "Estado",
                    "Op. gravada", "IGV", "Total"), ventas);
        });
    }

    public byte[] cajas(ReporteCajas r) {
        return libro(l -> {
            List<Object[]> v = new ArrayList<>();
            for (ReporteCajas.PorVendedor p : r.porVendedor()) {
                v.add(fila(p.vendedor(), p.cajas(), p.conFaltante(), p.faltante(), p.sobrante(), p.vendido()));
            }
            l.hoja("Por vendedor", List.of("Vendedor", "Cajas", "Con faltante", "Faltante", "Sobrante", "Vendido"), v);
            List<Object[]> d = new ArrayList<>();
            for (ReporteCajas.CajaFila c : r.detalle()) {
                d.add(fila(c.id(), c.vendedor(), c.estado(), c.fechaApertura(), c.fechaCierre(), c.montoInicial(),
                        c.ventasEfectivo(), c.ventasOtrosMedios(), c.ingresos(), c.egresos(), c.esperado(),
                        c.contado(), c.diferencia(), c.observacion()));
            }
            l.hoja("Cajas", List.of("N.°", "Vendedor", "Estado", "Apertura", "Cierre", "Inicial", "Ventas efectivo",
                    "Otros medios", "Ingresos", "Egresos", "Esperado", "Contado", "Diferencia", "Observación"), d);
        });
    }

    public byte[] kardex(Kardex k) {
        return libro(l -> {
            List<Object[]> filas = new ArrayList<>();
            filas.add(fila(null, "SALDO INICIAL", null, null, null, null, k.stockInicial()));
            for (Kardex.Movimiento m : k.movimientos()) {
                filas.add(fila(m.fecha(), m.tipo(), m.motivo(), m.usuario(), m.entrada(), m.salida(), m.saldo()));
            }
            filas.add(fila(null, "TOTALES", null, null, k.entradas(), k.salidas(), k.stockFinal()));
            l.hoja("Kardex " + k.codigo(), List.of("Fecha", "Tipo", "Motivo", "Usuario", "Entrada", "Salida", "Saldo"), filas);
        });
    }

    // ------------------------------------------------------------------ armado del libro

    @FunctionalInterface
    private interface Contenido {
        void escribir(Libro l);
    }

    /** Envuelve el XSSFWorkbook con los estilos comunes. */
    private final class Libro {
        private final XSSFWorkbook wb = new XSSFWorkbook();
        private final CellStyle cabecera;
        private final CellStyle soles;
        private final CellStyle fecha;

        Libro() {
            cabecera = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font negrita = wb.createFont();
            negrita.setBold(true);
            negrita.setColor(IndexedColors.WHITE.getIndex());
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setBorderBottom(BorderStyle.THIN);

            soles = wb.createCellStyle();
            soles.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            fecha = wb.createCellStyle();
            fecha.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
        }

        void hoja(String nombre, List<String> titulos, List<Object[]> filas) {
            Sheet hoja = wb.createSheet(nombre.length() > 31 ? nombre.substring(0, 31) : nombre);
            Row r0 = hoja.createRow(0);
            for (int i = 0; i < titulos.size(); i++) {
                Cell c = r0.createCell(i);
                c.setCellValue(titulos.get(i));
                c.setCellStyle(cabecera);
            }
            int n = 1;
            for (Object[] fila : filas) {
                Row r = hoja.createRow(n++);
                for (int i = 0; i < fila.length; i++) {
                    escribir(r.createCell(i), fila[i]);
                }
            }
            hoja.createFreezePane(0, 1);
            for (int i = 0; i < titulos.size(); i++) {
                hoja.setColumnWidth(i, Math.min(60, Math.max(12, titulos.get(i).length() + 4)) * 256);
            }
        }

        private void escribir(Cell c, Object v) {
            if (v == null) {
                c.setBlank();
            } else if (v instanceof BigDecimal b) {
                c.setCellValue(b.doubleValue());
                c.setCellStyle(soles);
            } else if (v instanceof Number num) {
                c.setCellValue(num.doubleValue());
            } else if (v instanceof Instant i) {
                c.setCellValue(FECHA_HORA.format(i.atZone(zona)));
            } else if (v instanceof LocalDate d) {
                c.setCellValue(java.util.Date.from(d.atStartOfDay(zona).toInstant()));
                c.setCellStyle(fecha);
            } else {
                c.setCellValue(String.valueOf(v));
            }
        }
    }

    private byte[] libro(Contenido contenido) {
        Libro l = new Libro();
        try (XSSFWorkbook wb = l.wb; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            contenido.escribir(l);
            wb.write(salida);
            return salida.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel: " + e.getMessage(), e);
        }
    }

    private static List<Object[]> filas(List<Fila> lista) {
        List<Object[]> r = new ArrayList<>();
        for (Fila f : lista) {
            r.add(fila(f.clave(), f.cantidad(), f.total()));
        }
        return r;
    }

    private static Object[] fila(Object... valores) {
        return valores;
    }
}

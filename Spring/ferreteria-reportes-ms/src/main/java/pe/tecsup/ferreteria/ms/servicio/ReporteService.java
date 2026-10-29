package pe.tecsup.ferreteria.ms.servicio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.tecsup.ferreteria.ms.config.FerreteriaProperties;
import pe.tecsup.ferreteria.ms.dominio.django.DetalleVenta;
import pe.tecsup.ferreteria.ms.dominio.django.Producto;
import pe.tecsup.ferreteria.ms.dominio.django.Venta;
import pe.tecsup.ferreteria.ms.repositorio.ProductoRepository;
import pe.tecsup.ferreteria.ms.repositorio.VentaRepository;
import pe.tecsup.ferreteria.ms.web.dto.ReporteStockCritico;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia.Fila;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia.ProductoVendido;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasDia.VentasPorHora;

/** Reportes gerenciales calculados sobre los datos de Django (solo lectura). */
@Service
@Transactional(readOnly = true)
public class ReporteService {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final VentaRepository ventas;
    private final ProductoRepository productos;
    private final ZoneId zona;

    public ReporteService(VentaRepository ventas, ProductoRepository productos, FerreteriaProperties props) {
        this.ventas = ventas;
        this.productos = productos;
        this.zona = props.zona();
    }

    /** Reporte de ventas de un día calendario en hora de Lima (Django guarda las fechas en UTC). */
    public ReporteVentasDia ventasDelDia(LocalDate fecha) {
        Instant desde = fecha.atStartOfDay(zona).toInstant();
        Instant hasta = fecha.plusDays(1).atStartOfDay(zona).toInstant();
        List<Venta> delDia = ventas.buscarDelPeriodo(desde, hasta);

        List<Venta> ok = delDia.stream().filter(Venta::esCompletada).toList();
        List<Venta> anuladas = delDia.stream().filter(v -> !v.esCompletada()).toList();

        BigDecimal subtotal = sumar(ok, Venta::getSubtotal);
        BigDecimal igv = sumar(ok, Venta::getIgv);
        BigDecimal total = sumar(ok, Venta::getTotal);
        BigDecimal ticket = ok.isEmpty() ? CERO
                : total.divide(BigDecimal.valueOf(ok.size()), 2, RoundingMode.HALF_UP);

        return new ReporteVentasDia(
                fecha, zona.getId(), ok.size(), anuladas.size(),
                subtotal, igv, total, ticket, sumar(anuladas, Venta::getTotal),
                agrupar(ok, Venta::getMetodoPago),
                agrupar(ok, v -> v.getVendedor().getNombreVisible()),
                agrupar(ok, Venta::getOrigen),
                porHora(ok),
                topProductos(ok, 10),
                Instant.now());
    }

    /** Productos por reponer, con cantidad sugerida (mismo criterio que Django: hasta el doble del mínimo). */
    public ReporteStockCritico stockCritico() {
        List<Producto> criticos = productos.buscarStockCritico();
        List<ReporteStockCritico.Item> items = new ArrayList<>();
        BigDecimal costoTotal = CERO;
        long agotados = 0;

        for (Producto p : criticos) {
            int stock = p.getStockActual();
            int minimo = p.getStockMinimo();
            int sugerida = Math.max(minimo * 2 - stock, 1);
            BigDecimal costo = p.getPrecioCompra().multiply(BigDecimal.valueOf(sugerida)).setScale(2, RoundingMode.HALF_UP);
            if (stock == 0) {
                agotados++;
            }
            costoTotal = costoTotal.add(costo);
            items.add(new ReporteStockCritico.Item(
                    p.getCodigo(), p.getNombre(), p.getCategoria().getNombre(),
                    p.getProveedor() == null ? null : p.getProveedor().getRazonSocial(),
                    p.getUnidadMedida(), stock, minimo, stock == 0 ? "AGOTADO" : "BAJO",
                    sugerida, p.getPrecioCompra(), costo));
        }
        return new ReporteStockCritico(items.size(), agotados, productos.contarActivos(), costoTotal, items, Instant.now());
    }

    // ------------------------------------------------------------------ utilidades

    private static BigDecimal sumar(List<Venta> lista, Function<Venta, BigDecimal> campo) {
        return lista.stream().map(campo).reduce(CERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<Fila> agrupar(List<Venta> lista, Function<Venta, String> clave) {
        Map<String, long[]> cantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> totales = new LinkedHashMap<>();
        for (Venta v : lista) {
            String k = clave.apply(v);
            cantidades.computeIfAbsent(k, x -> new long[1])[0]++;
            totales.merge(k, v.getTotal(), BigDecimal::add);
        }
        return cantidades.keySet().stream()
                .map(k -> new Fila(k, cantidades.get(k)[0], totales.get(k).setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(Fila::total).reversed())
                .toList();
    }

    private List<VentasPorHora> porHora(List<Venta> lista) {
        long[] cantidad = new long[24];
        BigDecimal[] total = new BigDecimal[24];
        for (int h = 0; h < 24; h++) {
            total[h] = CERO;
        }
        for (Venta v : lista) {
            int h = v.getFecha().atZone(zona).getHour();
            cantidad[h]++;
            total[h] = total[h].add(v.getTotal());
        }
        List<VentasPorHora> r = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            r.add(new VentasPorHora(h, cantidad[h], total[h].setScale(2, RoundingMode.HALF_UP)));
        }
        return r;
    }

    private static List<ProductoVendido> topProductos(List<Venta> lista, int limite) {
        Map<String, String> nombres = new LinkedHashMap<>();
        Map<String, Long> unidades = new LinkedHashMap<>();
        Map<String, BigDecimal> importes = new LinkedHashMap<>();
        for (Venta v : lista) {
            for (DetalleVenta d : v.getDetalles()) {
                String codigo = d.getProducto().getCodigo();
                nombres.putIfAbsent(codigo, d.getProducto().getNombre());
                unidades.merge(codigo, d.getCantidad().longValue(), Long::sum);
                importes.merge(codigo, d.getSubtotal(), BigDecimal::add);
            }
        }
        return unidades.keySet().stream()
                .map(c -> new ProductoVendido(c, nombres.get(c), unidades.get(c), importes.get(c).setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparingLong(ProductoVendido::unidades).reversed())
                .limit(limite)
                .toList();
    }
}

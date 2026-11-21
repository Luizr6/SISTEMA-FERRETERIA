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
import pe.tecsup.ferreteria.ms.dominio.django.Caja;
import pe.tecsup.ferreteria.ms.dominio.django.DetalleVenta;
import pe.tecsup.ferreteria.ms.dominio.django.MovimientoCaja;
import pe.tecsup.ferreteria.ms.dominio.django.MovimientoInventario;
import pe.tecsup.ferreteria.ms.dominio.django.Producto;
import pe.tecsup.ferreteria.ms.dominio.django.Venta;
import pe.tecsup.ferreteria.ms.repositorio.CajaRepository;
import pe.tecsup.ferreteria.ms.repositorio.MovimientoInventarioRepository;
import pe.tecsup.ferreteria.ms.repositorio.ProductoRepository;
import pe.tecsup.ferreteria.ms.repositorio.VentaRepository;
import pe.tecsup.ferreteria.ms.web.dto.Kardex;
import pe.tecsup.ferreteria.ms.web.dto.ReporteCajas;
import pe.tecsup.ferreteria.ms.web.dto.ReporteVentasPeriodo;
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

    /** Rango máximo de un reporte, para no cargar años de ventas en memoria. */
    private static final int MAX_DIAS = 366;

    private final VentaRepository ventas;
    private final ProductoRepository productos;
    private final CajaRepository cajas;
    private final MovimientoInventarioRepository kardex;
    private final ZoneId zona;

    public ReporteService(VentaRepository ventas, ProductoRepository productos, CajaRepository cajas,
                          MovimientoInventarioRepository kardex, FerreteriaProperties props) {
        this.ventas = ventas;
        this.productos = productos;
        this.cajas = cajas;
        this.kardex = kardex;
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


    /** Ventas de un rango [desde, hasta] (ambos inclusive, hora de Lima). */
    public ReporteVentasPeriodo ventasDelPeriodo(LocalDate desde, LocalDate hasta, boolean conDetalle) {
        validarRango(desde, hasta);
        List<Venta> todas = ventas.buscarDelPeriodo(inicio(desde), inicio(hasta.plusDays(1)));
        List<Venta> ok = todas.stream().filter(Venta::esCompletada).toList();
        List<Venta> anuladas = todas.stream().filter(v -> !v.esCompletada()).toList();
        BigDecimal total = sumar(ok, Venta::getTotal);
        BigDecimal ticket = ok.isEmpty() ? CERO : total.divide(BigDecimal.valueOf(ok.size()), 2, RoundingMode.HALF_UP);

        // Por día: todos los días del rango, aunque no haya ventas (para el gráfico)
        Map<String, long[]> cantDia = new LinkedHashMap<>();
        Map<String, BigDecimal> totalDia = new LinkedHashMap<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            cantDia.put(d.toString(), new long[1]);
            totalDia.put(d.toString(), CERO);
        }
        for (Venta v : ok) {
            String d = v.getFecha().atZone(zona).toLocalDate().toString();
            cantDia.get(d)[0]++;
            totalDia.merge(d, v.getTotal(), BigDecimal::add);
        }
        List<Fila> porDia = cantDia.keySet().stream()
                .map(d -> new Fila(d, cantDia.get(d)[0], totalDia.get(d).setScale(2, RoundingMode.HALF_UP)))
                .toList();

        // Por categoría: valor de venta de los productos (sin IGV)
        Map<String, long[]> cantCat = new LinkedHashMap<>();
        Map<String, BigDecimal> totalCat = new LinkedHashMap<>();
        for (Venta v : ok) {
            for (DetalleVenta d : v.getDetalles()) {
                String cat = d.getProducto().getCategoria().getNombre();
                cantCat.computeIfAbsent(cat, x -> new long[1])[0] += d.getCantidad();
                totalCat.merge(cat, d.getSubtotal(), BigDecimal::add);
            }
        }
        List<Fila> porCategoria = cantCat.keySet().stream()
                .map(k -> new Fila(k, cantCat.get(k)[0], totalCat.get(k).setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(Fila::total).reversed())
                .toList();

        List<ReporteVentasPeriodo.VentaFila> detalle = !conDetalle ? null : todas.stream()
                .map(v -> new ReporteVentasPeriodo.VentaFila(v.getId(), v.getNumero(), v.getFecha(),
                        v.getCliente() == null ? "Clientes varios" : v.getCliente().getNombre(),
                        v.getVendedor().getNombreVisible(), v.getMetodoPago(), v.getOrigen(), v.getEstado(),
                        v.getSubtotal(), v.getIgv(), v.getTotal()))
                .toList();

        return new ReporteVentasPeriodo(desde, hasta, ok.size(), anuladas.size(),
                sumar(ok, Venta::getSubtotal), sumar(ok, Venta::getIgv), total, ticket, sumar(anuladas, Venta::getTotal),
                porDia, agrupar(ok, Venta::getMetodoPago), agrupar(ok, v -> v.getVendedor().getNombreVisible()),
                agrupar(ok, Venta::getOrigen), porCategoria, topProductos(ok, 20), detalle, Instant.now());
    }

    /** Arqueos de las cajas abiertas en el rango, con faltantes y sobrantes por vendedor. */
    public ReporteCajas cajas(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        List<Caja> lista = cajas.delPeriodo(inicio(desde), inicio(hasta.plusDays(1)));
        Map<Long, List<MovimientoCaja>> movimientos = new LinkedHashMap<>();
        if (!lista.isEmpty()) {
            for (MovimientoCaja m : cajas.movimientosDe(lista.stream().map(Caja::getId).toList())) {
                movimientos.computeIfAbsent(m.getCajaId(), k -> new ArrayList<>()).add(m);
            }
        }

        List<ReporteCajas.CajaFila> detalle = new ArrayList<>();
        Map<String, Object[]> vendedores = new LinkedHashMap<>(); // [cajas, conFaltante, faltante, sobrante, vendido]
        BigDecimal faltanteTotal = CERO;
        BigDecimal sobranteTotal = CERO;
        BigDecimal vendidoTotal = CERO;
        long abiertas = 0;
        long conFaltante = 0;

        for (Caja c : lista) {
            BigDecimal efectivo = CERO;
            BigDecimal otros = CERO;
            for (Object[] fila : cajas.ventasPorMetodo(c.getId())) {
                BigDecimal monto = (BigDecimal) fila[1];
                if ("EFECTIVO".equals(fila[0])) {
                    efectivo = efectivo.add(monto);
                } else {
                    otros = otros.add(monto);
                }
            }
            BigDecimal ingresos = CERO;
            BigDecimal egresos = CERO;
            for (MovimientoCaja m : movimientos.getOrDefault(c.getId(), List.of())) {
                if ("EGRESO".equals(m.getTipo())) {
                    egresos = egresos.add(m.getMonto());
                } else {
                    ingresos = ingresos.add(m.getMonto());
                }
            }
            BigDecimal esperado = c.esAbierta() || c.getMontoEsperado() == null
                    ? c.getMontoInicial().add(efectivo).add(ingresos).subtract(egresos)
                    : c.getMontoEsperado();
            BigDecimal dif = c.getDiferencia() == null ? null : c.getDiferencia().setScale(2, RoundingMode.HALF_UP);
            String vendedor = c.getUsuario().getNombreVisible();

            detalle.add(new ReporteCajas.CajaFila(c.getId(), vendedor, c.getEstado(), c.getFechaApertura(),
                    c.getFechaCierre(), dos(c.getMontoInicial()), dos(efectivo), dos(otros), dos(ingresos), dos(egresos),
                    dos(esperado), c.getMontoContado() == null ? null : dos(c.getMontoContado()), dif, c.getObservacion()));

            Object[] acum = vendedores.computeIfAbsent(vendedor, k -> new Object[] {0L, 0L, CERO, CERO, CERO});
            acum[0] = (Long) acum[0] + 1;
            acum[4] = ((BigDecimal) acum[4]).add(efectivo).add(otros);
            vendidoTotal = vendidoTotal.add(efectivo).add(otros);
            if (c.esAbierta()) {
                abiertas++;
            }
            if (dif != null && dif.signum() < 0) {
                conFaltante++;
                acum[1] = (Long) acum[1] + 1;
                acum[2] = ((BigDecimal) acum[2]).add(dif.negate());
                faltanteTotal = faltanteTotal.add(dif.negate());
            } else if (dif != null && dif.signum() > 0) {
                acum[3] = ((BigDecimal) acum[3]).add(dif);
                sobranteTotal = sobranteTotal.add(dif);
            }
        }
        List<ReporteCajas.PorVendedor> porVendedor = vendedores.entrySet().stream()
                .map(e -> new ReporteCajas.PorVendedor(e.getKey(), (Long) e.getValue()[0], (Long) e.getValue()[1],
                        dos((BigDecimal) e.getValue()[2]), dos((BigDecimal) e.getValue()[3]), dos((BigDecimal) e.getValue()[4])))
                .sorted(Comparator.comparing(ReporteCajas.PorVendedor::faltante).reversed())
                .toList();
        return new ReporteCajas(desde, hasta, lista.size(), abiertas, conFaltante, dos(faltanteTotal),
                dos(sobranteTotal), dos(vendidoTotal), porVendedor, detalle, Instant.now());
    }

    /** Cajas que siguen abiertas (para las alertas del cierre del día). */
    public List<Caja> cajasAbiertas() {
        return cajas.abiertas();
    }

    /** Kardex de un producto por su código. */
    public Kardex kardex(String codigo, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        Producto p = kardex.productoPorCodigo(codigo == null ? "" : codigo.trim())
                .orElseThrow(() -> new java.util.NoSuchElementException("No existe un producto con el código " + codigo + "."));
        Instant ini = inicio(desde);
        List<MovimientoInventario> movs = kardex.kardex(p.getId(), ini, inicio(hasta.plusDays(1)));
        int stockInicial = kardex.findFirstByProductoIdAndFechaBeforeOrderByFechaDescIdDesc(p.getId(), ini)
                .map(MovimientoInventario::getStockResultante)
                .orElseGet(() -> movs.isEmpty() ? p.getStockActual() : movs.get(0).getStockResultante() - movs.get(0).getCantidad());
        long entradas = 0;
        long salidas = 0;
        List<Kardex.Movimiento> filas = new ArrayList<>();
        for (MovimientoInventario m : movs) {
            int entrada = Math.max(m.getCantidad(), 0);
            int salida = Math.max(-m.getCantidad(), 0);
            entradas += entrada;
            salidas += salida;
            filas.add(new Kardex.Movimiento(m.getFecha(), m.getTipo(), m.getMotivo(),
                    m.getUsuario() == null ? null : m.getUsuario().getNombreVisible(), entrada, salida, m.getStockResultante()));
        }
        int stockFinal = movs.isEmpty() ? stockInicial : movs.get(movs.size() - 1).getStockResultante();
        return new Kardex(p.getCodigo(), p.getNombre(), p.getCategoria().getNombre(), p.getUnidadMedida(), desde, hasta,
                stockInicial, entradas, salidas, stockFinal, p.getStockActual(), filas, Instant.now());
    }

    private Instant inicio(LocalDate dia) {
        return dia.atStartOfDay(zona).toInstant();
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        if (desde.plusDays(MAX_DIAS).isBefore(hasta)) {
            throw new IllegalArgumentException("El rango máximo es de " + MAX_DIAS + " días.");
        }
    }

    private static BigDecimal dos(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
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

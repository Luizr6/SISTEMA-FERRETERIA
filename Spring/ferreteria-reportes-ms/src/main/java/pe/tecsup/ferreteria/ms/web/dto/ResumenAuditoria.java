package pe.tecsup.ferreteria.ms.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ResumenAuditoria(
        int dias,
        Instant desde,
        long totalEventos,
        Map<String, Long> porTipo,
        Map<String, Long> porNivel,
        List<EventoResponse> ultimosCriticos) { }

package pe.tecsup.ferreteria.ms.web.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import pe.tecsup.ferreteria.ms.dominio.auditoria.NivelEvento;

public record EventoResponse(
        Long id,
        Instant fecha,
        String tipo,
        NivelEvento nivel,
        String origen,
        String modulo,
        String usuario,
        Long usuarioId,
        String entidad,
        String entidadId,
        String descripcion,
        JsonNode datos,
        String ip) { }

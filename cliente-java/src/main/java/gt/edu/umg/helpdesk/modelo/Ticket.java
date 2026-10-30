package gt.edu.umg.helpdesk.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

/** Un ticket tal como lo devuelve el WF5 (la API no incluye el correo del solicitante). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Ticket(
        @JsonProperty("codigo") String codigo,
        @JsonProperty("solicitante_nombre") String solicitanteNombre,
        @JsonProperty("departamento") String departamento,
        @JsonProperty("asunto") String asunto,
        @JsonProperty("categoria") String categoria,
        @JsonProperty("prioridad") String prioridad,
        @JsonProperty("estado") String estado,
        @JsonProperty("motivo_clasificacion") String motivoClasificacion,
        @JsonProperty("fecha_creacion") String fechaCreacion,
        @JsonProperty("fecha_clasificacion") String fechaClasificacion) {

    public Optional<Prioridad> prioridadAsignada() {
        return Prioridad.desde(prioridad);
    }

    public boolean estaClasificado() {
        return "CLASIFICADO".equals(estado);
    }
}

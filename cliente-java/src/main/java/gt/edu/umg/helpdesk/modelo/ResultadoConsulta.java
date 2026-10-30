package gt.edu.umg.helpdesk.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Respuesta completa del WF5. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResultadoConsulta(
        @JsonProperty("tickets") List<Ticket> tickets,
        @JsonProperty("resumen") Resumen resumen) {

    public ResultadoConsulta {
        tickets = tickets == null ? List.of() : List.copyOf(tickets);
    }
}

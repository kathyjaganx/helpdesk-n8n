package gt.edu.umg.helpdesk.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** Resumen que devuelve el WF5: totales por estado, prioridad y categoría. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Resumen(
        @JsonProperty("total") int total,
        @JsonProperty("por_estado") Map<String, Integer> porEstado,
        @JsonProperty("por_prioridad") Map<String, Integer> porPrioridad,
        @JsonProperty("por_categoria") Map<String, Integer> porCategoria) {

    public Resumen {
        porEstado = porEstado == null ? Map.of() : Map.copyOf(porEstado);
        porPrioridad = porPrioridad == null ? Map.of() : Map.copyOf(porPrioridad);
        porCategoria = porCategoria == null ? Map.of() : Map.copyOf(porCategoria);
    }

    public int enEspera() {
        return porEstado.getOrDefault("NUEVO", 0);
    }
}

package gt.edu.umg.helpdesk.servicio;

import gt.edu.umg.helpdesk.modelo.Prioridad;
import gt.edu.umg.helpdesk.modelo.Ticket;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reglas para atender la cola de tickets: qué está pendiente y qué
 * atender primero. Trabaja sobre la lista que devuelve el WF5.
 */
public class AnalizadorCola {

    /** Tickets que el WF2 todavía no clasifica. */
    public List<Ticket> enEspera(List<Ticket> tickets) {
        return tickets.stream().filter(t -> "NUEVO".equals(t.estado())).toList();
    }

    /** Cantidad de tickets por prioridad. Siempre incluye las tres prioridades. */
    public Map<Prioridad, Long> contarPorPrioridad(List<Ticket> tickets) {
        Map<Prioridad, Long> conteo = new EnumMap<>(Prioridad.class);
        for (Prioridad p : Prioridad.values()) {
            conteo.put(p, 0L);
        }
        tickets.forEach(t -> t.prioridadAsignada().ifPresent(p -> conteo.merge(p, 1L, Long::sum)));
        return conteo;
    }

    /**
     * Siguiente ticket a atender: el clasificado de mayor prioridad y, entre
     * los de igual prioridad, el más antiguo.
     */
    public Optional<Ticket> siguienteAAtender(List<Ticket> tickets) {
        return tickets.stream()
                .filter(Ticket::estaClasificado)
                .filter(t -> t.prioridadAsignada().isPresent())
                .min(Comparator
                        .comparing((Ticket t) -> t.prioridadAsignada().get())
                        .thenComparing(t -> t.fechaCreacion() == null ? "" : t.fechaCreacion()));
    }
}

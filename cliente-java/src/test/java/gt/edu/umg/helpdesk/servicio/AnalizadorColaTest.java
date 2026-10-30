package gt.edu.umg.helpdesk.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import gt.edu.umg.helpdesk.modelo.Prioridad;
import gt.edu.umg.helpdesk.modelo.Ticket;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AnalizadorCola")
class AnalizadorColaTest {

    private final AnalizadorCola cola = new AnalizadorCola();

    private static Ticket t(String codigo, String prioridad, String estado, String fecha) {
        return new Ticket(codigo, "Ana", "TI", "Asunto", null, prioridad, estado, null, fecha, null);
    }

    private final List<Ticket> tickets = List.of(
            t("TCK-000001", "BAJA", "CLASIFICADO", "2026-10-29 08:00"),
            t("TCK-000002", "ALTA", "CLASIFICADO", "2026-10-29 10:00"),
            t("TCK-000003", "ALTA", "CLASIFICADO", "2026-10-29 09:00"),
            t("TCK-000004", null, "NUEVO", "2026-10-29 11:00"),
            t("TCK-000005", "MEDIA", "CLASIFICADO", null));

    @Test
    @DisplayName("enEspera devuelve solo los tickets NUEVO")
    void enEspera() {
        assertEquals(List.of("TCK-000004"), cola.enEspera(tickets).stream().map(Ticket::codigo).toList());
    }

    @Test
    @DisplayName("cuenta por prioridad e ignora los que no tienen prioridad")
    void contarPorPrioridad() {
        assertEquals(Map.of(Prioridad.ALTA, 2L, Prioridad.MEDIA, 1L, Prioridad.BAJA, 1L), cola.contarPorPrioridad(tickets));
    }

    @Test
    @DisplayName("con lista vacía cuenta cero en las tres prioridades")
    void contarVacio() {
        assertEquals(Map.of(Prioridad.ALTA, 0L, Prioridad.MEDIA, 0L, Prioridad.BAJA, 0L), cola.contarPorPrioridad(List.of()));
    }

    @Test
    @DisplayName("siguiente a atender: mayor prioridad y, en empate, el más antiguo")
    void siguienteAAtender() {
        assertEquals("TCK-000003", cola.siguienteAAtender(tickets).orElseThrow().codigo());
    }

    @Test
    @DisplayName("un ticket sin fecha no rompe el orden")
    void sinFecha() {
        List<Ticket> medias = List.of(t("TCK-000005", "MEDIA", "CLASIFICADO", null),
                t("TCK-000006", "MEDIA", "CLASIFICADO", "2026-10-29 09:00"));
        assertEquals("TCK-000005", cola.siguienteAAtender(medias).orElseThrow().codigo());
    }

    @Test
    @DisplayName("sin tickets clasificados no hay siguiente")
    void sinClasificados() {
        assertFalse(cola.siguienteAAtender(List.of(t("TCK-000004", null, "NUEVO", null))).isPresent());
    }
}

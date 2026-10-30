package gt.edu.umg.helpdesk.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gt.edu.umg.helpdesk.excepciones.DatosInvalidosException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Modelo y excepciones")
class ModeloTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({"ALTA, ALTA", "media, MEDIA", "' Baja ', BAJA"})
    @DisplayName("Prioridad.desde reconoce las prioridades sin importar mayúsculas")
    void prioridadValida(String texto, Prioridad esperada) {
        assertEquals(Optional.of(esperada), Prioridad.desde(texto));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "URGENTE", "Sin asignar"})
    @DisplayName("Prioridad.desde devuelve vacío si no hay prioridad válida")
    void prioridadInvalida(String texto) {
        assertTrue(Prioridad.desde(texto).isEmpty());
    }

    @Test
    @DisplayName("el orden de Prioridad es el orden de urgencia")
    void ordenPrioridad() {
        assertTrue(Prioridad.ALTA.compareTo(Prioridad.MEDIA) < 0);
        assertTrue(Prioridad.MEDIA.compareTo(Prioridad.BAJA) < 0);
    }

    @Test
    @DisplayName("Ticket informa si está clasificado")
    void ticketClasificado() {
        Ticket nuevo = new Ticket("TCK-000001", "Ana", "TI", "Asunto", null, null, "NUEVO", null, null, null);
        assertFalse(nuevo.estaClasificado());
        assertTrue(nuevo.prioridadAsignada().isEmpty());
    }

    @Test
    @DisplayName("Resumen y ResultadoConsulta son inmutables y toleran valores nulos")
    void inmutables() {
        Resumen resumen = new Resumen(0, null, null, null);
        assertEquals(0, resumen.enEspera());
        assertThrows(UnsupportedOperationException.class, () -> resumen.porEstado().put("NUEVO", 1));

        List<Ticket> lista = new ArrayList<>();
        ResultadoConsulta r = new ResultadoConsulta(lista, resumen);
        lista.add(new Ticket("TCK-000001", null, null, null, null, null, "NUEVO", null, null, null));
        assertTrue(r.tickets().isEmpty(), "la copia no debe cambiar si cambia la lista original");
        assertTrue(new ResultadoConsulta(null, resumen).tickets().isEmpty());
    }

    @Test
    @DisplayName("DatosInvalidosException conserva una copia inmutable de los errores")
    void datosInvalidos() {
        List<String> errores = new ArrayList<>(List.of("Falta el campo asunto"));
        DatosInvalidosException e = new DatosInvalidosException(errores);
        errores.add("otro");
        assertEquals(List.of("Falta el campo asunto"), e.getErrores());
        assertEquals("Datos inválidos: Falta el campo asunto", e.getMessage());
    }
}

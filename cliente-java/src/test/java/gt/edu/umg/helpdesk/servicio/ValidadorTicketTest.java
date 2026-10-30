package gt.edu.umg.helpdesk.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gt.edu.umg.helpdesk.modelo.SolicitudTicket;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ValidadorTicket: mismas reglas que el WF1")
class ValidadorTicketTest {

    private final ValidadorTicket validador = new ValidadorTicket();

    private static SolicitudTicket valida() {
        return new SolicitudTicket("María García", "maria.garcia@empresa.com", "Contabilidad",
                "No tengo internet", "Desde esta mañana no hay conexión wifi");
    }

    private static SolicitudTicket conAsunto(String asunto) {
        SolicitudTicket v = valida();
        return new SolicitudTicket(v.solicitanteNombre(), v.solicitanteCorreo(), v.departamento(), asunto, v.descripcion());
    }

    private static SolicitudTicket conCorreo(String correo) {
        SolicitudTicket v = valida();
        return new SolicitudTicket(v.solicitanteNombre(), correo, v.departamento(), v.asunto(), v.descripcion());
    }

    @Nested
    @DisplayName("datos válidos")
    class Validos {
        @Test
        @DisplayName("acepta una solicitud completa")
        void aceptaSolicitudCompleta() {
            assertTrue(validador.validar(valida()).isEmpty());
        }

        @Test
        @DisplayName("acepta los límites exactos de longitud")
        void aceptaLimitesExactos() {
            SolicitudTicket s = new SolicitudTicket("Ana", "a@b.co", "TI", "Falla", "x".repeat(2000));
            assertTrue(validador.validar(s).isEmpty());
        }

        @Test
        @DisplayName("prepara los datos limpios con los nombres de campo del WF1")
        void preparaDatosLimpios() {
            SolicitudTicket s = new SolicitudTicket("  María   García ", "Maria@Empresa.COM", "Contabilidad",
                    "No   tengo internet", "Desde esta mañana no hay wifi");
            Map<String, String> datos = validador.aMapa(s);
            assertEquals("María García", datos.get("solicitante_nombre"));
            assertEquals("maria@empresa.com", datos.get("solicitante_correo"));
            assertEquals("No tengo internet", datos.get("asunto"));
            assertEquals(List.of("solicitante_nombre", "solicitante_correo", "departamento", "asunto", "descripcion"),
                    List.copyOf(datos.keySet()));
        }
    }

    @Nested
    @DisplayName("datos inválidos")
    class Invalidos {
        @Test
        @DisplayName("rechaza una solicitud nula")
        void rechazaNula() {
            assertEquals(List.of("La solicitud no puede ser nula"), validador.validar(null));
        }

        @Test
        @DisplayName("lista los cinco campos cuando todos faltan")
        void listaCamposFaltantes() {
            List<String> errores = validador.validar(new SolicitudTicket(null, "", "  ", null, ""));
            assertEquals(5, errores.size());
            assertTrue(errores.stream().allMatch(e -> e.startsWith("Falta el campo")));
        }

        @ParameterizedTest(name = "asunto \"{0}\" → {1}")
        @CsvSource({
            "Hola,     asunto debe tener al menos 5 caracteres",
            "'   ',    Falta el campo asunto"
        })
        @DisplayName("valida la longitud del asunto")
        void validaLongitudAsunto(String asunto, String esperado) {
            assertEquals(List.of(esperado), validador.validar(conAsunto(asunto)));
        }

        @Test
        @DisplayName("rechaza un asunto de más de 150 caracteres")
        void rechazaAsuntoLargo() {
            assertEquals(List.of("asunto no puede superar 150 caracteres"), validador.validar(conAsunto("a".repeat(151))));
        }

        @ParameterizedTest(name = "correo \"{0}\"")
        @ValueSource(strings = {"correo-sin-arroba.com", "ana@empresa", "ana @empresa.com", "@empresa.com"})
        @DisplayName("rechaza correos con formato inválido")
        void rechazaCorreosInvalidos(String correo) {
            assertTrue(validador.validar(conCorreo(correo)).contains("solicitante_correo no tiene un formato válido"));
        }
    }

    @Nested
    @DisplayName("códigos de ticket")
    class Codigos {
        @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
        @CsvSource({"TCK-000001, TCK-000001", "' tck-000012 ', TCK-000012"})
        @DisplayName("normaliza códigos válidos")
        void normalizaValidos(String entrada, String esperado) {
            assertEquals(esperado, validador.normalizarCodigo(entrada));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"TCK-12", "ABC-000001", "TCK-0000012", "TCK-1' OR 1=1"})
        @DisplayName("rechaza códigos inválidos o intentos de inyección")
        void rechazaInvalidos(String entrada) {
            assertNull(validador.normalizarCodigo(entrada));
        }
    }
}

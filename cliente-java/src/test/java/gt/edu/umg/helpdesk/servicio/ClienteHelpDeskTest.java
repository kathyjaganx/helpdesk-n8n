package gt.edu.umg.helpdesk.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.edu.umg.helpdesk.excepciones.AccesoDenegadoException;
import gt.edu.umg.helpdesk.excepciones.ConexionException;
import gt.edu.umg.helpdesk.excepciones.DatosInvalidosException;
import gt.edu.umg.helpdesk.excepciones.ErrorServidorException;
import gt.edu.umg.helpdesk.excepciones.HelpDeskException;
import gt.edu.umg.helpdesk.excepciones.LimiteExcedidoException;
import gt.edu.umg.helpdesk.http.RespuestaHttp;
import gt.edu.umg.helpdesk.http.TransporteHttp;
import gt.edu.umg.helpdesk.modelo.ResultadoConsulta;
import gt.edu.umg.helpdesk.modelo.SolicitudTicket;
import gt.edu.umg.helpdesk.modelo.Ticket;
import gt.edu.umg.helpdesk.modelo.TicketCreado;
import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/**
 * Pruebas del cliente con Mockito: el TransporteHttp es un "mock" que
 * simula las respuestas de n8n, así que no hace falta el sistema encendido.
 */
@DisplayName("ClienteHelpDesk (con Mockito)")
class ClienteHelpDeskTest {

    private static final String TICKET_JSON = """
            {"codigo":"TCK-000015","solicitante_nombre":"Irma Jagan","departamento":"Contabilidad",
             "asunto":"La VPN no conecta","categoria":"Red","prioridad":"ALTA","estado":"CLASIFICADO",
             "motivo_clasificacion":"Categoría Red por: vpn. Prioridad ALTA por urgencia: urgente",
             "fecha_creacion":"2026-10-29 16:57","fecha_clasificacion":"2026-10-29 16:58","campo_nuevo":"se ignora"}""";

    private TransporteHttp transporte;
    private ClienteHelpDesk cliente;

    @BeforeEach
    void preparar() {
        transporte = mock(TransporteHttp.class);
        cliente = new ClienteHelpDesk(transporte, new ValidadorTicket());
    }

    @AfterEach
    void limpiarInterrupcion() {
        Thread.interrupted();
    }

    private static SolicitudTicket solicitud() {
        return new SolicitudTicket("Irma Jagan", "Irma.Jagan@Empresa.com", "Contabilidad",
                "La VPN no conecta", "Desde esta mañana la VPN no conecta. Es urgente.");
    }

    @Test
    @DisplayName("no se puede crear sin transporte o sin validador")
    void requiereDependencias() {
        assertThrows(NullPointerException.class, () -> new ClienteHelpDesk(null, new ValidadorTicket()));
        assertThrows(NullPointerException.class, () -> new ClienteHelpDesk(transporte, null));
    }

    @Nested
    @DisplayName("registrar (WF1)")
    class Registrar {

        @Test
        @DisplayName("201: devuelve el código del ticket creado")
        void registraTicket() throws Exception {
            when(transporte.post(anyString(), anyString()))
                    .thenReturn(new RespuestaHttp(201, "{\"ok\":true,\"codigo\":\"TCK-000015\",\"estado\":\"NUEVO\"}"));

            TicketCreado creado = cliente.registrar(solicitud());

            assertEquals(new TicketCreado("TCK-000015", "NUEVO"), creado);
        }

        @Test
        @DisplayName("envía a /tickets un JSON limpio con los nombres de campo del WF1")
        void enviaJsonCorrecto() throws Exception {
            when(transporte.post(anyString(), anyString()))
                    .thenReturn(new RespuestaHttp(201, "{\"codigo\":\"TCK-000016\",\"estado\":\"NUEVO\"}"));
            ArgumentCaptor<String> ruta = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);

            cliente.registrar(solicitud());

            verify(transporte).post(ruta.capture(), cuerpo.capture());
            assertEquals("/tickets", ruta.getValue());
            JsonNode enviado = new ObjectMapper().readTree(cuerpo.getValue());
            assertEquals("irma.jagan@empresa.com", enviado.get("solicitante_correo").asText());
            assertEquals("La VPN no conecta", enviado.get("asunto").asText());
            assertEquals(5, enviado.size());
        }

        @Test
        @DisplayName("datos inválidos: falla sin llamar al sistema")
        void noLlamaSiHayErroresLocales() {
            // "TI" tiene 2 caracteres: es el mínimo permitido, así que el departamento es válido
            SolicitudTicket mala = new SolicitudTicket("Al", "sin-arroba", "TI", "Hola", "corto");

            DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> cliente.registrar(mala));

            assertEquals(List.of(
                    "solicitante_nombre debe tener al menos 3 caracteres",
                    "asunto debe tener al menos 5 caracteres",
                    "descripcion debe tener al menos 10 caracteres",
                    "solicitante_correo no tiene un formato válido"), e.getErrores());
            verifyNoMoreInteractions(transporte);
        }

        @Test
        @DisplayName("400: devuelve los errores que informa el WF1")
        void errores400() throws Exception {
            when(transporte.post(anyString(), anyString())).thenReturn(new RespuestaHttp(400,
                    "{\"ok\":false,\"mensaje\":\"Solicitud rechazada\",\"errores\":[\"asunto no puede superar 150 caracteres\"]}"));

            DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> cliente.registrar(solicitud()));

            assertEquals(List.of("asunto no puede superar 150 caracteres"), e.getErrores());
        }

        @Test
        @DisplayName("400 con cuerpo ilegible: usa un mensaje genérico")
        void errores400SinCuerpo() throws Exception {
            when(transporte.post(anyString(), anyString())).thenReturn(new RespuestaHttp(400, "<html>error</html>"));

            DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> cliente.registrar(solicitud()));

            assertEquals(List.of("El sistema rechazó los datos"), e.getErrores());
        }

        @Test
        @DisplayName("403: token incorrecto")
        void accesoDenegado() throws Exception {
            when(transporte.post(anyString(), anyString())).thenReturn(new RespuestaHttp(403, ""));
            assertThrows(AccesoDenegadoException.class, () -> cliente.registrar(solicitud()));
        }

        @Test
        @DisplayName("429: límite de solicitudes de nginx")
        void limiteExcedido() throws Exception {
            when(transporte.post(anyString(), anyString())).thenReturn(new RespuestaHttp(429, ""));
            assertThrows(LimiteExcedidoException.class, () -> cliente.registrar(solicitud()));
        }

        @ParameterizedTest(name = "HTTP {0}")
        @ValueSource(ints = {500, 502, 404, 413})
        @DisplayName("otros códigos: error del servidor con el código HTTP")
        void errorServidor(int codigo) throws Exception {
            when(transporte.post(anyString(), anyString()))
                    .thenReturn(new RespuestaHttp(codigo, "{\"message\":\"Error in workflow\"}"));

            ErrorServidorException e = assertThrows(ErrorServidorException.class, () -> cliente.registrar(solicitud()));

            assertEquals(codigo, e.getCodigoHttp());
        }

        @Test
        @DisplayName("red caída: error de conexión con la causa original")
        void sinConexion() throws Exception {
            when(transporte.post(anyString(), anyString())).thenThrow(new ConnectException("Connection refused"));

            ConexionException e = assertThrows(ConexionException.class, () -> cliente.registrar(solicitud()));

            assertInstanceOf(IOException.class, e.getCause());
        }

        @Test
        @DisplayName("interrupción: error de conexión y conserva la marca de interrupción del hilo")
        void interrumpido() throws Exception {
            when(transporte.post(anyString(), anyString())).thenThrow(new InterruptedException());

            assertThrows(ConexionException.class, () -> cliente.registrar(solicitud()));

            assertTrue(Thread.currentThread().isInterrupted());
        }

        @Test
        @DisplayName("201 con un cuerpo que no es JSON: error claro")
        void respuestaNoJson() throws Exception {
            when(transporte.post(anyString(), anyString())).thenReturn(new RespuestaHttp(201, "no es json {"));
            assertThrows(HelpDeskException.class, () -> cliente.registrar(solicitud()));
        }
    }

    @Nested
    @DisplayName("consultar (WF5)")
    class Consultar {

        @Test
        @DisplayName("200: convierte tickets y resumen, ignorando campos desconocidos")
        void consultaTickets() throws Exception {
            when(transporte.get("/consulta?limite=50")).thenReturn(new RespuestaHttp(200,
                    "{\"ok\":true,\"tickets\":[" + TICKET_JSON + "],\"resumen\":{\"total\":15,"
                            + "\"por_estado\":{\"CLASIFICADO\":14,\"NUEVO\":1},\"por_prioridad\":{\"ALTA\":6},"
                            + "\"por_categoria\":{\"Red\":5}}}"));

            ResultadoConsulta r = cliente.consultar(50);

            assertEquals(1, r.tickets().size());
            Ticket t = r.tickets().get(0);
            assertEquals("TCK-000015", t.codigo());
            assertEquals("Red", t.categoria());
            assertTrue(t.estaClasificado());
            assertEquals(15, r.resumen().total());
            assertEquals(1, r.resumen().enEspera());
            assertEquals(5, r.resumen().porCategoria().get("Red"));
        }

        @ParameterizedTest(name = "límite {0}")
        @ValueSource(ints = {0, -1, 201, 5000})
        @DisplayName("límite fuera de 1..200: falla sin llamar al sistema")
        void limiteInvalido(int limite) {
            assertThrows(IllegalArgumentException.class, () -> cliente.consultar(limite));
            verifyNoMoreInteractions(transporte);
        }

        @Test
        @DisplayName("acepta los límites exactos 1 y 200")
        void limitesExactos() throws Exception {
            when(transporte.get(anyString())).thenReturn(new RespuestaHttp(200, "{\"tickets\":[],\"resumen\":{\"total\":0}}"));

            cliente.consultar(1);
            cliente.consultar(200);

            verify(transporte).get("/consulta?limite=1");
            verify(transporte).get("/consulta?limite=200");
        }

        @Test
        @DisplayName("una respuesta sin tickets ni resumen no rompe el cliente")
        void respuestaVacia() throws Exception {
            when(transporte.get(anyString())).thenReturn(new RespuestaHttp(200, "{\"resumen\":{\"total\":0}}"));

            ResultadoConsulta r = cliente.consultar(10);

            assertTrue(r.tickets().isEmpty());
            assertTrue(r.resumen().porEstado().isEmpty());
        }

        @Test
        @DisplayName("una respuesta con un tipo incorrecto: error de formato")
        void formatoIncorrecto() throws Exception {
            when(transporte.get(anyString())).thenReturn(new RespuestaHttp(200, "{\"tickets\":\"no es una lista\"}"));
            assertThrows(HelpDeskException.class, () -> cliente.consultar(10));
        }
    }

    @Nested
    @DisplayName("buscar (WF5)")
    class Buscar {

        @Test
        @DisplayName("normaliza el código antes de consultar")
        void normalizaCodigo() throws Exception {
            when(transporte.get("/consulta?codigo=TCK-000015"))
                    .thenReturn(new RespuestaHttp(200, "{\"tickets\":[" + TICKET_JSON + "]}"));

            Optional<Ticket> t = cliente.buscar("  tck-000015 ");

            assertTrue(t.isPresent());
            assertEquals("La VPN no conecta", t.get().asunto());
            verify(transporte, times(1)).get("/consulta?codigo=TCK-000015");
        }

        @Test
        @DisplayName("código inexistente: devuelve vacío")
        void noExiste() throws Exception {
            when(transporte.get(anyString())).thenReturn(new RespuestaHttp(200, "{\"tickets\":[]}"));
            assertFalse(cliente.buscar("TCK-999999").isPresent());
        }

        @Test
        @DisplayName("código inválido o inyección: falla sin llamar al sistema")
        void codigoInvalido() throws Exception {
            assertThrows(IllegalArgumentException.class, () -> cliente.buscar("TCK-1' OR 1=1"));
            verify(transporte, never()).get(anyString());
        }

        @Test
        @DisplayName("403 al consultar: token incorrecto")
        void accesoDenegado() throws Exception {
            when(transporte.get(anyString())).thenReturn(new RespuestaHttp(403, null));
            assertThrows(AccesoDenegadoException.class, () -> cliente.buscar("TCK-000001"));
        }
    }
}

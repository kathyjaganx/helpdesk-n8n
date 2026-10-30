package gt.edu.umg.helpdesk.servicio;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cliente del Help Desk: registra y consulta tickets usando los workflows
 * de n8n como backend.
 *
 *   registrar() -> POST /tickets   (WF1)
 *   consultar() -> GET  /consulta  (WF5)
 *   buscar()    -> GET  /consulta?codigo=...  (WF5)
 *
 * Convierte cada código HTTP en una excepción con significado, para que
 * quien use el cliente no tenga que interpretar números.
 */
public class ClienteHelpDesk {

    static final int LIMITE_MINIMO = 1;
    static final int LIMITE_MAXIMO = 200;

    private final TransporteHttp transporte;
    private final ValidadorTicket validador;
    private final ObjectMapper json = new ObjectMapper();

    public ClienteHelpDesk(TransporteHttp transporte, ValidadorTicket validador) {
        this.transporte = Objects.requireNonNull(transporte, "transporte");
        this.validador = Objects.requireNonNull(validador, "validador");
    }

    /** Registra un ticket. Valida primero localmente; si hay errores, no llama al sistema. */
    public TicketCreado registrar(SolicitudTicket solicitud) {
        List<String> errores = validador.validar(solicitud);
        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }
        String cuerpo = aJson(validador.aMapa(solicitud));
        JsonNode respuesta = ejecutar(() -> transporte.post("/tickets", cuerpo), 201);
        return new TicketCreado(respuesta.path("codigo").asText(), respuesta.path("estado").asText());
    }

    /** Devuelve los últimos tickets y el resumen general. */
    public ResultadoConsulta consultar(int limite) {
        if (limite < LIMITE_MINIMO || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException(
                    "El límite debe estar entre " + LIMITE_MINIMO + " y " + LIMITE_MAXIMO);
        }
        JsonNode respuesta = ejecutar(() -> transporte.get("/consulta?limite=" + limite), 200);
        return convertir(respuesta, ResultadoConsulta.class);
    }

    /** Busca un ticket por su código. Acepta minúsculas y espacios alrededor. */
    public Optional<Ticket> buscar(String codigo) {
        String normalizado = validador.normalizarCodigo(codigo);
        if (normalizado == null) {
            throw new IllegalArgumentException("El código debe tener el formato TCK-000000");
        }
        JsonNode respuesta = ejecutar(() -> transporte.get("/consulta?codigo=" + normalizado), 200);
        return convertir(respuesta, ResultadoConsulta.class).tickets().stream().findFirst();
    }

    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface Llamada {
        RespuestaHttp ejecutar() throws IOException, InterruptedException;
    }

    private JsonNode ejecutar(Llamada llamada, int estadoEsperado) {
        RespuestaHttp respuesta;
        try {
            respuesta = llamada.ejecutar();
        } catch (IOException e) {
            throw new ConexionException("No se pudo conectar con el sistema: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConexionException("La operación fue interrumpida", e);
        }
        return interpretar(respuesta, estadoEsperado);
    }

    private JsonNode interpretar(RespuestaHttp respuesta, int estadoEsperado) {
        int estado = respuesta.estado();
        if (estado == estadoEsperado) {
            return leer(respuesta.cuerpo());
        }
        switch (estado) {
            case 400:
                throw new DatosInvalidosException(erroresDe(respuesta.cuerpo()));
            case 403:
                throw new AccesoDenegadoException();
            case 429:
                throw new LimiteExcedidoException();
            default:
                throw new ErrorServidorException(estado);
        }
    }

    private List<String> erroresDe(String cuerpo) {
        List<String> errores = new ArrayList<>();
        try {
            leer(cuerpo).path("errores").forEach(e -> errores.add(e.asText()));
        } catch (HelpDeskException e) {
            // cuerpo ilegible: se usa el mensaje genérico de abajo
        }
        if (errores.isEmpty()) {
            errores.add("El sistema rechazó los datos");
        }
        return errores;
    }

    private JsonNode leer(String cuerpo) {
        try {
            return json.readTree(cuerpo == null ? "" : cuerpo);
        } catch (IOException e) {
            throw new HelpDeskException("La respuesta del sistema no es JSON válido", e);
        }
    }

    private <T> T convertir(JsonNode nodo, Class<T> tipo) {
        try {
            return json.treeToValue(nodo, tipo);
        } catch (IOException e) {
            throw new HelpDeskException("La respuesta del sistema no tiene el formato esperado", e);
        }
    }

    private String aJson(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (IOException e) {
            throw new HelpDeskException("No se pudo preparar la solicitud", e);
        }
    }
}

package gt.edu.umg.helpdesk.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueba el transporte real contra un servidor HTTP local del JDK que
 * hace el papel de n8n y registra lo que recibe.
 */
@DisplayName("TransporteJavaHttp (con servidor HTTP local)")
class TransporteJavaHttpTest {

    private HttpServer servidor;
    private String url;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> token = new AtomicReference<>();
    private final AtomicReference<String> tipo = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/webhook", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().toString());
            token.set(intercambio.getRequestHeaders().getFirst("X-API-Key"));
            tipo.set(intercambio.getRequestHeaders().getFirst("Content-Type"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] respuesta = "{\"ok\":true,\"mensaje\":\"Atención\"}".getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            intercambio.sendResponseHeaders(201, respuesta.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(respuesta);
            }
        });
        servidor.start();
        url = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/webhook/";
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("POST: envía el JSON en UTF-8 con el token y devuelve estado y cuerpo")
    void post() throws Exception {
        RespuestaHttp r = new TransporteJavaHttp(url, "token-de-prueba").post("/tickets", "{\"asunto\":\"Contraseña\"}");

        assertEquals(201, r.estado());
        assertEquals("{\"ok\":true,\"mensaje\":\"Atención\"}", r.cuerpo());
        assertEquals("POST", metodo.get());
        assertEquals("/webhook/tickets", ruta.get());
        assertEquals("token-de-prueba", token.get());
        assertTrue(tipo.get().startsWith("application/json"));
        assertEquals("{\"asunto\":\"Contraseña\"}", cuerpo.get());
    }

    @Test
    @DisplayName("GET: conserva los parámetros de la ruta")
    void get() throws Exception {
        new TransporteJavaHttp(url, "token-de-prueba").get("/consulta?codigo=TCK-000001&limite=5");

        assertEquals("GET", metodo.get());
        assertEquals("/webhook/consulta?codigo=TCK-000001&limite=5", ruta.get());
    }

    @Test
    @DisplayName("sin token (llamadas a través de nginx): no envía el encabezado X-API-Key")
    void sinToken() throws Exception {
        new TransporteJavaHttp(url, "  ").get("/consulta");
        assertNull(token.get());

        new TransporteJavaHttp(url, null).get("/consulta");
        assertNull(token.get());
    }

    @Test
    @DisplayName("servidor apagado: lanza IOException")
    void servidorApagado() throws Exception {
        int puertoLibre;
        try (ServerSocket s = new ServerSocket(0)) {
            puertoLibre = s.getLocalPort();
        }
        TransporteJavaHttp transporte = new TransporteJavaHttp("http://127.0.0.1:" + puertoLibre, null);
        assertThrows(IOException.class, () -> transporte.get("/consulta"));
    }

    @Test
    @DisplayName("la URL base es obligatoria")
    void urlObligatoria() {
        assertThrows(NullPointerException.class, () -> new TransporteJavaHttp(null, "x"));
    }
}

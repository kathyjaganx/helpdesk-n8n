package gt.edu.umg.helpdesk.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Implementación real de {@link TransporteHttp} con el cliente HTTP de Java.
 *
 * Si se indica un token, lo envía en el encabezado X-API-Key (llamadas
 * directas a n8n). Si el token es nulo, no envía el encabezado: es el caso
 * de las llamadas a través de nginx, que lo agrega del lado del servidor.
 */
public class TransporteJavaHttp implements TransporteHttp {

    private static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(15);

    private final HttpClient cliente;
    private final String urlBase;
    private final String token;

    public TransporteJavaHttp(String urlBase, String token) {
        this(HttpClient.newBuilder().connectTimeout(TIEMPO_MAXIMO).build(), urlBase, token);
    }

    TransporteJavaHttp(HttpClient cliente, String urlBase, String token) {
        this.cliente = Objects.requireNonNull(cliente);
        this.urlBase = quitarBarraFinal(Objects.requireNonNull(urlBase, "urlBase"));
        this.token = (token == null || token.isBlank()) ? null : token;
    }

    @Override
    public RespuestaHttp post(String ruta, String cuerpoJson) throws IOException, InterruptedException {
        HttpRequest peticion = base(ruta)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpoJson, StandardCharsets.UTF_8))
                .build();
        return enviar(peticion);
    }

    @Override
    public RespuestaHttp get(String rutaConParametros) throws IOException, InterruptedException {
        return enviar(base(rutaConParametros).GET().build());
    }

    private HttpRequest.Builder base(String ruta) {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(urlBase + ruta))
                .timeout(TIEMPO_MAXIMO)
                .header("Accept", "application/json");
        if (token != null) {
            constructor.header("X-API-Key", token);
        }
        return constructor;
    }

    private RespuestaHttp enviar(HttpRequest peticion) throws IOException, InterruptedException {
        HttpResponse<String> respuesta =
                cliente.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new RespuestaHttp(respuesta.statusCode(), respuesta.body());
    }

    private static String quitarBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

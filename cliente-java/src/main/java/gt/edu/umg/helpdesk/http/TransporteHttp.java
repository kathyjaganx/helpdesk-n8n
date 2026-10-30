package gt.edu.umg.helpdesk.http;

import java.io.IOException;

/**
 * Abstracción de la comunicación HTTP con los webhooks de n8n.
 *
 * El cliente depende de esta interfaz y no de java.net.http directamente.
 * Así, en las pruebas unitarias Mockito puede simular cualquier respuesta
 * del sistema (201, 400, 403, 429, 500, red caída) sin tener n8n encendido.
 */
public interface TransporteHttp {

    RespuestaHttp post(String ruta, String cuerpoJson) throws IOException, InterruptedException;

    RespuestaHttp get(String rutaConParametros) throws IOException, InterruptedException;
}

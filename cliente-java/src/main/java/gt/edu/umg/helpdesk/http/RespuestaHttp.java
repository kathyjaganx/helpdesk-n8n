package gt.edu.umg.helpdesk.http;

/** Código de estado y cuerpo de una respuesta HTTP. */
public record RespuestaHttp(int estado, String cuerpo) {
}

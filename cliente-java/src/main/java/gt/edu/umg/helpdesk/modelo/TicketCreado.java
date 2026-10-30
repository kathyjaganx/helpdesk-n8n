package gt.edu.umg.helpdesk.modelo;

/** Respuesta del WF1 cuando el ticket se registra (HTTP 201). */
public record TicketCreado(String codigo, String estado) {
}

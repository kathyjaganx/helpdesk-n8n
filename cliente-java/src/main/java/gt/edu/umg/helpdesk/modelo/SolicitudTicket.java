package gt.edu.umg.helpdesk.modelo;

/** Datos que el solicitante envía para registrar un ticket (entrada del WF1). */
public record SolicitudTicket(
        String solicitanteNombre,
        String solicitanteCorreo,
        String departamento,
        String asunto,
        String descripcion) {
}

package gt.edu.umg.helpdesk;

import gt.edu.umg.helpdesk.excepciones.HelpDeskException;
import gt.edu.umg.helpdesk.http.TransporteJavaHttp;
import gt.edu.umg.helpdesk.modelo.Prioridad;
import gt.edu.umg.helpdesk.modelo.ResultadoConsulta;
import gt.edu.umg.helpdesk.modelo.SolicitudTicket;
import gt.edu.umg.helpdesk.modelo.TicketCreado;
import gt.edu.umg.helpdesk.servicio.AnalizadorCola;
import gt.edu.umg.helpdesk.servicio.ClienteHelpDesk;
import gt.edu.umg.helpdesk.servicio.ValidadorTicket;
import java.util.Objects;

/**
 * Programa de línea de comandos para usar el cliente contra el sistema real.
 *
 * Variables de entorno:
 *   HELPDESK_URL    URL base de los webhooks (por defecto http://n8n:5678/webhook)
 *   HELPDESK_TOKEN  token de la credencial Header Auth de n8n
 *
 * Comandos:
 *   consultar [limite]
 *   buscar TCK-000001
 *   registrar "Nombre" "correo" "Departamento" "Asunto" "Descripción"
 */
public final class Aplicacion {

    private Aplicacion() {
    }

    public static void main(String[] args) {
        String url = System.getenv().getOrDefault("HELPDESK_URL", "http://n8n:5678/webhook");
        ClienteHelpDesk cliente = new ClienteHelpDesk(
                new TransporteJavaHttp(url, System.getenv("HELPDESK_TOKEN")), new ValidadorTicket());
        String comando = args.length > 0 ? args[0] : "consultar";
        try {
            switch (comando) {
                case "registrar" -> registrar(cliente, args);
                case "buscar" -> System.out.println(cliente.buscar(args.length > 1 ? args[1] : "")
                        .map(t -> t.codigo() + " | " + t.asunto() + " | " + t.categoria() + " | "
                                + t.prioridad() + " | " + t.estado() + "\n  Motivo: " + t.motivoClasificacion())
                        .orElse("No existe ese ticket"));
                default -> consultar(cliente, args.length > 1 ? Integer.parseInt(args[1]) : 10);
            }
        } catch (HelpDeskException | IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void registrar(ClienteHelpDesk cliente, String[] a) {
        if (a.length < 6) {
            throw new IllegalArgumentException("Uso: registrar \"Nombre\" \"correo\" \"Departamento\" \"Asunto\" \"Descripción\"");
        }
        TicketCreado creado = cliente.registrar(new SolicitudTicket(a[1], a[2], a[3], a[4], a[5]));
        System.out.println("Ticket registrado: " + creado.codigo() + " (" + creado.estado() + ")");
    }

    private static void consultar(ClienteHelpDesk cliente, int limite) {
        ResultadoConsulta r = cliente.consultar(limite);
        AnalizadorCola cola = new AnalizadorCola();
        System.out.println("Total de tickets: " + r.resumen().total() + " | En espera: " + r.resumen().enEspera());
        System.out.println("Últimos " + r.tickets().size() + ":");
        r.tickets().forEach(t -> System.out.printf("  %s  %-30s  %-9s  %-6s  %s%n",
                t.codigo(), t.asunto(), Objects.toString(t.categoria(), "Pendiente"),
                Objects.toString(t.prioridad(), "-"), t.estado()));
        System.out.println("Por prioridad: " + cola.contarPorPrioridad(r.tickets()));
        cola.siguienteAAtender(r.tickets()).ifPresent(t ->
                System.out.println("Siguiente a atender: " + t.codigo() + " - " + t.asunto()
                        + " (" + t.prioridadAsignada().map(Prioridad::name).orElse("") + ")"));
    }
}

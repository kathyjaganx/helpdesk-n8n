package gt.edu.umg.helpdesk.excepciones;

/** Se superó el límite de solicitudes por minuto (HTTP 429). */
public class LimiteExcedidoException extends HelpDeskException {
    private static final long serialVersionUID = 1L;

    public LimiteExcedidoException() {
        super("Se superó el límite de solicitudes; intente de nuevo en un minuto");
    }
}

package gt.edu.umg.helpdesk.excepciones;

/** Error base del cliente. Todas las fallas del cliente heredan de esta clase. */
public class HelpDeskException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HelpDeskException(String mensaje) {
        super(mensaje);
    }

    public HelpDeskException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

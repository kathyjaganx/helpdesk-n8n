package gt.edu.umg.helpdesk.excepciones;

/** Falla inesperada del sistema (HTTP 5xx u otro código no previsto). El WF4 la registra. */
public class ErrorServidorException extends HelpDeskException {
    private static final long serialVersionUID = 1L;

    private final int codigoHttp;

    public ErrorServidorException(int codigoHttp) {
        super("El sistema respondió con un error inesperado (HTTP " + codigoHttp + ")");
        this.codigoHttp = codigoHttp;
    }

    public int getCodigoHttp() {
        return codigoHttp;
    }
}

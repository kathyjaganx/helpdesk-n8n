package gt.edu.umg.helpdesk.excepciones;

/** El sistema rechazó el token (HTTP 403). */
public class AccesoDenegadoException extends HelpDeskException {
    private static final long serialVersionUID = 1L;

    public AccesoDenegadoException() {
        super("Acceso denegado: el token no es válido o no se envió");
    }
}

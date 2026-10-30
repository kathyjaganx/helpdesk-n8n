package gt.edu.umg.helpdesk.excepciones;

/** No se pudo comunicar con el sistema (contenedores apagados, red, tiempo agotado). */
public class ConexionException extends HelpDeskException {
    private static final long serialVersionUID = 1L;

    public ConexionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

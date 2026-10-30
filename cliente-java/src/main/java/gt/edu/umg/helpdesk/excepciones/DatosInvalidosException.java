package gt.edu.umg.helpdesk.excepciones;

import java.util.List;

/** Los datos no cumplen las reglas (validación local o respuesta HTTP 400 del sistema). */
public class DatosInvalidosException extends HelpDeskException {
    private static final long serialVersionUID = 1L;

    private final transient List<String> errores;

    public DatosInvalidosException(List<String> errores) {
        super("Datos inválidos: " + String.join("; ", errores));
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}

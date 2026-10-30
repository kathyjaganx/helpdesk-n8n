package gt.edu.umg.helpdesk.servicio;

import gt.edu.umg.helpdesk.modelo.SolicitudTicket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Valida una solicitud ANTES de enviarla, con las mismas reglas del WF1.
 *
 * El WF1 vuelve a validar todo: esta validación solo evita viajes
 * innecesarios a la red y da mensajes inmediatos. Los mensajes usan el
 * mismo formato que el WF1 para que el resultado sea igual en ambos lados.
 */
public class ValidadorTicket {

    private static final Pattern CORREO =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODIGO = Pattern.compile("^TCK-\\d{6}$");

    /** campo -> {mínimo, máximo} */
    private static final Map<String, int[]> REGLAS = new LinkedHashMap<>();

    static {
        REGLAS.put("solicitante_nombre", new int[] {3, 100});
        REGLAS.put("solicitante_correo", new int[] {6, 150});
        REGLAS.put("departamento", new int[] {2, 100});
        REGLAS.put("asunto", new int[] {5, 150});
        REGLAS.put("descripcion", new int[] {10, 2000});
    }

    public List<String> validar(SolicitudTicket solicitud) {
        List<String> errores = new ArrayList<>();
        if (solicitud == null) {
            errores.add("La solicitud no puede ser nula");
            return errores;
        }
        Map<String, String> valores = aMapa(solicitud);
        for (Map.Entry<String, int[]> regla : REGLAS.entrySet()) {
            String campo = regla.getKey();
            String valor = limpiar(valores.get(campo));
            int minimo = regla.getValue()[0];
            int maximo = regla.getValue()[1];
            if (valor.isEmpty()) {
                errores.add("Falta el campo " + campo);
            } else if (valor.length() < minimo) {
                errores.add(campo + " debe tener al menos " + minimo + " caracteres");
            } else if (valor.length() > maximo) {
                errores.add(campo + " no puede superar " + maximo + " caracteres");
            }
        }
        String correo = limpiar(solicitud.solicitanteCorreo());
        if (!correo.isEmpty() && !CORREO.matcher(correo).matches()) {
            errores.add("solicitante_correo no tiene un formato válido");
        }
        return errores;
    }

    /** Devuelve los datos limpios con los nombres de campo que espera el WF1. */
    public Map<String, String> aMapa(SolicitudTicket solicitud) {
        Map<String, String> mapa = new LinkedHashMap<>();
        mapa.put("solicitante_nombre", limpiar(solicitud.solicitanteNombre()));
        mapa.put("solicitante_correo", limpiar(solicitud.solicitanteCorreo()).toLowerCase());
        mapa.put("departamento", limpiar(solicitud.departamento()));
        mapa.put("asunto", limpiar(solicitud.asunto()));
        mapa.put("descripcion", limpiar(solicitud.descripcion()));
        return mapa;
    }

    /** Normaliza un código de ticket; devuelve null si no tiene el formato TCK-000000. */
    public String normalizarCodigo(String codigo) {
        if (codigo == null) {
            return null;
        }
        String normalizado = codigo.trim().toUpperCase();
        return CODIGO.matcher(normalizado).matches() ? normalizado : null;
    }

    static String limpiar(String texto) {
        return texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
    }
}

package gt.edu.umg.helpdesk.modelo;

import java.util.Optional;

/** Prioridades que asigna el WF2. El orden de declaración es el orden de urgencia. */
public enum Prioridad {
    ALTA, MEDIA, BAJA;

    /** Convierte el texto de la API; devuelve vacío si el ticket aún no tiene prioridad. */
    public static Optional<Prioridad> desde(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(texto.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

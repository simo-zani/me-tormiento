import java.util.HashMap;
import java.util.Map;

class RisultatoScala {
    boolean valida;
    Map<Integer, Carta> jokerSostituzioni;

    RisultatoScala(boolean valida) {
        this.valida = valida;
        this.jokerSostituzioni = new HashMap<>();
    }

    RisultatoScala(boolean valida, Map<Integer, Carta> jokerSostituzioni) {
        this.valida = valida;
        this.jokerSostituzioni = jokerSostituzioni;
    }

    public boolean isValida() {
        return valida;
    }

    public Map<Integer, Carta> getJokerSostituzioni() {
        return jokerSostituzioni;
    }
}

import java.util.ArrayList;
import java.util.List;

public class Mano {
    private final List<Carta> carte = new ArrayList<>();

    public List<Carta> getCarte() {
        return carte;
    }

    public void aggiungiCarta(Carta carta) {
        carte.add(carta);
    }

    public void rimuoviCarta(Carta carta) {
        carte.remove(carta);
    }

    public boolean contiene(Carta carta) {
        return carte.contains(carta);
    }
}

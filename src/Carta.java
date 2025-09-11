import java.util.Objects;

//una carta è del tipo 8H, 3S, KD, 1C, joker
public class Carta {
    private final String valore; // es. "5", "J", "10"
    private final String seme;   // es. "spades", "diamonds", null (per il joker)

    public Carta(String valore, String seme) {
        this.valore = valore;
        this.seme = convertiSeme(seme); //converto il seme italiano in inglese se necessario
    }

    public String getValore() {
        return valore;
    }

    public String getSeme() {
        return seme;
    }

    public boolean isJoker() {
        return "Joker".equalsIgnoreCase(valore);
    }

    private String convertiSeme(String semeInput) {
        if (semeInput == null || semeInput.equalsIgnoreCase("Joker")) {
            return null; //il joker non ha un seme specifico
        }
        switch (semeInput.toLowerCase()) {
            case "cuori":
                return "H";
            case "quadri":
                return "D";
            case "fiori":
                return "C";
            case "picche":
                return "S";
            default:
                return semeInput;
        }
    }

    public String getImageFilename() {
        if (isJoker()) return "Joker.jpg";
        String nomeValore = switch (valore) {
            case "1" -> "1";
            case "J" -> "J";
            case "Q" -> "Q";
            case "K" -> "K";
            default -> valore;
        };
        String nomeSeme = switch (seme) {
            case "S" -> "spades";   //picche
            case "H" -> "hearts";   //cuori
            case "D" -> "diamonds"; //quadri
            case "C" -> "clubs";    //fiori
            default -> "unknown";
        };
        return nomeValore + "_" + nomeSeme + ".jpg";
    }

    public String getNomeCartaScartata_Ita(){
        if (isJoker()) return "il Joker";
        String nomeValore = switch (valore) {
            case "1" -> "l'Asso";
            case "8" -> "l'8";
            case "J" -> "il Fante";
            case "Q" -> "la Donna";
            case "K" -> "il Re";
            default -> "il " + valore;
        };
        String nomeSeme = switch (seme) {
            case "S" -> "picche";
            case "H" -> "cuori";
            case "D" -> "quadri";
            case "C" -> "fiori";
            default -> "unknown";
        };
        return nomeValore + " di " + nomeSeme;
    }

    @Override
    public String toString() {
        return (seme == null) ? "Joker" : valore + seme;
    }

    // serve sovrascrivere questo metodo perché il metodo remove(Object o), che chiamo quando scarto una carta dalla mano (List<Carte>) rimuove l’oggetto solo se lo trova tramite equals()
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Carta)) return false;
        Carta other = (Carta) obj;

        //se entrambi sono Joker, li consideriamo uguali solo se sono la stessa istanza di oggetto
        if (this.isJoker() && other.isJoker()) {
            return this == other; 
        }

        //per tutte le altre carte (o se uno solo è un Joker), il confronto è basato su valore e seme
        return Objects.equals(valore, other.valore) && Objects.equals(seme, other.seme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valore, seme);
    }
}
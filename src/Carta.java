import java.util.Objects;

public class Carta {
    private final String valore; // es. "5", "J", "10"
    private final String seme;   // es. "♠", "♦", null (per il joker)

    public Carta(String valore, String seme) {
        this.valore = valore;
        this.seme = seme;
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

    @Override
    public String toString() {
        return (seme == null) ? "Joker" : valore + seme;
    }
}
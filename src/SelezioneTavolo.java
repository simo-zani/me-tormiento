import java.util.List;

public class SelezioneTavolo {
    
    private List<Carta> aperturaAssociata; //l'apertura (tris o scala) a cui si vuole attaccare
    private Carta cartaCliccata;           //l'elemento specifico cliccato (una Carta se è un Joker, o un riferimento a un placeholder)
    private final int idGiocatore;         //per risalire al giocatore che ha messo sul tavolo l'apertura a cui appartiene la carta selezionata
    private final String posizioneAttacco; //"sx"/"dx" -> per capire se viene selezionato un placeholder prima o dopo la scala (nei tris sarà sempre dopo)

    public SelezioneTavolo(List<Carta> apertura, Carta cartaCliccata, int idGiocatore, String posizioneAttacco) {
        this.aperturaAssociata = apertura;
        this.cartaCliccata = cartaCliccata;
        this.idGiocatore = idGiocatore;
        this.posizioneAttacco = posizioneAttacco;
    }
    
    public List<Carta> getAperturaAssociata() {
        return aperturaAssociata;
    }
    
    public Carta getCartaCliccata() {
        return cartaCliccata;
    }

    public int getIdGiocatore() {
        return idGiocatore;
    }

    public String getPosizioneAttacco() {
        return posizioneAttacco;
    }

    @Override
    public String toString() {
        if (cartaCliccata != null) {
            //è stato cliccato un Joker
            return "Joker del Player " + idGiocatore + " su apertura: " + aperturaAssociata.toString();
        } else {
            //è stato cliccato un placeholder
            return "Placeholder " + posizioneAttacco + " del Player " + idGiocatore + " su apertura: " + aperturaAssociata.toString();
        }
    }
}

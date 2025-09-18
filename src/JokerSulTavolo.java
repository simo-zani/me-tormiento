import java.util.List;

public class JokerSulTavolo {
    
    private List<Carta> aperturaAssociata; //l'apertura (tris o scala) a cui si vuole attaccare
    private Carta jokerAssociato;          //il joker effettivo
    private String semeSostituto;          //il seme associato a questo joker
    private String valoreSostituto;        //il valore associato a questo joker
    private final int idGiocatore;         //per risalire al giocatore che ha messo sul tavolo l'apertura a cui appartiene la carta selezionata

    public JokerSulTavolo(List<Carta> apertura, Carta jokerAssociato, String semeSostituto, String valoreSostituto, int idGiocatore){
        this.aperturaAssociata = apertura;
        this.jokerAssociato = jokerAssociato;
        this.semeSostituto = semeSostituto;
        this.valoreSostituto = valoreSostituto;
        this.idGiocatore = idGiocatore;
    }

    public List<Carta> getAperturaAssociata() {
        return aperturaAssociata;
    }

    public Carta getJokerAssociato(){
        return jokerAssociato;
    }

    public String getSemeSostituto(){
        return semeSostituto;
    }

    public String getValoreSostituto(){
        return valoreSostituto;
    }

    public int getIdGiocatore() {
        return idGiocatore;
    }
}
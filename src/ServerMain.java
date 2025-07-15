// ===== SERVER TCP MULTIGIOCATORE (Java) =====

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerMain {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 4;
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static final List<Carta> deck = new ArrayList<>();
    private static final Map<Integer, List<Carta>> maniGiocatori = new HashMap<>();
    private static final Stack<Carta> pilaScarti = new Stack<>();
    private static int currentPlayerIndex = 0;
    private static int carteMano = 6; //al primo round
    private static int currentRound = 1;
    private static final Map<Integer, Integer> punteggiGiocatori = new HashMap<>(); //per tenere traccia dei punteggi cumulativi
    private static int giocatoriProntiPerNuovoRound = 0; //contatore per sapere quando tutti i client sono pronti

    public static void main(String[] args) throws IOException {
        System.out.println("[SERVER] Avviato su porta " + PORT);
        ServerSocket serverSocket = new ServerSocket(PORT);
        generateDeck();

        while (clients.size() < MAX_PLAYERS) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            punteggiGiocatori.put(clients.indexOf(handler), 0); //inizializzo i punteggi dei giocatori a 0
            new Thread(handler).start();
            System.out.println("[SERVER] Giocatore connesso. Totale: " + clients.size() + " giocatori online");
        }
    }

    private static void generateDeck() {
        String[] semi = {"H", "D", "C", "S"}; //hearts (cuori), diamonds (quadri), clubs (fiori), spades (picche)
        String[] valori = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        for (int i = 0; i < 2; i++) { //due cicli perché devo aggiungere 2 mazzi (108 carte totali)
            for (String seme : semi) {
                for (String valore : valori) {
                    deck.add(new Carta(valore, seme));
                }
            }
            deck.add(new Carta("Joker", null));
            deck.add(new Carta("Joker", null));
        }
        Collections.shuffle(deck);
    }

    //alla prima mano vengono distribuite 6 carte
    private static synchronized List<Carta> drawHand() {
        List<Carta> hand = new ArrayList<>();
        for (int i = 0; i < carteMano; i++) {
            hand.add(deck.remove(deck.size() - 1)); //rimuovo sempre quella in ultima posizione del deck (ovvero quella in cima al mazzo)
        }
        return hand;
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                List<Carta> hand = drawHand();
                int playerId;

                synchronized (clients) {
                    playerId = clients.indexOf(this);
                    maniGiocatori.put(playerId, hand);
                }

                //invio l'ID del giocatore al client subito dopo la connessione
                out.println("YOUR_PLAYER_ID:" + playerId);

                //invia carte iniziali
                List<String> imagePaths = hand.stream().map(Carta::getImageFilename).toList();
                out.println("CARTE_INIZIALI_ROUND_" + currentRound + ":" + String.join(",", imagePaths));
                
                if(playerId == 0){
                    System.out.println("Tocca a 'player " + playerId + "'");
                    out.println("TOCCA_A_TE");
                    if (pilaScarti.isEmpty()) {
                        out.println("STATO_PILA_SCARTI:VUOTA");
                    } else {
                        out.println("STATO_PILA_SCARTI:NON_VUOTA");
                    }
                }
                
                //per ascoltare i comandi dal client
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("[SERVER] Ricevuto: " + line);

                    if (line.startsWith("SCARTA:")) {
                        String cartaStr = line.substring(7).trim();
                        Carta cartaScartata = parseCartaFromString(cartaStr);
                        maniGiocatori.get(clients.indexOf(this)).remove(cartaScartata); //rimuovo la carta scartata dalla mano del giocatore sul server

                        synchronized (pilaScarti) {
                            pilaScarti.push(cartaScartata);
                        }

                        System.out.println("[SERVER] Player " + clients.indexOf(this) + " ha scartato " + cartaStr);
                        ServerMain.passaAlProssimoGiocatore(); //passo il turno al giocatore successivo

                    } else if (line.equals("PESCA_MAZZO")) {
                        Carta pescata;
                        synchronized (deck) {
                            pescata = deck.remove(deck.size() - 1);
                        }
                        maniGiocatori.get(playerId).add(pescata);
                        out.println("PESCATA:" + pescata.getImageFilename());
                        
                    } else if (line.equals("PESCA_SCARTO")) {
                        Carta pescata;
                        synchronized (pilaScarti) {
                            if (pilaScarti.isEmpty()) {
                                out.println("ERRORE: La pila degli scarti è vuota");
                                continue;
                            }
                            pescata = pilaScarti.pop();
                        }
                        maniGiocatori.get(playerId).add(pescata);
                        out.println("PESCATA:" + pescata.getImageFilename());
                    } else if (line.equals("ROUND_FINITO")){
                        // Il client notifica che ha terminato le carte
                        // Il server deve ora chiedere le mani rimanenti a tutti
                        ServerMain.handleRoundFinished(clients.indexOf(this)); //passo l'ID del giocatore che ha finito
                    } else if (line.startsWith("CARTE_RIMANENTI:")) { //il client invia le sue carte rimanenti
                        String carteString = line.substring(16);
                        List<Carta> remaining = new ArrayList<>();
                        if (!carteString.isEmpty()) { //controllo se non è una stringa vuota (mano vuota)
                             String[] cardFilenames = carteString.split(",");
                             for (String filename : cardFilenames) {
                                 remaining.add(parseCartaFromString(filename));
                             }
                        }
                        ServerMain.receiveRemainingCards(clients.indexOf(this), remaining);
                    } else if (line.equals("DISCONNETTI")){
                        playerId = clients.indexOf(this);
                        System.out.println("[SERVER] Player " + playerId + " si è disconnesso volontariamente.");

                        synchronized (clients){
                            clients.remove(this);
                            maniGiocatori.remove(playerId);
                        }
                        break; //termina il thread del client
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) out.close();
                    socket.close(); //chiude il socket dopo l'uso
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void notificaTurno() {
            out.println("TOCCA_A_TE");
            if (pilaScarti.isEmpty()) {
                out.println("STATO_PILA_SCARTI:VUOTA");
            } else {
                out.println("STATO_PILA_SCARTI:NON_VUOTA");
            }
            System.out.println("Inviato 'TOCCA_A_TE' al player " + currentPlayerIndex);
        }

        //metodo per inviare un messaggio specifico a questo client
        public void sendToClient(String message) {
            out.println(message);
        }
    }

    public static synchronized void passaAlProssimoGiocatore() {
        currentPlayerIndex = (currentPlayerIndex + 1) % MAX_PLAYERS; //in questo modo col "resto" gestisco il currentPlayer id (es: currId = 3 + 1 = 4, che non esiste perché si parte da 0; 4%4=1 con resto 0)
        ClientHandler prossimo = clients.get(currentPlayerIndex);
        System.out.println("[SERVER] Tocca a player " + currentPlayerIndex);
        prossimo.notificaTurno();
    }

    private static Carta parseCartaFromString(String s) {
        if (s.equalsIgnoreCase("Jolly") || s.equalsIgnoreCase("Joker")) return new Carta("Joker", null);
        String valore = s.substring(0, s.length() - 1);
        String seme = s.substring(s.length() - 1);
        return new Carta(valore, seme);
    }

    //metodo per gestire la fine del round
    public static synchronized void handleRoundFinished(int finishingPlayerId) {
        System.out.println("[SERVER] Giocatore " + finishingPlayerId + " ha terminato il round " + currentRound);

        //notifico a tutti i client che il round è finito e di inviare le loro carte rimanenti
        for (ClientHandler client : clients) {
            client.sendToClient("ROUND_TERMINATO");
        }
        
        // Attendi che tutti i client inviino le loro mani rimanenti per il calcolo del punteggio
        // La logica di calcolo dei punti e di avanzamento del round avverrà dopo aver ricevuto
        // tutte le mani rimanenti.
    }

    //metodo per ricevere le carte rimanenti e calcolare i punti
    public static synchronized void receiveRemainingCards(int playerId, List<Carta> remainingHand) {
        //calcolo i punti per la mano rimanente del giocatore
        int puntiMano = calcolaPuntiMano(remainingHand);
        
        //aggiungo i punti al punteggio cumulativo del giocatore
        punteggiGiocatori.put(playerId, punteggiGiocatori.get(playerId) + puntiMano);
        System.out.println("[SERVER] Punti per Giocatore " + playerId + " in questo round: " + puntiMano + ". Totale: " + punteggiGiocatori.get(playerId));

        giocatoriProntiPerNuovoRound++;
        if (giocatoriProntiPerNuovoRound == MAX_PLAYERS) {
            //tutti i giocatori hanno inviato le loro mani rimanenti, procedo al nuovo round
            System.out.println("[SERVER] Tutti i giocatori hanno inviato le carte rimanenti. Inizio nuovo round.");
            startNewRound();
        }
    }

    //metodo per calcolare i punti di una mano (al server arriva la mano sotto forma di stringa)
    //es: CARTE_RIMANENTI:6_diamonds.jpg,6_clubs.jpg,10_clubs.jpg,6_hearts.jpg,8_hearts.jpg,1_clubs.jpg,Joker.jpg
    private static int calcolaPuntiMano(List<Carta> mano) {
        //rimuovo il prefisso e divido le carte
        int puntiTot = 0;

        for (Carta carta : mano) {
            String valore = carta.getValore().split("\\.")[0]; //prendo il valore della carta (6_diamonds.jpg/Joker.jpg), lo separo in due in base al punto "\\." (con \\ eseguo l'escape, infatti in java lo split con solo il punto significa "qualsiasi carattere") -> ottengo un'array [6_diamonds/Joker; jpg]) e prendo il primo elemento [0] dell'array -> 6_diamonds/Joker
            if(valore.equalsIgnoreCase("joker") || valore.equalsIgnoreCase("jolly")){
                puntiTot += 50;
            } else{
                valore = valore.split("_")[0]; //come prima, ma questa volta prendo il valore prima dell'underscore "_"
                switch (valore) {
                    case "1":   //Asso
                    case "J":   //Jack/Fante
                    case "Q":   //Queen/Donna
                    case "K":   //King/Re
                        puntiTot += 10;
                        break;
                    default:
                        try {
                            int numero = Integer.parseInt(valore);
                            puntiTot += numero;
                        } catch (NumberFormatException e) {
                            System.err.println("Valore carta non valido: " + valore);
                        }
                        break;
                }
            }
        }
        return puntiTot;
    }

    //metodo per avviare un nuovo round
    private static synchronized void startNewRound() {
        currentRound++;
        if(carteMano==12){
            carteMano=12; //gli ultimi 2 round hanno sempre 12 carte ma cambiano gli obiettivi
        }else{
            carteMano++;
        }

        System.out.println("[SERVER] Inizio Round " + currentRound + " con " + carteMano + " carte.");

        //azzero le mani di tutti i giocatori
        maniGiocatori.clear();

        //rimescolo il mazzo completo
        deck.clear();
        generateDeck(); //rigenero un nuovo mazzo completo

        //svuoto la pila degli scarti per il nuovo round
        pilaScarti.clear();

        //currentPlayerIndex è già stato aggiornato in passaAlProssimoGiocatore() quando il giocatore ha scartato/aperto

        giocatoriProntiPerNuovoRound = 0; //resetto il contatore per il prossimo round

        //distribuisco nuove carte a tutti i giocatori e notifica i client
        for (int i = 0; i < clients.size(); i++) {
            List<Carta> newHand = drawHand();
            maniGiocatori.put(i, newHand);
            List<String> imagePaths = newHand.stream().map(Carta::getImageFilename).toList();
            clients.get(i).sendToClient("CARTE_INIZIALI_ROUND_" + currentRound + ":" + String.join(",", imagePaths));
            //invio anche i punteggi aggiornati a tutti i client
            clients.get(i).sendToClient("PUNTEGGI_AGGIORNATI:" + formatPunteggi());
        }

        //notifico al giocatore che deve iniziare il turno
        clients.get(currentPlayerIndex).notificaTurno();
    }

    //helper per formattare i punteggi per l'invio ai client
    private static String formatPunteggi() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            sb.append("Player ").append(i).append(": ").append(punteggiGiocatori.getOrDefault(i, 0)).append(";");
        }
        sb.setLength(sb.length() - 1); //rimuovo l'ultimo ";"
        return sb.toString();
    }
}
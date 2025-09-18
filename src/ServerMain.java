// ===== SERVER TCP MULTIGIOCATORE (Java) =====

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList; // Usa CopyOnWriteArrayList per broadcast thread-safe

public class ServerMain {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 4;
    // Usa CopyOnWriteArrayList per consentire iterazioni sicure durante le modifiche (es. disconnessioni)
    private static final List<ClientHandler> clients = new CopyOnWriteArrayList<>(); 
    //private static final List<ClientHandler> clients = new ArrayList<>();
    private static final List<Carta> deck = new ArrayList<>();  //con deck.size() so quante carte ci sono nel mazzo in ogni istante
    private static final Map<Integer, List<Carta>> maniGiocatori = new HashMap<>();
    private static final Stack<Carta> pilaScarti = new Stack<>();
    private static int currentPlayerIndex = 0;
    private static int giocatoreInAttesaPesca = -1;
    private static int giocatoreInTormento = -1;
    private static int carteMano = 6; //al primo round
    private static int currentRound = 1;
    private static final Map<Integer, Integer> punteggiGiocatori = new HashMap<>(); //per tenere traccia dei punteggi cumulativi
    private static final Map<Integer, Integer> roundVinti = new HashMap<>(); //per tenere traccia dei round vinti dai giocatori
    private static int giocatoriProntiPerNuovoRound = 0; //contatore per sapere quando tutti i client sono pronti
    private static Timer tormentoTimer;
    //mappa che associa l'ID del giocatore ai set che ha aperto; ogni set è una lista di carte (tris o scale)
    private static final Map<Integer, List<List<Carta>>> apertureSulTavolo = new HashMap<>();
    //mappa per i joker sul tavolo: playerId -> (indice_apertura -> lista di joker sostituiti in quell'apertura)
    private static final Map<Integer, Map<Integer, List<Carta>>> jokerSulTavolo = new HashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("[SERVER] Avviato su porta " + PORT);
        ServerSocket serverSocket = new ServerSocket(PORT);
        generateDeck();

        while (clients.size() < MAX_PLAYERS) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket);
            clients.add(handler);
            punteggiGiocatori.put(clients.indexOf(handler), 0); //inizializzo i punteggi dei giocatori a 0
            roundVinti.put(clients.indexOf(handler), 0);        //inizializzo i round vinti dai giocatori a 0
            new Thread(handler).start();
            System.out.println("[SERVER] Giocatore connesso. Totale: " + clients.size() + " giocatori online");
        }
    }

    //genero un mazzo da 108 carte
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

    //rigenero il mazzo dalla pila degli scarti una volta che le carte del mazzo esauriscono
    private static void reGenerateDeck(){
        
        Carta cima = pilaScarti.get(pilaScarti.size() - 1); //mi salvo la carta in cima alla pila degli scarti
        List<Carta> nuovoDeck = new ArrayList<>(pilaScarti.subList(0, pilaScarti.size() - 1)); //copio nel nuovo deck tutte le carti presenti nella pila dalla pos 0 alla pos ultima-1
        Collections.shuffle(nuovoDeck);

        deck.clear(); //in teoria è già vuoto se arrivo a chiamare questo metodo
        deck.addAll(nuovoDeck);

        pilaScarti.clear(); //svuoto la pila e ci rimetto dentro solo la cima
        pilaScarti.add(cima);

        broadcastMessage("CARTE_MAZZO:" + deck.size()); //invio nuovamente a TUTTI la dimensione del nuovo mazzo
    }

    //alla prima mano vengono distribuite 6 carte
    private static synchronized List<Carta> drawHand() {
        List<Carta> mano = new ArrayList<>();
        for (int i = 0; i < carteMano; i++) {
            mano.add(deck.remove(deck.size() - 1)); //rimuovo sempre quella in ultima posizione del deck (ovvero quella in cima al mazzo) e la aggiungo alla mano
        }
        return mano;
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
                broadcastMessage("CARTE_MAZZO:" + deck.size()); //invio a TUTTI quante carte sono presenti nel mazzo
                
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
                    System.out.println("[CLIENT " + playerId + "] " + line);

                    if (line.startsWith("SCARTA:")) {
                        String cartaStr = line.substring(7).trim();
                        Carta cartaScartata = parseCartaFromString(cartaStr);
                        maniGiocatori.get(playerId).remove(cartaScartata); //rimuovo la carta scartata dalla mano del giocatore sul server
                        
                        synchronized (pilaScarti) {
                            pilaScarti.push(cartaScartata);
                        }

                        System.out.println("[SERVER] Player " + clients.indexOf(this) + " ha scartato " + cartaStr);
                        broadcastMessage("PILA_SCARTI:" + serializePilaScarti()); //invio a TUTTI la pila degli scarti
                        broadcastMessage("CARTE_MAZZO:" + deck.size()); //invio a TUTTI quante carte sono presenti nel mazzo

                        broadcastMessage("GIOCATORE_X_HA_SCARTATO_LA_CARTA_X:" + playerId + " ha scartato " + cartaScartata.getNomeCartaScartata_Ita()); //invio a TUTTI il giocatore X che ha scartato la carta X
                        broadcastMessage("GIOCATORE_X_HA_IN_MANO_X_CARTE:" + maniGiocatori.get(playerId).size()); //invio a TUTTI quante carte ha in mano il giocatore X quando finisce il turno

                        ServerMain.passaAlProssimoGiocatore(); //passo il turno al giocatore successivo
                    } else if (line.equals("PESCA_MAZZO")) {
                        /*
                         * regola TORMENTO
                         * quando il giocatore pesca dal mazzo, e quindi non gli serve la carta dalla pila degli scarti, prima di ricevere
                         * la carta si intromette il server e manda un messaggio al giocatore successivo -> TORMENTO_CHANCE.
                         * Il client avrà 10 secondi per rispondere e rimandare al server un messaggio -> TORMENTO_RISPOSTA:SI/NO
                         * In entrambi i casi dopo che avverrà o meno il tormento il server autorizzerà la pescata dal mazzo
                         * del giocatore originale con PESCA_MAZZO_AUTORIZZATA
                         */
                        int nextPlayerId = (currentPlayerIndex + 1) % MAX_PLAYERS;
                        giocatoreInAttesaPesca = currentPlayerIndex; //mi salvo chi vuole pescare dal mazzo
                        giocatoreInTormento = nextPlayerId;
                        if(!pilaScarti.isEmpty()){
                            sendMessageToNextPlayer("TORMENTO_CHANCE:" + pilaScarti.get(pilaScarti.size()-1).getImageFilename());
                            
                            //timer 10 secondi per la risposta
                            tormentoTimer = new Timer();
                            tormentoTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    ServerMain.gestisciTormento(giocatoreInTormento, false); //se il giocatore non risponde -> NO di default allo scadere
                                    
                                    clients.get(giocatoreInTormento).out.println("TIMER_SCADUTO");
                                }
                            }, 10000); //10 secondi in millisecondi
                        } else{
                            pescaCartaDalMazzo(giocatoreInAttesaPesca);
                        }
                    } else if (line.startsWith("TORMENTO_RISPOSTA:")) {
                        /*
                         * IN QUESTO BLOCCO 
                         * currentPlayerIndex -> il giocatore che ha ricevuto la richiesta di tormento (è lui che manda la risposta)
                         * giocatoreInAttesaPesca -> il giocatore che sta aspettando la risposta del tormento
                         *
                        */
                        String risposta = line.substring("TORMENTO_RISPOSTA:".length()); //mi salvo il SI o il NO
                        
                        if (tormentoTimer != null) {
                            tormentoTimer.cancel();
                            tormentoTimer = null;
                        }

                        if (risposta.equalsIgnoreCase("SI")) {
                            ServerMain.gestisciTormento(giocatoreInTormento, true);
                        } else {
                            ServerMain.gestisciTormento(giocatoreInTormento, false);
                        }

                        giocatoreInTormento = -1;

                        //finito il tormento, chi era in attesa PESCA
                        pescaCartaDalMazzo(giocatoreInAttesaPesca);
                        
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
                        out.println("PESCATA:" + pescata.getImageFilename()); //arriva al client che ha pescato
                        broadcastMessage("PESCATA_DA_PILA_SCARTI:" + pescata.getImageFilename());  //invio a TUTTI la carta pescata dalla pila degli scarti
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
                    } else if (line.startsWith("APRI:")) { //il client invia le sue carte usate per l'apertura
                        String carteString = line.substring(5).trim();
                        String[] cardFilenames = carteString.split(",");
                        
                        //lista per conservare le carte dell'apertura
                        List<Carta> carteAperte  = new ArrayList<>();
                        for (String filename : cardFilenames) {
                            Carta carta = parseCartaFromFileNameString(filename);
                            if (carta != null) {
                                carteAperte.add(carta);
                            }
                        }

                        //rimuovo una carta alla volta al posto di usare removeAll (andrebbe a rimuovere carte uguali nella mano che invece non deve eliminare)
                        List<Carta> manoCorrente = maniGiocatori.get(playerId);
                        for(Carta cartaAperta : carteAperte) {
                            if (cartaAperta.isJoker()) {
                                //rimuovo il primo Joker trovato nella mano
                                Iterator<Carta> it = manoCorrente.iterator();
                                while (it.hasNext()) {
                                    Carta c = it.next();
                                    if (c.isJoker()) {
                                        it.remove();
                                        break;
                                    }
                                }
                            } else {
                                //carte normali: basta equals
                                manoCorrente.remove(cartaAperta);
                            }
                        }

                        //raggruppo le carte in set in base al round (2 tris, 1 tris e 1 scala, ecc.)
                        List<List<Carta>> setsDelGiocatore = new ArrayList<>();
                        
                        if (currentRound == 1) {
                            //il client invia 6 carte, le raggruppo in 2 tris da 3
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(0, 3)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(3, 6)));
                        } else if (currentRound == 2) {
                            //il client invia 7 carte, le raggruppo in 1 tris da 3 e 1 scala da 4
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(0, 3)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(3, 7)));
                        } else if (currentRound == 3) {
                            //il client invia 8 carte, le raggruppo in 2 scale da 4
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(0, 4)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(4, 8)));
                        } else if (currentRound == 4) {
                            //il client invia 9 carte, le raggruppo in 3 tris da 3
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(0, 3)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(3, 6)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(6, 9)));
                        } else if (currentRound == 5) {
                            //il client invia 10 carte, le raggruppo in 2 tris da 3 e 1 scala da 4
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(0, 3)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(3, 6)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(6, 10)));
                        } else if (currentRound == 6) {
                            //il client invia 11 carte, le raggruppo in 1 tris da 3 e 2 scale da 4
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(0, 3)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(3, 7)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(7, 11)));
                        } else if (currentRound == 7) {
                            //il client invia 12 carte, le raggruppo in 4 tris da 3
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(0, 3)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(3, 6)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(6, 9)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(9, 12)));
                        } else if (currentRound == 8) {
                            //il client invia 12 carte, le raggruppo in 3 scale da 4
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(0, 4)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(4, 8)));
                            setsDelGiocatore.add(new ArrayList<>(carteAperte.subList(8, 12)));
                        }

                        //aggiungo i set del giocatore al tavolo
                        apertureSulTavolo.put(playerId, setsDelGiocatore);

                        System.out.println("[SERVER] Player " + playerId + " ha aperto. Tavolo aggiornato.");

                        //notifico tutti i client il tavolo aggiornato
                        broadcastTableState();
                    } else if (line.startsWith("APRI_EXTRA:")){ //il client invia le sue carte usate per l'apertura extra
                        String carteString = line.substring(11).trim();
                        String[] cardFilenames = carteString.split(",");
                        
                        //lista per conservare le carte dell'apertura extra
                        List<Carta> carteAperteExtra  = new ArrayList<>();
                        for (String filename : cardFilenames) {
                            Carta carta = parseCartaFromFileNameString(filename);
                            if (carta != null) {
                                carteAperteExtra.add(carta);
                            }
                        }

                        //rimuovo una carta alla volta al posto di usare removeAll (andrebbe a rimuovere carte uguali nella mano che invece non deve eliminare)
                        List<Carta> manoCorrente = maniGiocatori.get(playerId);
                        for(Carta cartaAperta : carteAperteExtra) {
                            if (cartaAperta.isJoker()) {
                                //rimuovo il primo Joker trovato nella mano
                                Iterator<Carta> it = manoCorrente.iterator();
                                while (it.hasNext()) {
                                    Carta c = it.next();
                                    if (c.isJoker()) {
                                        it.remove();
                                        break;
                                    }
                                }
                            } else {
                                //carte normali: basta equals
                                manoCorrente.remove(cartaAperta);
                            }
                        }

                        //recupero la lista di set esistenti per il giocatore che esegue l'apertura extra
                        List<List<Carta>> setsEsistentiDelGiocatore = apertureSulTavolo.get(playerId);
                        if (setsEsistentiDelGiocatore == null) { //non dovrebbe essere possibile perché APRI_EXTRA può essere fatto dopo un APRI di base
                            setsEsistentiDelGiocatore = new ArrayList<>();
                            apertureSulTavolo.put(playerId, setsEsistentiDelGiocatore);
                        }

                        if (carteAperteExtra.size()==3) { //TRIS
                            setsEsistentiDelGiocatore.add(new ArrayList<>(carteAperteExtra.subList(0, 3)));
                        } else if (carteAperteExtra.size()==4) { //SCALA
                            setsEsistentiDelGiocatore.add(new ArrayList<>(carteAperteExtra.subList(0, 4)));
                        } else {
                            System.out.println("[SERVER] Errore nell'apertura extra.");
                        }

                        //aggiungo i set del giocatore al tavolo
                        apertureSulTavolo.put(playerId, setsEsistentiDelGiocatore);

                        System.out.println("[SERVER] Player " + playerId + " ha aperto extra. Tavolo aggiornato.");
                        //notifico tutti i client il tavolo aggiornato
                        broadcastTableState();                        
                    } else if (line.startsWith("JOKER_SOSTITUTI:")){ //il client invia il/i joker usati
                        /*arriva un messaggio di questo tipo: 
                            "JOKER_SOSTITUTI:0:8_hearts.jpg|1:7_clubs.jpg" -> un joker per ogni apertura
                            "JOKER_SOSTITUTI:0:8_hearts.jpg,8_clubs.jpg"   -> due joker nella stessa apertura (per 2,3 o 4 joker è di questo tipo)
                        */
                        //il numero prima della carta indica in quale apertura è presente il joker
                        int separatorIndex = line.indexOf(':'); //cerca i primi due punti
                        if (separatorIndex != -1) {
                            String apertureJokerString = line.substring(separatorIndex + 1).trim();
                            String[] apertureTokens = apertureJokerString.split("\\|"); //separo per ogni "|" ossia per ogni apertura

                            for (String aperturaToken : apertureTokens) {
                                String[] aperturaParts = aperturaToken.split(":");
                                int aperturaIndex = Integer.parseInt(aperturaParts[0]); //indice legato all'apertura
                                String carteStr = aperturaParts[1]; //es: 8_hearts.jpg,8_diamonds.jpg

                                String[] carteArray = carteStr.split(","); //separo per ogni "," ossia per ogni carta
                            
                                synchronized (ServerMain.class) {
                                    //se non esiste ancora una mappa per quel playerId ne creo una nuova
                                    List<Carta> listaCarte = jokerSulTavolo
                                        .computeIfAbsent(playerId, k -> new HashMap<>())
                                        .computeIfAbsent(aperturaIndex, k -> new ArrayList<>());
                                    
                                    for(String cStr : carteArray){
                                        Carta carta = parseCartaFromFileNameString(cStr);
                                        if (carta != null) {  //qui la carta dovrebbe sempre esistere se non ci sono errori
                                            listaCarte.add(carta);
                                        } else {
                                            System.err.println("Errore di parsing del filename: " + cStr);
                                        }
                                    }
                                }
                            }

                            broadcastJokerState();
                        }
                        
                    } else if (line.startsWith("SWAP_CARTE:")){ //il client invia lo swap carte
                        /*arriva un messaggio di questo tipo: 
                            "SWAP_CARTE:joker.jpg,carta.jpg,idGiocatoreApertura,aperturaIndex,posizioneJokerNell'Apertura"
                        */
                        String swapString = line.substring(11).trim();
                        String[] dati = swapString.split(",");
                        String jokerFilename = dati[0];
                        String cartaFilename = dati[1];
                        int idGiocatoreApertura = Integer.parseInt(dati[2]);
                        int aperturaIndex = Integer.parseInt(dati[3]);
                        int posJoker = Integer.parseInt(dati[4]);

                        playerId = clients.indexOf(this); //id del client che ha inviato lo swap

                        //ricostruisco le carte dagli imageFilename
                        Carta joker = parseCartaFromFileNameString(jokerFilename);
                        Carta carta = parseCartaFromFileNameString(cartaFilename);

                        //aggiorno la mano del giocatore che ha fatto lo swap
                        int posCarta = -1;
                        for (int i = 0; i < maniGiocatori.get(playerId).size(); i++) {
                            if (maniGiocatori.get(playerId).get(i).equals(carta)) {
                                posCarta = i;
                                break;
                            }
                        }
                        if (posCarta != -1) {
                            maniGiocatori.get(playerId).set(posCarta, joker);
                        }

                        //aggiorno il tavolo scambiando il joker con la carta al suo posto
                        List<Carta> apertura = apertureSulTavolo.get(idGiocatoreApertura).get(aperturaIndex);
                        apertura.set(posJoker, carta); //sostituisce l'oggetto Carta in posJoker con l'oggetto "carta" di tipo Carta
                        
                        //aggiorno la struttura dei joker sul tavolo -> rimuovo il joker (e anche il giocatore se non ha più joker sul tavolo) dalla mappa
                        Map<Integer, List<Carta>> apertureJoker = jokerSulTavolo.get(idGiocatoreApertura);
                        if (apertureJoker != null) {
                            List<Carta> sostituti = apertureJoker.get(aperturaIndex);
                            if (sostituti != null) {
                                //rimuovo la carta sostituita dal Joker appena scambiato
                                sostituti.remove(carta);
                                //se la lista dei sostituti è vuota (per il giocatore associato), rimuovo l'intera entrata per quell'apertura
                                if (sostituti.isEmpty()) {
                                    apertureJoker.remove(aperturaIndex);
                                }
                            }
                            //se il giocatore non ha più Joker sul tavolo, rimuovo la sua entry
                            if (apertureJoker.isEmpty()) {
                                jokerSulTavolo.remove(idGiocatoreApertura);
                            }
                        }

                        //mando a tutti i client l'aggiornamento del tavolo
                        broadcastTableState();
                        broadcastJokerState();
                        
                    } else if (line.startsWith("ATTACCA_CARTA:")){ //il client invia la carta da attaccare
                        /*arriva un messaggio di questo tipo: 
                            "ATTACCA_CARTA:cartaDaAttaccare.jpg,jokerSostituto.jpg,idGiocatoreApertura,aperturaIndex,posizioneAttacco (dx o sx)"
                        */
                        String attaccoString = line.substring(14).trim();
                        String[] dati = attaccoString.split(",");

                        String cartaDaAttaccareFilename = dati[0];
                        String jokerSostitutoFilename = dati[1]; //sarà una stringa vuota se il client attacca una carta nota
                        int idGiocatoreApertura = Integer.parseInt(dati[2]);
                        int aperturaIndex = Integer.parseInt(dati[3]);
                        String posAttacco = dati[4];

                        playerId = clients.indexOf(this); //id del client che ha inviato la carta da attaccare

                        //ricostruisco la carta da attaccare e il sostituto Joker (se esiste)
                        Carta cartaDaAttaccare = parseCartaFromFileNameString(cartaDaAttaccareFilename);
                        Carta jokerSostituto = null;
                        if(!jokerSostitutoFilename.isEmpty()){
                            jokerSostituto = parseCartaFromFileNameString(jokerSostitutoFilename);
                        }

                        //rimuovo la carta dalla mano del giocatore che ha fatto l'attacco
                        if (cartaDaAttaccare.isJoker()) {
                            int posJokerInMano = -1;
                            for (int i = 0; i < maniGiocatori.get(playerId).size(); i++) {
                                if (maniGiocatori.get(playerId).get(i).isJoker()) {
                                    posJokerInMano = i;
                                    break; 
                                }
                            }
                            if (posJokerInMano != -1) {
                                maniGiocatori.get(playerId).remove(posJokerInMano);
                            }
                        } else {
                            int posCarta = -1;
                            for (int i = 0; i < maniGiocatori.get(playerId).size(); i++) {
                                if (maniGiocatori.get(playerId).get(i).equals(cartaDaAttaccare)) {
                                    posCarta = i;
                                    break;
                                }
                            }
                            if (posCarta != -1) {
                                maniGiocatori.get(playerId).remove(posCarta);
                            }
                        }

                        System.out.println("DEBUG: Stato del tavolo PRIMA dell'attacco:");
                        System.out.println(apertureSulTavolo);

                        //aggiorno il tavolo attaccando la carta/joker al posto corretto (dx o sx -> se tris per forza a dx)
                        List<Carta> apertura = apertureSulTavolo.get(idGiocatoreApertura).get(aperturaIndex);
                        if (posAttacco.equals("sx")) {
                            apertura.add(0, cartaDaAttaccare); //aggiunta in testa e shifta tutti gli altri oggetti
                        } else {
                            apertura.add(cartaDaAttaccare); //aggiunta in coda
                        }
                        
                        //se attacco un joker aggiorno la struttura dei joker sul tavolo -> aggiungo il joker (e anche il giocatore se non ha joker sul tavolo) dalla mappa
                        if(cartaDaAttaccare.isJoker()){
                            Map<Integer, List<Carta>> apertureJoker = jokerSulTavolo.getOrDefault(idGiocatoreApertura, new HashMap<>());
                            List<Carta> sostituti = apertureJoker.getOrDefault(aperturaIndex, new ArrayList<>());

                            //se c'è già un joker sostituto nella scala allora devo inserirlo in modo ordinato
                            if (posAttacco.equals("sx")) {
                                sostituti.add(0, jokerSostituto);
                            } else {
                                sostituti.add(jokerSostituto);
                            }

                            apertureJoker.put(aperturaIndex, sostituti);
                            jokerSulTavolo.put(idGiocatoreApertura, apertureJoker);
                        }

                        //mando a tutti i client l'aggiornamento del tavolo
                        broadcastTableState();
                        if (cartaDaAttaccare.isJoker()){
                            broadcastJokerState();
                        }
                        
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

        //per inviare un messaggio specifico a questo client
        public void sendToClient(String message) {
            out.println(message);
        }

        //aggiunge la carta pescata alla mano del giocatore passato come parametro
        public void pescaCartaDalMazzo(int playerId){
            Carta pescata;
            synchronized (deck) {
                pescata = deck.remove(deck.size() - 1);
            }
            if(deck.isEmpty()){
                reGenerateDeck();
                broadcastMessage("PILA_SCARTI:" + serializePilaScarti()); //invio a TUTTI la pila degli scarti vuota con solo la cima
            }
            maniGiocatori.get(playerId).add(pescata);
            
            //INVIO SOLO AL PLAYER CHE PESCA
            clients.get(playerId).out.println("PESCATA:" + pescata.getImageFilename());
            //INVIO A TUTTI aggiornamenti generali
            broadcastMessage("CARTE_MAZZO:" + deck.size()); //invio a TUTTI quante carte sono presenti nel mazzo quando qualcuno pesca
            broadcastMessage("CARTA_PESCATA:" + pescata.getImageFilename()); //invio a TUTTI che carta è stata pescata
        }
    }

    public static synchronized void passaAlProssimoGiocatore() {
        currentPlayerIndex = (currentPlayerIndex + 1) % MAX_PLAYERS; //in questo modo col "resto" gestisco il currentPlayer id (es: currId = 3 + 1 = 4, che non esiste perché si parte da 0; 4%4=1 con resto 0)
        ClientHandler prossimo = clients.get(currentPlayerIndex);
        System.out.println("[SERVER] Tocca a player " + currentPlayerIndex);
        prossimo.notificaTurno();

        System.out.println("\nPLAYER 0:");
        for(int i = 0; i < maniGiocatori.get(0).size(); i++){
            System.out.println(maniGiocatori.get(0).get(i).getImageFilename());
        }
        System.out.println("\nPLAYER 1:");
        for(int i = 0; i < maniGiocatori.get(1).size(); i++){
            System.out.println(maniGiocatori.get(1).get(i).getImageFilename());
        }
        System.out.println("\nPLAYER 2:");
        for(int i = 0; i < maniGiocatori.get(2).size(); i++){
            System.out.println(maniGiocatori.get(2).get(i).getImageFilename());
        }
        System.out.println("\nPLAYER 3:");
        for(int i = 0; i < maniGiocatori.get(3).size(); i++){
            System.out.println(maniGiocatori.get(3).get(i).getImageFilename());
        }
    }

    //converto una stringa (nel formato della carta tipo 8H) in un oggetto di tipo Carta
    private static Carta parseCartaFromString(String s) {
        if (s.equalsIgnoreCase("Jolly") || s.equalsIgnoreCase("Joker")) return new Carta("Joker", null);
        String valore = s.substring(0, s.length() - 1);
        String seme = s.substring(s.length() - 1);
        return new Carta(valore, seme);
    }

    //converto una stringa (nel formato fileName della carta del tipo valore_seme.jpg) in un oggetto di tipo Carta
    private static Carta parseCartaFromFileNameString(String filename) {
        if (filename.equalsIgnoreCase("Jolly.jpg") || filename.equalsIgnoreCase("Joker.jpg")) return new Carta("Joker", null);

        //rimuovo l'estensione .jpg
        String baseFilename = filename.replace(".jpg", "");

        //il formato ora è valore_seme
        String[] parts = baseFilename.split("_");
        if (parts.length != 2) {
            System.err.println("Errore di parsing del filename: " + filename);
            return null;
        }

        String valore = parts[0];
        String nomeSeme = parts[1];

        //mappo il nome del seme al codice del seme
        String seme = switch (nomeSeme) {
            case "spades" -> "S";
            case "hearts" -> "H";
            case "diamonds" -> "D";
            case "clubs" -> "C";
            default -> null; //seme non riconosciuto
        };

        if (seme == null) {
            System.err.println("Seme non riconosciuto dal filename: " + filename);
            return null;
        }

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

    //metodo per ricevere le carte rimanenti in mano alla fine del turno e calcolare i punti
    public static synchronized void receiveRemainingCards(int playerId, List<Carta> remainingHand) {
        if(remainingHand.isEmpty()){
            //il giocatore ha vinto il round
            int vittorieAttuali = roundVinti.getOrDefault(playerId, 0);
            roundVinti.put(playerId, vittorieAttuali + 1);
            System.out.println("[SERVER] Giocatore " + playerId + " ha vinto il round! Vittorie totali: " + roundVinti.get(playerId));
        } else {
            //il giocatore ha perso il round quindi calcolo i punti per le carte rimanenti in mano
            int puntiMano = calcolaPuntiMano(remainingHand);
            
            //aggiungo i punti al punteggio cumulativo del giocatore
            punteggiGiocatori.put(playerId, punteggiGiocatori.get(playerId) + puntiMano);
            System.out.println("[SERVER] Punti per Giocatore " + playerId + " in questo round: " + puntiMano + ". Totale: " + punteggiGiocatori.get(playerId));
        }

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

    //helper per formattare i punteggi per l'invio ai client
    private static String formatPunteggi() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_PLAYERS; i++) {
            sb.append("Player ").append(i).append(": ").append(punteggiGiocatori.getOrDefault(i, 0)).append(";");
        }
        sb.setLength(sb.length() - 1); //rimuovo l'ultimo ";"
        return sb.toString();
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
        
        deck.clear();   //svuoto il mazzo
        generateDeck(); //rigenero un nuovo mazzo completo

        //svuoto la pila degli scarti per il nuovo round
        pilaScarti.clear();

        //svuoto il tavolo
        apertureSulTavolo.clear();

        //svuoto i joker
        jokerSulTavolo.clear();

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

        clients.get(currentPlayerIndex).notificaTurno(); //notifico al giocatore che deve iniziare il turno
    }

    //per convertire la pila degli scarti (Stack<Carta>) in una stringa per l'invio ai client
    private static String serializePilaScarti() {
        if (pilaScarti.isEmpty()) {
            return "VUOTA";
        }
        //creo una lista temporanea per iterare dal basso all'alto se necessario, o direttamente dallo stack se il client 
        //gestisce l'ordine. Per semplicità, inviamo l'ultima carta scartata come prima della stringa, che è la cima della pila
        StringBuilder sb = new StringBuilder();
        //itero dalla base dello stack per mantenere un ordine consistente per il client 
        //Per coerenza con aggiornaPilaScartiGrafica nel Client, invio dalla base alla cima.
        
        //converto lo Stack in List per poterlo serializzare in ordine di aggiunta
        List<Carta> tempPila = new ArrayList<>(pilaScarti);
        
        for (int i = 0; i < tempPila.size(); i++) {
            sb.append(tempPila.get(i).getImageFilename());
            if (i < tempPila.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    //per gestire la fase di richiesta di tormento
    public static void gestisciTormento(int idGiocatore, boolean siTormenta) {
        if (siTormenta) { //il giocatore decide di TORMENTARSI -> prende la carta dalla pila degli scarti + una dal mazzo
            Carta dallaPila = pilaScarti.pop();
            Carta dalMazzo = deck.remove(deck.size()-1);

            maniGiocatori.get(idGiocatore).add(dallaPila);
            maniGiocatori.get(idGiocatore).add(dalMazzo);

            broadcastMessage("TORMENTO_ESEGUITO:" + idGiocatore); //quando riceve il messaggio aggiorna la mano
            sendMessageToNextPlayer("TORMENTO_ESEGUITO_CARTE_AGGIUNTE:" + dallaPila.getImageFilename() + ";" + dalMazzo.getImageFilename());
            
            broadcastMessage("PILA_SCARTI:" + serializePilaScarti()); //invio a TUTTI la pila degli scarti
            broadcastMessage("CARTE_MAZZO:" + deck.size()); //invio a TUTTI quante carte sono presenti nel mazzo
        } else {
            System.out.println("[SERVER] Tormento ignorato dal Player " + idGiocatore);
        }
    }

    //per inviare un messaggio a tutti i client
    public static synchronized void broadcastMessage(String message) {
        System.out.println("[SERVER - BROADCAST] " + message);
        for (ClientHandler client : clients) {
            client.sendToClient(message);
        }
    }

    /**
     * Invia un messaggio al giocatore successivo rispetto al currentPlayerIndex attuale.
     * Se currentPlayerIndex è l'ultimo giocatore, il messaggio va al primo giocatore.
     *
     * @param message Il messaggio da inviare.
     */
    public static synchronized void sendMessageToNextPlayer(String message) {
        int nextPlayerId = (currentPlayerIndex + 1) % MAX_PLAYERS;

        //(nel caso in cui non tutti gli slot siano occupati, o un client si sia disconnesso)
        if (nextPlayerId >= 0 && nextPlayerId < clients.size()) {
            ClientHandler nextClient = clients.get(nextPlayerId);
            System.out.println("[SERVER - MESSAGGIO AL PLAYER " + nextPlayerId + "]: " + message);
            nextClient.sendToClient(message);
        } else {
            System.out.println("[SERVER - ERRORE] Impossibile trovare il client successivo con ID " + nextPlayerId);
        }
    }

    //invio a tutti i client il tavolo aggiornato
    public static synchronized void broadcastTableState() {
        StringBuilder tableState = new StringBuilder("TAVOLO_AGGIORNATO:");
        boolean firstPlayer = true;

        for (Map.Entry<Integer, List<List<Carta>>> entry : apertureSulTavolo.entrySet()) {
            int playerId = entry.getKey();
            List<List<Carta>> sets = entry.getValue();

            if (!sets.isEmpty()) {
                if (!firstPlayer) {
                    tableState.append("|"); //aggiungo il separatore "|" tra i giocatori solo se non è il primo giocatore
                }

                tableState.append(playerId).append(":");
                boolean firstSet = true;

                for (List<Carta> set : sets) {
                    if (!firstSet) {
                        tableState.append(";"); //aggiungo il separatore ";" tra le aperture solo se non è la prima apertura del un giocatore
                    }

                    boolean firstCard = true;
                    for (Carta card : set) {
                        if (!firstCard) {
                            tableState.append(","); //aggiungo il separatore "," tra le carte solo se non è la prima carta dell'apertura
                        }
                        tableState.append(card.getImageFilename());
                        firstCard = false;
                    }
                    firstSet = false;
                }
                firstPlayer = false;
            }
        }
        broadcastMessage(tableState.toString());
    }
    
    //invio a tutti i client i joker sul tavolo aggiornati. Es. messaggio "JOKER_AGGIORNATI:1:0=8_spades.jpg;1=8_clubs.jpg|2:1=3_diamonds.jpg"
    public static synchronized void broadcastJokerState() {
        StringBuilder jokerState = new StringBuilder("JOKER_AGGIORNATI:");
        boolean firstPlayer = true;

        //i separatori (|, ;, ,) vengono aggiunti prima di ogni elemento (giocatore, apertura o carta) se non è il primo della sequenza
        for (Map.Entry<Integer, Map<Integer, List<Carta>>> playerEntry : jokerSulTavolo.entrySet()) {
            if (!firstPlayer) {
                jokerState.append("|"); //separatore tra i giocatori
            }
            int playerId = playerEntry.getKey();
            Map<Integer, List<Carta>> apertureMap = playerEntry.getValue();
            jokerState.append(playerId).append(":");
            
            boolean firstApertura = true;
            for (Map.Entry<Integer, List<Carta>> aperturaEntry : apertureMap.entrySet()) {
                if (!firstApertura) {
                    jokerState.append(";"); //separatore tra le aperture
                }
                int aperturaIndex = aperturaEntry.getKey();
                List<Carta> carte = aperturaEntry.getValue();
                jokerState.append(aperturaIndex).append("=");
                
                boolean firstCard = true;
                for (Carta c : carte) {
                    if (!firstCard) {
                        jokerState.append(","); //separatore tra le carte
                    }
                    if (c != null) {
                        jokerState.append(c.getImageFilename());
                    }
                    firstCard = false;
                }
                firstApertura = false;
            }
            firstPlayer = false;
        }
        
        broadcastMessage(jokerState.toString());
    }

}
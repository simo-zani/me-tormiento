// ===== CLIENT JAVA FX BASE (Grafica + Connessione) =====

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*; //HBox, VBox, FlowPane (va automaticamente a capo quando lo spazio non basta), BorderPane
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class GameClient extends Application {

    private Mano mano = new Mano();
    private FlowPane carteBox;
    private Label status; //si aggiorna ad ogni mossa che faccio
    private Label header; //avvisa in che sezione mi trovo (apri, pesca, scarta, aspetta il tuo turno...)
    private Label round;  //resta visibile il round corrente
    private Label feedbackLabel; //mostra i feedback in conseguenza a un tasto cliccato (di errore o non)
    private Label punteggiLabel; //mostra i punteggi di tutti
    private int playerId = -1; //mostra l'id del giocatore
    private Label playerName; //mostra il nome del giocatore
    
    private BorderPane root;
    private PrintWriter out;

    private boolean mioTurno = false;    //si attiva quando è il mio turno
    private boolean haPescato = false;   //si attiva quando entro in modalità PESCA
    private boolean inScarto = false;    //si attiva quando entro in modalità SCARTO
    private boolean inApertura = false;  //si attiva quando entro in modalità APRI
    private boolean inTormento = false;  //si attiva quando entro in modalità TORMENTO

    private int currentRound = 1;        //il round corrente (1, 2, 3...)
    private int carteMano = 6;           //al primo round
    private int trisPerAprire;           //numero di tris richiesti per il round corrente (2, 3 o 4)
    private int scalePerAprire;          //numero di scale richieste per il round corrente (1, 2, o 3)
    private int trisConfermatiCorrenti;  //quanti tris il giocatore ha già confermato con successo in questa apertura
    private int scaleConfermateCorrenti; //quante scale il giocatore ha già confermato con successo in questa apertura

    //liste per gestire le carte durante l'apertura
    private List<Carta> carteSelezionatePerAperturaTotale = new ArrayList<>(); //tutte le carte selezionate per l'apertura complessiva
    private List<List<Carta>> trisCompletiConfermati = new ArrayList<>();      //lista di liste di carte, ogni sub-lista è un tris confermato
    private List<List<Carta>> scaleCompleteConfermate = new ArrayList<>();     //lista di liste di carte, ogni sub-lista è un tris confermato
    private List<Carta> currentTrisSelection = new ArrayList<>();
    private List<Carta> currentScalaSelection = new ArrayList<>();
    private List<Carta> selezionatePerApertura = new ArrayList<>();
    private Map<Carta, ImageView> cardImageViewMap = new HashMap<>(); //per applicare e rimuovere facilmente gli stili dalle ImageView corrispondenti alle Carta in mano

    //elementi UI per la sezione di APERTURA
    private VBox openingSectionContainer;      //contenitore per tutti gli slot di apertura (tris e scale) e i bottoni di conferma
    private HBox[] trisSlotContainers;         //array di HBox, uno per ogni gruppo di 3 slot (un tris)
    private HBox[] scalaSlotContainers;        //array di HBox, uno per ogni gruppo di 4 slot (una scala)
    private ImageView[][] trisSlotViews;       //matrice 2D per le ImageView degli slot (es. trisSlotViews[0][0] è il primo slot del primo tris)
    private ImageView[][] scalaSlotViews;      //matrice 2D per le ImageView degli slot (es. scalaSlotViews[0][0] è il primo slot della prima scala)
    private Button[] confermaTrisButtons;      //array di bottoni per confermare ogni singolo tris
    private Button[] confermaScalaButtons;     //array di bottoni per confermare ogni singola scala
    private Button completaAperturaButton;     //il bottone finale "COMPLETA APERTURA"
    private ScrollPane scrollPaneApertura;

    private Button pescaMazzoBtn;
    private Button pescaScartiBtn;
    private Button scartaBtn;
    private Button apriBtn;
    private Button esciBtn;
    
    @Override
    public void start(Stage primaryStage) { //lo Stage è una finestra del programma, noi ora creiamo quella principale dove vediamo le nostre carte
        root = new BorderPane(); //per posizionare gli elementi sopra, al centro e sotto

        // === Box in alto (header, status, round, playerName e punteggi) ===
        header = new Label("BENVENUTO A 'ME TORMIENTO' - ASPETTA IL TUO TURNO");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        status = new Label("Connessione in corso...");
        round = new Label("PRIMO ROUND - 2 TRIS");
        round.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        playerName = new Label("");
        punteggiLabel = new Label("");
        punteggiLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: blue;");
        VBox headerBox = new VBox(5, header, status, round, playerName, punteggiLabel);
        headerBox.setAlignment(Pos.CENTER);
        root.setTop(headerBox);

        //flowPane per le carte in mano
        carteBox = new FlowPane();
        carteBox.setHgap(10); //spaziatura orizzontale tra le carte
        carteBox.setVgap(10); //spaziatura verticale tra le righe di carte
        carteBox.setPrefWrapLength(500); //larghezza alla quale iniziare ad andare a capo
        
        //scrollPane che contiene il FlowPane - aggiunge barra di scorrimento verticale quando necessaria
        ScrollPane scrollPane = new ScrollPane(carteBox);
        scrollPane.setFitToWidth(true); //si adatta in larghezza
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); //no barra orizzontale
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); //sì barra verticale quando serve
        
        // === Sezione UI di Apertura ===
        openingSectionContainer = new VBox(15); //spazio verticale tra gli elementi
        openingSectionContainer.setAlignment(Pos.CENTER);
        openingSectionContainer.setPadding(new Insets(10));
        openingSectionContainer.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-background-color: #f8f8f8;");
        //bottone per completare l'apertura
        completaAperturaButton = new Button("COMPLETA APERTURA");
        completaAperturaButton.setDisable(true); //inizialmente disabilitato
        openingSectionContainer.getChildren().add(completaAperturaButton);

        scrollPaneApertura = new ScrollPane(openingSectionContainer);
        scrollPaneApertura.setFitToWidth(true);
        scrollPaneApertura.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPaneApertura.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPaneApertura.setVisible(false); //nascosto all'inizio
        scrollPaneApertura.setManaged(false); //non occupa spazio quando nascosto
        
        VBox centerContentBox = new VBox(10); //contenitore per scrollPane e scrollPaneApertura
        centerContentBox.setAlignment(Pos.CENTER);
        centerContentBox.getChildren().addAll(scrollPane, scrollPaneApertura);
        root.setCenter(centerContentBox); //il centro del BorderPane ora è centerContentBox


        // === Label feedback ===
        feedbackLabel = new Label();
        feedbackLabel.setStyle("-fx-text-fill: red;");

        // === Contenitore pulsanti in basso ===
        HBox buttonsBox = new HBox(20);
        pescaMazzoBtn = new Button("Pesca dal mazzo");
        pescaScartiBtn = new Button("Pesca dalla pila degli scarti");
        scartaBtn = new Button("SCARTA");
        apriBtn = new Button("APRI");
        esciBtn = new Button("ESCI");
        //rendo i pulsanti disattivati all'apertura del client
        pescaMazzoBtn.setDisable(true);
        pescaScartiBtn.setDisable(true);
        scartaBtn.setDisable(true);
        apriBtn.setDisable(true);

        buttonsBox.getChildren().addAll(pescaMazzoBtn, pescaScartiBtn, scartaBtn, apriBtn, esciBtn);
        buttonsBox.setAlignment(Pos.CENTER);
        //utilizzo bottomBox per includere feedbackLabel e buttonsBox
        VBox bottomBox = new VBox(5, feedbackLabel, buttonsBox);
        bottomBox.setAlignment(Pos.CENTER);
        root.setBottom(bottomBox); //pulsanti e label di feedback fissi in basso

        // === Scene e Stage ===
        Scene scene = new Scene(root, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        primaryStage.setTitle("ME TORMIENTO");
        primaryStage.setScene(scene); //dimensione della finestra del programma quando si avvia (è il contenuto)
        primaryStage.show();

        // === Requisiti iniziali del di come inizia la partita (primo round) ===
        round.setText("ROUND " + currentRound + " - " + carteMano + " CARTE: 2 TRIS PER APRIRE");

        // === Gestione click pulsanti ===
        pescaMazzoBtn.setOnAction(e -> {
            if (!mioTurno) {
                status.setText("Non è il tuo turno!");
                return;
            }
            if (haPescato) {
                status.setText("Hai gia' pescato una carta, ora devi scartarne una o aprire!");
                return;
            }
            out.println("PESCA_MAZZO"); //manda messaggio al server per pescare dal mazzo
            haPescato = true;
            pescaMazzoBtn.setDisable(true);
            pescaScartiBtn.setDisable(true);
            scartaBtn.setDisable(false);
            apriBtn.setDisable(false);
            status.setText("Hai pescato dal mazzo, ora devi scartarne una o aprire!");
            header.setText("APRI o SCARTA");
        });
        pescaScartiBtn.setOnAction(e -> {
            if (!mioTurno) {
                status.setText("Non è il tuo turno!");
                return;
            }
            if (haPescato) {
                status.setText("Hai gia' pescato una carta, ora devi scartarne una o aprire!");
                return;
            }
            out.println("PESCA_SCARTO"); //manda messaggio al server per pescare dalla cima degli scarti
            haPescato = true;
            pescaMazzoBtn.setDisable(true);
            pescaScartiBtn.setDisable(true);
            scartaBtn.setDisable(false);
            apriBtn.setDisable(false);
            status.setText("Hai pescato dalla pila degli scarti, ora devi scartarne una o aprire!");
            header.setText("APRI o SCARTA");
        });
        apriBtn.setOnAction(e -> {
            feedbackLabel.setText("");
                if (!inApertura) { //inApertura è false quindi entro in modalità apertura
                    
                    if (!mioTurno || !haPescato) { //controlla che sia il mio turno e che abbia pescato
                        feedbackLabel.setText("Devi essere nel tuo turno e aver pescato per provare ad aprire!");
                        return;
                    }
                    
                    scrollPaneApertura.setVisible(true);
                    scrollPaneApertura.setManaged(true);
                    inApertura = true;
                    apriBtn.setText("ESCI DALLA MODALITÀ APERTURA");

                    //disabilito i bottoni di pesca e scarto durante l'apertura
                    pescaMazzoBtn.setDisable(true);
                    pescaScartiBtn.setDisable(true);
                    scartaBtn.setDisable(true);

                    header.setText("SELEZIONA LE CARTE PER I TRIS");
                    status.setText("Seleziona 3 carte per il primo tris");

                    //inizializzo gli stati per l'apertura
                    trisConfermatiCorrenti = 0;
                    scaleConfermateCorrenti = 0;
                    currentTrisSelection = new ArrayList<>();
                    trisCompletiConfermati = new ArrayList<>();
                    scaleCompleteConfermate = new ArrayList<>();
                    carteSelezionatePerAperturaTotale = new ArrayList<>(); //resetto la selezione totale

                    //costruisco dinamicamente la UI per il numero richiesto di tris/scale
                    setupOpeningSlotsUI();

                    aggiornaManoGUI(); //aggiorna la mano per pulire eventuali evidenziazioni
                } else { //sono in modalità apertura e voglio uscire
                    handleEsciModalitaApertura();
                }
        });
        completaAperturaButton.setOnAction(e -> handleCompletaApertura());
        scartaBtn.setOnAction(e -> {
            if (!mioTurno || !haPescato) {
                status.setText("Deve essere il tuo turno e devi aver già pescato per poter scartare!");
                return;
            }

            if (inScarto) { //se sono già in modalità scarto, significa che voglio uscirne
                handleEsciModalitaScarto();
            } else { //entro in modalità scarto
                inScarto = true;
                inApertura = false;
                scartaBtn.setText("ESCI DALLA MODALITA' SCARTO");
                apriBtn.setDisable(true); //disabilito il bottone APRI mentre sono in modalità scarto
                pescaMazzoBtn.setDisable(true);
                pescaScartiBtn.setDisable(true);

                header.setText("SCARTA PER CONCLUDERE IL TURNO");
                status.setText("Seleziona una carta dalla tua mano da scartare");
                feedbackLabel.setText("Clicca sulla carta che vuoi scartare");
            }
        });
        esciBtn.setOnAction(e -> {
            out.println("DISCONNETTI");
            status.setText("Hai lasciato la partita.");
            pescaMazzoBtn.setDisable(true);
            pescaScartiBtn.setDisable(true);
            apriBtn.setDisable(true);
            scartaBtn.setDisable(true);
            //chiudi anche la finestra
            javafx.application.Platform.exit();
        });

        // === Connessione e caricamento carte iniziali ===
        new Thread(() -> {
            try {
                Socket socket = new Socket("localhost", 12345);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String line;
                while ((line = in.readLine()) != null) {       //il client resta in ascolto continuo dal server 
                    System.out.println("[SERVER] " + line);

                    if (line.startsWith("CARTE_INIZIALI_ROUND_")) {    //il server mi dice la mia mano sotto forma di stringa
                        String[] parts = line.substring("CARTE_INIZIALI_ROUND_".length()).split(":");
                        currentRound = Integer.parseInt(parts[0]); //aggiorno la variabile currentRound del client
                        String[] imageFiles = parts[1].split(",");

                        carteMano = 5 + currentRound;
                        if(carteMano == 12){
                            carteMano = 12; //per l'ultimo round restano sempre 12 carte
                        }

                        //qui devo aggiornare trisPerAprire e scalePerAprire in base al round (creerò un metodo)

                        javafx.application.Platform.runLater(() -> {
                            round.setText(titleRound(currentRound));
                            status.setText("Le tue carte per il Round " + currentRound + ":");

                            mano.getCarte().clear(); //svuoto la mano precedente (anche se in teoria già svuotata da ROUND_TERMINATO)
                            carteBox.getChildren().clear();

                            for (String fileName : imageFiles) {
                                try {
                                    String path = "/assets/decks/bycicle/" + fileName;
                                    Image img = new Image(getClass().getResourceAsStream(path));
                                    Carta carta = cartaFromFileName(fileName); //da implementare per mappare file a Carta
                                    mano.aggiungiCarta(carta);

                                    ImageView view = new ImageView(img);
                                    view.setFitWidth(100);
                                    view.setPreserveRatio(true);
                                    carteBox.getChildren().add(view);
                                } catch (Exception e) {
                                    root.getChildren().add(new Label("[IMG MANCANTE] " + fileName));
                                }
                            }
                            aggiornaManoGUI();
                        });
                    } else if(line.startsWith("YOUR_PLAYER_ID:")){ //il client riceve il proprio ID
                        playerId = Integer.parseInt(line.substring("YOUR_PLAYER_ID:".length()));
                        javafx.application.Platform.runLater(() -> {
                            playerName.setText("Player " + String.valueOf(playerId)); //TODO: quando sarà implementata l'autenticazione sostituire il playerName col vero nome scelto dal giocatore
                            header.setText("BENVENUTO PLAYER " + playerId + " - ASPETTA IL TUO TURNO");
                        });
                    } else if (line.equals("ROUND_TERMINATO")) { //il server chiede le carte rimanenti
                        javafx.application.Platform.runLater(() -> {
                            status.setText("Il round è terminato! Invio delle tue carte rimanenti...");

                            //preparo e invio le carte rimanenti al server
                            StringBuilder remainingCardsMessage = new StringBuilder("CARTE_RIMANENTI:");
                            if (!mano.getCarte().isEmpty()) { //solo se la mano non è già vuota (se ha finito per primo)
                                for (Carta c : mano.getCarte()) {
                                    remainingCardsMessage.append(c.getImageFilename()).append(",");
                                }
                                remainingCardsMessage.setLength(remainingCardsMessage.length() - 1); //rimuovo l'ultima virgola
                            }
                            out.println(remainingCardsMessage.toString());

                            //svuoto la mano locale e la GUI per TUTTI i client
                            mano.getCarte().clear();
                            aggiornaManoGUI(); //pulisco la visualizzazione delle carte
                            
                            //disabilito tutto in attesa del nuovo round
                            resetPerNuovoRound();

                            header.setText("ROUND TERMINATO! Attendi le nuove carte.");
                            status.setText("Attendere il rimescolamento e la distribuzione delle carte per il prossimo round.");
                        });
                    } else if (line.startsWith("PUNTEGGI_AGGIORNATI:")) { //il server invia i punteggi aggiornati
                        String punteggiString = line.substring(20);
                        javafx.application.Platform.runLater(() -> {
                            punteggiLabel.setText("Punteggi: " + punteggiString.replace(";", " | "));
                        });
                    } else if (line.equals("TOCCA_A_TE")) { //il server comunica il proprio turno
                        javafx.application.Platform.runLater(() -> {
                            header.setText("PESCA UNA CARTA");
                            mioTurno = true;
                            haPescato = false;
                            inScarto = false;
                            inApertura = false;
                            selezionatePerApertura.clear(); //pulisco le selezioni
                            apriBtn.setText("APRI");
                            feedbackLabel.setText("");

                            pescaMazzoBtn.setDisable(false);
                            pescaScartiBtn.setDisable(false);
                            scartaBtn.setDisable(true);
                            apriBtn.setDisable(true);
                            status.setText("Tocca a te! Pesca dal mazzo o dalla pila degli scarti.");
                            aggiornaManoGUI();
                        });
                    } else if (line.startsWith("PESCATA:")) {   //il server mi dice che carta ho pescato
                        String fileName = line.substring(8);
                        Carta nuovaCarta = cartaFromFileName(fileName);
                        mano.aggiungiCarta(nuovaCarta);
                        javafx.application.Platform.runLater(() -> {
                            status.setText("Hai pescato: " + nuovaCarta);
                            aggiornaManoGUI();
                            //dopo aver pescato abilito SCARTA e APRI
                            pescaMazzoBtn.setDisable(true);
                            pescaScartiBtn.setDisable(true);
                            scartaBtn.setDisable(false);
                            apriBtn.setDisable(false);

                            inScarto = false;
                            inApertura = false;
                            selezionatePerApertura.clear();
                            apriBtn.setText("APRI");
                            feedbackLabel.setText("");
                        });
                    } else if (line.startsWith("STATO_PILA_SCARTI:")) { //il server mi aggiorna sullo stato della pila degli scarti (vuota o non)
                        String stato = line.substring(18); // VUOTA o NON_VUOTA
                        boolean vuota = stato.equals("VUOTA");

                        javafx.application.Platform.runLater(() -> {
                            pescaScartiBtn.setDisable(vuota);
                            if (vuota && mioTurno && !haPescato) {
                                status.setText("La pila degli scarti è vuota, puoi pescare solo dal mazzo.");
                            }
                        });
                    } else if (line.startsWith("GIOCATORE_HA_APERTURA:")) { //il server dice che ho aperto regolarmente
                        String playerName = line.substring(line.indexOf(":") + 1);
                        javafx.application.Platform.runLater(() -> {
                            status.setText(playerName + " ha aperto!");
                            header.setText("SCARTA UNA CARTA");
                            // TODO: AGGIORNARE GUI TAVOLO CON TRIS E SCALE APERTE PER FUTURI ATTACCHI
                        });
                    } else if (line.equals("ERRORE_APERTURA_NON_VALIDA")) { //il server riscontra un problema sull'apertura
                        javafx.application.Platform.runLater(() -> {
                            status.setText("Apertura non valida! Riprova o scarta.");
                            header.setText("APRI o SCARTA");
                            selezionatePerApertura.clear(); //pulisci le selezioni
                            inApertura = false; //reimposta lo stato
                            apriBtn.setText("APRI"); //reimposta il testo del pulsante
                            aggiornaManoGUI(); //rimuovi l'evidenziazione dalle carte
                        });
                    }
                }
                in.close();
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> status.setText("Connessione fallita."));
            }
        }).start();
    }

    private void setupOpeningSlotsUI() {
        //pulisco la configurazione precedente
        openingSectionContainer.getChildren().clear();
        trisPerAprire = 0;
        scalePerAprire = 0;
        
        FlowPane trisScaleFlowPane = new FlowPane(); //contenitore per tutti i tris e le scale
        trisScaleFlowPane.setHgap(30); //spazio tra i tris
        trisScaleFlowPane.setVgap(30); //spazio verticale se vanno a capo
        trisScaleFlowPane.setAlignment(Pos.CENTER);
        trisScaleFlowPane.setPrefWrapLength(800); //se superano 800px, vanno a capo

        switch (currentRound) {
            case 1: //ROUND 1: 6 carte - 2 tris
                trisPerAprire = 2;
                trisSlotContainers = new HBox[trisPerAprire];
                trisSlotViews = new ImageView[trisPerAprire][3];
                confermaTrisButtons = new Button[trisPerAprire]; //mi crea 2 bottoni perché devo fare 2 tris

                for (int i = 0; i < trisPerAprire; i++) {
                    //creo un contenitore HBox per i 3 slot di un singolo tris
                    HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                    trisRow.setAlignment(Pos.CENTER);
                    trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso al primo tris per indicare che è quello attivo
                    if (i == 0) {
                        trisRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 3 ImageView per gli slot
                    for (int j = 0; j < 3; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                        trisRow.getChildren().add(slotWrapper);
                    }
                    trisSlotContainers[i] = trisRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int trisIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaTris(trisIndex));
                    confermaTrisButtons[i] = confirmBtn;

                    VBox trisGroup = new VBox(10, trisRow, confirmBtn);
                    trisGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(trisGroup);
                }
                //svuoto e aggiungo tutto in orizzontale
                openingSectionContainer.getChildren().clear();
                openingSectionContainer.getChildren().addAll(trisScaleFlowPane, completaAperturaButton);
                completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris individuali sono confermati
                break;
            case 2: //ROUND 2: 7 carte - 1 tris e 1 scala
                trisPerAprire = 1;
                scalePerAprire = 1;

                trisSlotContainers = new HBox[trisPerAprire];
                trisSlotViews = new ImageView[trisPerAprire][3];

                scalaSlotContainers = new HBox[scalePerAprire];
                scalaSlotViews = new ImageView[scalePerAprire][4];

                confermaTrisButtons = new Button[trisPerAprire]; //mi crea 1 bottone perché devo fare 1 tris
                confermaScalaButtons = new Button[scalePerAprire]; //mi crea 1 bottone perché devo fare 1 scala

                for (int i = 0; i < trisPerAprire; i++) {
                    //creo un contenitore HBox per i 3 slot di un singolo tris
                    HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                    trisRow.setAlignment(Pos.CENTER);
                    trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso al primo tris per indicare che è quello attivo
                    if (i == 0) {
                        trisRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 3 ImageView per gli slot
                    for (int j = 0; j < 3; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                        trisRow.getChildren().add(slotWrapper);
                    }
                    trisSlotContainers[i] = trisRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int trisIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaTris(trisIndex));
                    confermaTrisButtons[i] = confirmBtn;

                    VBox trisGroup = new VBox(10, trisRow, confirmBtn);
                    trisGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(trisGroup);
                }

                for (int i = 0; i < scalePerAprire; i++) {
                    //creo un contenitore HBox per i 4 slot di una singola scala
                    HBox scalaRow = new HBox(10); //piccolo spazio tra le carte
                    scalaRow.setAlignment(Pos.CENTER);
                    scalaRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso alla prima scala per indicare che è quella attiva
                    if (i == 0) {
                        scalaRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 4 ImageView per gli slot
                    for (int j = 0; j < 4; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                        scalaRow.getChildren().add(slotWrapper);
                    }
                    scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int scalaIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                    confermaScalaButtons[i] = confirmBtn;

                    VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                    scalaGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(scalaGroup);
                }
                //svuoto e aggiungo tutto in orizzontale
                openingSectionContainer.getChildren().clear();
                openingSectionContainer.getChildren().addAll(trisScaleFlowPane, completaAperturaButton);
                completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris individuali sono confermati
                break;
            case 3: //ROUND 2: 8 carte - 2 scale
                scalePerAprire = 2;
                scalaSlotContainers = new HBox[scalePerAprire];
                scalaSlotViews = new ImageView[scalePerAprire][4];
                confermaScalaButtons = new Button[scalePerAprire]; //mi crea 1 bottone perché devo fare 1 scala

                for (int i = 0; i < scalePerAprire; i++) {
                    //creo un contenitore HBox per i 4 slot di una singola scala
                    HBox scalaRow = new HBox(10); //piccolo spazio tra le carte
                    scalaRow.setAlignment(Pos.CENTER);
                    scalaRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso alla prima scala per indicare che è quella attiva
                    if (i == 0) {
                        scalaRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 4 ImageView per gli slot
                    for (int j = 0; j < 4; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                        scalaRow.getChildren().add(slotWrapper);
                    }
                    scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int scalaIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                    confermaScalaButtons[i] = confirmBtn;

                    VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                    scalaGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(scalaGroup);
                }
                //svuoto e aggiungo tutto in orizzontale
                openingSectionContainer.getChildren().clear();
                openingSectionContainer.getChildren().addAll(trisScaleFlowPane, completaAperturaButton);
                completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris individuali sono confermati
                break;
            case 4: //ROUND 2: 9 carte - 3 tris
                trisPerAprire = 3;
                trisSlotContainers = new HBox[trisPerAprire];
                trisSlotViews = new ImageView[trisPerAprire][3];
                confermaTrisButtons = new Button[trisPerAprire]; //mi crea 2 bottoni perché devo fare 2 tris

                for (int i = 0; i < trisPerAprire; i++) {
                    //creo un contenitore HBox per i 3 slot di un singolo tris
                    HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                    trisRow.setAlignment(Pos.CENTER);
                    trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso al primo tris per indicare che è quello attivo
                    if (i == 0) {
                        trisRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 3 ImageView per gli slot
                    for (int j = 0; j < 3; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                        trisRow.getChildren().add(slotWrapper);
                    }
                    trisSlotContainers[i] = trisRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int trisIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaTris(trisIndex));
                    confermaTrisButtons[i] = confirmBtn;

                    VBox trisGroup = new VBox(10, trisRow, confirmBtn);
                    trisGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(trisGroup);
                }
                //svuoto e aggiungo tutto in orizzontale
                openingSectionContainer.getChildren().clear();
                openingSectionContainer.getChildren().addAll(trisScaleFlowPane, completaAperturaButton);
                completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris individuali sono confermati
                break;
            case 5: //ROUND 2: 10 carte - 2 tris e 1 scala
                trisPerAprire = 2;
                scalePerAprire = 1;

                trisSlotContainers = new HBox[trisPerAprire];
                trisSlotViews = new ImageView[trisPerAprire][3];

                scalaSlotContainers = new HBox[scalePerAprire];
                scalaSlotViews = new ImageView[scalePerAprire][4];

                confermaTrisButtons = new Button[trisPerAprire]; //mi crea 1 bottone perché devo fare 1 tris
                confermaScalaButtons = new Button[scalePerAprire]; //mi crea 1 bottone perché devo fare 1 scala

                for (int i = 0; i < trisPerAprire; i++) {
                    //creo un contenitore HBox per i 3 slot di un singolo tris
                    HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                    trisRow.setAlignment(Pos.CENTER);
                    trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso al primo tris per indicare che è quello attivo
                    if (i == 0) {
                        trisRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 3 ImageView per gli slot
                    for (int j = 0; j < 3; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                        trisRow.getChildren().add(slotWrapper);
                    }
                    trisSlotContainers[i] = trisRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int trisIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaTris(trisIndex));
                    confermaTrisButtons[i] = confirmBtn;

                    VBox trisGroup = new VBox(10, trisRow, confirmBtn);
                    trisGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(trisGroup);
                }

                for (int i = 0; i < scalePerAprire; i++) {
                    //creo un contenitore HBox per i 4 slot di una singola scala
                    HBox scalaRow = new HBox(10); //piccolo spazio tra le carte
                    scalaRow.setAlignment(Pos.CENTER);
                    scalaRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso alla prima scala per indicare che è quella attiva
                    if (i == 0) {
                        scalaRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 4 ImageView per gli slot
                    for (int j = 0; j < 4; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                        scalaRow.getChildren().add(slotWrapper);
                    }
                    scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int scalaIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                    confermaScalaButtons[i] = confirmBtn;

                    VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                    scalaGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(scalaGroup);
                }
                //svuoto e aggiungo tutto in orizzontale
                openingSectionContainer.getChildren().clear();
                openingSectionContainer.getChildren().addAll(trisScaleFlowPane, completaAperturaButton);
                completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris individuali sono confermati
                break;
            case 6: //ROUND 2: 11 carte - 1 tris e 2 scale
                trisPerAprire = 1;
                scalePerAprire = 2;

                trisSlotContainers = new HBox[trisPerAprire];
                trisSlotViews = new ImageView[trisPerAprire][3];

                scalaSlotContainers = new HBox[scalePerAprire];
                scalaSlotViews = new ImageView[scalePerAprire][4];

                confermaTrisButtons = new Button[trisPerAprire]; //mi crea 1 bottone perché devo fare 1 tris
                confermaScalaButtons = new Button[scalePerAprire]; //mi crea 1 bottone perché devo fare 1 scala

                for (int i = 0; i < trisPerAprire; i++) {
                    //creo un contenitore HBox per i 3 slot di un singolo tris
                    HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                    trisRow.setAlignment(Pos.CENTER);
                    trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso al primo tris per indicare che è quello attivo
                    if (i == 0) {
                        trisRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 3 ImageView per gli slot
                    for (int j = 0; j < 3; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                        trisRow.getChildren().add(slotWrapper);
                    }
                    trisSlotContainers[i] = trisRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int trisIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaTris(trisIndex));
                    confermaTrisButtons[i] = confirmBtn;

                    VBox trisGroup = new VBox(10, trisRow, confirmBtn);
                    trisGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(trisGroup);
                }

                for (int i = 0; i < scalePerAprire; i++) {
                    //creo un contenitore HBox per i 4 slot di una singola scala
                    HBox scalaRow = new HBox(10); //piccolo spazio tra le carte
                    scalaRow.setAlignment(Pos.CENTER);
                    scalaRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso alla prima scala per indicare che è quella attiva
                    if (i == 0) {
                        scalaRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 4 ImageView per gli slot
                    for (int j = 0; j < 4; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                        scalaRow.getChildren().add(slotWrapper);
                    }
                    scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int scalaIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                    confermaScalaButtons[i] = confirmBtn;

                    VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                    scalaGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(scalaGroup);
                }
                //svuoto e aggiungo tutto in orizzontale
                openingSectionContainer.getChildren().clear();
                openingSectionContainer.getChildren().addAll(trisScaleFlowPane, completaAperturaButton);
                completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris individuali sono confermati
                break;
            case 7: //ROUND 2: 12 carte - 4 tris
                trisPerAprire = 4;
                trisSlotContainers = new HBox[trisPerAprire];
                trisSlotViews = new ImageView[trisPerAprire][3];
                confermaTrisButtons = new Button[trisPerAprire]; //mi crea 2 bottoni perché devo fare 2 tris

                for (int i = 0; i < trisPerAprire; i++) {
                    //creo un contenitore HBox per i 3 slot di un singolo tris
                    HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                    trisRow.setAlignment(Pos.CENTER);
                    trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso al primo tris per indicare che è quello attivo
                    if (i == 0) {
                        trisRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 3 ImageView per gli slot
                    for (int j = 0; j < 3; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                        trisRow.getChildren().add(slotWrapper);
                    }
                    trisSlotContainers[i] = trisRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int trisIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaTris(trisIndex));
                    confermaTrisButtons[i] = confirmBtn;

                    VBox trisGroup = new VBox(10, trisRow, confirmBtn);
                    trisGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(trisGroup);
                }
                //svuoto e aggiungo tutto in orizzontale
                openingSectionContainer.getChildren().clear();
                openingSectionContainer.getChildren().addAll(trisScaleFlowPane, completaAperturaButton);
                completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris individuali sono confermati
                break;
            case 8: //ROUND 2: 12 carte - 3 scale
                scalePerAprire = 3;
                scalaSlotContainers = new HBox[scalePerAprire];
                scalaSlotViews = new ImageView[scalePerAprire][4];
                confermaScalaButtons = new Button[scalePerAprire]; //mi crea 1 bottone perché devo fare 1 scala

                for (int i = 0; i < scalePerAprire; i++) {
                    //creo un contenitore HBox per i 4 slot di una singola scala
                    HBox scalaRow = new HBox(10); //piccolo spazio tra le carte
                    scalaRow.setAlignment(Pos.CENTER);
                    scalaRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                    
                    //applico uno stile diverso alla prima scala per indicare che è quella attiva
                    if (i == 0) {
                        scalaRow.setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }

                    //creo 4 ImageView per gli slot
                    for (int j = 0; j < 4; j++) {
                        StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                        scalaRow.getChildren().add(slotWrapper);
                    }
                    scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox

                    //creo il bottone CONFERMA per questo tris
                    Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                    confirmBtn.setDisable(true); //inizialmente disabilitato
                    final int scalaIndex = i; //per l'uso nella lambda expression
                    confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                    confermaScalaButtons[i] = confirmBtn;

                    VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                    scalaGroup.setAlignment(Pos.CENTER);

                    trisScaleFlowPane.getChildren().add(scalaGroup);
                }
                //svuoto e aggiungo tutto in orizzontale
                openingSectionContainer.getChildren().clear();
                openingSectionContainer.getChildren().addAll(trisScaleFlowPane, completaAperturaButton);
                completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris individuali sono confermati
                break;
            default:
                break;
        }
    }

    //gestione della conferma di un singolo tris
    private void handleConfermaTris(int trisIndex) {
        feedbackLabel.setText("");
        //mi assicuro che l'utente stia confermando il tris corrente in ordine
        if (trisIndex != trisConfermatiCorrenti) {
            feedbackLabel.setText("Devi confermare i tris in ordine!");
            return;
        }

        //verifico che siano state selezionate esattamente 3 carte per questo tris
        if (currentTrisSelection.size() == 3) {
            if (verificaTris(currentTrisSelection)) {
                //tris valido! lo aggiungo all’elenco confermato
                trisCompletiConfermati.add(new ArrayList<>(currentTrisSelection)); //aggiungo una copia del tris confermato
                carteSelezionatePerAperturaTotale.addAll(currentTrisSelection); //aggiungo alla lista totale
                //applico stile verde e disabilito le carte nella GUI dopo la conferma
                for (Carta c : currentTrisSelection) {
                    ImageView view = cardImageViewMap.get(c);
                    if (view != null) {
                        view.getStyleClass().remove("selected-card-apertura"); //rimuovo il bordo oro
                        view.getStyleClass().add("confirmed-card-apertura"); //aggiungo il bordo verde
                        view.setDisable(true); //rendo la carta non più cliccabile
                    }
                }
                aggiornaManoGUI();
                trisConfermatiCorrenti++;
                feedbackLabel.setText("Tris " + trisConfermatiCorrenti + " confermato con successo!");

                //disabilito il bottone di conferma per questo tris e lo evidenzio come confermato (verde)
                confermaTrisButtons[trisIndex].setDisable(true);
                trisSlotContainers[trisIndex].setStyle("-fx-border-color: green; -fx-border-width: 2; -fx-padding: 5;");

                //preparo per il tris successivo o per la conferma finale
                if (trisConfermatiCorrenti < trisPerAprire) {
                    currentTrisSelection.clear(); //resetto la selezione per il prossimo tris
                    status.setText("Seleziona 3 carte per il tris successivo (" + (trisConfermatiCorrenti + 1) + "/" + trisPerAprire + ").");

                    //rimuovo l'highlight dal tris precedente e evidenzio il prossimo
                    if (trisConfermatiCorrenti > 0) {
                         trisSlotContainers[trisIndex].setStyle("-fx-border-color: green; -fx-border-width: 2; -fx-padding: 10;");
                    }
                    if (trisConfermatiCorrenti < trisPerAprire) { //evidenzio il prossimo tris
                        trisSlotContainers[trisConfermatiCorrenti].setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                    }
                } else {
                    //tutti i tris richiesti sono stati confermati!
                    status.setText("Tutti i tris sono stati confermati. Clicca 'COMPLETA APERTURA' per inviare.");
                    completaAperturaButton.setDisable(false); //abilito il bottone finale
                    //rimuovo l'highlight dall'ultimo tris confermato
                    trisSlotContainers[trisIndex].setStyle("-fx-border-color: green; -fx-border-width: 2; -fx-padding: 10;");
                }
                aggiornaManoGUI(); //rimuovo l'evidenziazione dalle carte in mano (se sono state spostate agli slot)
                updateOpeningSlotsGUI(); //aggiorno la visualizzazione degli slot
            } else {
                //tris non valido
                feedbackLabel.setText("Questo tris non è valido. Seleziona altre carte");
                currentTrisSelection.clear(); // Pulisci la selezione corrente
                //rimuovo visivamente le carte dagli slot e resetta il loro stile
                for (ImageView slot : trisSlotViews[trisIndex]) {
                    slot.setImage(null);
                    slot.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-background-color: #f0f0f0;");
                }
                //rimuovo l'highlight gold dalle carte che erano state selezionate per questo tris non valido
                //e anche dalla lista generale delle selezionate per evitare duplicazioni visive
                for (Carta c : new ArrayList<>(selezionatePerApertura)) { //copia per evitare ConcurrentModificationException
                    if (currentTrisSelection.contains(c)) { //solo le carte che erano nella selezione corrente
                        ImageView view = cardImageViewMap.get(c);
                        if (view != null) {
                            view.getStyleClass().remove("selected-card-apertura");
                        }
                        selezionatePerApertura.remove(c); //rimuovo dalla selezione generale
                    }
                }
                aggiornaManoGUI(); //aggiorno la mano per togliere l'evidenziazione
                confermaTrisButtons[trisIndex].setDisable(true); //disabilito il bottone finché non ci sono 3 carte nuove
            }
        } else {
            feedbackLabel.setText("Devi selezionare 3 carte per confermare questo tris");
        }
    }

    private void handleConfermaScala(int scalaIndex) {
        //controllo che si stia confermando la scala nell'ordine corretto
        if (scalaIndex != scaleConfermateCorrenti) {
            feedbackLabel.setText("Devi confermare le scale in ordine!");
            return;
        }

        //verifico che siano state selezionate esattamente 4 carte
        if (currentScalaSelection.size() == 4) {
            //verifico validità della scala
            RisultatoScala risultato = verificaScalaConJoker(currentScalaSelection, Optional.empty());

            if (risultato.isValida()) {
                //scala valida! la aggiungo all’elenco confermato
                scaleCompleteConfermate.add(new ArrayList<>(currentScalaSelection));
                carteSelezionatePerAperturaTotale.addAll(currentScalaSelection);
                //applico stile verde e disabilito le carte nella GUI dopo la conferma
                for (Carta c : currentScalaSelection) {
                    ImageView view = cardImageViewMap.get(c);
                    if (view != null) {
                        view.getStyleClass().remove("selected-card-apertura"); //rimuovo il bordo oro
                        view.getStyleClass().add("confirmed-card-apertura"); //aggiungo il bordo verde
                        view.setDisable(true); //rendo la carta non più cliccabile
                    }
                }
                aggiornaManoGUI();
                scaleConfermateCorrenti++;
                feedbackLabel.setText("Scala " + scaleConfermateCorrenti + " confermata con successo!");

                //disabilito il bottone di conferma per questa scala e la evidenzio come confermata (verde)
                confermaScalaButtons[scalaIndex].setDisable(true);
                scalaSlotContainers[scalaIndex].setStyle("-fx-border-color: green; -fx-border-width: 2; -fx-padding: 5;");

                //preparo per la prossima scala (se ce ne sono altre)
                if (scaleConfermateCorrenti < scalePerAprire) {
                    currentScalaSelection.clear();
                    status.setText("Seleziona 4 carte per la prossima scala (" + (scaleConfermateCorrenti + 1) + "/" + scalePerAprire + ").");
                    //evidenzio il prossimo slot scala con un bordo grigio
                    scalaSlotContainers[scaleConfermateCorrenti].setStyle("-fx-border-color: gray; -fx-border-width: 2; -fx-padding: 10;");
                } else {
                    //tutte le scale confermate
                    status.setText("Tutte le scale sono state confermate. Clicca 'COMPLETA APERTURA' per inviare.");
                    completaAperturaButton.setDisable(false);
                    scalaSlotContainers[scalaIndex].setStyle("-fx-border-color: green; -fx-border-width: 2; -fx-padding: 10;");
                }
                updateOpeningSlotsGUI();
            } else {
                //scala non valida
                feedbackLabel.setText("Questa scala non è valida. Seleziona altre carte");
                currentScalaSelection.clear();

                //resetto visivamente gli slot scala
                for (ImageView slot : scalaSlotViews[scalaIndex]) {
                    slot.setImage(null);
                    slot.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-background-color: #f0f0f0;");
                }
                //rimuovo l'highlight gold dalle carte che erano state selezionate per questa scala non valida
                //e anche dalla lista generale delle selezionate per evitare duplicazioni visive
                for (Carta c : new ArrayList<>(selezionatePerApertura)) { //copia per evitare ConcurrentModificationException
                    if (currentScalaSelection.contains(c)) { //solo le carte che erano nella selezione corrente
                        ImageView view = cardImageViewMap.get(c);
                        if (view != null) {
                            view.getStyleClass().remove("selected-card-apertura");
                        }
                        selezionatePerApertura.remove(c); //rimuovo dalla selezione generale
                    }
                }
                aggiornaManoGUI();
                confermaScalaButtons[scalaIndex].setDisable(true);
            }
        } else {
            feedbackLabel.setText("Devi selezionare 4 carte per confermare questa scala");
        }
    }

    private void handleCompletaApertura() {
        feedbackLabel.setText("");
        //tutti i tris/scale individuali sono già stati validati e confermati.
        //devo inviare al server le carte totali dell'apertura
        int carteRimanentiDopoApertura = mano.getCarte().size() - carteSelezionatePerAperturaTotale.size();

        //controllo regolamento: "non puoi finire le carte con un'apertura"
        if (carteRimanentiDopoApertura == 0) {
            feedbackLabel.setText("Non puoi finire le carte con un'apertura! Devi scartare una carta per terminare il round!");
            handleEsciModalitaApertura(); //resetto la UI, pulisco le selezioni e riabilito i pulsanti APRI/SCARTA
            return; //se la regola è violata, non inviamo nulla al server e non rimuoviamo le carte dalla mano
        }

        //se arrivo qui, significa che l'apertura è valida e non svuota la mano; procedo con l'invio al server e la rimozione delle carte
        StringBuilder aperturaMessage = new StringBuilder("APRI:");
        for (Carta c : carteSelezionatePerAperturaTotale) {
            aperturaMessage.append(c.getImageFilename()).append(",");
        }
        if (aperturaMessage.length() > "APRI:".length()) {
            aperturaMessage.setLength(aperturaMessage.length() - 1); //rimuovo la virgola finale
        }
        out.println(aperturaMessage.toString());

        //rimuovo le carte dalla mano locale dopo l'invio al server
        for (Carta c : carteSelezionatePerAperturaTotale) {
            mano.rimuoviCarta(c);
        }
        aggiornaManoGUI(); //aggiorno la mano dopo aver rimosso le carte

        status.setText(textApertura(currentRound));
        header.setText("SCARTA"); //passo alla fase di scarto
        feedbackLabel.setText("");

        //reset dello stato dopo l'apertura riuscita
        inApertura = false;
        apriBtn.setText("APRI");
        apriBtn.setDisable(true); //il giocatore ha aperto, non può aprire di nuovo in questo round.
        completaAperturaButton.setDisable(true); //disabilito il bottone finale
        
        mioTurno = true; //il turno è ancora del giocatore per lo scarto finale
        haPescato = true; //necessario per abilitare lo scarto
        pescaMazzoBtn.setDisable(true);
        pescaScartiBtn.setDisable(true);
        scartaBtn.setDisable(false); //ABILITO SOLO SCARTA
        status.setText("Apertura riuscita! Ora devi scartare una carta per terminare il turno.");

        //nascondo completamente la sezione di apertura
        scrollPaneApertura.setVisible(false);
        scrollPaneApertura.setManaged(false);
    }

    private void handleEsciModalitaApertura() {
        feedbackLabel.setText("Modalità apertura annullata.");
        status.setText("Puoi scartare una carta o provare ad aprire nuovamente.");
        header.setText("APRI o SCARTA");

        //resetto lo stato di apertura
        inApertura = false;
        trisConfermatiCorrenti = 0;
        scaleConfermateCorrenti = 0;
        currentTrisSelection.clear();
        currentScalaSelection.clear();
        trisCompletiConfermati.clear();
        scaleCompleteConfermate.clear();
        carteSelezionatePerAperturaTotale.clear();
        selezionatePerApertura.clear();

        //nascondo la sezione di apertura
        scrollPaneApertura.setVisible(false);
        scrollPaneApertura.setManaged(false);

        //nascondo il bottone "COMPLETA APERTURA"
        completaAperturaButton.setDisable(true);

        //riabilito i bottoni principali per la fine del turno
        pescaMazzoBtn.setDisable(true); //ancora disabilitati perché ho già pescato
        pescaScartiBtn.setDisable(true); //ancora disabilitati perché ho già pescato
        scartaBtn.setDisable(false); //abilito SCARTA
        apriBtn.setText("APRI"); //ripristino il testo del bottone
        apriBtn.setDisable(false); //abilito nuovamente APRI

        aggiornaManoGUI(); //aggiorno la mano per rimuovere evidenziazioni
    }

    private void handleEsciModalitaScarto(){
        inScarto = false; //esco dalla modalità scarto
        scartaBtn.setText("SCARTA"); //ripristino il testo del bottone
        
        //riabilito il bottone APRI, dato che il giocatore può ancora scegliere tra scartare e aprire
        apriBtn.setDisable(false); 
        
        //ripristina lo stato del turno dopo aver pescato
        status.setText("Hai pescato. Ora puoi scartare una carta o aprire");
        feedbackLabel.setText(""); //pulisco il feedback

        scrollPaneApertura.setVisible(false);
        scrollPaneApertura.setManaged(false);
    }

    private void scartaCarta(Carta carta) {
        if (!mioTurno) {
            status.setText("Non è il tuo turno!");
            return;
        }
        if (!haPescato) {
            status.setText("Devi prima pescare prima di scartare!");
            return;
        }
        if (!mano.contiene(carta)) {
            status.setText("Non hai questa carta in mano!");
            return;
        }

        mano.rimuoviCarta(carta);
        aggiornaManoGUI();
        
        out.println("SCARTA:" + carta.toString());
        status.setText("Hai scartato: " + carta.toString() + ". Attendi il tuo prossimo turno.");

        mioTurno = false;
        haPescato = false;
        inScarto = false; //resetto la modalità scarto

        scartaBtn.setText("SCARTA");
        
        pescaMazzoBtn.setDisable(true);
        pescaScartiBtn.setDisable(true);
        scartaBtn.setDisable(true);
        apriBtn.setDisable(true);

        header.setText("HAI FINITO IL TURNO, ASPETTA IL PROSSIMO"); //imposta l'header
        feedbackLabel.setText(""); //svuoto il feedbackLabel

        if (mano.getCarte().isEmpty()) {
            feedbackLabel.setText("Hai finito le carte! Hai vinto questo round!");
            out.println("ROUND_FINITO"); //notifico al server che il round è finito
        }

        scrollPaneApertura.setVisible(false);
        scrollPaneApertura.setManaged(false);
    }

    //metodo per verificare la validità di un tris
    private boolean verificaTris(List<Carta> carte){
        //il tris deve essere composto da esattamente 3 carte
        if (carte.size() != 3) {
            return false;
        }

        //separo i Joker dalle carte normali
        List<Carta> carteNormali = new ArrayList<>();
        int numJoker = 0;
        for (Carta c : carte) {
            if (c.getValore().equalsIgnoreCase("Joker")) {
                numJoker++;
            } else {
                carteNormali.add(c);
            }
        }

        //caso con 3 Joker
        if (numJoker == 3) {
            return true;
        }

        //conteggio dei valori delle carte normali
        Map<String, Integer> conteggioValori = new HashMap<>();
        for (Carta c : carteNormali) {
            conteggioValori.put(c.getValore(), conteggioValori.getOrDefault(c.getValore(), 0) + 1);
        }

        //un tris richiede che ci sia un solo tipo di carta normale
        if (conteggioValori.size() > 1) {
            // Se ci sono più di un valore di carta normale, e non tutti i jolly sono stati usati,
            // allora non possono formare un tris dello stesso valore.
            // Esempio: [Re, Donna, Joker] -> conteggioValori.size() = 2
            // Esempio: [Re, Re, Donna] -> conteggioValori.size() = 2
            return false;
        }

        if (conteggioValori.isEmpty()) {
            // Questo caso si verifica solo se le carte originali erano tutte Joker (già gestito numJoker == 3).
            // O se in qualche modo carteNormali è vuota e numJoker < 3.
            return false;
        }

        String valoreUnico = conteggioValori.keySet().iterator().next();
        int countValoreUnico = conteggioValori.get(valoreUnico);

        if(countValoreUnico==3){
            return true;
        } else if(countValoreUnico == 2 && numJoker == 1){
            return true;
        } else if (countValoreUnico == 1 && numJoker == 2){
            return true;
        } else {
            return false;
        }
    }

    //metodo per verificare la validità di una scala
    /*
     * verifica se una scala è valida (4 carte in ordine e delle stesso seme)
     * tiene traccia di quale carta ogni Joker sta sostituendo
     * gestisce il caso limite con 4 Joker richiedendo un'assegnazione manuale del primo Joker 
     */
    private RisultatoScala verificaScalaConJoker(List<Carta> carte, Optional<Carta> primoJokerManuale) {
        RisultatoScala risultato = new RisultatoScala(false);

        if (carte.size() != 4) return risultato;

        List<Integer> posJoker = new ArrayList<>();
        List<Integer> valori = new ArrayList<>();
        List<Carta> carteNormali = new ArrayList<>();
        List<String> semi = new ArrayList<>();

        for (int i = 0; i < carte.size(); i++) {
            Carta c = carte.get(i);
            if (c.getValore().equalsIgnoreCase("Joker")) {
                posJoker.add(i);
            } else {
                int val = convertiValoreNumerico(c.getValore());
                if (val == -1) return new RisultatoScala(false);
                valori.add(val);
                semi.add(c.getSeme());
                carteNormali.add(c);
            }
        }

        int numJoker = posJoker.size();

        //caso limite: 4 joker
        if (numJoker == 4) {
            if (primoJokerManuale.isEmpty()) return new RisultatoScala(false);
            Carta base = primoJokerManuale.get();
            int valoreIniziale = convertiValoreNumerico(base.getValore());
            String seme = base.getSeme();
            if (valoreIniziale == -1 || valoreIniziale > 11) return new RisultatoScala(false);

            Map<Integer, Carta> jokerSub = new HashMap<>();
            for (int i = 0; i < 4; i++) {
                int val = valoreIniziale + i;
                if (val > 14) return new RisultatoScala(false);
                jokerSub.put(posJoker.get(i), new Carta(convertiValoreTestuale(val), seme));
            }
            return new RisultatoScala(true, jokerSub);
        }

        //i semi devono essere tutti uguali per le carte normali
        if (!semi.stream().allMatch(s -> s.equals(semi.get(0)))) return new RisultatoScala(false);
        String semeComune = semi.get(0);

        //asso può essere 1 o 14
        TreeSet<Integer> baseValori = new TreeSet<>(valori);
        if (baseValori.contains(1) && baseValori.contains(13)) baseValori.add(14);

        //provo tutte le sequenze possibili
        int min = baseValori.first() - numJoker;
        int max = baseValori.last();

        for (int start = Math.max(1, min); start <= max; start++) {
            List<Integer> sequenza = new ArrayList<>();
            Map<Integer, Carta> jokerSub = new HashMap<>();
            int jollyUsati = 0;
            int val = start;

            for (int i = 0; i < 4; i++) {
                if (baseValori.contains(val)) {
                    sequenza.add(val);
                } else if (jollyUsati < numJoker) {
                    int jokerIndex = posJoker.get(jollyUsati);
                    jokerSub.put(jokerIndex, new Carta(convertiValoreTestuale(val), semeComune));
                    sequenza.add(val);
                    jollyUsati++;
                } else {
                    break;
                }
                val++;
            }

            if (sequenza.size() == 4) {
                //verifica se la sequenza generata corrisponde a quella inserita (ordine importante)
                boolean matchConInput = true;

                for (int i = 0; i < 4; i++) {
                    Carta c = carte.get(i);
                    int valCorrente = convertiValoreNumerico(c.getValore());
                    if (!c.getValore().equalsIgnoreCase("Joker")) {
                        int valAtteso = sequenza.get(i);
                        if (valCorrente != valAtteso && !(valCorrente == 1 && valAtteso == 14)) {
                            matchConInput = false;
                            break;
                        }
                    }
                }

                if (matchConInput) {
                    return new RisultatoScala(true, jokerSub);
                }
            }
        }

        feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto.");
        return new RisultatoScala(false);
    }

    private void aggiornaManoGUI() {
        carteBox.getChildren().clear();

        for (Carta carta : mano.getCarte()) {
            String fileName = carta.getImageFilename();
            String path = "/assets/decks/bycicle/" + fileName;
            Image img = new Image(getClass().getResourceAsStream(path));
            ImageView view = new ImageView(img);
            view.setFitWidth(100);
            view.setPreserveRatio(true);

            // PRIORITÀ STILI:
            // 1. Se selezionata nel tris o nella scala corrente → oro
            // 2. Se già confermata (in carteSelezionatePerAperturaTotale) → verde e opaca
            // 3. Altrimenti, nessuno stile

            if ((currentTrisSelection != null && currentTrisSelection.contains(carta)) ||
                (currentScalaSelection != null && currentScalaSelection.contains(carta))) {
                //evidenziata in oro (selezione corrente)
                view.setStyle("-fx-effect: dropshadow(three-pass-box, gold, 8, 0, 0, 0);");
            } else if (carteSelezionatePerAperturaTotale != null && carteSelezionatePerAperturaTotale.contains(carta)) {
                //verde e opaca (già confermata)
                view.setStyle("-fx-effect: dropshadow(three-pass-box, green, 8, 0, 0, 0); -fx-opacity: 0.7;");
            } else {
                view.setStyle(""); //nessuno stile
            }

            //GESTIONE CLICK
            view.setOnMouseClicked(ev -> {
                if (inApertura) {
                    //=== FASE TRIS ===
                    if(trisConfermatiCorrenti < trisPerAprire){

                        if (carteSelezionatePerAperturaTotale.contains(carta)){
                            return;
                        }
                        if (currentTrisSelection.contains(carta)) {
                            currentTrisSelection.remove(carta);
                            feedbackLabel.setText("");
                        } else if (currentTrisSelection.size() < 3) {
                            currentTrisSelection.add(carta);
                        } else {
                            feedbackLabel.setText("Hai già selezionato 3 carte per il tris corrente. Clicca CONFERMA TRIS " + (trisConfermatiCorrenti + 1) + " o deseleziona");
                        }

                        aggiornaManoGUI();
                        updateOpeningSlotsGUI();

                    //=== FASE SCALA ===
                    } else if (scaleConfermateCorrenti < scalePerAprire) {
                        if (carteSelezionatePerAperturaTotale.contains(carta)) return;

                        if (currentScalaSelection.contains(carta)) {
                            currentScalaSelection.remove(carta);
                            feedbackLabel.setText("");
                        } else if (currentScalaSelection.size() < 4) {
                            currentScalaSelection.add(carta);
                        } else {
                            feedbackLabel.setText("Hai già selezionato 4 carte per la scala corrente. Clicca CONFERMA SCALA " + (scaleConfermateCorrenti + 1) + " o deseleziona");
                        }

                        aggiornaManoGUI();
                        updateOpeningSlotsGUI();
                    }
                } else if (inScarto && haPescato && mioTurno) {
                    scartaCarta(carta);
                }
            });
            carteBox.getChildren().add(view);
        }
    }

    //resetto lo stato del client per l'inizio di un nuovo round
    private void resetPerNuovoRound() {
        
        //resetto e pulisco tutto
        feedbackLabel.setText(""); //pulisco eventuali feedback
        mioTurno = false;
        haPescato = false;
        inScarto = false;
        inApertura = false;
        
        trisConfermatiCorrenti = 0;
        scaleConfermateCorrenti = 0;

        pescaMazzoBtn.setDisable(true);
        pescaScartiBtn.setDisable(true);
        scartaBtn.setDisable(true);
        apriBtn.setDisable(true);
        completaAperturaButton.setDisable(true);

        scrollPaneApertura.setVisible(false);
        scrollPaneApertura.setManaged(false);

        currentTrisSelection = new ArrayList<>();
        trisCompletiConfermati = new ArrayList<>();
        currentScalaSelection = new ArrayList<>();
        scaleCompleteConfermate = new ArrayList<>();
        carteSelezionatePerAperturaTotale = new ArrayList<>();

        selezionatePerApertura.clear();
    }

    //per aggiornare gli slot visivi nella fase di apertura
    private void updateOpeningSlotsGUI() {
        //FASE TRIS - pulisco e popolo gli slot del tris attualmente in costruzione
        int currentIndex = trisConfermatiCorrenti;
        if (currentIndex < trisPerAprire) { //mi assicuro di essere in un tris valido da popolare
            //pulisco tutti gli slot di questo tris
            for (int j = 0; j < 3; j++) {
                trisSlotViews[currentIndex][j].setImage(null);
                trisSlotViews[currentIndex][j].setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-background-color: #f0f0f0;");
            }

            //popolo gli slot con le carte selezionate per il tris corrente
            for (int j = 0; j < currentTrisSelection.size(); j++) {
                Carta carta = currentTrisSelection.get(j);
                String path = "/assets/decks/bycicle/" + carta.getImageFilename();
                Image img = new Image(getClass().getResourceAsStream(path));
                trisSlotViews[currentIndex][j].setImage(img);
                trisSlotViews[currentIndex][j].setStyle("-fx-effect: dropshadow(three-pass-box, gold, 8, 0, 0, 0);");
            }

            //abilito/disabilito il bottone di conferma per il tris corrente
            if (confermaTrisButtons[currentIndex] != null) {
                confermaTrisButtons[currentIndex].setDisable(currentTrisSelection.size() != 3);
            }
        }

        //FASE SCALA - pulisco e popolo gli slot della scala attualmente in costruzione
        int currentScalaIndex = scaleConfermateCorrenti;
        if (currentScalaIndex < scalePerAprire) {
            for (int j = 0; j < 4; j++) {
                scalaSlotViews[currentScalaIndex][j].setImage(null);
                scalaSlotViews[currentScalaIndex][j].setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-background-color: #f0f0f0;");
            }

            for (int j = 0; j < currentScalaSelection.size(); j++) {
                Carta carta = currentScalaSelection.get(j);
                String path = "/assets/decks/bycicle/" + carta.getImageFilename();
                Image img = new Image(getClass().getResourceAsStream(path));
                scalaSlotViews[currentScalaIndex][j].setImage(img);
                scalaSlotViews[currentScalaIndex][j].setStyle("-fx-effect: dropshadow(three-pass-box, gold, 8, 0, 0, 0);");
            }

            if (confermaScalaButtons[currentScalaIndex] != null) {
                confermaScalaButtons[currentScalaIndex].setDisable(currentScalaSelection.size() != 4);
            }
        }
    }

    //metodo di supporto da implementare per convertire filename in Carta
    private Carta cartaFromFileName(String fileName) {
        fileName = fileName.replace(".jpg", "");
        if (fileName.equalsIgnoreCase("joker")) return new Carta("Joker", null);

        String[] parts = fileName.split("_");
        String valore = parts[0]; // es. "1", "10", "J"
        String semeCode = switch (parts[1]) {
            case "spades"   -> "S";
            case "hearts"   -> "H";
            case "diamonds" -> "D";
            case "clubs"    -> "C";
            default         -> null;
        };
        return new Carta(valore, semeCode);
    }

    //imposto il titolo del round
    private String titleRound(int currentRound){
        switch (currentRound) {
            case 1:
                return "ROUND " + currentRound + " - 6 CARTE: 2 TRIS PER APRIRE";
            case 2:
                return "ROUND " + currentRound + " - 7 CARTE: 2 TRIS e 1 SCALA PER APRIRE";
            case 3:
                return "ROUND " + currentRound + " - 8 CARTE: 2 SCALE PER APRIRE";
            case 4:
                return "ROUND " + currentRound + " - 9 CARTE: 3 TRIS PER APRIRE";
            case 5:
                return "ROUND " + currentRound + " - 10 CARTE: 2 TRIS e 1 SCALA PER APRIRE";
            case 6:
                return "ROUND " + currentRound + " - 11 CARTE: 1 TRIS e 2 SCALE PER APRIRE";
            case 7:
                return "ROUND " + currentRound + " - 12 CARTE: 4 TRIS PER APRIRE";
            case 8:
                return "ROUND " + currentRound + " - 12 CARTE: 3 SCALE PER APRIRE";
            default:
                break;
        }
        return "";
    }

    //imposto il messaggio di apertura in base al round
    private String textApertura(int currentRound){
        switch (currentRound) {
            case 1:
                return "Apertura completata con 2 tris!";
            case 2:
                return "Apertura completata con 1 tris e 1 scala!";
            case 3:
                return "Apertura completata con 2 scale!";
            case 4:
                return "Apertura completata con 3 tris!";
            case 5:
                return "Apertura completata con 2 tris e 1 scala!";
            case 6:
                return "Apertura completata con 1 tris e 2 scale!";
            case 7:
                return "Apertura completata con 4 tris!";
            case 8:
                return "Apertura completata con 3 scale!";
            default:
                break;
        }
        return "";
    }

    //creo lo slot per i tris e le scale dove andrà la carta selezionata
    private StackPane  creaImgPlaceholderApertura(ImageView[][] imageViewOutput, int i, int j){
        ImageView slot = new ImageView();
        slot.setFitWidth(100);
        slot.setFitHeight(150);
        
        //sfondo visivo degli slot delle carte tramite Region
        Region background = new Region();
        background.setPrefSize(100, 150);
        background.setStyle(
            "-fx-background-color: #d9d9d9ff;" +
            "-fx-border-color: gray;" +
            "-fx-border-width: 2;"
        );

        StackPane slotWrapper = new StackPane(background, slot);

        if (imageViewOutput != null) {
            imageViewOutput[i][j] = slot;
        }

        return slotWrapper;
    }

    
    private int convertiValoreNumerico(String valore) {
        switch (valore.toUpperCase()) {
            case "A": return 1;   //l'asso può valere anche 14 se attaccato dopo il K (viene gestito nella logica di sequenza)
            case "J": return 11;
            case "Q": return 12;
            case "K": return 13;
            default:
                try {
                    return Integer.parseInt(valore);
                } catch (NumberFormatException e) {
                    return -1; //valore non valido
                }
        }
    }

    private String convertiValoreTestuale(int valore) {
        switch (valore) {
            case 1:  return "A";
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            case 14: return "A"; //Asso alto
            default: return String.valueOf(valore);
        }
    }

    private String getSimboloSeme(String seme) {
        switch (seme.toLowerCase()) {
            case "hearts":
                return "♥";
            case "diamonds":
                return "♦";
            case "clubs":
                return "♣";
            case "spades":
                return "♠";
            default:
                return "?";
        }
    }

    private boolean isConsecutivo(List<Integer> lista) {
        if (lista.size() < 2) return true;
        for (int i = 1; i < lista.size(); i++) {
            if (lista.get(i) - lista.get(i - 1) != 1) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// ===== CLIENT JAVA FX BASE (Grafica + Connessione) =====

import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*; //HBox, VBox, FlowPane (va automaticamente a capo quando lo spazio non basta), BorderPane
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.Node;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private Label tormentoTitleLabel; //mostra il titolo tormento
    private Label tormentoLabel; //mostra la richiesta di tormento
    private Label tormentoTimerLabel; //mostra il timer del tormento

    private BorderPane root;
    private PrintWriter out; //per mandare messaggi al server. PrintWriter è una classe di Java che permette di scrivere testo in un flusso di output

    private boolean mioTurno = false;    //si attiva quando è il mio turno
    private boolean haPescato = false;   //si attiva quando entro in modalità PESCA
    private boolean inScarto = false;    //si attiva quando entro in modalità SCARTO
    private boolean inApertura = false;  //si attiva quando entro in modalità APRI
    private boolean inTormento = false;  //se true attiva la scelta per rispondere TORMENTO SI'/NO
    private boolean inTavolo = false;    //si attiva quando entro in visualizzazione TAVOLO
    private boolean haAperto = false;    //si resetta ogni inizio turno e si attiva dopo che un giocatore apre (si può aprire una sola volta per turno)
    private boolean haApertoRichiesta = false;    //si attiva quando un giocatore apre la prima volta (effettua l'apertura richiesta dal round) e si resette all'inizio di un nuovo round
    private boolean inAttacco = false;   //si attiva quando entro in modalità ATTACCO
    private boolean trisExtra = false;
    private boolean scalaExtra = false;

    private Carta cartaManoSelezionata = null;                         //si riempirà quando entro in fase di attacco e clicco su una carta in mano
    private SelezioneTavolo elementoTavoloSelezionato = null;          //si riempirà quando entro in fase di attacco e clicco su un joker o un placeholder sul tavolo
    private List<JokerSulTavolo> jokerSulTavolo = new ArrayList<>();   //lista che funge da registro per tutti i Joker sul tavolo (max 4)
    
    private int carteRimanentiNelMazzo = 108;
    private List<Carta> pilaScarti = new ArrayList<>();
    private int currentRound = 1;           //il round corrente (1, 2, 3...)
    private int carteMano = 6;              //al primo round
    private int trisPerAprire;              //numero di tris richiesti per il round corrente (2, 3 o 4)
    private int scalePerAprire;             //numero di scale richieste per il round corrente (1, 2, o 3)
    private int trisConfermatiCorrenti;     //quanti tris il giocatore ha già confermato con successo in questa apertura
    private int scaleConfermateCorrenti;    //quante scale il giocatore ha già confermato con successo in questa apertura
    private int numTurno = 0;               //per tenere traccia di quanti turni fa un singolo giocatore per round
    private int numTurnoAperturaRound = 0;  //per tenere traccia quanto un giocatore fa la prima apertura
    private int numApertura = 0;            //per tenere traccia di quante aperture compie ogni giocatore per ogni round (serve per sapere dove si trovano i joker con maggiore precisione)

    //liste per gestire le carte durante l'apertura
    private List<Carta> carteSelezionatePerAperturaTotale = new ArrayList<>(); //tutte le carte selezionate per l'apertura complessiva
    private List<List<Carta>> trisCompletiConfermati = new ArrayList<>();      //lista di liste di carte, ogni sub-lista è un tris confermato
    private List<List<Carta>> scaleCompleteConfermate = new ArrayList<>();     //lista di liste di carte, ogni sub-lista è un tris confermato
    private List<Carta> currentTrisSelection = new ArrayList<>();              //lista di carte selezionate per il tris corrente
    private List<Carta> currentScalaSelection = new ArrayList<>();             //lista di carte selezionate per la scala corrente
    private List<Carta> selezionatePerApertura = new ArrayList<>();            //lista di carte selezionate per l'apertura
    private List<Carta> carteSostituiteDaJoker = new ArrayList<>();            //lista di carte locale temporanee prima di essere salvate in quelle confermate da inviare al server
    private List<Carta> carteSostituiteDaJokerConfermate = new ArrayList<>();  //lista delle carte locale che i joker stanno sostituendo sul tavolo
    private Map<Carta, ImageView> cardImageViewMap = new HashMap<>();          //per applicare e rimuovere facilmente gli stili dalle ImageView corrispondenti alle Carta in mano
    private Map<ImageView, Boolean> selezioniGUI = new HashMap<>();            //mappa per tenere traccia dello stato di selezione di ogni singola ImageView, necessaria per distinguere tra carte uguali
    private Map<Integer, List<List<Carta>>> tavoloGioco = new HashMap<>();     //mappa per rappresentare il tavolo con tutte le aperture
    private Map<Integer, Map<Integer, List<Carta>>> jokerTotaliSulTavolo = new HashMap<>(); //globale (per tutti i client) idGiocatore -> aperturaIndex -> lista carte sostituite dai Joker (max 4)

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
    private ScrollPane scrollPaneTavolo;
    private StackPane mazzoStack;              //mazzo di carte
    private StackPane pilaScartiStack;         //pila degli scarti
    private StackPane badge = new StackPane();
    private VBox tormentoBox;
    private VBox tavoloGiocoBox;

    private Button pescaBtn;                   //cliccandoci sopra compaiono le opzioni per pescare dal mazzo o dalla pila
    private Button pescaMazzoBtn;
    private Button pescaScartiBtn;
    private Button scartaBtn;
    private Button apriBtn;
    private Button attaccaBtn;
    private Button confermaAttaccaBtn;
    private Button esciBtn;
    private Button tavoloBtn;                  //cliccandoci sopra si nasconde tutto e compare il tavolo di gioco con le aperture effettuate
    private Button tormentoSiBtn;
    private Button tormentoNoBtn;
    private Button addTrisButton;              //tris extra per ulteriore apertura
    private Button addScalaButton;             //scala extra per ulteriore apertura

    @Override
    public void start(Stage primaryStage) { //lo Stage è una finestra del programma, noi ora creiamo quella principale dove vediamo le nostre carte
        root = new BorderPane(); //per posizionare gli elementi sopra, al centro e sotto
        String imagePath = getClass().getResource("/assets/wallpaper/green_table.jpg").toExternalForm();
        root.setStyle("-fx-background-image: url('" + imagePath + "'); " +
                    "-fx-background-repeat: no-repeat; " +
                    "-fx-background-size: auto; " +
                    "-fx-background-position: center center;");
        root.setPadding(new Insets(20, 20, 20, 20)); //top, right, bottom, left

        //badge per mostrare al giocatore che è il suo turno, da inserire in posizione absolute in un Pane
        badge = creaBadgeTurno("TOCCA\nA TE"); //uso il metodo per creare il badge circolare

        // === Box in alto (header, status, round, playerName e punteggi) ===
        round = new Label("PRIMO ROUND - 2 TRIS");
        round.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        header = new Label("BENVENUTO A 'ME TORMIENTO' - ASPETTA IL TUO TURNO");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        status = new Label("Connessione in corso...");
        status.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: blue;");
        playerName = new Label("");
        playerName.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        punteggiLabel = new Label("");
        punteggiLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: blue;");
        punteggiLabel.setVisible(false);  //nascosto
        punteggiLabel.setManaged(false);  //non occupa spazio
        VBox headerBox = new VBox(5, round, header, status, playerName, punteggiLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.5); " +
                   "-fx-padding: 10;" +
                   "-fx-background-radius: 50;");
        //creo uno stack che mi permette di centrare l'headerBox
        StackPane centeredHeader = new StackPane(headerBox);
        centeredHeader.setPrefWidth(Double.MAX_VALUE); //si allarga
        StackPane.setAlignment(headerBox, Pos.CENTER); //headerBox centrato
        //posiziono l'headerBox e il badge in un AnchorPane (perché supporta il posizionamento absolute a differenza del VBox)
        AnchorPane overlay = new AnchorPane(centeredHeader, badge);
        //nascondo il badge "TOCCA A TE"
        badge.setVisible(false);
        badge.setManaged(false);
        //setto il badge a sinistra e centrato verticalmente nel contenitore
        overlay.heightProperty().addListener((obs, oldVal, newVal) -> {
            double overlayHeight = newVal.doubleValue();
            double badgeHeight = badge.getBoundsInParent().getHeight();
            double topOffset = ((overlayHeight - badgeHeight) / 2) - 10;
            AnchorPane.setTopAnchor(badge, topOffset);
        });
        AnchorPane.setLeftAnchor(badge, 5.0);
        //fisso il blocco centrato su tutto il top
        AnchorPane.setTopAnchor(centeredHeader, 0.0);
        AnchorPane.setLeftAnchor(centeredHeader, 0.0);
        AnchorPane.setRightAnchor(centeredHeader, 0.0);
        //inserisco in alto dentro root
        root.setTop(overlay);

        // === Sezione UI di Apertura ===
        openingSectionContainer = new VBox(15); //spazio verticale tra gli elementi
        openingSectionContainer.setAlignment(Pos.CENTER);
        openingSectionContainer.setPadding(new Insets(10));
        openingSectionContainer.setBackground(Background.EMPTY); //rende il VBox completamente trasparente
        
        //bottone per completare l'apertura
        completaAperturaButton = new Button("COMPLETA APERTURA");
            completaAperturaButton.getStyleClass().add("standard-button");
            completaAperturaButton.setId("confirmButton");
        completaAperturaButton.setDisable(true); //inizialmente disabilitato
        openingSectionContainer.getChildren().add(completaAperturaButton);

        scrollPaneApertura = new ScrollPane(openingSectionContainer);
        scrollPaneApertura.setFitToWidth(true);
        scrollPaneApertura.setPrefHeight(320); // altezza
        scrollPaneApertura.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPaneApertura.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPaneApertura.setVisible(false); //nascosto all'inizio
        scrollPaneApertura.setManaged(false); //non occupa spazio quando nascosto
        scrollPaneApertura.setStyle("-fx-background-color: transparent;"); //rende trasparente lo ScrollPane esterno
        scrollPaneApertura.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                //applico il colore al viewport una volta che lo skin è disponibile
                Platform.runLater(() -> {
                    Node viewport = scrollPaneApertura.lookup(".viewport");
                    if (viewport != null) {
                        viewport.setStyle("-fx-background-color: rgba(255, 255, 255, 0.5); -fx-padding: 10; -fx-background-radius: 50;"); //bianco 50% di opacità
                    }
                });
            }
        });

        // === VISUALIZZAZIONE TORMENTO ===
        tormentoTitleLabel = new Label("TORMENTO");
        tormentoTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        tormentoLabel = new Label("Ti interessa questa carta?");
        tormentoLabel.setStyle("-fx-text-fill: black;");
        tormentoTimerLabel = new Label();
        tormentoTimerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 30px;");

        tormentoSiBtn = new Button("SI");
            tormentoSiBtn.getStyleClass().add("standard-button");
            tormentoSiBtn.setId("tormentoSiButton");
        tormentoNoBtn = new Button("NO");
            tormentoNoBtn.getStyleClass().add("standard-button");
            tormentoNoBtn.setId("tormentoNoButton");
        HBox tormentoButtonsBox = new HBox(20);
        tormentoButtonsBox.setAlignment(Pos.CENTER);
        tormentoButtonsBox.setStyle("-fx-background-color: transparent;");
        tormentoButtonsBox.getChildren().addAll(tormentoSiBtn, tormentoNoBtn);
        
        tormentoBox = new VBox(20);
        tormentoBox.setPrefWidth(500);
        tormentoBox.setPrefHeight(200);
        tormentoBox.setVisible(false); //nascosto all'inizio
        tormentoBox.setManaged(false); //non occupa spazio quando nascosto
        tormentoBox.setAlignment(Pos.CENTER);
        tormentoBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.5); -fx-padding: 10; -fx-background-radius: 50;");
        tormentoBox.getChildren().addAll(tormentoTitleLabel, tormentoLabel, tormentoTimerLabel, tormentoButtonsBox);
        // === fine VISUALIZZAZIONE TORMENTO ===

        VBox centerContentBox = new VBox(10); //scrollPaneApertura e tormentoBox (mai visibili contemporaneamente)
        VBox.setMargin(scrollPaneApertura, new Insets(20, 0, 0, 0)); // top, right, bottom, left
        centerContentBox.setAlignment(Pos.CENTER);
        centerContentBox.getChildren().addAll(scrollPaneApertura, tormentoBox);

        //=== MAZZO E PILA SCARTI ===
        HBox mazzoScartiBox = new HBox(20);
        mazzoScartiBox.setAlignment(Pos.CENTER_LEFT);
        mazzoScartiBox.setPadding(new Insets(10));
        mazzoScartiBox.setStyle("-fx-background-color: transparent;");
        mazzoStack = new StackPane();
        mazzoStack.setPrefSize(100, 150); //dimensioni base di una carta
        mazzoStack.setAlignment(Pos.BOTTOM_CENTER); //le carte crescono verso l’alto
        pilaScartiStack = new StackPane();
        pilaScartiStack.setAlignment(Pos.BOTTOM_CENTER); //le carte crescono verso l’alto
        mazzoScartiBox.getChildren().addAll(mazzoStack, pilaScartiStack);

        //=== TAVOLO DA GIOCO ===
        tavoloGiocoBox = new VBox(15);
        tavoloGiocoBox.setAlignment(Pos.CENTER);
        tavoloGiocoBox.setPadding(new Insets(10));
        tavoloGiocoBox.setBackground(Background.EMPTY); //rende il VBox completamente trasparente

        scrollPaneTavolo = new ScrollPane(tavoloGiocoBox);
        scrollPaneTavolo.setFitToWidth(true);
        scrollPaneTavolo.setPrefHeight(320); // altezza
        scrollPaneTavolo.setPrefWidth(1400); // larghezza
        scrollPaneTavolo.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPaneTavolo.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPaneTavolo.setVisible(false); //nascosto all'inizio
        scrollPaneTavolo.setManaged(false); //non occupa spazio quando nascosto
        scrollPaneTavolo.setStyle("-fx-background-color: transparent;"); //rende trasparente lo ScrollPane esterno
        scrollPaneTavolo.skinProperty().addListener((obs, oldSkin, newSkin) -> {
            if (newSkin != null) {
                //applico il colore al viewport una volta che lo skin è disponibile
                Platform.runLater(() -> {
                    Node viewport = scrollPaneTavolo.lookup(".viewport");
                    if (viewport != null) {
                        //viewport.setStyle("-fx-background-color: rgba(255, 255, 255, 0.5); -fx-padding: 10; -fx-background-radius: 50;"); //bianco 50% di opacità
                        viewport.setStyle("-fx-background-color: transparent; -fx-background: transparent;"); //bianco 50% di opacità
                    }
                });
            }
        });
        AnchorPane scrollPaneTavoloContainer = new AnchorPane();
        scrollPaneTavoloContainer.getChildren().add(scrollPaneTavolo);
        AnchorPane.setLeftAnchor(scrollPaneTavolo, 5.0);
        AnchorPane.setRightAnchor(scrollPaneTavolo, 5.0);
        AnchorPane.setTopAnchor(scrollPaneTavolo, 20.0);

        //zona centrale con mazzo, pila scarti, zona apertura e tavolo (non sono mai visibili tutti insieme)
        HBox centroPartitaBox = new HBox(30); //spazio tra mazzo e tavolo
        centroPartitaBox.setAlignment(Pos.CENTER);
        centroPartitaBox.setPadding(new Insets(10));
        //imposto una priorità di crescita per scrollPaneTavoloContainer
        HBox.setHgrow(scrollPaneTavoloContainer, Priority.ALWAYS);
        centroPartitaBox.getChildren().addAll(mazzoScartiBox, scrollPaneTavoloContainer);
        
        HBox mainCenterBox = new HBox(20); //margine verticale tra apertura e carte
        mainCenterBox.setAlignment(Pos.BOTTOM_CENTER);
        mainCenterBox.getChildren().addAll(centroPartitaBox, centerContentBox);
        
        //flowPane per le carte in mano
        carteBox = new FlowPane();
        carteBox.setAlignment(Pos.CENTER);
        carteBox.setHgap(10); //spaziatura orizzontale tra le carte
        carteBox.setVgap(10); //spaziatura verticale tra le righe di carte
        carteBox.setPrefWrapLength(500); //larghezza alla quale iniziare ad andare a capo
        carteBox.setStyle("-fx-background-color: transparent;");
        
        //scrollPane che contiene il FlowPane - aggiunge barra di scorrimento verticale quando necessaria
        ScrollPane scrollPane = new ScrollPane(carteBox);
        scrollPane.setFitToWidth(true); //si adatta in larghezza
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); //no barra orizzontale
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); //sì barra verticale quando serve
        scrollPane.setPrefHeight(175); // o quello che vuoi
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setBackground(Background.EMPTY);
        scrollPane.setBorder(Border.EMPTY);

        // === Label feedback ===
        feedbackLabel = new Label();
        feedbackLabel.setStyle("-fx-text-fill: red;");

        // === Contenitore pulsanti in basso ===
        HBox buttonsBox = new HBox(150);
        HBox buttonsAzioni = new HBox(20);
        HBox buttonsEsciBox = new HBox(20);
        pescaBtn = new Button("PESCA");
            pescaBtn.getStyleClass().add("standard-button");
            pescaBtn.setId("pescaButton");
        pescaMazzoBtn = new Button("PESCA DAL MAZZO");
            pescaMazzoBtn.getStyleClass().add("standard-button");
            pescaMazzoBtn.setId("pescaButton");
        pescaScartiBtn = new Button("PESCA DALLA PILA DEGLI SCARTI");
            pescaScartiBtn.getStyleClass().add("standard-button");
            pescaScartiBtn.setId("pescaButton");
        scartaBtn = new Button("SCARTA");
            scartaBtn.getStyleClass().add("standard-button");
            scartaBtn.setId("scartaButton");
        apriBtn = new Button("APRI");
            apriBtn.getStyleClass().add("standard-button");
            apriBtn.setId("apriButton");
        attaccaBtn = new Button("ATTACCA");
            attaccaBtn.getStyleClass().add("standard-button");
            attaccaBtn.setId("attaccaButton");
        confermaAttaccaBtn = new Button("CONFERMA ATTACCO");
            confermaAttaccaBtn.getStyleClass().add("standard-button");
            confermaAttaccaBtn.setId("attaccaButton");
        esciBtn = new Button("ESCI DALLA PARTITA");
            esciBtn.getStyleClass().add("standard-button");
            esciBtn.setId("esciButton");
        tavoloBtn = new Button("MOSTRA TAVOLO");
            tavoloBtn.getStyleClass().add("standard-button");
            tavoloBtn.setId("tavoloButton");

        //rendo i pulsanti disattivati all'apertura del client
        pescaBtn.setDisable(true);
        pescaMazzoBtn.setVisible(true);
        pescaMazzoBtn.setManaged(true);
        pescaScartiBtn.setVisible(true);
        pescaScartiBtn.setManaged(true);
        scartaBtn.setDisable(true);
        apriBtn.setDisable(true);
        attaccaBtn.setDisable(true);
        confermaAttaccaBtn.setDisable(true);
        confermaAttaccaBtn.setVisible(false);
        confermaAttaccaBtn.setManaged(false);

        buttonsAzioni.getChildren().addAll(pescaBtn, pescaMazzoBtn, pescaScartiBtn, apriBtn, attaccaBtn, confermaAttaccaBtn, scartaBtn);
        buttonsAzioni.setStyle("-fx-background-color: transparent; ");
        buttonsEsciBox.getChildren().addAll(tavoloBtn, esciBtn);
        buttonsEsciBox.setStyle("-fx-background-color: transparent; ");

        buttonsBox.getChildren().addAll(buttonsAzioni, buttonsEsciBox);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.setStyle("-fx-background-color: transparent; ");
        
        //utilizzo bottomBox per includere le carte, il feedbackLabel e buttonsBox
        VBox bottomContentBox = new VBox(10);
        bottomContentBox.getChildren().addAll(scrollPane, feedbackLabel, buttonsBox);
        bottomContentBox.setAlignment(Pos.BOTTOM_CENTER);
        bottomContentBox.setPadding(new Insets(10));
        bottomContentBox.setStyle("-fx-background-color: transparent;");

        //imposto tutto questo al centro del BorderPane principale (così resta in basso)
        root.setCenter(mainCenterBox);
        root.setBottom(bottomContentBox);

        // === Scene e Stage ===
        Scene scene = new Scene(root, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        primaryStage.setTitle("ME TORMIENTO");
        primaryStage.setScene(scene); //dimensione della finestra del programma quando si avvia (è il contenuto)
        primaryStage.show();

        // === Requisiti iniziali del di come inizia la partita (primo round) ===
        round.setText("ROUND " + currentRound + " - " + carteMano + " CARTE: 2 TRIS PER APRIRE");

        // === Gestione click pulsanti ===
        pescaBtn.setOnAction(e -> {
            //mostro i pulsanti delle opzioni di pescata
            pescaMazzoBtn.setVisible(true);  
            pescaMazzoBtn.setManaged(true);
            pescaScartiBtn.setVisible(true); 
            pescaScartiBtn.setManaged(true);
            //nascondo i tasti PESCA, SCARTA, APRI e ATTACCA
            pescaBtn.setVisible(false);  
            pescaBtn.setManaged(false);
            scartaBtn.setVisible(false);
            scartaBtn.setManaged(false);
            apriBtn.setVisible(false);
            apriBtn.setManaged(false);
            attaccaBtn.setVisible(false);
            attaccaBtn.setManaged(false);
        });
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

            //mostro i tasti PESCA, SCARTA, APRI e ATTACCA (disabilitati, si attiveranno SCARTA e APRI una volta ricevuta la carta)
            pescaBtn.setVisible(true);  
            pescaBtn.setManaged(true);
            pescaBtn.setDisable(true);
            scartaBtn.setVisible(true);  
            scartaBtn.setManaged(true);
            scartaBtn.setDisable(true);
            apriBtn.setVisible(true);  
            apriBtn.setManaged(true);
            apriBtn.setDisable(true);
            attaccaBtn.setVisible(true);
            attaccaBtn.setManaged(true);
            attaccaBtn.setDisable(true);
            
            //nascondo i tasti pescaMazzo e pescaScarti
            pescaMazzoBtn.setVisible(false);
            pescaMazzoBtn.setManaged(false);
            pescaScartiBtn.setVisible(false);
            pescaScartiBtn.setManaged(false);

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

            //mostro i tasti PESCA, SCARTA, APRI e ATTACCA
            pescaBtn.setVisible(true);  
            pescaBtn.setManaged(true);
            scartaBtn.setVisible(true);  
            scartaBtn.setManaged(true);
            apriBtn.setVisible(true);  
            apriBtn.setManaged(true);
            attaccaBtn.setVisible(true);
            attaccaBtn.setManaged(true);

            //nascondo i tasti pescaMazzo e pescaScarti
            pescaMazzoBtn.setVisible(false);
            pescaMazzoBtn.setManaged(false);
            pescaScartiBtn.setVisible(false);
            pescaScartiBtn.setManaged(false);

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

                    //disabilito e nascondo i bottoni di pesca, scarto e attacco durante l'apertura
                    pescaBtn.setDisable(true);
                    pescaBtn.setVisible(false);
                    pescaBtn.setManaged(false);
                    scartaBtn.setDisable(true);
                    scartaBtn.setVisible(false);
                    scartaBtn.setManaged(false);
                    attaccaBtn.setDisable(true);
                    attaccaBtn.setVisible(false);
                    attaccaBtn.setManaged(false);

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
        attaccaBtn.setOnAction(e -> {
            if (!inAttacco) {
                inAttacco = true;
                attaccaBtn.setText("ESCI DALLA MODALITÀ ATTACCO");

                //nascondo i tasti PESCA, SCARTA, APRI e MOSTRA TAVOLO
                pescaBtn.setVisible(false);
                pescaBtn.setManaged(false);
                scartaBtn.setVisible(false);  
                scartaBtn.setManaged(false);
                apriBtn.setVisible(false);  
                apriBtn.setManaged(false);
                tavoloBtn.setVisible(false);
                tavoloBtn.setManaged(false);
                //nascondo il mazzo e la pila degli scarti
                mazzoScartiBox.setVisible(false);
                mazzoScartiBox.setManaged(false);
                //mostro il tavolo e il tasto CONFERMA ATTACCO
                scrollPaneTavolo.setVisible(true);
                scrollPaneTavolo.setManaged(true);
                confermaAttaccaBtn.setVisible(true);
                confermaAttaccaBtn.setManaged(true);
                confermaAttaccaBtn.setDisable(true);

            } else {
                inAttacco = false;
                attaccaBtn.setText("ATTACCA");

                //mostro i tasti PESCA, SCARTA, APRI e MOSTRA TAVOLO
                pescaBtn.setVisible(true);
                pescaBtn.setManaged(true);
                scartaBtn.setVisible(true);  
                scartaBtn.setManaged(true);
                apriBtn.setVisible(true);  
                apriBtn.setManaged(true);
                tavoloBtn.setVisible(true);
                tavoloBtn.setManaged(true);
                //mostro il mazzo e la pila degli scarti
                mazzoScartiBox.setVisible(true);
                mazzoScartiBox.setManaged(true);
                //nascondo il tavolo e il tasto CONFERMA ATTACCO (e lo disabilito)
                scrollPaneTavolo.setVisible(false);
                scrollPaneTavolo.setManaged(false);
                confermaAttaccaBtn.setVisible(false);
                confermaAttaccaBtn.setManaged(false);
                confermaAttaccaBtn.setDisable(true);
                //cancello le selezioni
                cartaManoSelezionata = null;
                elementoTavoloSelezionato = null;
                aggiornaManoGUI();
            }

            //aggiorno il tavolo per mostrare o nascondere i placeholder
            aggiornaTavoloGUI(); 
        });
        confermaAttaccaBtn.setOnAction(e -> {
            boolean attaccoValido = verificaAttacco(cartaManoSelezionata, elementoTavoloSelezionato);
            if (attaccoValido) { //attacco valido
                cartaManoSelezionata = null;
                elementoTavoloSelezionato = null;
                confermaAttaccaBtn.setDisable(true);
                aggiornaManoGUI();
                aggiornaTavoloGUI();
            } else {  //attacco non valido
                out.println("ATTACCO ERRORE");
                cartaManoSelezionata = null;
                elementoTavoloSelezionato = null;
                confermaAttaccaBtn.setDisable(true);
                aggiornaManoGUI();
                aggiornaTavoloGUI();
            }
        });
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
                pescaBtn.setDisable(true);//disabilito il bottone PESCA mentre sono in modalità scarto
                attaccaBtn.setDisable(true);//disabilito il bottone ATTACCA mentre sono in modalità scarto

                apriBtn.setVisible(false);
                apriBtn.setManaged(false);
                pescaBtn.setVisible(false);
                pescaBtn.setManaged(false);
                attaccaBtn.setVisible(false);
                attaccaBtn.setManaged(false);

                header.setText("SCARTA PER CONCLUDERE IL TURNO");
                status.setText("Seleziona una carta dalla tua mano da scartare");
                feedbackLabel.setText("Clicca sulla carta che vuoi scartare");
            }
        });
        esciBtn.setOnAction(e -> {
            out.println("DISCONNETTI");
            status.setText("Hai lasciato la partita.");
            pescaBtn.setDisable(true);
            apriBtn.setDisable(true);
            scartaBtn.setDisable(true);
            //chiudi anche la finestra
            javafx.application.Platform.exit();
        });
        tormentoSiBtn.setOnAction(e -> {
            out.println("TORMENTO_RISPOSTA:SI");
            status.setText("Ti sei tormentato");

            visualizzaTormento(inTormento);
            inTormento=false;
        });
        tormentoNoBtn.setOnAction(e -> {
            out.println("TORMENTO_RISPOSTA:NO");
            status.setText("Hai rifiutato il tormentato");

            visualizzaTormento(inTormento);
            inTormento=false;
        });
        tavoloBtn.setOnAction(e -> {
            if(!inTavolo){  //NON sono in VISUALIZZAZIONE TAVOLO
                buttonsAzioni.setVisible(true);
                buttonsAzioni.setManaged(true);
                mazzoScartiBox.setVisible(true);
                mazzoScartiBox.setManaged(true);
                scrollPane.setVisible(true);
                scrollPane.setManaged(true);

                scrollPaneTavolo.setVisible(false);
                scrollPaneTavolo.setManaged(false);

                tavoloBtn.setText("MOSTRA TAVOLO");
                inTavolo=true;
            } else {        //sono in VISUALIZZAZIONE TAVOLO
                buttonsAzioni.setVisible(false);
                buttonsAzioni.setManaged(false);
                mazzoScartiBox.setVisible(false);
                mazzoScartiBox.setManaged(false);
                scrollPane.setVisible(true);
                scrollPane.setManaged(true);

                if (tavoloGioco.size() > 0) {
                    //se la mappa contiene delle aperture, rendi visibile lo ScrollPane
                    scrollPaneTavolo.setVisible(true);
                    scrollPaneTavolo.setManaged(true);
                } else {
                    //se la mappa è vuota, nascondo lo ScrollPane
                    scrollPaneTavolo.setVisible(false);
                    scrollPaneTavolo.setManaged(false);
                }

                tavoloBtn.setText("NASCONDI TAVOLO");
                inTavolo=false;
            }
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

                        haApertoRichiesta = false; //resetto

                        javafx.application.Platform.runLater(() -> {
                            round.setText(titleRound(currentRound));

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
                            status.setText("Carte distribuite per il round  " + currentRound);
                            aggiornaPilaScartiGrafica(pilaScarti);
                            aggiornaManoGUI();
                        });
                    } else if(line.startsWith("YOUR_PLAYER_ID:")){ //il client riceve il proprio ID
                        playerId = Integer.parseInt(line.substring("YOUR_PLAYER_ID:".length()));
                        javafx.application.Platform.runLater(() -> {
                            playerName.setText("Player " + String.valueOf(playerId)); //TODO: quando sarà implementata l'autenticazione sostituire il playerName col vero nome scelto dal giocatore
                            header.setText("BENVENUTO PLAYER " + playerId + " - ASPETTA IL TUO TURNO");
                            resetBtnAzioni();
                        });
                    } else if (line.startsWith("TORMENTO_CHANCE:")){
                        String cartaTormentoString = line.substring(16);
                        Carta cartaTormento = cartaFromFileName(cartaTormentoString);
                        javafx.application.Platform.runLater(() -> {
                            tormentoLabel.setText("Ti interessa " + cartaTormento.getNomeCartaScartata_Ita() + "?");
                            visualizzaTormento(inTormento);
                            inTormento=true;
                        });
                    } else if(line.startsWith("TORMENTO_ESEGUITO_CARTE_AGGIUNTE:")){
                        String cartaDaTormentoString = line.substring(33);
                        String[] carte = cartaDaTormentoString.split(";");
                        Carta cartaDaPila = cartaFromFileName(carte[0]);
                        Carta cartaDaMazzo = cartaFromFileName(carte[1]);
                        mano.aggiungiCarta(cartaDaPila);
                        mano.aggiungiCarta(cartaDaMazzo);

                        javafx.application.Platform.runLater(() -> {
                            status.setText("Player " + playerId + " si è tormentato pescando dalla pila degli scarti " + cartaDaPila.getNomeCartaScartata_Ita());
                            aggiornaPilaScartiGrafica(pilaScarti);
                            aggiornaManoGUI();
                        });
                    } else if(line.equals("TIMER_SCADUTO")){ //se il timer scade (10 secondi)
                        out.println("TORMENTO_RISPOSTA:NO");
                        javafx.application.Platform.runLater(() -> {
                            status.setText("Timer scaduto quindi tormento rifiutato");
                            tormentoBox.setVisible(false);
                            tormentoBox.setManaged(false);
                        });
                        inTormento=false;
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
                            punteggiLabel.setVisible(true);
                            punteggiLabel.setManaged(true);
                            punteggiLabel.setText("Punteggi: " + punteggiString.replace(";", " | "));
                        });
                    } else if (line.equals("TOCCA_A_TE")) { //il server comunica il proprio turno
                        javafx.application.Platform.runLater(() -> {
                            header.setText("PESCA UNA CARTA");
                            mioTurno = true;
                            haPescato = false;
                            inScarto = false;
                            inApertura = false;
                            haAperto = false;
                            selezionatePerApertura.clear(); //pulisco le selezioni
                            apriBtn.setText("APRI");
                            feedbackLabel.setText("");

                            //incremento i contatori del turno e dell'apertura richiesta dal round
                            numTurno++;
                            if(!haApertoRichiesta){ //quando un giocatore apre, il contatore della richiesta non incrementa più
                                numTurnoAperturaRound++;
                            }

                            //mostro il badge "TOCCA A TE"
                            badge.setVisible(true);
                            badge.setManaged(true);

                            //pulsanti pesca
                            pescaBtn.setDisable(false);
                            pescaBtn.setVisible(true);  
                            pescaBtn.setManaged(true);

                            if(carteRimanentiNelMazzo > 0){
                                pescaMazzoBtn.setDisable(false); //lo attiviamo anche se è nascosto, così che quando si clicca su PESCA compare
                            }
                            pescaMazzoBtn.setVisible(false);  
                            pescaMazzoBtn.setManaged(false);

                            pescaScartiBtn.setVisible(false);  
                            pescaScartiBtn.setManaged(false);

                            scartaBtn.setDisable(true);
                            apriBtn.setDisable(true);
                            attaccaBtn.setDisable(true);

                            status.setText("Tocca a te! Pesca dal mazzo o dalla pila degli scarti.");
                            aggiornaManoGUI();
                        });
                    } else if (line.startsWith("PESCATA:")) {   //il server mi dice che carta ho pescato
                        String fileName = line.substring(8);
                        Carta nuovaCarta = cartaFromFileName(fileName);
                        mano.aggiungiCarta(nuovaCarta);
                        javafx.application.Platform.runLater(() -> {
                            status.setText("Hai pescato " + nuovaCarta.getNomeCartaScartata_Ita());
                            aggiornaManoGUI();
                            //dopo aver pescato abilito SCARTA e APRI
                            pescaBtn.setDisable(true);
                            scartaBtn.setDisable(false);
                            apriBtn.setDisable(false);

                            inScarto = false;
                            inApertura = false;
                            selezionatePerApertura.clear();
                            apriBtn.setText("APRI");
                            feedbackLabel.setText("");

                            if(haApertoRichiesta && mano.getCarte().size()>1){
                                attaccaBtn.setDisable(false); //abilito ATTACCA
                            }else{
                                attaccaBtn.setDisable(true); //disabilito ATTACCA
                            }
                        });
                    } else if (line.startsWith("CARTE_MAZZO:")) { //il server aggiorna TUTTI su quante carte ci sono nel mazzo
                        String mazzoString = line.substring(12);
                        int numCarteMazzo = Integer.parseInt(mazzoString);

                        javafx.application.Platform.runLater(() -> {
                            aggiornaMazzoGrafico(numCarteMazzo);
                        });
                    } else if (line.startsWith("PESCATA_DA_PILA_SCARTI:")) { //il server aggiorna TUTTI su che carta è stata pescata dalla pila degli scarti
                        pilaScarti.remove(pilaScarti.size()-1); //non mi serve sapere la carta pescata, tanto è sicuramente l'ultima

                        javafx.application.Platform.runLater(() -> {
                            aggiornaPilaScartiGrafica(pilaScarti);
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
                    } else if (line.startsWith("PILA_SCARTI:")) { //il server aggiorna TUTTI sulla pila degli scarti
                        String pilaString = line.substring(12);
                        pilaScarti.clear(); //svuoto la pila degli scarti prima di ricrearla
                        if(!pilaString.equals("VUOTA")){ //può arrivare vuota quando un giocatore si tormenta quando c'è solo una carta nella pila degli scarti, e quindi facendolo svuota la pila
                            String[] pilaArray = pilaString.split(",");
                            List<String> pilaList = new ArrayList<>(Arrays.asList(pilaArray));
                            for (String nome : pilaList) {
                                Carta carta = cartaFromFileName(nome);
                                pilaScarti.add(carta);  //inserisco ogni carta presente nella stringa PILA_SCARTI nella pila vera e propria sotto forma di Carta
                            }
                        }
                        javafx.application.Platform.runLater(() -> {
                            aggiornaPilaScartiGrafica(pilaScarti);
                        });
                    } else if(line.startsWith("GIOCATORE_X_HA_SCARTATO_LA_CARTA_X:")){
                        String playerIdScartoString = line.substring(35);
                        javafx.application.Platform.runLater(() -> {
                            status.setText("Il Player " + playerIdScartoString);
                        });
                    } else if (line.startsWith("GIOCATORE_HA_APERTURA:")) { //il server dice che ho aperto regolarmente
                        String playerName = line.substring(line.indexOf(":") + 1);
                        javafx.application.Platform.runLater(() -> {
                            status.setText(playerName + " ha aperto!");
                            header.setText("SCARTA UNA CARTA");
                        });
                    } else if (line.startsWith("TAVOLO_AGGIORNATO:")) { //il server comunica il tavolo
                        tavoloGioco.clear();

                        String tavoloString = line.substring(18);
                        System.out.println("DEBUG: Messaggio TAVOLO_AGGIORNATO ricevuto. Linea completa: " + tavoloString);
                        
                        String[] giocatoriAperture = tavoloString.split("\\|"); //ogni giocatore è diviso da "|"
                        
                        if (tavoloString.isEmpty() || (giocatoriAperture.length == 1 && giocatoriAperture[0].isEmpty())) {
                            System.out.println("DEBUG: Nessun dato di apertura presente nel messaggio. Il tavolo è vuoto.");
                        } else {
                            for (String giocatoreApertura : giocatoriAperture) {
                                String[] parti = giocatoreApertura.split(":", 2); //separo l'ID del giocatore dalle sue aperture
                                if (parti.length < 2) {
                                    System.out.println("DEBUG: Errore di parsing per il giocatore. Stringa: " + giocatoreApertura);
                                    continue; //passo al prossimo giocatore
                                }

                                int idGiocatore = Integer.parseInt(parti[0]);
                                String[] aperture = parti[1].split(";");
                                
                                List<List<Carta>> apertureGiocatore = new ArrayList<>();
                                
                                for (String aperturaString : aperture) {
                                    String[] carteString = aperturaString.split(","); //separo le carte di ogni tris/scala
                                    List<Carta> trisScala = new ArrayList<>();
                                    for (String cartaString : carteString) {
                                        trisScala.add(cartaFromFileName(cartaString)); //ricreo la carta dall'immagine filename
                                    }
                                    trisScala.forEach(carta -> System.out.println("DEBUG: Aggiunta carta: " + carta.getValore() + " di " + carta.getSeme() + " per il giocatore " + idGiocatore));
                                    apertureGiocatore.add(trisScala);
                                }
                                tavoloGioco.put(idGiocatore, apertureGiocatore);
                            }
                        }

                        //aggiungo una stampa per mostrare lo stato finale della mappa
                        if (tavoloGioco.size() > 0) {
                            System.out.println("Contenuto del TAVOLO: " + tavoloGioco);
                        } else {
                            System.out.println("TAVOLO vuoto");
                        }

                        javafx.application.Platform.runLater(() -> {
                            aggiornaTavoloGUI(); //aggiorno la visualizzazione del tavolo
                        });
                    } else if (line.startsWith("JOKER_AGGIORNATI:")) { //il server comunica a tutti i joker sul tavolo
                        //esempio messaggio: "JOKER_AGGIORNATI:0:0=8_diamonds.jpg,8_hearts.jpg;1=7_clubs.jpg|1:0=1_spades.jpg"
                        //può anche arrivare un messaggio vuoto nel caso in cui si passa da un joker su un tavolo a zero dopo uno swap
                        String jokerTotaliString = line.substring(17);
                        
                        //controllo se la stringa è vuota
                        if (jokerTotaliString.isEmpty()) {
                            jokerTotaliSulTavolo.clear(); //svuoto la mappa dei jolly
                            javafx.application.Platform.runLater(() -> {
                                aggiornaTavoloGUI(); 
                            });
                        } else {
                            String[] playerTokens = jokerTotaliString.split("\\|"); //separo i giocatori
    
                            //ciclo i giocatori
                            for(String playerToken : playerTokens){
                                String[] playerParts = playerToken.split(":");
                                int playerId = Integer.parseInt(playerParts[0]); //es: "0"
                                String apertureData = playerParts[1]; //es: "0=8_diamonds.jpg,8_hearts.jpg;1=7_clubs.jpg"
    
                                Map<Integer, List<Carta>> apertureMap = new HashMap<>();
    
                                String[] apertureTokens = apertureData.split(";");
    
                                //ciclo le aperture dei giocatori
                                for(String aperturaToken : apertureTokens){
                                    String[] parts = aperturaToken.split("=");
                                    int aperturaIndex = Integer.parseInt(parts[0]); //es: "0"
                                    String[] carteStr = parts[1].split(","); //es: "8_diamonds.jpg" "8_hearts.jpg"
    
                                    List<Carta> carte = new ArrayList<>();
                                    for(String cStr : carteStr){
                                        Carta c = cartaFromFileName(cStr);
                                        if(c != null) carte.add(c);
                                    }
                                    apertureMap.put(aperturaIndex, carte);
                                }
    
                                jokerTotaliSulTavolo.put(playerId, apertureMap);
    
                                javafx.application.Platform.runLater(() -> {
                                    aggiornaTavoloGUI();
                                });
                            }
                        }
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
        if(haApertoRichiesta == false){ //non è ancora stata effettuta l'apertura richiesta dal round
            //pulisco la configurazione precedente
            openingSectionContainer.getChildren().clear();
            trisPerAprire = 0;
            scalePerAprire = 0;
            
            FlowPane trisScaleFlowPane = new FlowPane(); //contenitore per tutti i tris e le scale
            trisScaleFlowPane.setHgap(30); //spazio tra i tris/scale
            trisScaleFlowPane.setVgap(30); //spazio verticale se vanno a capo
            trisScaleFlowPane.setAlignment(Pos.CENTER);
            trisScaleFlowPane.setPrefWrapLength(800); //se superano 800px, vanno a capo
            trisScaleFlowPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    
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
                            trisRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 3 ImageView per gli slot
                        for (int j = 0; j < 3; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                            trisRow.getChildren().add(slotWrapper);
                        }
                        trisSlotContainers[i] = trisRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
                        confirmBtn.setDisable(true); //inizialmente disabilitato
                        final int trisIndex = i; //per l'uso nella lambda expression
                        confirmBtn.setOnAction(e -> handleConfermaTris(trisIndex));
                        confermaTrisButtons[i] = confirmBtn;
    
                        VBox trisGroup = new VBox(10, trisRow, confirmBtn);
                        trisGroup.setAlignment(Pos.CENTER);
                        trisGroup.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
    
                        trisScaleFlowPane.getChildren().add(trisGroup);
                    }
                    
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
                            trisRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 3 ImageView per gli slot
                        for (int j = 0; j < 3; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                            trisRow.getChildren().add(slotWrapper);
                        }
                        trisSlotContainers[i] = trisRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
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
                            scalaRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 4 ImageView per gli slot
                        for (int j = 0; j < 4; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                            scalaRow.getChildren().add(slotWrapper);
                        }
                        scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
                        confirmBtn.setDisable(true); //inizialmente disabilitato
                        final int scalaIndex = i; //per l'uso nella lambda expression
                        confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                        confermaScalaButtons[i] = confirmBtn;
    
                        VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                        scalaGroup.setAlignment(Pos.CENTER);
    
                        trisScaleFlowPane.getChildren().add(scalaGroup);
                    }
                    
                    break;
                case 3: //ROUND 2: 8 carte - 2 scale
                    scalePerAprire = 2;
                    scalaSlotContainers = new HBox[scalePerAprire];
                    scalaSlotViews = new ImageView[scalePerAprire][4];
                    confermaScalaButtons = new Button[scalePerAprire]; //mi crea 2 bottoni perché devo fare 2 scale
    
                    for (int i = 0; i < scalePerAprire; i++) {
                        //creo un contenitore HBox per i 4 slot di una singola scala
                        HBox scalaRow = new HBox(10); //piccolo spazio tra le carte
                        scalaRow.setAlignment(Pos.CENTER);
                        scalaRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                        
                        //applico uno stile diverso alla prima scala per indicare che è quella attiva
                        if (i == 0) {
                            scalaRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 4 ImageView per gli slot
                        for (int j = 0; j < 4; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                            scalaRow.getChildren().add(slotWrapper);
                        }
                        scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
                        confirmBtn.setDisable(true); //inizialmente disabilitato
                        final int scalaIndex = i; //per l'uso nella lambda expression
                        confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                        confermaScalaButtons[i] = confirmBtn;
    
                        VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                        scalaGroup.setAlignment(Pos.CENTER);
    
                        trisScaleFlowPane.getChildren().add(scalaGroup);
                    }
                    
                    break;
                case 4: //ROUND 2: 9 carte - 3 tris
                    trisPerAprire = 3;
                    trisSlotContainers = new HBox[trisPerAprire];
                    trisSlotViews = new ImageView[trisPerAprire][3];
                    confermaTrisButtons = new Button[trisPerAprire]; //mi crea 3 bottoni perché devo fare 3 tris
    
                    for (int i = 0; i < trisPerAprire; i++) {
                        //creo un contenitore HBox per i 3 slot di un singolo tris
                        HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                        trisRow.setAlignment(Pos.CENTER);
                        trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                        
                        //applico uno stile diverso al primo tris per indicare che è quello attivo
                        if (i == 0) {
                            trisRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 3 ImageView per gli slot
                        for (int j = 0; j < 3; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                            trisRow.getChildren().add(slotWrapper);
                        }
                        trisSlotContainers[i] = trisRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
                        confirmBtn.setDisable(true); //inizialmente disabilitato
                        final int trisIndex = i; //per l'uso nella lambda expression
                        confirmBtn.setOnAction(e -> handleConfermaTris(trisIndex));
                        confermaTrisButtons[i] = confirmBtn;
    
                        VBox trisGroup = new VBox(10, trisRow, confirmBtn);
                        trisGroup.setAlignment(Pos.CENTER);
    
                        trisScaleFlowPane.getChildren().add(trisGroup);
                    }
                    
                    break;
                case 5: //ROUND 2: 10 carte - 2 tris e 1 scala
                    trisPerAprire = 2;
                    scalePerAprire = 1;
    
                    trisSlotContainers = new HBox[trisPerAprire];
                    trisSlotViews = new ImageView[trisPerAprire][3];
    
                    scalaSlotContainers = new HBox[scalePerAprire];
                    scalaSlotViews = new ImageView[scalePerAprire][4];
    
                    confermaTrisButtons = new Button[trisPerAprire]; //mi crea 2 bottoni perché devo fare 2 tris
                    confermaScalaButtons = new Button[scalePerAprire]; //mi crea 1 bottone perché devo fare 1 scala
    
                    for (int i = 0; i < trisPerAprire; i++) {
                        //creo un contenitore HBox per i 3 slot di un singolo tris
                        HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                        trisRow.setAlignment(Pos.CENTER);
                        trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                        
                        //applico uno stile diverso al primo tris per indicare che è quello attivo
                        if (i == 0) {
                            trisRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 3 ImageView per gli slot
                        for (int j = 0; j < 3; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                            trisRow.getChildren().add(slotWrapper);
                        }
                        trisSlotContainers[i] = trisRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
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
                            scalaRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 4 ImageView per gli slot
                        for (int j = 0; j < 4; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                            scalaRow.getChildren().add(slotWrapper);
                        }
                        scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
                        confirmBtn.setDisable(true); //inizialmente disabilitato
                        final int scalaIndex = i; //per l'uso nella lambda expression
                        confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                        confermaScalaButtons[i] = confirmBtn;
    
                        VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                        scalaGroup.setAlignment(Pos.CENTER);
    
                        trisScaleFlowPane.getChildren().add(scalaGroup);
                    }
                    
                    break;
                case 6: //ROUND 2: 11 carte - 1 tris e 2 scale
                    trisPerAprire = 1;
                    scalePerAprire = 2;
    
                    trisSlotContainers = new HBox[trisPerAprire];
                    trisSlotViews = new ImageView[trisPerAprire][3];
    
                    scalaSlotContainers = new HBox[scalePerAprire];
                    scalaSlotViews = new ImageView[scalePerAprire][4];
    
                    confermaTrisButtons = new Button[trisPerAprire]; //mi crea 1 bottone perché devo fare 1 tris
                    confermaScalaButtons = new Button[scalePerAprire]; //mi crea 2 bottoni perché devo fare 2 scale
    
                    for (int i = 0; i < trisPerAprire; i++) {
                        //creo un contenitore HBox per i 3 slot di un singolo tris
                        HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                        trisRow.setAlignment(Pos.CENTER);
                        trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                        
                        //applico uno stile diverso al primo tris per indicare che è quello attivo
                        if (i == 0) {
                            trisRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 3 ImageView per gli slot
                        for (int j = 0; j < 3; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                            trisRow.getChildren().add(slotWrapper);
                        }
                        trisSlotContainers[i] = trisRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
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
                            scalaRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 4 ImageView per gli slot
                        for (int j = 0; j < 4; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                            scalaRow.getChildren().add(slotWrapper);
                        }
                        scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
                        confirmBtn.setDisable(true); //inizialmente disabilitato
                        final int scalaIndex = i; //per l'uso nella lambda expression
                        confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                        confermaScalaButtons[i] = confirmBtn;
    
                        VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                        scalaGroup.setAlignment(Pos.CENTER);
    
                        trisScaleFlowPane.getChildren().add(scalaGroup);
                    }
                    
                    break;
                case 7: //ROUND 2: 12 carte - 4 tris
                    trisPerAprire = 4;
                    trisSlotContainers = new HBox[trisPerAprire];
                    trisSlotViews = new ImageView[trisPerAprire][3];
                    confermaTrisButtons = new Button[trisPerAprire]; //mi crea 4 bottoni perché devo fare 4 tris
    
                    for (int i = 0; i < trisPerAprire; i++) {
                        //creo un contenitore HBox per i 3 slot di un singolo tris
                        HBox trisRow = new HBox(10); //piccolo spazio tra le carte
                        trisRow.setAlignment(Pos.CENTER);
                        trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                        
                        //applico uno stile diverso al primo tris per indicare che è quello attivo
                        if (i == 0) {
                            trisRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 3 ImageView per gli slot
                        for (int j = 0; j < 3; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                            trisRow.getChildren().add(slotWrapper);
                        }
                        trisSlotContainers[i] = trisRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
                        confirmBtn.setDisable(true); //inizialmente disabilitato
                        final int trisIndex = i; //per l'uso nella lambda expression
                        confirmBtn.setOnAction(e -> handleConfermaTris(trisIndex));
                        confermaTrisButtons[i] = confirmBtn;
    
                        VBox trisGroup = new VBox(10, trisRow, confirmBtn);
                        trisGroup.setAlignment(Pos.CENTER);
    
                        trisScaleFlowPane.getChildren().add(trisGroup);
                    }

                    break;
                case 8: //ROUND 2: 12 carte - 3 scale
                    scalePerAprire = 3;
                    scalaSlotContainers = new HBox[scalePerAprire];
                    scalaSlotViews = new ImageView[scalePerAprire][4];
                    confermaScalaButtons = new Button[scalePerAprire]; //mi crea 3 bottoni perché devo fare 3 scale
    
                    for (int i = 0; i < scalePerAprire; i++) {
                        //creo un contenitore HBox per i 4 slot di una singola scala
                        HBox scalaRow = new HBox(10); //piccolo spazio tra le carte
                        scalaRow.setAlignment(Pos.CENTER);
                        scalaRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
                        
                        //applico uno stile diverso alla prima scala per indicare che è quella attiva
                        if (i == 0) {
                            scalaRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                        }
    
                        //creo 4 ImageView per gli slot
                        for (int j = 0; j < 4; j++) {
                            StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                            scalaRow.getChildren().add(slotWrapper);
                        }
                        scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox
    
                        //creo il bottone CONFERMA per questo tris
                        Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                            confirmBtn.getStyleClass().add("standard-button");
                            confirmBtn.setId("confirmButton");
                        confirmBtn.setDisable(true); //inizialmente disabilitato
                        final int scalaIndex = i; //per l'uso nella lambda expression
                        confirmBtn.setOnAction(e -> handleConfermaScala(scalaIndex));
                        confermaScalaButtons[i] = confirmBtn;
    
                        VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
                        scalaGroup.setAlignment(Pos.CENTER);
    
                        trisScaleFlowPane.getChildren().add(scalaGroup);
                    }

                    break;
                default:
                    break;
            }
            //svuoto e aggiungo tutto in orizzontale
            openingSectionContainer.getChildren().clear();
            openingSectionContainer.getChildren().addAll(trisScaleFlowPane, completaAperturaButton);
            completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris individuali sono confermati
        } else{ //apertura richiesta dal round effettuata, ora si può aprire come si vuole con tris o scale a scelta
            
            //pulisco la configurazione precedente
            openingSectionContainer.getChildren().clear();

            //contenitore per i tris e le scale che verranno creati dinamicamente
            FlowPane dynamicSlotsFlowPane = new FlowPane(); 
            dynamicSlotsFlowPane.setHgap(30); 
            dynamicSlotsFlowPane.setVgap(30);
            dynamicSlotsFlowPane.setAlignment(Pos.CENTER);
            dynamicSlotsFlowPane.setPrefWrapLength(800);
            dynamicSlotsFlowPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

            //contenitore per i bottoni "Apri TRIS extra" e "Apri SCALA extra"
            HBox choiceButtonsContainer = new HBox(20);
            choiceButtonsContainer.setAlignment(Pos.CENTER);

            //bottone per aggiungere un nuovo tris
            addTrisButton = new Button("Apri TRIS extra");
            addTrisButton.getStyleClass().add("standard-button");
            addTrisButton.setId("apriButton");
            addTrisButton.setOnAction(e -> createAndAddDynamicTrisSlot(dynamicSlotsFlowPane));

            //bottone per aggiungere una nuova scala
            addScalaButton = new Button("Apri SCALA extra");
            addScalaButton.getStyleClass().add("standard-button");
            addScalaButton.setId("apriButton");
            addScalaButton.setOnAction(e -> createAndAddDynamicScalaSlot(dynamicSlotsFlowPane));

            choiceButtonsContainer.getChildren().addAll(addTrisButton, addScalaButton);

            //aggiungo i contenitori all'UI principale
            openingSectionContainer.getChildren().addAll(dynamicSlotsFlowPane, choiceButtonsContainer, completaAperturaButton);
            completaAperturaButton.setDisable(true); //sarà abilitato solo quando tutti i tris/scale individuali sono confermati
        }
    }

    /**
     * Crea e aggiunge un nuovo tris (gruppo di 3 carte) all'UI.
     * Questo metodo verrà chiamato quando l'utente clicca su "Apri TRIS extra".
     * @param parentFlowPane Il FlowPane in cui aggiungere il gruppo del tris.
     */
    private void createAndAddDynamicTrisSlot(FlowPane parentFlowPane) {
        
        addTrisButton.setVisible(false);
        addTrisButton.setManaged(false);
        addScalaButton.setVisible(false);
        addScalaButton.setManaged(false);
        
        trisExtra=true;
        scalaExtra=false;

        trisSlotContainers = new HBox[1];
        trisSlotViews = new ImageView[1][3];
        confermaTrisButtons = new Button[1]; //mi crea 1 bottone perché devo fare 1 tris

        for (int i = 0; i < 1; i++) {
            //creo un contenitore HBox per i 3 slot di un singolo tris
            HBox trisRow = new HBox(10); //piccolo spazio tra le carte
            trisRow.setAlignment(Pos.CENTER);
            trisRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
            
            //applico uno stile diverso al primo tris per indicare che è quello attivo
            if (i == 0) {
                trisRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
            }

            //creo 3 ImageView per gli slot
            for (int j = 0; j < 3; j++) {
                StackPane slotWrapper = creaImgPlaceholderApertura(trisSlotViews, i, j);
                trisRow.getChildren().add(slotWrapper);
            }
            trisSlotContainers[i] = trisRow; //memorizzo l'HBox

            //creo il bottone CONFERMA per questo tris
            Button confirmBtn = new Button("CONFERMA TRIS " + (i + 1));
                confirmBtn.getStyleClass().add("standard-button");
                confirmBtn.setId("confirmButton");
            confirmBtn.setDisable(true); //inizialmente disabilitato
            confirmBtn.setOnAction(e -> handleConfermaTrisExtra());
            confermaTrisButtons[i] = confirmBtn;

            VBox trisGroup = new VBox(10, trisRow, confirmBtn);
            trisGroup.setAlignment(Pos.CENTER);

            parentFlowPane.getChildren().add(trisGroup);
        }
    }

    /**
     * Crea e aggiunge una nuova scala (gruppo di 4 carte) all'UI.
     * Questo metodo verrà chiamato quando l'utente clicca su "Aggiungi Scala".
     * @param parentFlowPane Il FlowPane in cui aggiungere il gruppo della scala.
     */
    private void createAndAddDynamicScalaSlot(FlowPane parentFlowPane) {

        addTrisButton.setVisible(false);
        addTrisButton.setManaged(false);
        addScalaButton.setVisible(false);
        addScalaButton.setManaged(false);

        trisExtra=false;
        scalaExtra=true;

        scalaSlotContainers = new HBox[1];
        scalaSlotViews = new ImageView[1][4];
        confermaScalaButtons = new Button[1]; //mi crea 1 bottone perché devo fare 1 scala

        for (int i = 0; i < 1; i++) {
            //creo un contenitore HBox per i 4 slot di una singola scala
            HBox scalaRow = new HBox(10); //piccolo spazio tra le carte
            scalaRow.setAlignment(Pos.CENTER);
            scalaRow.setStyle("-fx-border-width: 2; -fx-padding: 10;"); //bordatura per raggruppare visivamente
            
            //applico uno stile diverso alla prima scala per indicare che è quella attiva
            if (i == 0) {
                scalaRow.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
            }

            //creo 4 ImageView per gli slot
            for (int j = 0; j < 4; j++) {
                StackPane slotWrapper = creaImgPlaceholderApertura(scalaSlotViews, i, j);
                scalaRow.getChildren().add(slotWrapper);
            }
            scalaSlotContainers[i] = scalaRow; //memorizzo l'HBox

            //creo il bottone CONFERMA per questo tris
            Button confirmBtn = new Button("CONFERMA SCALA " + (i + 1));
                confirmBtn.getStyleClass().add("standard-button");
                confirmBtn.setId("confirmButton");
            confirmBtn.setDisable(true); //inizialmente disabilitato
            confirmBtn.setOnAction(e -> handleConfermaScalaExtra());
            confermaScalaButtons[i] = confirmBtn;

            VBox scalaGroup = new VBox(10, scalaRow, confirmBtn);
            scalaGroup.setAlignment(Pos.CENTER);

            parentFlowPane.getChildren().add(scalaGroup);
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
            if (verificaTris(currentTrisSelection)) { //tris valido
                carteSostituiteDaJoker.clear(); //svuoto le carte sostituite per ogni singolo tris
                int numJoker = contaJoker(currentTrisSelection); //conto quanti joker ci sono nel tris
                
                if(numJoker == 1 || numJoker == 2 || numJoker == 3){
                    //se ci sono 1 o 2 joker apro il popup per la scelta del seme (il valore lo prendo dalle carte note)
                    //se ci sono 3 joker apro il popup per la scelta del valore del tris e del seme di ogni joker
                    Optional<List<Carta>> carteSostituiteOptional = showJokerTrisSemePopup(numJoker, currentTrisSelection);

                    //solo se il popup restituisce una lista di carte
                    if (carteSostituiteOptional.isPresent()) {
                        carteSostituiteDaJoker.addAll(carteSostituiteOptional.get()); //1, 2 o 3 carte
                    } else {
                        //l'utente ha chiuso il popup senza confermare, non faccio nulla e mantengo lo stato del gioco
                        return;
                    }
                }

                //aggiungo le carte sostituite dal joker a quelle confermate
                carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);

                //aggiungo il tris all’elenco di quelli confermati
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
                currentTrisSelection.clear();
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
                        trisSlotContainers[trisConfermatiCorrenti].setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                    }
                } else {
                    //tutti i tris richiesti sono stati confermati!
                    status.setText("Tutti i tris sono stati confermati");
                    
                    //rimuovo l'highlight dall'ultimo tris confermato
                    trisSlotContainers[trisIndex].setStyle("-fx-border-color: green; -fx-border-width: 2; -fx-padding: 10;");

                    //attivo il bottone finale SOLO se anche le scale richieste sono state confermate (ci sono round dove ci sono sia tris che scale richiesti per aprire)
                    if (trisConfermatiCorrenti >= trisPerAprire && scaleConfermateCorrenti >= scalePerAprire) {
                        status.setText("Tutte le aperture sono confermate. Clicca 'COMPLETA APERTURA' per inviare");
                        completaAperturaButton.setDisable(false); //abilito il bottone finale
                    }
                }
                aggiornaManoGUI(); //rimuovo l'evidenziazione dalle carte in mano (se sono state spostate agli slot)
                updateOpeningSlotsGUI(); //aggiorno la visualizzazione degli slot
            } else { //tris non valido
                feedbackLabel.setText("Questo tris non è valido. Seleziona altre carte");
                currentTrisSelection.clear(); //pulisco la selezione corrente
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

    //gestione della conferma di una singola scala
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
                currentScalaSelection.clear();
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
                    scalaSlotContainers[scaleConfermateCorrenti].setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");
                } else {
                    //tutte le scale confermate
                    status.setText("Tutte le scale sono state confermate");
                    
                    //rimuovo l'highlight dall'ultima scala confermata
                    scalaSlotContainers[scalaIndex].setStyle("-fx-border-color: green; -fx-border-width: 2; -fx-padding: 10;");

                    //attivo il bottone finale SOLO se anche le scale richieste sono state confermate (ci sono round dove ci sono sia tris che scale richiesti per aprire)
                    if (trisConfermatiCorrenti >= trisPerAprire && scaleConfermateCorrenti >= scalePerAprire) {
                        status.setText("Tutte le aperture sono confermate. Clicca 'COMPLETA APERTURA' per inviare");
                        completaAperturaButton.setDisable(false); //abilito il bottone finale
                    }
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

    //gestione della conferma del tris extra
    private void handleConfermaTrisExtra() {
        feedbackLabel.setText("");
        //verifico che siano state selezionate esattamente 3 carte per questo tris
        if (currentTrisSelection.size() == 3) {
            if (verificaTris(currentTrisSelection)) {
                carteSostituiteDaJoker.clear(); //svuoto le carte sostituite per ogni singolo tris
                int numJoker = contaJoker(currentTrisSelection); //conto quanti joker ci sono nel tris
                
                if(numJoker == 1 || numJoker == 2 || numJoker == 3){
                    //se ci sono 1 o 2 joker apro il popup per la scelta del seme (il valore lo prendo dalle carte note)
                    //se ci sono 3 joker apro il popup per la scelta del valore del tris e del seme di ogni joker
                    Optional<List<Carta>> carteSostituiteOptional = showJokerTrisSemePopup(numJoker, currentTrisSelection);

                    //solo se il popup restituisce una lista di carte
                    if (carteSostituiteOptional.isPresent()) {
                        carteSostituiteDaJoker.addAll(carteSostituiteOptional.get()); //1, 2 o 3 carte
                    } else {
                        //l'utente ha chiuso il popup senza confermare, non faccio nulla e mantengo lo stato del gioco
                        return;
                    }
                }

                //aggiungo le carte sostituite dal joker a quelle confermate
                carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);

                //tris extra valido! lo aggiungo all’elenco confermato
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

                //rimuovo l'highlight dal tris precedente
                trisSlotContainers[0].setStyle("-fx-border-color: green; -fx-border-width: 2; -fx-padding: 10;");
                confermaTrisButtons[0].setDisable(true);

                //resetto lo stato di selezione per i tris extra
                trisExtra = false;
                currentTrisSelection.clear();

                //abilita il bottone COMPLETA APERTURA
                completaAperturaButton.setDisable(false);
                feedbackLabel.setText("Tris extra confermato con successo!");

                aggiornaManoGUI();
                updateOpeningSlotsGUI();

            } else {
                //tris extra non valido
                feedbackLabel.setText("Questo tris extra non è valido. Seleziona altre carte");
                
                //pulisco la selezione e la GUI
                currentTrisSelection.clear();
                
                aggiornaManoGUI(); //rimuovo l'evidenziazione dalle carte in mano
                updateOpeningSlotsGUI(); //aggiorno gli slot per renderli vuoti
            }
        } else {
            feedbackLabel.setText("Devi selezionare 3 carte per confermare questo tris extra");
        }
    }

    //gestione della conferma della scala extra
    private void handleConfermaScalaExtra() {
        feedbackLabel.setText("");
        //verifico che siano state selezionate esattamente 4 carte per questa scala
        if (currentScalaSelection.size() == 4) {
            //verifico validità della scala
            RisultatoScala risultato = verificaScalaConJoker(currentScalaSelection, Optional.empty());

            if (risultato.isValida()) {
                //scala extra valida! la aggiungo all’elenco confermato
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
                
                //rimuovo l'highlight dalla scala precedente
                scalaSlotContainers[0].setStyle("-fx-border-color: green; -fx-border-width: 2; -fx-padding: 10;");
                confermaScalaButtons[0].setDisable(true);

                //resetto lo stato di selezione per le scale extra
                scalaExtra = false;
                currentScalaSelection.clear();

                //abilita il bottone COMPLETA APERTURA
                completaAperturaButton.setDisable(false);
                feedbackLabel.setText("Scala extra confermata con successo!");
                status.setText("Apertura extra completata. Clicca 'COMPLETA APERTURA' per inviare");

                aggiornaManoGUI();
                updateOpeningSlotsGUI();

            } else {
                //scala extra non valida
                feedbackLabel.setText("Questa scala extra non è valida. Seleziona altre carte");
                
                //pulisco la selezione e la GUI
                currentScalaSelection.clear();
                
                aggiornaManoGUI(); //rimuovo l'evidenziazione dalle carte in mano
                updateOpeningSlotsGUI(); //aggiorno gli slot per renderli vuoti
            }
        } else {
            feedbackLabel.setText("Devi selezionare 4 carte per confermare questa scala");
        }
    }

    //metodo per l'apertura del popup per la scelta della carta che il joker sta sostituendo (caso con 1 o 2 joker)
    private Optional<List<Carta>> showJokerTrisSemePopup(int numJoker, List<Carta> carteSelezionate) {
        
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Joker nel Tris");
        if(numJoker==1){
            dialog.setHeaderText("Un Joker è stato trovato nel tris. Scegli il seme che sta sostituendo");
        } else if (numJoker==2){
            dialog.setHeaderText("Due Joker sono stati trovati nel tris. Scegli i semi che stanno sostituendo");
        } else if (numJoker==3){
            dialog.setHeaderText("Tre Joker sono stati trovati nel tris. Scegli il valore del tris e i semi che stanno sostituendo");
        }

        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);

        //salvo i joker
        List<Carta> jokerCarteSelezionate = new ArrayList<>();
        for(Carta c : carteSelezionate){
            if(c.isJoker()){
                jokerCarteSelezionate.add(c);
            }
        }

        final String valoreTris;
        final ChoiceBox<String> valoreChoiceBox;

        //per un tris con 1 o 2 jolly il valore è noto
        if(numJoker < 3){
            String tempValore = "";
            for (Carta c : carteSelezionate) {
                if (!c.getValore().equalsIgnoreCase("Joker")) {
                    tempValore = c.getValore();
                    break;
                }
            }
            valoreTris = tempValore;
            valoreChoiceBox = null;
    
            //aggiungo un messaggio informativo per l'utente
            Label infoLabel = new Label("Il valore del tris è: " + valoreTris + ". Scegli un seme");
            content.getChildren().add(infoLabel);
        } else { //numJoker == 3
            valoreTris = null; //il valore non è ancora noto
            valoreChoiceBox = new ChoiceBox<>();

            Label infoLabel = new Label("Scegli il valore del tris:");
            content.getChildren().add(infoLabel);

            //aggiungo tutti i valori possibili (dall'Asso al Re)
            valoreChoiceBox.getItems().addAll("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K");
            valoreChoiceBox.getSelectionModel().selectFirst();
            content.getChildren().add(valoreChoiceBox);
        }

        //creo un ChoiceBox per ogni joker (1, 2 o 3)
        List<ChoiceBox<String>> semeChoiceBoxes = new ArrayList<>();
        for (int i = 0; i < numJoker; i++) {
            ChoiceBox<String> choiceBox = new ChoiceBox<>();
            choiceBox.getItems().addAll("CUORI", "QUADRI", "FIORI", "PICCHE");
            choiceBox.getSelectionModel().selectFirst();
            semeChoiceBoxes.add(choiceBox);
            content.getChildren().add(new Label("Sostituisci il Joker con il seme:"));
            content.getChildren().add(choiceBox);
        }

        dialog.getDialogPane().setContent(content);

        //aggiungo un pulsante "Conferma" al popup
        ButtonType confermaButtonType = new ButtonType("CONFERMA", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confermaButtonType, ButtonType.CANCEL);

        final Button confermaButton = (Button) dialog.getDialogPane().lookupButton(confermaButtonType);
        Label erroreLabel = new Label("");
        erroreLabel.setStyle("-fx-text-fill: red;");
        content.getChildren().add(erroreLabel);

        //listener per la validazione
        Runnable validator = () -> {
            String finalValoreTris;
            if (numJoker == 3) {
                finalValoreTris = valoreChoiceBox.getValue();
            } else {
                finalValoreTris = valoreTris;
            }
            boolean isValid = validaSceltaJoker(finalValoreTris, semeChoiceBoxes, carteSelezionate);
            confermaButton.setDisable(!isValid);
            if (!isValid) {
                erroreLabel.setText("La carta selezionata esiste già due volte negli scarti o nel tris corrente");
            } else {
                erroreLabel.setText("");
            }
        };

        if (numJoker == 3) {
            valoreChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> validator.run());
        }
        for (ChoiceBox<String> cb : semeChoiceBoxes) {
            cb.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> validator.run());
        }
        validator.run();

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == confermaButtonType) {
            List<String> semiScelti = new ArrayList<>();
            for (ChoiceBox<String> cb : semeChoiceBoxes) {
                semiScelti.add(cb.getValue());
            }

            List<Carta> carteSostituite = new ArrayList<>();
            String valoreFinale = (numJoker == 3) ? valoreChoiceBox.getValue() : valoreTris;
            
            for (int i = 0; i < numJoker; i++) {
                //aggiungo la carta sostituita alla lista
                carteSostituite.add(new Carta(valoreFinale, semiScelti.get(i)));
                
                //aggiungo il Joker alla lista globale con i valori scelti
                jokerSulTavolo.add(new JokerSulTavolo(carteSelezionate, jokerCarteSelezionate.get(i), semiScelti.get(i), valoreFinale, playerId));
            }

            return Optional.of(carteSostituite);
        }

        //se il popup viene chiuso con Annulla o la X, restituisco un Optional vuoto
        return Optional.empty();
    }

    //metodo per l'apertura del popup per la scelta della carta che il joker sta sostituendo (caso scala con 4 joker)
    private Optional<List<Carta>> showJokerScalaValSemePopup(List<Carta> carteSelezionate) {
        
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Joker nella Scala");
        dialog.setHeaderText("Scala di quattro Joker! Scegli il seme della scala e il valore del primo Joker");

        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);

        //salvo i 4 joker
        List<Carta> jokerCarteSelezionate = new ArrayList<>();
        for(Carta c : carteSelezionate){
            if(c.isJoker()){ //se non ci sono errori si entrerà sempre in questo if 
                jokerCarteSelezionate.add(c);
            }
        }

        //scelta del valore
        Label valoreLabel = new Label("Scegli il valore della prima carta della scala:");
        content.getChildren().add(valoreLabel);
        final ChoiceBox<String> valoreChoiceBox = new ChoiceBox<>();;
        //aggiungo tutti i valori possibili (dall'Asso al Jack)
        valoreChoiceBox.getItems().addAll("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J");
        valoreChoiceBox.getSelectionModel().selectFirst();
        content.getChildren().add(valoreChoiceBox);

        //scelta del seme
        Label semeLabel = new Label("Scegli il seme della scala:");
        content.getChildren().add(semeLabel);
        final ChoiceBox<String> semeChoiceBox = new ChoiceBox<>();;
        //aggiungo tutti i semi
        semeChoiceBox.getItems().addAll("CUORI", "QUADRI", "FIORI", "PICCHE");
        semeChoiceBox.getSelectionModel().selectFirst();
        content.getChildren().add(semeChoiceBox);

        dialog.getDialogPane().setContent(content);

        //aggiungo un pulsante "Conferma" al popup
        ButtonType confermaButtonType = new ButtonType("CONFERMA", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confermaButtonType, ButtonType.CANCEL);

        final Button confermaButton = (Button) dialog.getDialogPane().lookupButton(confermaButtonType);
        Label erroreLabel = new Label("");
        erroreLabel.setStyle("-fx-text-fill: red;");
        content.getChildren().add(erroreLabel);

        //listener per la validazione
        Runnable validator = () -> {
            String valoreIniziale = valoreChoiceBox.getValue();
            String semeScelto = semeChoiceBox.getValue();

            //creo una lista fittizia con un solo elemento (il seme scelto)
            List<ChoiceBox<String>> fittiziaSemeChoiceBoxes = new ArrayList<>();
            //creo una ChoiceBox temporanea e imposto il seme scelto dall'utente
            ChoiceBox<String> tempChoiceBox = new ChoiceBox<>();
            tempChoiceBox.getItems().add(semeScelto);
            tempChoiceBox.getSelectionModel().select(semeScelto);

            fittiziaSemeChoiceBoxes.add(tempChoiceBox);

            boolean isValid = validaSceltaJoker(valoreIniziale, fittiziaSemeChoiceBoxes, carteSelezionate);
            confermaButton.setDisable(!isValid);
            if (!isValid) {
                erroreLabel.setText("La carta selezionata esiste già due volte negli scarti o nel tris corrente");
            } else {
                erroreLabel.setText("");
            }
        };

        valoreChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> validator.run());
        semeChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> validator.run());
        validator.run();

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == confermaButtonType) {
            int valoreIniziale = convertiValoreNumerico(valoreChoiceBox.getValue());
            List<Integer> valoriScala = new ArrayList<>();
            valoriScala.add(valoreIniziale);
            valoriScala.add(valoreIniziale + 1);
            valoriScala.add(valoreIniziale + 2);
            valoriScala.add(valoreIniziale + 3);

            List<Carta> carteSostituite = new ArrayList<>();
            
            for (int i = 0; i < 4; i++) { //ci sono 4 joker nella scala
                //aggiungo la carta sostituita alla lista
                carteSostituite.add(new Carta(convertiValoreTestuale(valoriScala.get(i)), semeChoiceBox.getValue()));
                
                //aggiungo il Joker alla lista globale con i valori scelti
                jokerSulTavolo.add(new JokerSulTavolo(carteSelezionate, jokerCarteSelezionate.get(i), semeChoiceBox.getValue(), convertiValoreTestuale(valoriScala.get(i)), playerId));
            }

            return Optional.of(carteSostituite);
        }

        //se il popup viene chiuso con Annulla o la X, restituisco un Optional vuoto
        return Optional.empty();
    }

    /*
    popup per l'attacco di un joker a un tris e riceve come parametro:
        - i sostituti dei joker se l'apertura è composta da soli joker
        - l'apertura se c'è almeno una carta non joker all'interno
    */
    private Optional<Carta> showSemePopupAttacco(List<Carta> apertura) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Attacco Joker nel Tris");
        dialog.setHeaderText("Scegli il seme del Joker che sta sostituendo");

        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);

        //trova il valore noto dalla prima carta non-joker
        final String valoreTris;
        String tempValore = null;
        for (Carta c : apertura) {
            if (!c.isJoker()) {
                tempValore = c.getValore();
                break;
            }
        }

        if (tempValore == null) {
            //nessun valore noto, impossibile attaccare
            out.println("DEBUG: ERRORE NELL'ATTACCO");
            return Optional.empty();
        }
        valoreTris = tempValore;

        Label infoLabel = new Label("Il valore della carta che stai attaccando è: " + valoreTris + ". Scegli un seme");
        content.getChildren().add(infoLabel);

        //creo un ChoiceBox per il seme del joker
        ChoiceBox<String> semeChoiceBox = new ChoiceBox<>();
        semeChoiceBox.getItems().addAll("CUORI", "QUADRI", "FIORI", "PICCHE");
        semeChoiceBox.getSelectionModel().selectFirst();
        content.getChildren().add(semeChoiceBox);

        dialog.getDialogPane().setContent(content);

        //aggiungo un pulsante "Conferma" al popup
        ButtonType confermaButtonType = new ButtonType("CONFERMA", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confermaButtonType, ButtonType.CANCEL);
        final Button confermaButton = (Button) dialog.getDialogPane().lookupButton(confermaButtonType);
        Label erroreLabel = new Label("");
        erroreLabel.setStyle("-fx-text-fill: red;");
        content.getChildren().add(erroreLabel);

        //Creo una lista di ChoiceBox con un solo elemento
        List<ChoiceBox<String>> semeChoiceBoxesList = new ArrayList<>();
        semeChoiceBoxesList.add(semeChoiceBox);

        //listener per la validazione
        Runnable validator = () -> {
            //controllo se il seme scelto è valido
            boolean isValid = validaSceltaJoker(valoreTris, semeChoiceBoxesList, apertura);
            confermaButton.setDisable(!isValid);
            if (!isValid) {
                erroreLabel.setText("La carta selezionata esiste già due volte negli scarti o nel tris corrente");
            } else {
                erroreLabel.setText("");
            }
        };

        semeChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> validator.run());
        validator.run();

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == confermaButtonType) {
            String semeScelto = semeChoiceBox.getValue();
            return Optional.of(new Carta(valoreTris, semeScelto));
        }

        //se il popup viene chiuso con Annulla o la X, restituisco un Optional vuoto
        return Optional.empty();
    }

    //TODO da finire quando sarà implementato anche il tavolo che mostra le carte perché il joker che sostituisce una carta deve confrontarsi anche col tavolo (es sono già presenti due joker che sostituiscono entrambi un 3 di cuori, allora un altro joker in apertura non potrà essere un 3 di cuori ovviamente -> max 2 carti uguali)
    //per validare la scelta del seme del joker
    private boolean validaSceltaJoker(String valoreTris, List<ChoiceBox<String>> semeChoiceBoxes, List<Carta> carteSelezionate) {
        //mappa per tenere traccia del conteggio totale di ogni carta (max 2 per ogni carta)
        Map<Carta, Integer> conteggioTotaleCarte = new HashMap<>();

        // 1. aggiungo le carte già confermate nell'apertura corrente al conteggio
        for (Carta cartaConfermata : carteSelezionatePerAperturaTotale) {
            conteggioTotaleCarte.put(cartaConfermata, conteggioTotaleCarte.getOrDefault(cartaConfermata, 0) + 1);
        }

        // 2. aggiungo le carte del tris che si sta validando (esclusi i joker) al conteggio
        for (Carta c : carteSelezionate) {
            if (!c.isJoker()) {
                conteggioTotaleCarte.put(c, conteggioTotaleCarte.getOrDefault(c, 0) + 1);
            }
        }

        // 3. aggiungo le carte della pila degli scarti al conteggio
        for (Carta cartaScarto : pilaScarti) {
            conteggioTotaleCarte.put(cartaScarto, conteggioTotaleCarte.getOrDefault(cartaScarto, 0) + 1);
        }

        // 4. aggiungo le carte che i joker stanno tentando di sostituire
        for (ChoiceBox<String> cb : semeChoiceBoxes) {
            if (cb.getValue() == null) {
                return false;
            }
            Carta cartaTentata = new Carta(valoreTris, cb.getValue());
            conteggioTotaleCarte.put(cartaTentata, conteggioTotaleCarte.getOrDefault(cartaTentata, 0) + 1);
        }

        // 5. verifico che nessuna carta superi il conteggio di 2
        for (Integer count : conteggioTotaleCarte.values()) {
            if (count > 2) {
                return false;
            }
        }
        
        //se nessun controllo fallisce, la scelta è valida.
        return true;
    }

    //gestione della conferma dell'apertura
    //tutti i tris/scale individuali sono già stati validati e confermati
    private void handleCompletaApertura() {
        feedbackLabel.setText("");
        
        int carteRimanentiDopoApertura = mano.getCarte().size() - carteSelezionatePerAperturaTotale.size();

        //CHEK REGOLAMENTO: "non puoi finire le carte con un'apertura, quindi se dopo aver aperto si hanno 0 carte in mano -> APERTURA NON VALIDA"
        if (carteRimanentiDopoApertura == 0) {
            feedbackLabel.setText("Non puoi finire le carte con un'apertura! Devi scartare una carta per terminare il round!");
            handleEsciModalitaApertura(); //resetto la UI, pulisco le selezioni e riabilito i pulsanti APRI/SCARTA
            return; //se la regola è violata, non inviamo nulla al server e non rimuoviamo le carte dalla mano
        }

        //CHEK REGOLAMENTO: "non puoi finire il round scartando un joker, quindi se apri e rimane solo il joker in mano -> APERTURA NON VALIDA"
        if(carteRimanentiDopoApertura == 1){
            Carta ultimaCarta = null;
            //trovo l'ultima carta che rimane in mano dopo l'apertura
            for (Carta cartaInMano : mano.getCarte()) {
                if (!carteSelezionatePerAperturaTotale.contains(cartaInMano)) {
                    ultimaCarta = cartaInMano;
                    break;
                }
            }
            //verifico se la carta rimanente è un Joker
            if (ultimaCarta != null && ultimaCarta.isJoker()) {
                feedbackLabel.setText("Non puoi rimanere con solo un Joker in mano! Cambia apertura o scarta e passa il turno");
                handleEsciModalitaApertura(); //resetto la UI, pulisco le selezioni e riabilito i pulsanti APRI/SCARTA
                return; //se la regola è violata, non inviamo nulla al server e non rimuoviamo le carte dalla mano
            }
        }

        //se arrivo qui, significa che l'apertura è valida e non svuota interamente la mano; procedo con l'invio al server e la rimozione delle carte
        attaccaBtn.setVisible(true);
        attaccaBtn.setManaged(true);
        if(haApertoRichiesta && mano.getCarte().size()>1){
            attaccaBtn.setDisable(false); //abilito ATTACCA
        }else{
            attaccaBtn.setDisable(true); //disabilito ATTACCA
        }

        //splitto le varie aperture dalle carteSelezionatePerAperturaTotale in base al numero di carte
        List<List<Carta>> apertureDaInviare = new ArrayList<>();
        int sizeAperturaTotale = carteSelezionatePerAperturaTotale.size();

        switch (sizeAperturaTotale) {
            case 3: //tris extra
            case 4: //scala extra
                apertureDaInviare.add(new ArrayList<>(carteSelezionatePerAperturaTotale));
                break;
            case 6: //due tris
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(0, 3));
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(3, 6));
                break;
            case 7: //un tris e una scala
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(0, 3));
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(3, 7));
                break;
            case 8: //due scale
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(0, 4));
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(4, 8));
                break;
            case 9: //tre tris
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(0, 3));
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(3, 6));
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(6, 9));
                break;
            case 10: //due tris e una scala
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(0, 3));
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(3, 6));
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(6, 10));
                break;
            case 11: //un tris e due scale
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(0, 3));
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(3, 7));
                apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(7, 11));
                break;
            case 12:
                if(currentRound == 7){  //quattro tris
                    apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(0, 3));
                    apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(3, 6));
                    apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(6, 9));
                    apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(9, 12));
                }else{  //tre scale
                    apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(0, 4));
                    apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(4, 8));
                    apertureDaInviare.add(carteSelezionatePerAperturaTotale.subList(8, 12));
                }
                break;
            default: //non dovrebbe mai entrarci (5 o +12 carte non è possibile per il regolamente)
                break;
        }

        //alla prima apertura (quella richiesta) haApertoRichiesta è false quindi primaApertura diventa true (in fase extra sarà il contrario)
        boolean primaApertura = !haApertoRichiesta;
        StringBuilder aperturaMessage = new StringBuilder();

        if (primaApertura) {    //fase APERTURA RICHIESTA DAL ROUND (da 2 a 4 aperture in base al round)
            aperturaMessage.append("APRI:");
            int jokerIndex = 0; //puntatore sui joker sostituti globali

            //ciclo su tutte le aperture (tris, scale, ecc.)
            for (List<Carta> apertura : apertureDaInviare) {
                int aperturaId = numApertura++;

                //aggiungo tutte le carte della singola apertura al messaggio per il server
                for (Carta c : apertura) {
                    aperturaMessage.append(c.getImageFilename()).append(",");
                }

                int numJoker = contaJoker(apertura);
                if (numJoker > 0){
                    StringBuilder jokerSostitutiMessage = new StringBuilder("JOKER_SOSTITUTI:");
                    jokerSostitutiMessage.append(aperturaId).append(":");
                    for(int i = 0; i < numJoker; i++){
                        Carta sostituta = carteSostituiteDaJokerConfermate.get(jokerIndex++); //prende jokerIndex e poi lo incrementa
                        jokerSostitutiMessage.append(sostituta.getImageFilename()).append(",");
                    }

                    //tolgo l’ultima virgola e invio
                    jokerSostitutiMessage.setLength(jokerSostitutiMessage.length() - 1);
                    out.println(jokerSostitutiMessage.toString());
                }
            }

            //chiudo il messaggio APRI e invio al server
            aperturaMessage.setLength(aperturaMessage.length() - 1);
            out.println(aperturaMessage.toString());
            //svuoto la lista globale dei joker
            carteSostituiteDaJokerConfermate.clear();

        } else {  //fase EXTRA (una sola apertura)
            int aperturaId = numApertura++;
            List<Carta> apertura = apertureDaInviare.get(0);

            aperturaMessage.append("APRI_EXTRA:");
            for (Carta c : apertura) {
                aperturaMessage.append(c.getImageFilename()).append(",");
            }
            aperturaMessage.setLength(aperturaMessage.length() - 1);
            out.println(aperturaMessage.toString());

            //gestione joker nella fase extra
            int numJoker = contaJoker(apertura);
            if (numJoker > 0) {
                StringBuilder jokerSostitutiMessage = new StringBuilder("JOKER_SOSTITUTI:");
                jokerSostitutiMessage.append(aperturaId).append(":");

                for (int i = 0; i < numJoker; i++) {
                    Carta sostituta = carteSostituiteDaJokerConfermate.get(i);
                    jokerSostitutiMessage.append(sostituta.getImageFilename()).append(",");
                }

                jokerSostitutiMessage.setLength(jokerSostitutiMessage.length() - 1);
                out.println(jokerSostitutiMessage.toString());
            }

            carteSostituiteDaJokerConfermate.clear();
        }

        haApertoRichiesta=true;

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
        if (aperturaMessage.toString().contains("APRI_EXTRA:")) {
            apriBtn.setDisable(false); //il giocatore ha effettuato un'apertura extra, può farne altre in questo round
        } else {
            apriBtn.setDisable(true); //il giocatore ha effettuato l'apertura richiesta dal round, non può aprire di nuovo in questo round
        }
        completaAperturaButton.setDisable(true); //disabilito il bottone finale
        
        mioTurno = true; //il turno è ancora del giocatore per lo scarto finale
        haPescato = true; //necessario per abilitare lo scarto
        pescaBtn.setDisable(true);
        scartaBtn.setDisable(false); //ABILITO SOLO SCARTA
        status.setText("Apertura riuscita! Ora devi scartare una carta per terminare il turno");

        pescaBtn.setVisible(true);
        pescaBtn.setManaged(true);
        pescaBtn.setDisable(true);
        scartaBtn.setVisible(true);
        scartaBtn.setManaged(true);
        scartaBtn.setDisable(false);

        //nascondo completamente la sezione di apertura
        scrollPaneApertura.setVisible(false);
        scrollPaneApertura.setManaged(false);
    }

    private void handleEsciModalitaApertura() {
        status.setText("Puoi scartare una carta o provare ad aprire nuovamente");
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
        carteSostituiteDaJokerConfermate.clear();

        //nascondo la sezione di apertura
        scrollPaneApertura.setVisible(false);
        scrollPaneApertura.setManaged(false);

        //nascondo il bottone "COMPLETA APERTURA"
        completaAperturaButton.setDisable(true);

        //riabilito i bottoni principali per la fine del turno
        pescaBtn.setDisable(true); //ancora disabilitati perché ho già pescato
        scartaBtn.setDisable(false); //abilito SCARTA
        apriBtn.setText("APRI"); //ripristino il testo del bottone
        apriBtn.setDisable(false); //abilito nuovamente APRI

        if(haApertoRichiesta && mano.getCarte().size()>1){
            attaccaBtn.setDisable(false); //abilito nuovamente ATTACCA
        }else{
            attaccaBtn.setDisable(true); //disabilito nuovamente ATTACCA
        }

        pescaBtn.setVisible(true);
        pescaBtn.setManaged(true);
        scartaBtn.setVisible(true);
        scartaBtn.setManaged(true);
        attaccaBtn.setVisible(true);
        attaccaBtn.setManaged(true);

        aggiornaManoGUI(); //aggiorno la mano per rimuovere evidenziazioni
    }

    private void handleEsciModalitaScarto(){
        inScarto = false; //esco dalla modalità scarto
        scartaBtn.setText("SCARTA"); //ripristino il testo del bottone

        //ripristina lo stato del turno dopo aver pescato
        status.setText("Hai pescato. Ora puoi scartare una carta o aprire");
        feedbackLabel.setText(""); //pulisco il feedback

        scrollPaneApertura.setVisible(false);
        scrollPaneApertura.setManaged(false);

        apriBtn.setVisible(true);
        apriBtn.setManaged(true);
        pescaBtn.setVisible(true);
        pescaBtn.setManaged(true);
        attaccaBtn.setVisible(true);
        attaccaBtn.setManaged(true);

        //non ho effettutato l'aperture richiesta dal round
        if(!haApertoRichiesta){
            apriBtn.setDisable(false); //abilitato
        }

        //sono nel turno in cui ho effettutato l'apertura richiesta -> non posso aprire ulteriormente con una extra
        if(haApertoRichiesta && numTurno==numTurnoAperturaRound){
            apriBtn.setDisable(true); //disabilitato
        }

        //ho già effettutato l'apertura richiesta e quindi posso fare quante aperture extra desidero (haApertoRichiesta è sicuramente true in questo punto)
        if(numTurno>numTurnoAperturaRound){
            apriBtn.setDisable(false); //abilitato
            if(mano.getCarte().size()>1){
                attaccaBtn.setDisable(false); //abilitato nuovamente ATTACCA
            }else{
                attaccaBtn.setDisable(true); //disabilitato nuovamente ATTACCA
            }
        }
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
        if (carta.isJoker()){
            feedbackLabel.setText("Non puoi scartare un Joker!");
            return;
        }

        mano.rimuoviCarta(carta);
        aggiornaManoGUI();
        
        out.println("SCARTA:" + carta.toString());

        mioTurno = false;
        haPescato = false;
        inScarto = false; //resetto la modalità scarto

        badge.setVisible(false);
        badge.setManaged(false);

        scartaBtn.setText("SCARTA");
        
        pescaBtn.setDisable(true);
        scartaBtn.setDisable(true);
        apriBtn.setDisable(true);
        attaccaBtn.setDisable(true);

        pescaBtn.setVisible(true);
        pescaBtn.setManaged(true);
        apriBtn.setVisible(true);
        apriBtn.setManaged(true);
        attaccaBtn.setVisible(true);
        attaccaBtn.setManaged(true);

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
    /*
     * verifica se un tris è valido (3 carte dello stesso valore)
     * tiene traccia di quanti Joker ci sono
     * gestisce il caso limite con 3 Joker 
     */
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
            //questo caso si verifica solo se le carte originali erano tutte Joker (già gestito numJoker == 3).
            //oppure se in qualche modo carteNormali è vuota e numJoker < 3
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

    /*
     * verifica se una scala è valida (4 carte in ordine e delle stesso seme)
     * tiene traccia di quale carta ogni Joker sta sostituendo
     * gestisce il caso limite con 4 Joker richiedendo un'assegnazione manuale del primo Joker 
     */
    private RisultatoScala verificaScalaConJoker(List<Carta> carte, Optional<Carta> primoJokerManuale) {
        RisultatoScala risultato = new RisultatoScala(false);
        carteSostituiteDaJoker.clear();

        if (carte.size() != 4) return risultato;

        List<Integer> posJoker = new ArrayList<>();     //salvo la posizione del joker nelle 4 carte della scala (es: 4-5-x-7 -> posJoker.get(0)=2)
        List<Integer> valori = new ArrayList<>();       //salvo i valori delle carte non joker
        List<Carta> carteNormali = new ArrayList<>();   //salvo le carte non joker
        List<String> semi = new ArrayList<>();          //salvo i semi delle carte non joker

        //prendo valori e semi delle carte non joker della sequenza 
        for (int i = 0; i < carte.size(); i++) {
            Carta c = carte.get(i);
            if (c.isJoker()) {
                posJoker.add(i);
            } else {
                int val = convertiValoreNumerico(c.getValore());
                if (val == -1) return new RisultatoScala(false);
                valori.add(val);
                semi.add(c.getSeme());
                carteNormali.add(c);
            }
        }

        int numJoker = contaJoker(carte);

        //verifico se tutti i semi sono uguali:
        //se è 0 significa che la lista SEMI è vuota -> 4 joker
        //se è 1 significa che la lista SEMI è composta da solo semi uguali esclusi i joker -> 0, 1, 2 o 3 joker (se ci sono 3 joker significa che ci sarà solo una carta, e quindi solo un seme nella lista)
        boolean tuttiSemiUguali = semi.stream().distinct().count() <= 1;

        if(tuttiSemiUguali){
            Map<Integer, Carta> jokerSub = null;
            boolean isSequenzaStandard = true;

            switch (numJoker) {
                case 0: //0 Joker: tutte le 4 carte non joker devono essere in sequenza e dello stesso seme
                    
                    //Integer (chiave): Rappresenta la posizione del Joker (es. 0 se è la prima carta, 1 se è la seconda, ecc.). Questo è utile per sapere dove si trovava il Joker nella sequenza originale
                    //Carta (valore): Rappresenta la carta che il Joker "copia" per completare la scala. Ad esempio, se la tua sequenza è [5 cuori, Joker, 7 cuori, 8 cuori], il Joker (che si trova in posizione 1) dovrà agire come un 6 di cuori. La mappa salverà la coppia (1, Carta(6, cuori))
                    jokerSub = new HashMap<>();

                    //controllo standard: sequenza 1-13 (A-2-3-4 fino a 10-J-Q-K)
                    isSequenzaStandard = true;
                    for (int i = 0; i < valori.size() - 1; i++) {
                        if (valori.get(i + 1) != valori.get(i) + 1) {
                            //se la condizione non è soddisfatta, la sequenza non è in ordine
                            isSequenzaStandard = false;
                            break;
                        }
                    }
                    if(isSequenzaStandard){
                        return new RisultatoScala(true, jokerSub);
                    }

                    //gestione del caso J-Q-K-A
                    if (valori.get(0) == 11 && valori.get(1) == 12 && valori.get(2) == 13 && valori.get(3) == 1) {
                        //la sequenza è Jack, Donna, Re e Asso -> accettata
                        return new RisultatoScala(true, jokerSub);
                    }
                    
                    //se non è né una sequenza standard né J-Q-K-A -> scala NON valida
                    feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                    return new RisultatoScala(false);

                case 1: //1 Joker: le 3 carte non joker devono essere in sequenza e dello stesso seme gestendo la presenza del joker
                    jokerSub = new HashMap<>();

                    //lo switch si basa sulla posizione del Joker nella lista originale di 4 carte
                    switch (posJoker.get(0)) {
                        case 0: //Joker in posizione 0 nella sequenza

                            //controllo sequenze: 1-13 (x-2-3-4 fino a x-J-Q-K) || (x-Q-K-A)
                            if((valori.get(1) == valori.get(0) + 1) && (valori.get(2) == valori.get(1) + 1)
                            ||  (valori.get(0) == 12) && (valori.get(1) == 13) && (valori.get(2) == 1)){
                                //la carta che il Joker sostituisce è il valore della prima carta non-joker - 1
                                int valoreSostituzione = valori.get(0) - 1;
                                Carta cartaSostituita = new Carta(convertiValoreTestuale(valoreSostituzione), semi.get(0));
                                
                                jokerSub.put(posJoker.get(0), cartaSostituita);

                                carteSostituiteDaJoker.add(cartaSostituita);
                                carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);

                                return new RisultatoScala(true, jokerSub);
                            } else {
                                //se non è né una sequenza standard né J-Joker-K-A -> scala NON valida
                                feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                                return new RisultatoScala(false);
                            }

                        case 1: //Joker in posizione 1 nella sequenza

                            //controllo sequenze: 1-13 (1-x-3-4 fino a 10-x-Q-K) || (J-x-K-A)
                            if((valori.get(1) == valori.get(0) + 2) && (valori.get(2) == valori.get(1) + 1)
                            ||  (valori.get(0) == 11) && (valori.get(1) == 13) && (valori.get(2) == 1)){
                                //la carta che il Joker sostituisce è il valore della prima carta non-joker + 1
                                int valoreSostituzione = valori.get(0) + 1;
                                Carta cartaSostituita = new Carta(convertiValoreTestuale(valoreSostituzione), semi.get(0));
                                
                                jokerSub.put(posJoker.get(0), cartaSostituita);

                                carteSostituiteDaJoker.add(cartaSostituita);
                                carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);

                                return new RisultatoScala(true, jokerSub);
                            } else {
                                //se non è né una sequenza standard né J-Joker-K-A -> scala NON valida
                                feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                                return new RisultatoScala(false);
                            }

                        case 2: //Joker in posizione 2 nella sequenza

                            //controllo sequenze: 1-13 (1-2-x-4 fino a 10-J-x-K) || (J-Q-x-A)
                            if((valori.get(1) == valori.get(0) + 1) && (valori.get(2) == valori.get(1) + 2)
                            ||  (valori.get(0) == 11) && (valori.get(1) == 12) && (valori.get(2) == 1)){
                                //la carta che il Joker sostituisce è il valore della prima carta non-joker + 2
                                int valoreSostituzione = valori.get(0) + 2;
                                Carta cartaSostituita = new Carta(convertiValoreTestuale(valoreSostituzione), semi.get(0));
                                
                                jokerSub.put(posJoker.get(0), cartaSostituita);

                                carteSostituiteDaJoker.add(cartaSostituita);
                                carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                                
                                return new RisultatoScala(true, jokerSub);
                            } else {
                                //se non è né una sequenza standard né J-Joker-K-A -> scala NON valida
                                feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                                return new RisultatoScala(false);
                            }

                        case 3: //Joker in posizione 3 nella sequenza
                            
                            //controllo sequenze: 1-13 (1-2-3-x fino a 10-J-Q-x) || (J-Q-K-x)
                            if((valori.get(1) == valori.get(0) + 1) && (valori.get(2) == valori.get(1) + 1)
                            ||  (valori.get(0) == 11) && (valori.get(1) == 12) && (valori.get(2) == 13)){
                                //la carta che il Joker sostituisce è il valore della prima carta non-joker + 3
                                int valoreSostituzione = 0;
                                if(valori.get(0)<11){
                                    valoreSostituzione = valori.get(0) + 3;
                                } else{
                                    valoreSostituzione = 1; //caso in cui il joker sostituisce un asso
                                }
                                Carta cartaSostituita = new Carta(convertiValoreTestuale(valoreSostituzione), semi.get(0));
                                
                                jokerSub.put(posJoker.get(0), cartaSostituita);

                                carteSostituiteDaJoker.add(cartaSostituita);
                                carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                                
                                return new RisultatoScala(true, jokerSub);
                            } else {
                                //se non è né una sequenza standard né J-Joker-K-A -> scala NON valida
                                feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                                return new RisultatoScala(false);
                            }
                    
                        default:
                            break;
                    }
                    
                    break;
                case 2: //2 Joker: le 2 carte non joker devono essere in sequenza e dello stesso seme gestendo la presenza dei joker
                    jokerSub = new HashMap<>();

                    //controllo sequenze: 1-13 (x-x-3-4 fino a x-x-Q-K) || (x-x-K-A)
                    if((posJoker.get(0) == 0) && (posJoker.get(1) == 1)){
                        if((valori.get(1) == valori.get(0) + 1) || ((valori.get(0) == 13) && (valori.get(1) == 1))){
                            int valoreSostituzione1 = valori.get(0) - 2;
                            int valoreSostituzione2 = valori.get(0) - 1;
                            //creo le due Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            //aggiungo entrambe alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }
                    //controllo sequenze: 1-13 (x-2-x-4 fino a x-J-x-K) || (x-Q-x-A)
                    else if((posJoker.get(0) == 0) && (posJoker.get(1) == 2)){
                        if((valori.get(1) == valori.get(0) + 2) || ((valori.get(0) == 12) && (valori.get(1) == 1))){
                            int valoreSostituzione1 = valori.get(0) - 1;
                            int valoreSostituzione2 = valori.get(0) + 1;
                            //creo le due Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            //aggiungo entrambe alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }
                    //controllo sequenze: 1-13 (x-2-3-x fino a x-J-Q-x) || (x-Q-K-x)
                    else if((posJoker.get(0) == 0) && (posJoker.get(1) == 3)){
                        if((valori.get(1) == valori.get(0) + 1) || ((valori.get(0) == 12) && (valori.get(1) == 13))){
                            int valoreSostituzione1 = valori.get(0) - 1;
                            int valoreSostituzione2 = 0;
                            if(valori.get(0)<12){
                                valoreSostituzione2 = valori.get(1) + 1;
                            }else{
                                valoreSostituzione2 = 1; //caso in cui il secondo joker sostituisce un asso
                            }
                            //creo le due Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            //aggiungo entrambe alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }
                    //controllo sequenze: 1-13 (1-x-x-4 fino a 10-x-x-K) || (J-x-x-A)
                    else if((posJoker.get(0) == 1) && (posJoker.get(1) == 2)){
                        if((valori.get(1) == valori.get(0) + 3) || ((valori.get(0) == 11) && (valori.get(1) == 1))){
                            int valoreSostituzione1 = valori.get(0) + 1;
                            int valoreSostituzione2 = valori.get(1) - 1;
                            //creo le due Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            //aggiungo entrambe alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }
                    //controllo sequenze: 1-13 (1-x-3-x fino a 10-x-Q-x) || (J-x-K-x)
                    else if((posJoker.get(0) == 1) && (posJoker.get(1) == 3)){
                        if((valori.get(1) == valori.get(0) + 2) || ((valori.get(0) == 11) && (valori.get(1) == 13))){
                            int valoreSostituzione1 = valori.get(0) + 1;
                            int valoreSostituzione2 = 0;
                            if(valori.get(0)<11){
                                valoreSostituzione2 = valori.get(1) + 1;
                            }else{
                                valoreSostituzione2 = 1; //caso in cui il secondo joker sostituisce un asso
                            }
                            //creo le due Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            //aggiungo entrambe alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }
                    //controllo sequenze: 1-13 (1-2-x-x fino a 10-J-x-x) || (J-Q-x-x)
                    else if((posJoker.get(0) == 2) && (posJoker.get(1) == 3)){
                        if((valori.get(1) == valori.get(0) + 1) || ((valori.get(0) == 11) && (valori.get(1) == 12))){
                            int valoreSostituzione1 = valori.get(1) + 1;
                            int valoreSostituzione2 = 0;
                            if(valori.get(0)<11){
                                valoreSostituzione2 = valori.get(1) + 2;
                            }else{
                                valoreSostituzione2 = 1; //caso in cui il secondo joker sostituisce un asso
                            }
                            //creo le due Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            //aggiungo entrambe alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }

                    break;
                case 3: //3 Joker: scala sempre valida in base alla posizione della carta non joker
                    jokerSub = new HashMap<>();

                    //controllo sequenze: 1-13 (x-x-x-4 fino a x-x-x-K) || (x-x-x-A)
                    if((posJoker.get(0) == 0) && (posJoker.get(1) == 1) && (posJoker.get(2) == 2)){
                        if((valori.get(0) >= 4) && (valori.get(0) <= 13)){
                            int valoreSostituzione1 = valori.get(0) - 3;
                            int valoreSostituzione2 = valori.get(0) - 2;
                            int valoreSostituzione3 = valori.get(0) - 1;
                            //creo le tre Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            Carta cartaSostituita3 = new Carta(convertiValoreTestuale(valoreSostituzione3), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            jokerSub.put(posJoker.get(2), cartaSostituita3);
                            //aggiungo le carte alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJoker.add(cartaSostituita3);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else if (valori.get(0) == 1) { //caso in cui l'asso è in ultima posizione
                            int valoreSostituzione1 = 11;
                            int valoreSostituzione2 = 12;
                            int valoreSostituzione3 = 13;
                            //creo le tre Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            Carta cartaSostituita3 = new Carta(convertiValoreTestuale(valoreSostituzione3), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            jokerSub.put(posJoker.get(2), cartaSostituita3);
                            //aggiungo le carte alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJoker.add(cartaSostituita3);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }
                    //controllo sequenze: 1-13 (x-x-3-x fino a x-x-Q-x) || (x-x-K-x)
                    if((posJoker.get(0) == 0) && (posJoker.get(1) == 1) && (posJoker.get(2) == 3)){
                        if((valori.get(0) >= 3) && (valori.get(0) <= 12)){
                            int valoreSostituzione1 = valori.get(0) - 2;
                            int valoreSostituzione2 = valori.get(0) - 1;
                            int valoreSostituzione3 = valori.get(0) + 1;
                            //creo le tre Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            Carta cartaSostituita3 = new Carta(convertiValoreTestuale(valoreSostituzione3), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            jokerSub.put(posJoker.get(2), cartaSostituita3);
                            //aggiungo le carte alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJoker.add(cartaSostituita3);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else if (valori.get(0) == 13) { //caso in cui il terzo joker sostituisce un asso
                            int valoreSostituzione1 = 11;
                            int valoreSostituzione2 = 12;
                            int valoreSostituzione3 = 1;
                            //creo le tre Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            Carta cartaSostituita3 = new Carta(convertiValoreTestuale(valoreSostituzione3), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            jokerSub.put(posJoker.get(2), cartaSostituita3);
                            //aggiungo le carte alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJoker.add(cartaSostituita3);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }
                    //controllo sequenze: 1-13 (x-2-x-x fino a x-J-x-x) || (x-Q-x-x)
                    if((posJoker.get(0) == 0) && (posJoker.get(1) == 2) && (posJoker.get(2) == 3)){
                        if((valori.get(0) >= 2) && (valori.get(0) <= 11)){
                            int valoreSostituzione1 = valori.get(0) - 1;
                            int valoreSostituzione2 = valori.get(0) + 1;
                            int valoreSostituzione3 = valori.get(0) + 2;
                            //creo le tre Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            Carta cartaSostituita3 = new Carta(convertiValoreTestuale(valoreSostituzione3), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            jokerSub.put(posJoker.get(2), cartaSostituita3);
                            //aggiungo le carte alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJoker.add(cartaSostituita3);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else if (valori.get(0) == 12) { //caso in cui il terzo joker sostituisce un asso
                            int valoreSostituzione1 = 11;
                            int valoreSostituzione2 = 13;
                            int valoreSostituzione3 = 1;
                            //creo le tre Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            Carta cartaSostituita3 = new Carta(convertiValoreTestuale(valoreSostituzione3), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            jokerSub.put(posJoker.get(2), cartaSostituita3);
                            //aggiungo le carte alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJoker.add(cartaSostituita3);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }
                    //controllo sequenze: 1-13 (1-x-x-x fino a 10-x-x-x) || (J-x-x-x)
                    if((posJoker.get(0) == 1) && (posJoker.get(1) == 2) && (posJoker.get(2) == 3)){
                        if((valori.get(0) >= 1) && (valori.get(0) <= 10)){
                            int valoreSostituzione1 = valori.get(0) + 1;
                            int valoreSostituzione2 = valori.get(0) + 2;
                            int valoreSostituzione3 = valori.get(0) + 3;
                            //creo le tre Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            Carta cartaSostituita3 = new Carta(convertiValoreTestuale(valoreSostituzione3), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            jokerSub.put(posJoker.get(2), cartaSostituita3);
                            //aggiungo le carte alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJoker.add(cartaSostituita3);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else if (valori.get(0) == 11) { //caso in cui il terzo joker sostituisce un asso
                            int valoreSostituzione1 = 12;
                            int valoreSostituzione2 = 13;
                            int valoreSostituzione3 = 1;
                            //creo le tre Carte
                            Carta cartaSostituita1 = new Carta(convertiValoreTestuale(valoreSostituzione1), semi.get(0));
                            Carta cartaSostituita2 = new Carta(convertiValoreTestuale(valoreSostituzione2), semi.get(0));
                            Carta cartaSostituita3 = new Carta(convertiValoreTestuale(valoreSostituzione3), semi.get(0));
                            //le inserisco nella mappa dei joker
                            jokerSub.put(posJoker.get(0), cartaSostituita1);
                            jokerSub.put(posJoker.get(1), cartaSostituita2);
                            jokerSub.put(posJoker.get(2), cartaSostituita3);
                            //aggiungo le carte alla lista globale
                            carteSostituiteDaJoker.add(cartaSostituita1);
                            carteSostituiteDaJoker.add(cartaSostituita2);
                            carteSostituiteDaJoker.add(cartaSostituita3);
                            carteSostituiteDaJokerConfermate.addAll(carteSostituiteDaJoker);
                            return new RisultatoScala(true, jokerSub);
                        } else {
                            //scala NON valida
                            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                            return new RisultatoScala(false);
                        }
                    }

                    break;
                case 4: //4 Joker: scala sempre valida (se la prima carta è al massimo un Jack)
                    Optional<List<Carta>> carteSostituiteOptional = showJokerScalaValSemePopup(currentTrisSelection); //viene scelto seme + valore della prima carta della scala col popup
                    if (carteSostituiteOptional.isPresent()) {
                        List<Carta> carteSostituite = carteSostituiteOptional.get();
                        jokerSub = new HashMap<>();
    
                        //mappo ogni joker alla carta sostituita scelta
                        for (int i = 0; i < carteSostituite.size(); i++) {
                            Carta cartaSostituta = carteSostituite.get(i);
                            jokerSub.put(i, cartaSostituta);

                            //aggiungo la carta sostitutiva alle liste globali
                            carteSostituiteDaJoker.add(cartaSostituta);
                            carteSostituiteDaJokerConfermate.add(cartaSostituta);
                        }
    
                        return new RisultatoScala(true, jokerSub);
                    } else {
                        //scala NON valida
                        feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
                        return new RisultatoScala(false);
                    }
            
                default:
                    break;
            }
        } else {
            //scala NON valida
            feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto");
            return new RisultatoScala(false);
        }

        //qui in realtà non ci arriverò mai in quanto all'interno dell'if o dell'else c'è un return
        feedbackLabel.setText("La scala non è valida o le carte non sono in ordine corretto.");
        return new RisultatoScala(false);
    }

    //aggiorno la mano del giocatore corrente
    private void aggiornaManoGUI() {
        //svuoto i contenitori e la mappa di selezione prima di ricrearli
        carteBox.getChildren().clear();
        selezioniGUI.clear();

        for (Carta carta : mano.getCarte()) {
            String fileName = carta.getImageFilename();
            String path = "/assets/decks/bycicle/" + fileName;
            Image img = new Image(getClass().getResourceAsStream(path));
            ImageView view = new ImageView(img);
            view.setFitWidth(100);
            view.setPreserveRatio(true);
            //associa l'oggetto Carta alla sua ImageView
            view.setUserData(carta);

            //inizializzo lo stato di selezione per questa ImageView
            selezioniGUI.put(view, false);

            // PRIORITÀ STILI:
            // 1. Se selezionata nel tris o nella scala corrente → oro
            // 2. Se già confermata (in carteSelezionatePerAperturaTotale) → verde e opaca
            // 3. Altrimenti, nessuno stile

            boolean isSelectedInCurrentSet = false;
            if (currentTrisSelection != null) {
                //controllo se questa specifica istanza di carta è nel set corrente
                isSelectedInCurrentSet = currentTrisSelection.stream()
                                                            .anyMatch(c -> c == carta);
            }
            if (!isSelectedInCurrentSet && currentScalaSelection != null) {
                isSelectedInCurrentSet = currentScalaSelection.stream()
                                                            .anyMatch(c -> c == carta);
            }

            boolean isConfirmed = false;
            if (carteSelezionatePerAperturaTotale != null) {
                //verifico se l'istanza ESATTA della carta è stata confermata
                for (Carta c : carteSelezionatePerAperturaTotale) {
                    if (c == carta) {
                        isConfirmed = true;
                        break;
                    }
                }
            }

            boolean isSelectedForAttack = false;
            if (!isSelectedInCurrentSet && cartaManoSelezionata != null) {
                //confronto l'istanza della carta corrente con quella selezionata per l'attacco
                if (carta == cartaManoSelezionata) {
                    isSelectedForAttack = true;
                }
            }
            
            if (isSelectedInCurrentSet || isSelectedForAttack) {
                //evidenzio in oro (selezione corrente)
                view.setStyle("-fx-effect: dropshadow(three-pass-box, gold, 12, 0.5, 0, 0);");
                selezioniGUI.put(view, true);
            } else if (isConfirmed) {
                //verde e opaca (già confermata)
                view.setStyle("-fx-effect: dropshadow(three-pass-box, green, 8, 0, 0, 0); -fx-opacity: 0.7;");
            } else {
                view.setStyle(""); //nessuno stile
            }

            //GESTIONE CLICK
            view.setOnMouseClicked(ev -> {
                if (inApertura) {

                    //verifico se l'istanza esatta della carta è già stata occupata così da non poterla più selezionare
                    boolean cartaGiaOccupata = false;
                    for (Carta c : carteSelezionatePerAperturaTotale) {
                        if (c == carta) { //confronto per istanza (riferimento in memoria)
                            cartaGiaOccupata = true;
                            break;
                        }
                    }
                    if (cartaGiaOccupata) {
                        return;
                    }

                    if(!haApertoRichiesta){ //apertura richiesta dal round non ancora effettuata (è false all'inizio del round)
                        //=== FASE TRIS ===
                        if(trisConfermatiCorrenti < trisPerAprire){
                            
                            //controlla se questa specifica ImageView è già selezionata
                            boolean isSelected = selezioniGUI.getOrDefault(view, false);

                            if (isSelected) {
                                //deseleziono la carta
                                currentTrisSelection.remove(carta); //rimuovo l'istanza specifica
                                selezioniGUI.put(view, false);
                                feedbackLabel.setText("");
                            } else if (currentTrisSelection.size() < 3) {
                                //seleziono la carta
                                currentTrisSelection.add(carta); //aggiungo l'istanza specifica
                                selezioniGUI.put(view, true);
                            } else {
                                feedbackLabel.setText("Hai già selezionato 3 carte per il tris corrente. Clicca CONFERMA TRIS " + (trisConfermatiCorrenti + 1) + " o deseleziona");
                            }

                            aggiornaManoGUI();
                            updateOpeningSlotsGUI();

                        //=== FASE SCALA ===
                        } else if (scaleConfermateCorrenti < scalePerAprire) {
                            
                            //controlla se questa specifica ImageView è già selezionata
                            boolean isSelected = selezioniGUI.getOrDefault(view, false);

                            if (isSelected) {
                                //seseleziono la carta
                                currentScalaSelection.remove(carta); //rimuovo l'istanza specifica
                                selezioniGUI.put(view, false);
                                feedbackLabel.setText("");
                            } else if (currentScalaSelection.size() < 4) {
                                //seleziono la carta
                                currentScalaSelection.add(carta); //aggiungo l'istanza specifica
                                selezioniGUI.put(view, true);
                            } else {
                                feedbackLabel.setText("Hai già selezionato 4 carte per la scala corrente. Clicca CONFERMA SCALA " + (scaleConfermateCorrenti + 1) + " o deseleziona");
                            }

                            aggiornaManoGUI();
                            updateOpeningSlotsGUI();
                        }
                    } else { //apertura richiesta dal round effettuata (true) e quindi fase apertura extra
                        //=== FASE TRIS EXTRA ===
                        if(trisExtra){
                            
                            //controlla se questa specifica ImageView è già selezionata
                            boolean isSelected = selezioniGUI.getOrDefault(view, false);

                            if (isSelected) {
                                //deseleziono la carta
                                currentTrisSelection.remove(carta); //rimuovo l'istanza specifica
                                selezioniGUI.put(view, false);
                                feedbackLabel.setText("");
                            } else if (currentTrisSelection.size() < 3) {
                                //seleziono la carta
                                currentTrisSelection.add(carta); //aggiungo l'istanza specifica
                                selezioniGUI.put(view, true);
                            } else {
                                feedbackLabel.setText("Hai già selezionato 3 carte per il tris extra. Clicca CONFERMA TRIS o deseleziona");
                            }

                            aggiornaManoGUI();
                            updateOpeningSlotsGUI();

                        //=== FASE SCALA EXTRA ===
                        } else if (scalaExtra) {
                            
                            //controlla se questa specifica ImageView è già selezionata
                            boolean isSelected = selezioniGUI.getOrDefault(view, false);

                            if (isSelected) {
                                //seseleziono la carta
                                currentScalaSelection.remove(carta); //rimuovo l'istanza specifica
                                selezioniGUI.put(view, false);
                                feedbackLabel.setText("");
                            } else if (currentScalaSelection.size() < 4) {
                                //seleziono la carta
                                currentScalaSelection.add(carta); //aggiungo l'istanza specifica
                                selezioniGUI.put(view, true);
                            } else {
                                feedbackLabel.setText("Hai già selezionato 4 carte per la scala extra. Clicca CONFERMA SCALA o deseleziona");
                            }

                            aggiornaManoGUI();
                            updateOpeningSlotsGUI();
                        }
                    }
                } else if (inScarto && haPescato && mioTurno) {
                    scartaCarta(carta);
                } else if (inAttacco) {
                    //recupero l'oggetto Carta dall'ImageView
                    Carta cartaCliccata = (Carta) view.getUserData();
                    //controllo se la carta cliccata è già la carta selezionata
                    if (cartaCliccata == cartaManoSelezionata) {
                        //se è la stessa la deseleziono e rimuovo l'evidenziazione dorata dall'ImageView cliccato
                        cartaManoSelezionata = null;                        
                        confermaAttaccaBtn.setDisable(true);
                    } else {
                        //salvo la nuova carta selezionata
                        cartaManoSelezionata = cartaCliccata;
                        out.println("DEBUG: ATTACCO CARTA SELEZIONATA MANO: " + cartaManoSelezionata);
                    }
                    
                    aggiornaManoGUI();

                    //abilito/disabilito il pulsante "CONFERMA ATTACCO"
                    if (cartaManoSelezionata != null && elementoTavoloSelezionato != null) {
                        confermaAttaccaBtn.setDisable(false);
                    } else {
                        confermaAttaccaBtn.setDisable(true);
                    }
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
        inAttacco = false;

        numTurno = 0;
        numTurnoAperturaRound = 0;
        numApertura = 0;
        
        trisConfermatiCorrenti = 0;
        scaleConfermateCorrenti = 0;
        trisExtra=false;
        scalaExtra=false;

        resetBtnAzioni();

        scartaBtn.setDisable(true);
        apriBtn.setDisable(true);
        attaccaBtn.setDisable(true);
        completaAperturaButton.setDisable(true);

        scrollPaneApertura.setVisible(false);
        scrollPaneApertura.setManaged(false);

        currentTrisSelection = new ArrayList<>();
        trisCompletiConfermati = new ArrayList<>();
        currentScalaSelection = new ArrayList<>();
        scaleCompleteConfermate = new ArrayList<>();
        carteSelezionatePerAperturaTotale = new ArrayList<>();

        carteRimanentiNelMazzo = 108;
        pilaScarti.clear();

        selezionatePerApertura.clear();

        cartaManoSelezionata = null;
        elementoTavoloSelezionato = null;
        jokerSulTavolo.clear();

        tavoloGioco.clear();
        aggiornaTavoloGUI();
    }

    //per aggiornare gli slot visivi nella fase di apertura
    private void updateOpeningSlotsGUI() {
        if(!haApertoRichiesta){ //apertura richiesta dal round non ancora effettuata
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
                    trisSlotViews[currentIndex][j].setStyle("-fx-effect: dropshadow(three-pass-box, gold, 12, 0.5, 0, 0);");
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
                    scalaSlotViews[currentScalaIndex][j].setStyle("-fx-effect: dropshadow(three-pass-box, gold, 12, 0.5, 0, 0);");
                }

                if (confermaScalaButtons[currentScalaIndex] != null) {
                    confermaScalaButtons[currentScalaIndex].setDisable(currentScalaSelection.size() != 4);
                }
            }
        } else { //apertura richiesta dal round effettuata, quindi fase extra
            if(trisExtra){ //FASE EXTRA TRIS 
                for (int j = 0; j < 3; j++) {
                    trisSlotViews[0][j].setImage(null);
                    trisSlotViews[0][j].setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-background-color: #f0f0f0;");
                }
                for (int j = 0; j < currentTrisSelection.size(); j++) {
                    Carta carta = currentTrisSelection.get(j);
                    String path = "/assets/decks/bycicle/" + carta.getImageFilename();
                    Image img = new Image(getClass().getResourceAsStream(path));
                    trisSlotViews[0][j].setImage(img);
                    trisSlotViews[0][j].setStyle("-fx-effect: dropshadow(three-pass-box, gold, 12, 0.5, 0, 0);");
                }
                if (confermaTrisButtons[0] != null) {
                    confermaTrisButtons[0].setDisable(currentTrisSelection.size() != 3);
                }
            } else if (scalaExtra) { //FASE EXTRA SCALA
                for (int j = 0; j < 4; j++) {
                    scalaSlotViews[0][j].setImage(null);
                    scalaSlotViews[0][j].setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-background-color: #f0f0f0;");
                }
                for (int j = 0; j < currentScalaSelection.size(); j++) {
                    Carta carta = currentScalaSelection.get(j);
                    String path = "/assets/decks/bycicle/" + carta.getImageFilename();
                    Image img = new Image(getClass().getResourceAsStream(path));
                    scalaSlotViews[0][j].setImage(img);
                    scalaSlotViews[0][j].setStyle("-fx-effect: dropshadow(three-pass-box, gold, 12, 0.5, 0, 0);");
                }
                if (confermaScalaButtons[0] != null) {
                    confermaScalaButtons[0].setDisable(currentScalaSelection.size() != 4);
                }
            }
        }
    }

    //metodo di supporto da implementare per convertire filename in Carta
    private Carta cartaFromFileName(String fileName) {
        fileName = fileName.replace(".jpg", "");
        if (fileName.equalsIgnoreCase("joker")) return new Carta("Joker", null);

        String[] parts = fileName.split("_");
        String valore = parts[0]; //es. "1", "10", "J"
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
                return "ROUND " + currentRound + " - 7 CARTE: 1 TRIS e 1 SCALA PER APRIRE";
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

    //creo lo slot per i tris e le scale dove andrà la copia della carta selezionata nella mano
    private StackPane  creaImgPlaceholderApertura(ImageView[][] imageViewOutput, int i, int j){
        ImageView slot = new ImageView();
        slot.setFitWidth(100);
        slot.setFitHeight(150);
        
        //sfondo visivo degli slot delle carte tramite Region
        Region background = new Region();
        background.setPrefSize(100, 150);
        background.setStyle(
            "-fx-background-color: #d9d9d9ff;" +
            "-fx-border-color: black;" +
            "-fx-border-width: 2;"
        );

        StackPane slotWrapper = new StackPane(background, slot);

        if (imageViewOutput != null) {
            imageViewOutput[i][j] = slot;
        }

        return slotWrapper;
    }

    //converto il valore della carta da stringa a int
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

    //converto il valore della carta da int a stringa
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

    //aggiorno il mazzo che si visualizza nella GUI
    private void aggiornaMazzoGrafico(int carteRimaste) {
        mazzoStack.getChildren().clear(); //svuoto il mazzo prima di ricostruirlo

        int maxCarteVisibili = Math.min(carteRimaste, 20); //creo un mazzo di 20 carte fittizie, se le carte rimanenti sono meno di 20 allora saranno il numero corretto
        for (int i = 0; i < maxCarteVisibili; i++) {
            ImageView dorso = new ImageView(new Image(getClass().getResourceAsStream("/assets/decks/bycicle/dorso-red.jpg")));
            dorso.setFitHeight(120);
            dorso.setPreserveRatio(true);
            dorso.setTranslateY(-i * 2); // o i * -1.5 per un impilamento più fitto
            dorso.setEffect(new DropShadow(3, Color.BLACK));
            mazzoStack.getChildren().add(dorso);
        }
    }

    //aggiorno la pila degli scarti che si visualizza nella GUI
    private void aggiornaPilaScartiGrafica(List<Carta> pilaScartiCorrente) {
        System.out.println("CARTE_PILA: " + pilaScartiCorrente.size());
        pilaScartiStack.getChildren().clear(); //svuoto la pila degli scarti prima di ricostruirla

        //pila vuota (dopo aver pescato l'ultima carta dalla pila degli scarti o all'inizio del gioco)
        if (pilaScartiCorrente.isEmpty()) {
            return;
        }

        int numCarteVisibili = Math.min(pilaScartiCorrente.size(), 20); //creo una pila fino a un max di 20 carte fittizie, di cui l'ultima visibile è quella in cima
        
        //calcolo l'indice di partenza per prendere le ultime 'numCarteVisibili' carte; serve se le carte sono + di 20 così che caricherò le prime 20 dalla cima
        int startIndex = pilaScartiCorrente.size() - numCarteVisibili;

        //itero TUTTE le carte che devono essere visualizzate; l'ultima iterazione del ciclo aggiungerà la carta in cima alla pila
        for (int i = 0; i < numCarteVisibili; i++) {
            Carta currentCard = pilaScartiCorrente.get(startIndex + i);
            ImageView cardImg = new ImageView(new Image(getClass().getResourceAsStream("/assets/decks/bycicle/" + currentCard.getImageFilename()))); 
            cardImg.setFitHeight(120);
            cardImg.setPreserveRatio(true);
            //l'offset Y dipende dalla sua posizione nella pila visuale (0 per la più bassa, -2 per la successiva, ecc.)
            cardImg.setTranslateY(-(i * 2)); 
            cardImg.setEffect(new DropShadow(3, Color.BLACK));
            pilaScartiStack.getChildren().add(cardImg);
        }
    }

    private void resetBtnAzioni(){
        //mostro il tasto PESCA
        pescaBtn.setDisable(true);
        pescaBtn.setVisible(true);  
        pescaBtn.setManaged(true);
        //nascondo i tasti pescaMazzo e pescaScarti
        pescaMazzoBtn.setDisable(true);
        pescaMazzoBtn.setVisible(false);
        pescaMazzoBtn.setManaged(false);
        pescaScartiBtn.setDisable(true);
        pescaScartiBtn.setVisible(false);
        pescaScartiBtn.setManaged(false);
    }

    //per creaare il badge rotondo "TOCCA A TE"
    public StackPane creaBadgeTurno(String testo) {
        Circle cerchio = new Circle(40); //raggio = 40px, quindi diametro 80px
        cerchio.setFill(Color.RED);

        Text label = new Text(testo);
        label.setFill(Color.WHITE);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        label.setTextAlignment(TextAlignment.CENTER);

        StackPane badge = new StackPane();
        badge.getChildren().addAll(cerchio, label);

        //posizionamento opzionale se serve overlay in assoluto
        badge.setTranslateX(10); // modifica in base a dove vuoi posizionarlo
        badge.setTranslateY(10);

        return badge;
    }

    //per contare quanti joker sono presenti all'interno di un tris o di una scala
    public int contaJoker(List<Carta> carteTrisScala){
        int numJoker=0;

        for(Carta carta : carteTrisScala){
            if(carta.isJoker()){
                numJoker++;
            }
        }

        return numJoker;
    }

    //per aprire il box dove comparirà la richiesta di tormento
    public void visualizzaTormento(Boolean inTormento){
        if(!inTormento){ //inTormento = false, quindi non siamo in tormento e ci entriamo
            tormentoBox.setVisible(true);
            tormentoBox.setManaged(true);
            scrollPaneApertura.setVisible(false);
            scrollPaneApertura.setManaged(false);
            scrollPaneTavolo.setVisible(false);
            scrollPaneTavolo.setManaged(false);
            
            avviaCountdown(tormentoTimerLabel);
        } else {        //inTormento = true, quindi siamo in tormento e ci usciamo
            tormentoBox.setVisible(false);
            tormentoBox.setManaged(false);
        }
    }

    /*aggiorna il tavolo con 
        - tutte le aperture effettuate da tutti i giocatori
        - i placeholder delle carte dove ci può essere un potenziale attacco
        - i valori delle carte sopra i joker
    */
    @SuppressWarnings("unchecked") //questa annotazione serve sui metodi che contiengono un cast. Dice al compilatore che sono consapevole del potenziale rischio (cast non sicuro perché magari non gli passo l'oggeto specifico richiesto) ma che sono certo che il cast funzionerà (sono certo al 100% dell'oggeto che casto)
    private void aggiornaTavoloGUI() {
        //cancello il contenuto attuale del tavolo per evitare duplicati
        tavoloGiocoBox.getChildren().clear(); 

        //riscrivo ogni giocatore e le sue aperture "entrando" nella mappa e separando ogni giocatore (che ha una coppia: id e lista di lista di carte)
        for (Map.Entry<Integer, List<List<Carta>>> entry : tavoloGioco.entrySet()) {
            int idGiocatore = entry.getKey();
            List<List<Carta>> apertureGiocatore = entry.getValue();

            VBox giocatoreBox = new VBox(5);
            giocatoreBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.5); -fx-padding: 10; -fx-background-radius: 50;");
            giocatoreBox.setAlignment(Pos.CENTER);
            giocatoreBox.setPadding(new Insets(10, 10, 10, 10));

            //aggiungo la Label con l'ID del giocatore
            Label labelGiocatore = new Label("Aperture Player " + idGiocatore);
            labelGiocatore.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: black;");
            giocatoreBox.getChildren().add(labelGiocatore);

            //creo un HBox per visualizzare le aperture
            FlowPane apertureBox = new FlowPane(15, 15); //spazio orizzontale e verticale tra gli elementi
            apertureBox.setAlignment(Pos.CENTER);
            apertureBox.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            
            for (List<Carta> apertura : apertureGiocatore) {
                HBox trisScalaBox = new HBox(5);
                trisScalaBox.setStyle("-fx-border-color: black; -fx-border-width: 2; -fx-padding: 10;");

                if(inAttacco){
                    //aggiungo il placeholder all'inizio se è una scala a meno che la prima carta non è un asso o un joker che sostituisce un asso
                    if(isScala(apertura)){ //isScala ritorna true se la sequenza di carte si tratta effettivamente di una scala
                        if((convertiValoreNumerico(apertura.get(0).getValore()) == 1)
                            || (apertura.get(0).isJoker() && convertiValoreNumerico(apertura.get(1).getValore()) == 2)
                            || (apertura.get(0).isJoker() && apertura.get(1).isJoker() && convertiValoreNumerico(apertura.get(2).getValore()) == 3)
                            || (apertura.get(0).isJoker() && apertura.get(1).isJoker() && apertura.get(2).isJoker() && convertiValoreNumerico(apertura.get(3).getValore()) == 4)
                            || (apertura.get(0).isJoker() && apertura.get(1).isJoker() && apertura.get(2).isJoker() && apertura.get(3).isJoker() && convertiValoreNumerico(apertura.get(4).getValore()) == 5))
                            {
                            //nulla
                        } else {
                            ImageView placeholderView = new ImageView(new Image(getClass().getResourceAsStream("/assets/decks/bycicle/attack.jpg")));
                            placeholderView.setFitWidth(100);
                            placeholderView.setPreserveRatio(true);
                            placeholderView.setUserData(apertura);

                            //Evidenziazione: applico lo stile dorato solo se questo placeholder è selezionato
                            if (elementoTavoloSelezionato != null && elementoTavoloSelezionato.getAperturaAssociata() == apertura && elementoTavoloSelezionato.getCartaCliccata() == null && "sx".equals(elementoTavoloSelezionato.getPosizioneAttacco())) {
                                placeholderView.setStyle("-fx-effect: dropshadow(three-pass-box, gold, 12, 0.5, 0, 0);");
                            } else {
                                placeholderView.setStyle("-fx-opacity: 0.5;"); //semi trasparente
                            }

                            //logica del click
                            placeholderView.setOnMouseClicked(event -> {
                                List<Carta> aperturaCliccata = (List<Carta>) placeholderView.getUserData();
                                if (elementoTavoloSelezionato != null && elementoTavoloSelezionato.getAperturaAssociata() == aperturaCliccata && elementoTavoloSelezionato.getCartaCliccata() == null) {
                                    elementoTavoloSelezionato = null;
                                } else {
                                    elementoTavoloSelezionato = new SelezioneTavolo(aperturaCliccata, null, idGiocatore, "sx");
                                    out.println("DEBUG: ATTACCO CARTA SELEZIONATA TAVOLO: " + elementoTavoloSelezionato);
                                }
                                aggiornaTavoloGUI();
                                //abilito/disabilito il pulsante "CONFERMA ATTACCO"
                                if (cartaManoSelezionata != null && elementoTavoloSelezionato != null) {
                                    confermaAttaccaBtn.setDisable(false);
                                } else {
                                    confermaAttaccaBtn.setDisable(true);
                                }
                            });

                            trisScalaBox.getChildren().add(placeholderView);
                        }
                    }
                }
                
                //aggiungo tutte le carte dell'apertura
                for (Carta c : apertura) {
                    ImageView cardView = new ImageView(new Image(getClass().getResourceAsStream("/assets/decks/bycicle/" + c.getImageFilename())));
                    cardView.setFitWidth(100);
                    cardView.setPreserveRatio(true);
                    cardView.setUserData(c);

                    Node nodoFinale = cardView; //di default il nodo è solo la carta

                    //se la carta è un Joker, cerco la carta che sta sostituendo in jokerTotaliSulTavolo
                    if (c.isJoker()) {
                        StackPane jokerPane = new StackPane(cardView);

                        Map<Integer, List<Carta>> apertureJoker = jokerTotaliSulTavolo.get(idGiocatore);
                        if (apertureJoker != null) {
                            int indiceApertura = trovaIndiceApertura(apertureGiocatore, apertura);
                            List<Carta> sostituti = apertureJoker.get(indiceApertura);

                            if (sostituti != null && !sostituti.isEmpty()) {
                                //trovo l'indice del joker corrente nell'apertura
                                int jokerIndex = (int) apertura.stream()
                                        .filter(x -> x.isJoker())
                                        .toList()
                                        .indexOf(c);
                                
                                if (jokerIndex >= 0 && jokerIndex < sostituti.size()) {
                                    Carta sost = sostituti.get(jokerIndex);
                                    String overlayPath = null;

                                    if (!isScala(apertura) && apertura.stream().allMatch(x -> x.getValore().equals(sost.getValore()) || x.isJoker())) { 
                                        // --- TRIS -> overlay con seme ---
                                        String semeOverlay = switch (sost.getSeme()) {
                                            case "H" -> "hearts.jpg";
                                            case "D" -> "diamonds.jpg";
                                            case "S" -> "spades.jpg";
                                            case "C" -> "clubs.jpg";
                                            default -> null;
                                        };
                                        if (semeOverlay != null) {
                                            overlayPath = "/assets/decks/bycicle/valori_semi/" + semeOverlay;
                                        }
                                    } else {
                                        // --- SCALA -> overlay con valore ---
                                        String colore = (sost.getSeme().equals("S") || sost.getSeme().equals("C")) ? "black" : "red";
                                        overlayPath = "/assets/decks/bycicle/valori_semi/" + sost.getValore() + "_" + colore + ".jpg";
                                    }

                                    if (overlayPath != null) {
                                        ImageView overlay = new ImageView(new Image(getClass().getResourceAsStream(overlayPath)));
                                        overlay.setFitWidth(45);   //quadrato fisso
                                        overlay.setFitHeight(45);
                                        overlay.setPreserveRatio(false);
                                        StackPane.setAlignment(overlay, Pos.TOP_RIGHT); //posizione alto dx
                                        jokerPane.getChildren().add(overlay);
                                    }
                                }
                            }
                        }

                        nodoFinale = jokerPane; // il nodo finale da aggiungere non è più solo cardView ma lo stack con overlay
                    }

                    //aggiungo il gestore di click solo se la carta è un Joker
                    if (c.isJoker() && inAttacco) {
                        //evidenziazione del Joker
                        if (elementoTavoloSelezionato != null && elementoTavoloSelezionato.getCartaCliccata() != null && elementoTavoloSelezionato.getCartaCliccata().equals(c)) {
                            cardView.setStyle("-fx-effect: dropshadow(three-pass-box, gold, 12, 0.5, 0, 0);");
                        } else {
                            cardView.setStyle("");
                        }

                        //logica del click per il Joker
                        cardView.setOnMouseClicked(event -> {
                            Carta cartaCliccata = (Carta) cardView.getUserData();
                            List<Carta> aperturaAssociata = trovaAperturaPerJoker(cartaCliccata); 
                            if (elementoTavoloSelezionato != null && elementoTavoloSelezionato.getCartaCliccata() != null && elementoTavoloSelezionato.getCartaCliccata().equals(cartaCliccata)) {
                                elementoTavoloSelezionato = null;
                            } else {
                                elementoTavoloSelezionato = new SelezioneTavolo(aperturaAssociata, cartaCliccata, idGiocatore, "joker");
                                out.println("DEBUG: ATTACCO CARTA SELEZIONATA TAVOLO: " + elementoTavoloSelezionato);
                            }
                            aggiornaTavoloGUI();
                            //abilito/disabilito il pulsante "CONFERMA ATTACCO"
                            if (cartaManoSelezionata != null && elementoTavoloSelezionato != null) {
                                confermaAttaccaBtn.setDisable(false);
                            } else {
                                confermaAttaccaBtn.setDisable(true);
                            }
                        });
                    }
                    //trisScalaBox.getChildren().add(cardView);
                    trisScalaBox.getChildren().add(nodoFinale);
                }

                //aggiungo il placeholder alla fine sia che sia una scala sia che sia un tris a meno che
                // 1) la scala termina con un asso (o joker che fa da asso)
                // 2) il tris è completo -> tutte le 8 carte attaccate
                int ultimaPosScala = apertura.size() - 1;
                if(inAttacco){
                    if(isScala(apertura)){ //SCALA
                        if((convertiValoreNumerico(apertura.get(ultimaPosScala).getValore()) == 1)
                            || (apertura.get(ultimaPosScala).isJoker() && convertiValoreNumerico(apertura.get(ultimaPosScala - 1).getValore()) == 13)
                            || (apertura.get(ultimaPosScala).isJoker() && apertura.get(ultimaPosScala - 1).isJoker() && convertiValoreNumerico(apertura.get(ultimaPosScala - 2).getValore()) == 12)
                            || (apertura.get(ultimaPosScala).isJoker() && apertura.get(ultimaPosScala - 1).isJoker() && apertura.get(ultimaPosScala - 2).isJoker() && convertiValoreNumerico(apertura.get(3).getValore()) == 11)
                            || (apertura.get(ultimaPosScala).isJoker() && apertura.get(ultimaPosScala - 1).isJoker() && apertura.get(ultimaPosScala - 2).isJoker() && apertura.get(ultimaPosScala - 3).isJoker() && convertiValoreNumerico(apertura.get(4).getValore()) == 10))
                            {
                            //nulla
                        } else {
                            ImageView placeholderView = new ImageView(new Image(getClass().getResourceAsStream("/assets/decks/bycicle/attack.jpg")));
                            placeholderView.setFitWidth(100);
                            placeholderView.setPreserveRatio(true);
                            placeholderView.setUserData(apertura);

                            //Evidenziazione: applico lo stile dorato solo se questo placeholder è selezionato
                            if (elementoTavoloSelezionato != null && elementoTavoloSelezionato.getAperturaAssociata() == apertura && elementoTavoloSelezionato.getCartaCliccata() == null && "dx".equals(elementoTavoloSelezionato.getPosizioneAttacco())) {
                                placeholderView.setStyle("-fx-effect: dropshadow(three-pass-box, gold, 12, 0.5, 0, 0);");
                            } else {
                                placeholderView.setStyle("-fx-opacity: 0.5;");
                            }

                            //logica del click
                            placeholderView.setOnMouseClicked(event -> {
                                List<Carta> aperturaCliccata = (List<Carta>) placeholderView.getUserData();
                                if (elementoTavoloSelezionato != null && elementoTavoloSelezionato.getAperturaAssociata() == aperturaCliccata && elementoTavoloSelezionato.getCartaCliccata() == null) {
                                    elementoTavoloSelezionato = null;
                                } else {
                                    elementoTavoloSelezionato = new SelezioneTavolo(aperturaCliccata, null, idGiocatore, "dx");
                                    out.println("DEBUG: ATTACCO CARTA SELEZIONATA TAVOLO: " + elementoTavoloSelezionato);
                                }
                                aggiornaTavoloGUI();
                                //abilito/disabilito il pulsante "CONFERMA ATTACCO"
                                if (cartaManoSelezionata != null && elementoTavoloSelezionato != null) {
                                    confermaAttaccaBtn.setDisable(false);
                                } else {
                                    confermaAttaccaBtn.setDisable(true);
                                }
                            });

                            trisScalaBox.getChildren().add(placeholderView);
                        }
                    } else { //TRIS (può avere fino a 8 carte dello stesso valore)
                        if(apertura.size() == 8){
                            //nulla
                        } else {
                            ImageView placeholderView = new ImageView(new Image(getClass().getResourceAsStream("/assets/decks/bycicle/attack.jpg")));
                            placeholderView.setFitWidth(100);
                            placeholderView.setPreserveRatio(true);
                            placeholderView.setUserData(apertura);

                            //Evidenziazione: applico lo stile dorato solo se questo placeholder è selezionato
                            if (elementoTavoloSelezionato != null && elementoTavoloSelezionato.getAperturaAssociata() == apertura && elementoTavoloSelezionato.getCartaCliccata() == null) {
                                placeholderView.setStyle("-fx-effect: dropshadow(three-pass-box, gold, 12, 0.5, 0, 0);");
                            } else {
                                placeholderView.setStyle("-fx-opacity: 0.5;");
                            }

                            //logica del click
                            placeholderView.setOnMouseClicked(event -> {
                                List<Carta> aperturaCliccata = (List<Carta>) placeholderView.getUserData();
                                if (elementoTavoloSelezionato != null && elementoTavoloSelezionato.getAperturaAssociata() == aperturaCliccata && elementoTavoloSelezionato.getCartaCliccata() == null) {
                                    elementoTavoloSelezionato = null;
                                } else {
                                    elementoTavoloSelezionato = new SelezioneTavolo(aperturaCliccata, null, idGiocatore, "fine");
                                    out.println("DEBUG: ATTACCO CARTA SELEZIONATA TAVOLO: " + elementoTavoloSelezionato);
                                }
                                aggiornaTavoloGUI();
                                //abilito/disabilito il pulsante "CONFERMA ATTACCO"
                                if (cartaManoSelezionata != null && elementoTavoloSelezionato != null) {
                                    confermaAttaccaBtn.setDisable(false);
                                } else {
                                    confermaAttaccaBtn.setDisable(true);
                                }
                            });

                            trisScalaBox.getChildren().add(placeholderView);
                        }
                    }
                }

                apertureBox.getChildren().add(trisScalaBox);
            }
            giocatoreBox.getChildren().add(apertureBox);
            tavoloGiocoBox.getChildren().add(giocatoreBox);
        }
    }

    //serve a mappare la List<Carta> di tavoloGioco all’indice corrispondente nella mappa jokerTotaliSulTavolo
    private int trovaIndiceApertura(List<List<Carta>> apertureGiocatore, List<Carta> apertura){
        return apertureGiocatore.indexOf(apertura);
    }

    /**
     * Trova l'apertura a cui appartiene un Joker cliccato in fase di attacco per poterlo sostituire con quella in mano
     * Usa i dati memorizzati nella mappa dei Joker sostituiti
     *
     * @param cartaJoker Il Joker cliccato
     * @return La lista di carte (l'apertura) a cui appartiene il Joker, o null se non trovata
     */
    private List<Carta> trovaAperturaPerJoker(Carta cartaJoker) {
        //scorro ogni giocatore e le sue aperture sul tavolo
        for (Map.Entry<Integer, List<List<Carta>>> entry : tavoloGioco.entrySet()) {
            List<List<Carta>> apertureGiocatore = entry.getValue();
            //scorro ogni apertura per trovare il Joker
            for (List<Carta> apertura : apertureGiocatore) {
                if (apertura.contains(cartaJoker)) {
                    return apertura;
                }
            }
        }
        return null;
    }

    //per verificare se un attacco va a buon fine o meno
    private boolean verificaAttacco(Carta cartaManoSelezionata, SelezioneTavolo elementoTavoloSelezionato){

        if (mano.getCarte().size() == 1){
            feedbackLabel.setText("Scambio non permesso. Non si può finire le carte attaccando");
            return false;
        }
        
        List<Carta> apertura = elementoTavoloSelezionato.getAperturaAssociata();
        int idGiocatore = elementoTavoloSelezionato.getIdGiocatore();

        // --- SCAMBIO CARTA/JOKER ---
        if (elementoTavoloSelezionato.getCartaCliccata() != null && elementoTavoloSelezionato.getCartaCliccata().isJoker()) {
            System.out.println("DEBUG: SWAP");
            Carta jokerTavolo = elementoTavoloSelezionato.getCartaCliccata();

            //recupero la mappa sostituzione di un giocatore
            Map<Integer, List<Carta>> apertureJokerGiocatore = jokerTotaliSulTavolo.get(idGiocatore);

            if (apertureJokerGiocatore != null) {
                //trovo l'indice dell'apertura corrente
                int aperturaIndex = trovaIndiceApertura(tavoloGioco.get(idGiocatore), apertura);

                if (aperturaIndex != -1) {
                    //recupero la lista delle carte sostituite per quell'apertura
                    List<Carta> sostituti = apertureJokerGiocatore.get(aperturaIndex);

                    if (sostituti != null  && !sostituti.isEmpty()) {

                        if (sostituti.contains(cartaManoSelezionata)) {
                            //la carta selezionata in mano è un sostituto valido
                            swapCartaJoker(cartaManoSelezionata, jokerTavolo, apertura, idGiocatore);
                            feedbackLabel.setText("Scambio avvenuto con successo");
                            return true;
                        } else {
                            //la carta non corrisponde al sostituto preciso
                            feedbackLabel.setText("Scambio non permesso. Prova con altre carte o fai un'altra azione");
                            return false;
                        }
                    }
                }
            }

            //se arrivo qui lo scambio non è valido
            return false;

        // --- ATTACCO SUL TAVOLO ---
        } else {
            System.out.println("DEBUG: ATTACCO SUL TAVOLO");

            //attacco su SCALA
            if (isScala(apertura)){
                System.out.println("DEBUG: ATTACCO SU SCALA");
                String posPlaceholder = elementoTavoloSelezionato.getPosizioneAttacco();
                if (cartaManoSelezionata.isJoker()){ //un joker può sempre essere attaccato, ma bisogna assegnare i giusti valori al sostituto
                    //ATTACCO JOKER
                    System.out.println("DEBUG: ATTACCO JOKER SU SCALA");
                    
                    //se sto attaccando un joker significa che al massimo ci sono 3 joker nella scala e di conseguenza ci sarà almeno una carta nota, e quindi non serve un popup
                    
                    //prendo il seme della scala
                    String semeScala = "";
                    for(Carta c : apertura){
                        if (!c.isJoker() ){
                            semeScala = c.getSeme();
                            break;
                        }
                    }

                    //trovo prima (e magari anche unica) carta nota e la sua poszione nella scala per determinare il valore del joker
                    Carta primaCartaNota = null;
                    int posCartaNota = -1;
                    for (int i=0; i<apertura.size(); i++) {
                        if (!apertura.get(i).isJoker()) {
                            if (primaCartaNota == null) {
                                primaCartaNota = apertura.get(i);
                                posCartaNota = i;
                            }
                        }
                    }

                    /*
                    costruisco la carta sostituto del joker
                    es: X_J-J-J-6-7_X  (la scala va dal primo J al 7)
                        *se attacco il nuovo J a sx:
                           - cerco il 6 (la prima carta nota, ce ne sarà sempre almeno una, e potenzialmente anche unica)
                           - mi salvo la sua posizione nella scala
                           - il valore del sostituto è valore_carta_nota - poszione_carta_nota - 1
                        *se attacco il nuovo J a dx:
                           - cerco il 6 (la prima carta nota, ce ne sarà sempre almeno una, e potenzialmente anche unica)
                           - mi salvo la sua posizione nella scala
                           - il valore del sostituto è valore_carta_nota + (size_apertura - 1 - pos_carta_nota) + 1
                    */
                    Optional<Carta> sostituto = null;
                    int valoreCorrente = Integer.parseInt(primaCartaNota.getValore());
                    String valoreSostitutoTestuale;

                    if (posPlaceholder.equals("sx")) {
                        int valoreSostituto = valoreCorrente - posCartaNota - 1;

                        if (valoreSostituto == 1) {
                            valoreSostitutoTestuale = "1";
                        } else {
                            valoreSostitutoTestuale = convertiValoreTestuale(valoreSostituto);
                        }
                        sostituto = Optional.of(new Carta(convertiValoreTestuale(valoreSostituto), semeScala));
                    } else {
                        int valoreSostituto = valoreCorrente + apertura.size() - posCartaNota;

                        if (valoreSostituto == 14) {
                            valoreSostitutoTestuale = "1";
                        } else {
                            valoreSostitutoTestuale = convertiValoreTestuale(valoreSostituto);
                        }
                        sostituto = Optional.of(new Carta(convertiValoreTestuale(valoreSostituto), semeScala));
                    }
                    
                    //joker attaccato sempre concesso
                    attaccaCartaTavolo(cartaManoSelezionata, sostituto, apertura, posPlaceholder, idGiocatore);
                    feedbackLabel.setText("Attacco avvenuto con successo");
                    return true;

                } else {
                    //ATTACCO CARTA NOTA
                    System.out.println("DEBUG: ATTACCO CARTA NOTA SU SCALA");
                    if (apertura.size() == contaJoker(apertura)){ //scala interamente composta da joker
                        
                        //recupero la mappa sostituzione di un giocatore
                        Map<Integer, List<Carta>> apertureJokerGiocatore = jokerTotaliSulTavolo.get(idGiocatore);

                        if (apertureJokerGiocatore != null) {
                            //trovo l'indice dell'apertura corrente
                            int aperturaIndex = trovaIndiceApertura(tavoloGioco.get(idGiocatore), apertura);

                            if (aperturaIndex != -1) {
                                //recupero la lista delle carte sostituite per quell'apertura
                                List<Carta> sostituti = apertureJokerGiocatore.get(aperturaIndex);

                                if (sostituti != null  && !sostituti.isEmpty()) {
                                    //prendo il seme della scala
                                    String semeScala = sostituti.get(0).getSeme();

                                    //trovo prima e ultima carta dei sostituti per controllare il valore e il seme con quella che sto attaccando
                                    Carta primoSostituto = sostituti.get(0);
                                    Carta ultimoSostituto = sostituti.get(sostituti.size() - 1);

                                    //controllo se l'attacco è compatibile
                                    if (posPlaceholder.equals("sx")) {
                                        //ATTACCO A SX
                                        int valoreAttacco = Integer.parseInt(primoSostituto.getValore()) - 1;

                                        //controllo per l'Asso (gestendo sia "A" che "1")
                                        boolean isAssoMatch = cartaManoSelezionata.getValore().equals("A") || cartaManoSelezionata.getValore().equals("1");

                                        if ((cartaManoSelezionata.getValore().equals(convertiValoreTestuale(valoreAttacco)) &&
                                            cartaManoSelezionata.getSeme().equals(semeScala))
                                            || (valoreAttacco == 1 && isAssoMatch && cartaManoSelezionata.getSeme().equals(semeScala))){
                                                //match esatto: l'attacco è permesso
                                                attaccaCartaTavolo(cartaManoSelezionata, Optional.empty(), apertura, "sx", idGiocatore);
                                                feedbackLabel.setText("Attacco avvenuto con successo");
                                                return true;
                                        } else {
                                            //la carta non corrisponde al placeholder preciso
                                            feedbackLabel.setText("Attacco non permesso. Prova con altre carte o fai un'altra azione");
                                            return false;
                                        }
                                    } else if (posPlaceholder.equals("dx")){
                                        //ATTACCO A DX
                                        int valoreAttacco = Integer.parseInt(ultimoSostituto.getValore()) + 1;

                                        //controllo per l'Asso (gestendo sia "A" che "1")
                                        boolean isAssoMatch = cartaManoSelezionata.getValore().equals("A") || cartaManoSelezionata.getValore().equals("1");

                                        if ((cartaManoSelezionata.getValore().equals(convertiValoreTestuale(valoreAttacco)) &&
                                            cartaManoSelezionata.getSeme().equals(semeScala))
                                            || (valoreAttacco == 14 && isAssoMatch && cartaManoSelezionata.getSeme().equals(semeScala))){
                                                //match esatto: l'attacco è permesso
                                                attaccaCartaTavolo(cartaManoSelezionata, Optional.empty(), apertura, "dx", idGiocatore);
                                                feedbackLabel.setText("Attacco avvenuto con successo");
                                                System.out.println("DEBUG: ATTACCO CARTA NOTA A DX SU SCALA - " + valoreAttacco);
                                                return true;
                                        } else {
                                            //la carta non corrisponde al placeholder preciso
                                            feedbackLabel.setText("Attacco non permesso. Prova con altre carte o fai un'altra azione");
                                            return false;
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        //prendo il seme della scala (ci sarà per forza una carta nota)
                        String semeScala = "";
                        for(Carta c : apertura){
                            if (!c.isJoker() ){
                                semeScala = c.getSeme();
                                break;
                            }
                        }

                        //trovo prima (e magari anche unica) carta nota e la sua poszione nella scala per determinare il valore del joker
                        Carta primaCartaNota = null;
                        int posCartaNota = -1;
                        for (int i=0; i<apertura.size(); i++) {
                            if (!apertura.get(i).isJoker()) {
                                if (primaCartaNota == null) {
                                    primaCartaNota = apertura.get(i);
                                    posCartaNota = i;
                                }
                            }
                        }

                        //controllo se l'attacco è compatibile
                        if (posPlaceholder.equals("sx")) {
                            //ATTACCO A SX
                            int valoreCorrente = Integer.parseInt(primaCartaNota.getValore());
                            int valoreSostituto = valoreCorrente - posCartaNota - 1;

                            //controllo per l'Asso (gestendo sia "A" che "1")
                            boolean isAssoMatch = cartaManoSelezionata.getValore().equals("A") || cartaManoSelezionata.getValore().equals("1");

                            if ((cartaManoSelezionata.getValore().equals(convertiValoreTestuale(valoreSostituto)) &&
                                cartaManoSelezionata.getSeme().equals(semeScala)) 
                                || (valoreSostituto == 1 && isAssoMatch && cartaManoSelezionata.getSeme().equals(semeScala))){
                                    //match esatto: l'attacco è permesso
                                    attaccaCartaTavolo(cartaManoSelezionata, Optional.empty(), apertura, "sx", idGiocatore);
                                    feedbackLabel.setText("Attacco avvenuto con successo");
                                    return true;
                            } else {
                                //la carta non corrisponde al placeholder preciso
                                feedbackLabel.setText("Attacco non permesso. Prova con altre carte o fai un'altra azione");
                                return false;
                            }
                        } else if (posPlaceholder.equals("dx")){
                            //ATTACCO A DX
                            int valoreCorrente = Integer.parseInt(primaCartaNota.getValore());
                            int valoreSostituto = valoreCorrente + apertura.size() - posCartaNota;
                            
                            //controllo per l'Asso (gestendo sia "A" che "1")
                            boolean isAssoMatch = cartaManoSelezionata.getValore().equals("A") || cartaManoSelezionata.getValore().equals("1");

                            if ((cartaManoSelezionata.getValore().equals(convertiValoreTestuale(valoreSostituto)) &&
                                cartaManoSelezionata.getSeme().equals(semeScala)) 
                                || (valoreSostituto == 14 && isAssoMatch && cartaManoSelezionata.getSeme().equals(semeScala))){
                                    //match esatto: l'attacco è permesso
                                    attaccaCartaTavolo(cartaManoSelezionata, Optional.empty(), apertura, "dx", idGiocatore);
                                    feedbackLabel.setText("Attacco avvenuto con successo");
                                    return true;
                            } else {
                                //la carta non corrisponde al placeholder preciso
                                feedbackLabel.setText("Attacco non permesso. Prova con altre carte o fai un'altra azione");
                                return false;
                            }
                        }
                    }
                }

            //attacco su TRIS
            } else {
                System.out.println("DEBUG: ATTACCO SU TRIS");
                //JOKER -> un joker può sempre essere attaccato, ma bisogna assegnare i giusti valori al sostituto
                if (cartaManoSelezionata.isJoker()){ 

                    //TRIS composto da soli Joker
                    if (apertura.size() == contaJoker(apertura)) {
                        //l'attacco è permesso, ma devo scegliere il seme del Joker e prendere il valore di uno degli altri Joker (tutti uguali ovviamente essendo un tris)
                        
                        //recupero la mappa sostituzione di un giocatore
                        Map<Integer, List<Carta>> apertureJokerGiocatore = jokerTotaliSulTavolo.get(idGiocatore);
                        int aperturaIndex = trovaIndiceApertura(tavoloGioco.get(idGiocatore), apertura);

                        //recupero i joker sostituti
                        List<Carta> carteSostituite = apertureJokerGiocatore.get(aperturaIndex);

                        //apro il popup
                        Optional<Carta> cartaSostituitaOptional = showSemePopupAttacco(carteSostituite);

                        if (cartaSostituitaOptional.isPresent()) {
                            //scelta del seme effettuata
                            attaccaCartaTavolo(cartaManoSelezionata, cartaSostituitaOptional, apertura, "dx", idGiocatore);
                            feedbackLabel.setText("Attacco avvenuto con successo");
                            return true;
                        } else {
                            feedbackLabel.setText("Attacco annullato");
                            return false;
                        }

                    //TRIS composto da almeno una carta nota
                    } else {
                        //apro il popup
                        Optional<Carta> cartaSostituitaOptional = showSemePopupAttacco(apertura);
                        
                        if (cartaSostituitaOptional.isPresent()) {
                            //scelta del seme effettuata
                            attaccaCartaTavolo(cartaManoSelezionata, cartaSostituitaOptional, apertura, "dx", idGiocatore);
                            feedbackLabel.setText("Attacco avvenuto con successo");
                            return true;
                        } else {
                            feedbackLabel.setText("Attacco annullato");
                            return false;
                        }
                    }

                //CARTA NOTA
                } else {
                    //TRIS composto da soli Joker
                    if (apertura.size() == contaJoker(apertura)) {
                        //recupero la mappa sostituzione di un giocatore
                        Map<Integer, List<Carta>> apertureJokerGiocatore = jokerTotaliSulTavolo.get(idGiocatore);
                        int aperturaIndex = trovaIndiceApertura(tavoloGioco.get(idGiocatore), apertura);

                        //recupero i joker sostituti
                        List<Carta> carteSostituite = apertureJokerGiocatore.get(aperturaIndex);

                        if (cartaManoSelezionata.getValore().equals(carteSostituite.get(0).getValore())) {
                            //match esatto: lo scambio è permesso
                            attaccaCartaTavolo(cartaManoSelezionata, Optional.empty(), apertura, "dx", idGiocatore);
                            feedbackLabel.setText("Attacco avvenuto con successo");
                            return true;
                        } else {
                            //la carta non corrisponde al placeholder preciso
                            feedbackLabel.setText("Attacco non permesso. Prova con altre carte o fai un'altra azione");
                            return false;
                        }
    
                    //TRIS composto da almeno una carta nota
                    } else {
                        //trovo il primo valore noto e controllo che la carta che sto attaccando abbia lo stesso valore
                        String valoreNoto = null;
                        for(Carta c : apertura){
                            if (!c.isJoker()) {
                                valoreNoto = c.getValore();
                            }
                        }

                        if (valoreNoto.equals(cartaManoSelezionata.getValore())) {
                            //match esatto: lo scambio è permesso
                            attaccaCartaTavolo(cartaManoSelezionata, Optional.empty(), apertura, "dx", idGiocatore);
                            feedbackLabel.setText("Attacco avvenuto con successo");
                            return true;
                        } else {
                            //la carta non corrisponde al placeholder preciso
                            feedbackLabel.setText("Attacco non permesso. Prova con altre carte o fai un'altra azione");
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    //per scambiare una carta in mano con un joker sul tavolo
    private void swapCartaJoker(Carta cartaMano, Carta jokerTavolo, List<Carta> apertura, int idGiocatoreApertura){
        
        //1. scambio la carta in mano col joker
        int posCartaMano = -1;
        for (int i = 0; i < mano.getCarte().size(); i++) {
            if (mano.getCarte().get(i).equals(cartaMano)) {
                posCartaMano = i;
                break;
            }
        }
        mano.getCarte().set(posCartaMano, jokerTavolo);

        //2. rimuovo joker dall'apertura sul tavolo e salvo in che posizione era
        int posJoker = -1;
        for (int i = 0; i < apertura.size(); i++) {
            if (apertura.get(i).equals(jokerTavolo)) {
                posJoker = i;
                apertura.remove(i);
                break;
            }
        }
            if (posJoker == -1) {
            System.err.println("ERRORE: Joker non trovato nell'apertura!");
            return;
        }

        //3. rimuovo joker dalla lista di joker sul tavolo (jokerTotaliSulTavolo)
        int aperturaIndex = trovaIndiceApertura(tavoloGioco.get(idGiocatoreApertura), apertura);
        Map<Integer, List<Carta>> apertureJoker = jokerTotaliSulTavolo.get(idGiocatoreApertura);
        if (apertureJoker != null) {
            apertureJoker.remove(aperturaIndex);
        }

        //4. aggiungo la carta all'apertura nella posizione in cui era il joker
        apertura.add(posJoker, cartaMano);

        //5. comunico al server i cambiamenti in mano e sul tavolo
        //es: SWAP_CARTE:joker.jpg,carta.jpg,idGiocatoreApertura,aperturaIndex,posizioneJokerNell'Apertura
        String messaggio = "SWAP_CARTE:" + jokerTavolo.getImageFilename() + "," + cartaMano.getImageFilename() 
                        + "," + idGiocatoreApertura + "," + aperturaIndex + "," + posJoker;
        out.println(messaggio);

        //6. aggiorno mano
        aggiornaManoGUI();
    }

    //per attaccare una carta in mano a un'apertura sul tavolo
    private void attaccaCartaTavolo(Carta cartaMano, Optional<Carta> jokerSostituto, List<Carta> apertura, String posPlaceHolder, int idGiocatoreApertura){
        
        //1. rimuovo la carta dalla mano
        mano.rimuoviCarta(cartaMano);

        //2. aggiungo la carta all'apertura a sx o a dx
        if (posPlaceHolder.equals("sx")) {
            apertura.add(0, cartaMano); //aggiunta in testa e shifta tutti gli altri oggetti
        } else {
            apertura.add(cartaMano); //aggiunta in coda
        }

        //calcolo l'indice dell'apertura
        int aperturaIndex = trovaIndiceApertura(tavoloGioco.get(idGiocatoreApertura), apertura);

        //3. se attacco un joker aggiorno la lista di joker sul tavolo (jokerTotaliSulTavolo)
        if (jokerSostituto.isPresent()){
            //se la mappa per il giocatore non esiste la creo
            Map<Integer, List<Carta>> apertureGiocatore = jokerTotaliSulTavolo.get(idGiocatoreApertura);
            if (apertureGiocatore == null) {
                apertureGiocatore = new HashMap<>();
                jokerTotaliSulTavolo.put(idGiocatoreApertura, apertureGiocatore);
            }
            List<Carta> listaSostituti = apertureGiocatore.getOrDefault(aperturaIndex, new ArrayList<>());
            //aggiungo il nuovo joker sostituto alla lista
            listaSostituti.add(jokerSostituto.get());
            //risostituisco la lista aggiornata nella mappa
            apertureGiocatore.put(aperturaIndex, listaSostituti);
        }

        //4. comunico al server i cambiamenti in mano e sul tavolo
        //es: ATTACCA_CARTA:cartaDaAttaccare.jpg,jokerSostituto.jpg,idGiocatoreApertura,aperturaIndex,posizioneAttacco (dx o sx)
        String nomeFileJokerSostituto = jokerSostituto.isPresent() ? jokerSostituto.get().getImageFilename() : "";
        String messaggio = "ATTACCA_CARTA:" + cartaMano.getImageFilename() + "," + nomeFileJokerSostituto 
                        + "," + idGiocatoreApertura + "," + aperturaIndex + "," + posPlaceHolder;
        out.println(messaggio);

        //5. aggiorno mano
        aggiornaManoGUI();
    }

    //per capire se un'apertura è un tris (return false) o una scala (return true)
    //funziona se l'apertura NON è composta da soli joker (unico caso è 4 joker e non sa riconoscere se è compongono una scala o un tris a cui è stato attaccato un altro joker)
    private boolean isScala(List<Carta> apertura){
        if(apertura.size() == 3){
            return false; //certamente è un tris
        }

        //caso speciale: apertura di 4 carte, tutte Joker
        if(apertura.size() == 4 && contaJoker(apertura) == 4){
            //prendo la mappa delle aperture con i joker sul tavolo (appartengono tutti alla stessa apertura in questo caso speciale)
            for (Map<Integer, List<Carta>> apertureGiocatore : jokerTotaliSulTavolo.values()) {
                //cerco l'apertura corrente all'interno della mappa dei sostituti
                for (List<Carta> sostituti : apertureGiocatore.values()) {
                    if (sostituti.size() == 4) { //trovata l'unica apertura di 4 joker
                        //controllo i valori dei sostituti
                        String valoreDiRiferimento = sostituti.get(0).getValore();
                        boolean tuttiStessoSeme = true;
                        for (int i = 1; i < sostituti.size(); i++) {
                            if (!sostituti.get(i).getValore().equals(valoreDiRiferimento)) {
                                tuttiStessoSeme = false;
                                break;
                            }
                        }
                        //se i valori sono tutti uguali, è un tris (false), altrimenti è una scala (true)
                        return !tuttiStessoSeme;
                    }
                }
            }
        }

        //se l'apertura ha più di tre carte può essere un tris (necessariamente con una o più carte attaccate) oppure una scala (standard o con carte attaccate)

        //controllo se i valori sono tutti diversi (ignorando i Joker)
        String valoreDiRiferimento = null;
        for (Carta c : apertura) {
            if (!c.isJoker()) {
                if (valoreDiRiferimento == null) {
                    valoreDiRiferimento = c.getValore(); //imposto il valore di riferimento sulla prima carta non Joker
                } else if (!c.getValore().equals(valoreDiRiferimento)) {
                    return true; //se anche un solo valore è diverso da un altro allora è certamente una scala
                }
            }
        }
    
        //se supero tutti i controlli allora è un tris (tutte le carte non joker hanno lo stesso valore)
        return false;
    }

    private void avviaCountdown(Label labelTimer) {
        labelTimer.setText("10"); //inizializzo
        final int[] secondiRimasti = {10};

        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            secondiRimasti[0]--;
            labelTimer.setText(String.valueOf(secondiRimasti[0]));

            //effetto pulse
            ScaleTransition pulse = new ScaleTransition(Duration.millis(200), labelTimer);
            pulse.setFromX(1.0);
            pulse.setFromY(1.0);
            pulse.setToX(1.3);
            pulse.setToY(1.3);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(2);
            pulse.play();

            //cambio colore ultimi 3 secondi
            if (secondiRimasti[0] <= 3) {
                labelTimer.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #8B0000;");
            } else{
                labelTimer.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #000000;");
            }

            /*
            //suono ad ogni secondo che passa
            AudioClip sound = new AudioClip(getClass().getResource("/assets/sounds/alert.mp3").toString());
            sound.play();
            */
        }));
        timeline.setCycleCount(10); //eseguo 10 volte
        timeline.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}







/*
TODO
    LATO CLIENT
    1) quando carico l'immagine del seme/valore sul joker, fare il caso di un tris con solo joker (quindi nel primo mostrare anche il valore oltre al seme) e di una scala con solo joker (quindi nel primo mostrare anche il seme oltre il valore)
    2) un joker può essere scartato come carta finale, e quindi può rimanere in mano come ultima carta dopo un'apertura

    LATO SERVER
    1) fare fine gioco alla fine del round 8
    2) fare classifica finale con punteggio (in caso di parità vince chi ha vinto più turni)
    3) fare regolamento con pulsante sempre cliccabile
*/ 
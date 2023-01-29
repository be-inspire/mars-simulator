---
Implementazione di un simulatore di Mission Controller
---
# Come implementare un simulatore di Mission Controller

## Riferimenti
L'API per implementare un <abbr title="Mission Controller">MC</abbr> si trova nel progetto *mc-lib*.

Un esempio di implementazione di un <abbr title="Mission Controller">MC</abbr>  si trova nel progetto *mc-sample* (classe `mars.mc.samples.Mc1`).

Iniziamo ad analizzarne la struttura.

## Primi passi

Nel nostro esempio si parte modellando un semplicissimo **drone** nella classe `mars.mc.samples.Drone` con queste caratteristiche:
- un `id` che lo identifica
- un `PayloadOnBoard` che rappresenta il **payload** a bordo del drone

Il payload è definito nella libreria dalla classe `mars.side.signals.PayloadOnBoard` ed ha queste proprietà:
- un `id` per identificarlo
- un `payloadType` che ne identifica il modello o la tipologia
- una `charge` che rappresenta il livello della carica residua (tra 0 e 1)

Nell'esempio si passa poi a definere una classe `mars.mc.samples.PitStop`.

Questa classe è molto importante perchè il suo ruolo è quello di funzionare da contenitore di tutte le informazioni necessarie a gestire il **pitstop** di un drone, informazioni che vengono scambiate dal momento in cui ne viene fatta richiesta al momento in cui il pitstop ha luogo.

Sulla classe `PitStop` si ritornerà in seguito.

Per implementare un simulatore di <abbr title="Mission Controller">MC</abbr> è ora necessario definire una classe che implementi l'interfaccia `mars.mc.McLogics`.

Nel nostro esempio è la classe `mars.mc.samples.Mc1` a farlo:
```java
@Override
	public void start(McComms<PitStop> comms, McSideComms sideComms) {
		...
	}
```

L'interfaccia ha un unico metodo `start` che riceve come parametri sia il canale di comunicazione `McComms` sia il *side-channel* `McSideComms` che rappresenta il canale di comunicazione per i messaggi necessari al funzionamento della parte simulativa. Come si vede è proprio a questo punto che viene condivisa con `McComms` l'implementazione del <abbr title="PitStop">PS</abbr> che verrà poi utilizzata per lo scambio dei dati, nel nostro caso la classe `PitStop`.

All'interno del metodo `start` la classe `Mc1` salva un riferimento locale ai due canali di comunicazione per poterli poi utilizzare:
```java
this.comms = comms;
this.sideComms = sideComms;
```

Nello stesso punto è necessario collegare a `comms` una implementazione dell'interfaccia `mars.mc.McCommsListener`. Nell'esempio è sempre la classe `Mc1` che si fa carico di implementare tale interfaccia, per cui il collegamento è effettuato nel metodo `start` così:
```java
comms.setListener(this);
```

### Interfaccia McComms

`McComms` contiene quindi i metodi che il <abbr title="Mission Controller">MC</abbr> può utilizzare per inviare le proprie richieste alla **Tower**.
Questi metodi hanno il prefisso `send`:

- `sendPsDemandRequest` invia una richiesta di pitstop fornendo un id della richiesta, il tipo o categoria del payload ed un istante di fine vita delle batterie del drone
- `sendPlatformReachabilityIndication` invia una richiesta per conoscere la disponibilità delle **platform** gestite da Tower in uno specifico intervallo di tempo. È sempre legata ad un richiesta di pitstop precedentemente inviata ed è correlata ad uno di questi 2 eventi:
  - Tower non ha risposto a una precedente richiesta di pitstop
  - il <abbr title="Mission Controller">MC</abbr> ha rilevato un ritardo nell'eta di arrivo su una platform precedentemente allocata per il pitstop
- `sendPlatformCylinderEnterRequest` invia una richiesta di ingresso nel cilindro di una platform. Viene indicato l'id della richiesta di pitstop precedentemente inviata e il nome della platform coinvolta. 
- `sendDroneLandedIndication` invia una notifica di avvenuto atterraggio di uno specifico drone, identificato dal suo id, nel contesto di un pitstop richiesto su una specifica platform
- `sendPlatformCylinderLeftIndication` invia una notifica di uscita dal cilindro di una plaform nel contesto di un pitstop richiesto in precedenza

Oltre ad essi, il metodo `isTowerActive` serve per pingare lo stato della Tower mentre il metodo `setListener` già visto serve per collegare una istanza di `McCommsListener` che avrà la funzione di raccogliere i messaggi in arrivo dal sistema.

### Interfaccia McCommsListener

Se `McComms` consente di comunicare verso la Tower, `McCommsListener` rappresenta il ricevitore dei messaggi provenienti dalla Tower e dalla piattaforma di comunicazione.

I suoi metodi si dividono in 3 gruppi a seconda del tipo di informazione veicolato:
- metodi per la ricezione di notifiche sullo stato della Tower (inviati dalla piattaforma di comunicazione)
- metodi che restituiscono l'esito di una richiesta precedentemente inviata tramite l'istanza di `McComms` (inviati dalla Tower)
- metodi per la ricezione di messaggi dalla Tower che non sono feedback immediati a richieste del <abbr title="Mission Controller">MC</abbr>

#### Notifiche sullo stato della Tower

```java
void onTowerDiscovered(String agentName);
void onTowerLost(String agentName);
void onTowerReturned(String agentName);
```
Questi 3 metodi vengono invocati:
- quando una nuova Tower entra nel sistema di comunicazione (quando un MC si aggancia il sistema può verificare lo stato della Tower attraverso il metodo `isTowerActive` indicato in precedenza)
- quando la connessione con la Tower si è interrotta
- quando la Tower, precedentemente sconnessa, ritorna online nel sistema

#### Risposte a richieste

Sono presenti in gruppi di 3 per ogni metodo di invio richieste/indicazioni in `McComms`:

```java
void onPsDemandResponse(PsDemandResponse psdemandresponse, T ps);
void onPsDemandTimeout(T ps);
void onPsDemandError(Throwable t, T ps);
```
La classe `PsDemandResponse` contiene l'elenco delle piattaforme disponibili per la richiesta di pitstop corrente. Ne vedremo l'utilizzo in seguito nel contesto dell'esempio.

```java
void onPlatformReachabilityConfirm(T ps);
void onPlatformReachabilityTimeout(T ps);
void onPlatformReachabilityError(Throwable t, T ps);
```

```java
void onCylynderEnterResponse(PlatformCylinderEnterResponse r, T ps);
void onCylinderEnterTimeout(T ps);
void onCylinderEnterError(Throwable t, T ps);
```

```java
void onDroneLandedConfirmed(DroneLandedConfirm r, T ps);
void onDroneLandedTimeout(T ps);
void onDroneLandedError(Throwable t, T ps);
```

```java
void onCylinderLeftConfirmed(PlatformCylinderLeftConfirm c, T ps);
void onCylinderLeftTimeout(T ps);
void onCylinderLeftError(Throwable t, T ps);
```

Le triplette sono costituite da:
- un messaggio che indica l'avvenuta presa in carico di una richiesta e l'eventuale risposta
- un messaggio (generato dalla piattaforma di comunicazione) di timeout che indica che la Tower non ha risposto nel tempo massimo ad una specifica richiesta
- un messaggio di errore che riporta al <abbr title="Mission Controller">MC</abbr> che **una sua richiesta non è soddisfacibile** o che si è verificato **un errore imprevisto lato Tower**

#### Messaggi in ingresso

```java
PsAbortConfirm onPsAbortIndication(PsAbortIndication p);
```
Questo messaggio è un comando che la Tower invia al <abbr title="Mission Controller">MC</abbr> per annullare un pitstop precedentemente concordato. 

```java
PlatformAvailabilityConfirm onPlatformAvailabilityIndication(PlatformAvailabilityIndication p);
```
Con questo messaggio la Tower passa al <abbr title="Mission Controller">MC</abbr> una lista di piattaforme disponibile per il pitstop. I dati sono analoghi a quelli contenuti nella classe `PsDemandResponse` vista in precedenza.

```java
PlatformAssignmentConfirm onPlatformAssignmentIndication PlatformAssignmentIndication p);
```
In questo caso la Tower comunica al <abbr title="Mission Controller">MC</abbr> che gli è stato assegnata una specifica platform per un certo pitstop ad un certo istante.

```java
PsCompletedIndication onPsCompletedIndication(PsCompletedIndication p);
```
Con questo messaggio la Tower comunica al <abbr title="Mission Controller">MC</abbr> che il pitstop è stato eseguito e quindi, teoricamente, il drone può riprendere il volo.

### Interfaccia McSideComms

Lo scopo dell'interfaccia `McSideComms` è quello di veicolare delle notifiche di stato da parte dei vari agenti partecipanti la simulazione ad altri agenti, specificatamente ad agenti di controllo od osservatori. La comunicazione avviene quindi sempre in un solo senso, non sono previsti eventi di risposta.

Al momento l'interfaccia dispone di quest'unico metodo:
```java
<S> void emitSideSignal(String name, String eventName, S payload);
```

Il parametro `name` indica il *tipo di segnale* (per ora ne sono previsti 2: `EVENT` e `ERROR`), il parametro `eventName` specifica la categoria dell'evento o dell'errore e il payload è una classe contenitore di informazioni relative al segnale.

## Definizione delle logiche

Abbiamo già visto come il punto di partenza del <abbr title="Mission Controller">MC</abbr> sia il metodo `start` dall'interfaccia `McLogics`. Dopo aver collegato le istanze dei 2 canali di comunicazione ed essersi registrato come listener, il <abbr title="Mission Controller">MC</abbr> controlla se Tower è online e, in caso affermativo, simula una nuova richiesta di pitstop:

```java
if (comms.isTowerActive()) {
		LOGGER.info("Tower is active, scheduling next PS");
		simulateNewRequestInRandomTime();
	} else {
		LOGGER.info("Waiting for tower is active for scheduling next PS");
	}
```

Se la tower è offline, è sull'evento di `OnTowerDiscovered` che viene generata la richiesta (si noti come l'esempio presenti una situazione estremamente semplificata, nella realtà l'evento avrebbe dovuto gestire lo stato di eventuali pitstop in corso):

```java
@Override
public void onTowerDiscovered(String agentName) {
	LOGGER.info("Tower has become active, scheduling next PS");
	simulateNewRequestInRandomTime();
	}
```

La procedura `simulateNewRequestInRandomTime` attende un tempo casuale, in questo caso, ad esempio, tra 3 e 8 secondi, e poi invia una richiesta di pitstop:

```java
private void simulateNewRequestInRandomTime() {
	final int seconds = random.nextInt(5) + 3;
	LOGGER.info("Simulating next PS request in {} seconds", seconds);
	El.setTimeout(seconds * 1000, this::sendPsRequest);
}
```
È importante notare che qualsiasi attesa nel <abbr title="Mission Controller">MC</abbr> debba passare attraverso l'istanza `El` del sistema di **EventLoop**.

### EventLoop

L'EventLoop è una coda di esecuzione eventi che assicura che essi vengano eseguiti in un unico thread, per evitare corse sui dati. Inoltre fornisce un sistema di riferimento temporale comune a tutti gli agenti operanti nel simulatore.

Il sistema di messaggistica provvede già ad accodare nell'El l'esecuzione dei metodi derivanti da messaggi in ingresso, che invece sono ricevuti dal thread di ascolto di RabbitMq.

Di fatto, tutti i metodi devono essere eseguiti all'interno dell'El.

Per avere l'ora di esecuzione dell'evento corrente utilizzare esclusivamente il metodo `El.now()`

```java
	public static Instant now();
```

tale orario resta **invariato** durante l'esecuzione dell'evento. Non utilizzare altri metodi legati al tempo: l'El può essere fatto funzionare in uno scenario a "tempo accelerato" e non in real time. In questo caso, chiedere il tempo con altri metodi sarebbe disastroso.

L'El deve sempre essere libero, ovvero i metodi **devono rientrare nel minor tempo possibile**.

Nel caso di necessità di dover eseguire un "long run", sarà cura dello sviluppatore farlo in un thread separato, preoccupandosi, a job concluso, di far rientrare i risultati all'interno dell'El.

Il modo per farlo è ottenere un *executor* prima di far partire il thread, quindi ancora all'interno del thread di El (1), poi, all'interno del thread di long run, finita l'eleborazione, si può accodare un evento (2) che usa i dati ottenuti dal long run e che verrà eseguito all'interno dell'El.

```java
		final var executor = El.executor();
		thread = new Thread(() -> {
			LOGGER.info("Calling long calculation");
			... (1)

			executor.accept(() -> {
				... (2)
			});
		});
		thread.start();
```

Una classe più completa potrebbe gestire anche la cancellazione del long run.

```java
public class LongRun {
	private final static Logger LOGGER = LoggerFactory.getLogger(LongRun.class);

	private boolean cancelled;
	private final Thread thread;

	public LongRun(...) {
		final Instant now = El.now();
		final var executor = El.executor();
		thread = new Thread(() -> {
			...(1) // here we can use now to get the execution time
			executor.accept(() -> {
				if (cancelled)
					LOGGER.info("Run has been cancelled");
				else
					... (2) // use the result inside the El
			});
		});
		LOGGER.info("Starting run thread");
		thread.start();
	}

	public void cancel() {
		thread.interrupt();
		cancelled = true;
	}
}
```

Se si vuole far eseguire un evento dopo un certo tempo usare il metodo

```java
	public static Timeout setTimeout(long millis, Event c);
```

ad esempio

```java
	El.setTimeout(2000, () -> System.out.println("Two seconds have passed")));
```

L'oggetto restituito è di tipo

```java
public interface Timeout {
	void cancel();
}
```

invocare il metodo `cancel()` serve a eliminare l'evento dalla coda: esso non verrà più eseguito.


## Generazione della richiesta di pitstop

Il metodo del <abbr title="Mission Controller">MC</abbr> che genera la richiesta di pitstop è questo:

```java
private void sendPsRequest() {
	final var ps = new PitStop(nextRequestId++,
			new Drone("DRONE-XY", new PayloadOnBoard(UUID.randomUUID().toString(), "PT1", .234)), El.now());
	comms.sendPsDemandRequest(new PsDemandRequest(ps.requestId, "PT1", El.now().plus(20, ChronoUnit.MINUTES)), ps);
}
```
Questo metodo prima crea una istanza della classe `PitStop` relativa ad un nuovo `Drone` "DRONE-XY" con un payload a bordo di tipo "PT1" e una carica residua del 23,4%, di cui salva anche l'istante attuale (`El.now()`) come istante di creazione.

Poi invia la richiesta a Tower per l'atterraggio sulla piattaforma "PT1" entro 20 minuti dall'istante attuale (si noti come sia sempre `El` il mediatore dei conti sui tempi).

La classe `PitStop`, già anticipata all'inizio, contiene un set di informazioni di base relativamente al pitstop (qualche istante e poco altro). Simulatori di <abbr title="Mission Controller">MC</abbr> più complessi o <abbr title="Mission Controller">MC</abbr> reali dovranno implementare un modello molto più complesso.

Inviata la richiesta, il <abbr title="Mission Controller">MC</abbr> riceverà la risposta da Tower in uno dei 3 eventi dell'interfaccia `McCommsListener` associati alla chiamata di `McComms` utilizzata per l'invio.

In caso di timeout nell'esempio si è scelto di non fare nulla:
```java	
@Override
public void onPsDemandTimeout(PitStop ps) {
	LOGGER.error("PS Demand Request timed out for PS {}!", ps.pitStopId);	
}
```

In caso di errore, il <abbr title="Mission Controller">MC</abbr> invierà una nuova richiesta di pitstop per un nuovo drone:

```java	
@Override
public void onPsDemandError(Throwable t, PitStop ps) {
	LOGGER.error("PS Demand Request got an error for PS: " + ps.pitStopId, t);
	simulateNewRequestInRandomTime();
}
```

In caso di risposta positiva invece, il <abbr title="Mission Controller">MC</abbr> procederà ad analizzare le *availabilities*, ovvero le disponibilità di platform, restituite da Tower:

```java
@Override
public void onPsDemandResponse(PsDemandResponse r, PitStop ps) {
	ps.pitStopId = r.psId();
	repo.add(ps);
	final List<PsPlatformAvailability> availabilities = r.psPlatformAvailabilities();
	final List<PsPlatformReachability> reachabilities = availabilities.stream().map(this::calculateReachability)
			.collect(Collectors.toList());
	final PlatformReachabilityIndication platformReachabilityIndication = new PlatformReachabilityIndication(
			ps.pitStopId, reachabilities);
	comms.sendPlatformReachabilityIndication(ps, platformReachabilityIndication);
}
```

Nella procedura viene innanzitutto salvato l'id del pitstop restituito da Tower e l'istanza del pitstop viene salvata in un elenco interno al <abbr title="Mission Controller">MC</abbr>.

Dalle *availabilities* restituite da Tower, si procede poi al calcolo delle *reachabilities* (ovvero gli intervalli temporali necessari per raggiungere le platform disponibili). L'algoritmo di calcolo è implementato in questo metodo:

```java
private PsPlatformReachability calculateReachability(PsPlatformAvailability ) {
	final Instant etaMin = El.now().plus(Duration.ofMinutes(1));
	final Instant etaMax = El.now().plus(Duration.ofMinutes(2));
	return new PsPlatformReachability(a.platformId(), etaMin, etaMax);
}
```
Come evidente il calcolo implementato nell'esempio è triviale: tutte le piattaforme sono dichiarate raggiungibili in un intervallo di tempo tra un minuto e due minuti a partire dall'istante corrente (si noti sempre il ruolo di `El`). Un <abbr title="Mission Controller">MC</abbr> più sofisticato di questo potrà effettuare dei conteggi precisi basandosi sulla posizione del drone, che dovrebbe monitorare in autonomia, e sulle coordinate geografiche delle platform che sono indicate da Tower nelle *availabilities* restituite:

```java
public record PsPlatformAvailability(String platformId, GeoCoord geoCoord) {}
```

Nell'esempio, nei 3 eventi `onPlatformReachability-qualcosa` non viene fatto niente se non la compilazione del log.

La procedura ora rimane in attesa che Tower comunichi al <abbr title="Mission Controller">MC</abbr> una assegnazione (*Assignment*).

## Assegnazione

Quando la Tower ha computato le allocazioni, comunica al <abbr title="Mission Controller">MC</abbr> l'assegnazione e viene eseguito il codice di questo evento:

```java
@Override
public PlatformAssignmentConfirm onPlatformAssignmentIndication(PlatformAssignmentIndication p) {
	final PitStop ps = searchPs(p.psId());
	ps.platformId = p.platformId();
	ps.platformAvailableAt = El.now();
	final var timeOfArrival = p.at();
	final var theoricTravelTimeInMillis = El.now().until(timeOfArrival, ChronoUnit.MILLIS);
	final var delay = -Math.min(10_000, theoricTravelTimeInMillis) + (long) exponentialDistribution.sample();
	final var travelTimeInMillis = theoricTravelTimeInMillis + delay;
	LOGGER.info("Sending the drone to platform: {} for PS: {} in: {} ms (instead of {})", ps.platformId,
			ps.pitStopId, travelTimeInMillis, theoricTravelTimeInMillis);
	ps.travelTimeout.ifPresent(t -> t.cancel());
	ps.travelTimeout = Optional.of(El.setTimeout(travelTimeInMillis, () -> droneArrivedAtPlatform(ps)));
	return new PlatformAssignmentConfirm();
}
```

Nello specifico:
- a partire dal tempo di arrivo assegnato da Tower (calcolato nel rispetto delle *reachabilities* comunicate in precedenza), si calcola il tempo di volo del drone fino alla piattaforma assegnata (si noti sempre il ruolo di `El`)
- ai fine di simulare una situazione non ideale, si calcola un ritardo sull'eta tra -10 secondi (arrivo in anticipo) e un valore positivo casuale da distribuzione esponenziale
- viene poi pianificato, grazie a `El`, l'esecuzione della procedura `droneArrivedAtPlaform` al termine del volo del drone. Prima di questo però viene eliminato, se presente, un eventuale evento già pianificato: ```ps.travelTimeout.ifPresent(t -> t.cancel());```. Questo è importante nel contesto del nostro esempio perchè durante il volo simulato verso la platform potrebbe accadere che Tower comunichi al <abbr title="Mission Controller">MC</abbr> una nuova assegnazione (attraverso l'evento di ```onPlatformAvailabilityIndication``` già analizzato)
- viene restituita una istanza di ```PlatformAssignmentConfirm``` per confermare l'avvenuta elaborazione del messaggio di Tower

## Arrivo alla platform

Nel nostro esempio, passato il tempo previsto per l'arrivo del drone a ridosso del cilindro della platform, viene eseguito il metodo:
```java
private void droneArrivedAtPlatform(PitStop ps) {
	LOGGER.info("Drone arrived at platform: {} for PS: {}", ps.platformId, ps.pitStopId);
	ps.arrivedAtPlatformCylinder = El.now();
	final PlatformCylinderEnterRequest req = new PlatformCylinderEnterRequest(ps.pitStopId, ps.platformId);
	comms.sendPlatformCylinderEnterRequest(req, ps);
}
```

Il metodo semplicemente invia a Tower la richiesta di poter entrare nel cilindro della platform e sulla risposta di Tower si procede a simulare l'atterraggio:

```java
@Override
public void onCylinderEnterResponse(PlatformCylinderEnterResponse r, PitStop ps) {
	LOGGER.info("{} for PS: {}", r, ps.pitStopId);	
	ps.authorizedEnterCylinderAt = El.now();
	landDrone(ps);
}
```

La procedura che simula l'atterraggio ipotizza che il tempo di atterraggio sia sempre di 10 secondi e schedula un evento (sempre con l'aiuto di `El`) alla cui esecuzione viene inviata una notifica Events.MARS_DRONE_LANDED sul *side-channel* e la notifica ufficiale a Tower attraverso il canale di comunicazione principale:

```java
private void landDrone(PitStop ps) {
	LOGGER.info("Drone will land on platform {} in {} ms", ps.platformId, timeToCylinderSurface);

	El.setTimeout(timeToCylinderSurface, () -> {
		sideComms.emitSideSignal(Signals.EVENT.name(), Events.MARS_DRONE_LANDED,
				new DroneLandedEvent(ps.drone.id(), ps.platformId, ps.drone.payloadOnBoard()));
		comms.sendDroneLandedIndication(new DroneLandedIndication(ps.pitStopId, ps.platformId, ps.drone.id()), ps);
	});
}
```

Quando Tower restituisce un feedback, inizia ufficialmente il pitstop:

```java
@Override
public void onDroneLandedConfirmed(DroneLandedConfirm r, PitStop ps) {
	LOGGER.info("{} for PS: {}", r, ps.pitStopId);
	ps.landedAt = El.now();
}
```

### Fine del pitstop

Quando il pitstop è terminato, da Tower arriverà un messaggio di *indication*:

```java
@Override
public PsCompletedIndication onPsCompletedIndication(PsCompletedIndication p) {	
	final PitStop ps = searchPs(p.psId());

	if (!ps.platformId.equals(p.platformId()))
		throw new RuntimeException("Bad platform for PS: " + ps.pitStopId + " expected: " + ps.platformId
				+ " but got: " + p.platformId());

	LOGGER.info("Completed PS: {}", ps.pitStopId);	
	takeOff(p.platformId(), ps);

	return new PsCompletedIndication(ps.pitStopId, ps.platformId);
}
```

Il <abbr title="Mission Controller">MC</abbr> esegue la procedura `takeOff` e restituisce una istanza di `PsCompletedIndication` per confermare l'avvenuta gestione dell'evento fatto scattare da Tower.

Il metodo `takeOff` notifica sul *side-channel* l'avvenuto decollo del drone e poi imposta un evento, sempre posticipato di 10 secondi e sempre con l'aiuto di `El`, per simulare l'uscita del drone dal cilindro della platform:

```java
private void takeOff(String platformId, final PitStop ps) {
	LOGGER.info("Drone takes off and will leave platform {} cylinder in {}ms", ps.platformId,
			timeToCylinderSurface);

	sideComms.emitSideSignal(Signals.EVENT.name(), Events.MARS_DRONE_TOOK_OFF,
			new DroneTookOffEvent(ps.drone.id(), ps.platformId));
	El.setTimeout(timeToCylinderSurface, () -> {
		ps.leftCylinderAt = El.now();
		comms.sendPlatformCylinderLeftIndication(new PlatformCylinderLeftIndication(ps.pitStopId, platformId), ps);
	});
}
```

Passati i 10 secondi, grazie a EventLoop viene inviata a Tower una notifica di uscita dal cilindro (`sendPlatformCylinderLeftIndication`) la cui risposta da Tower fa scattare l'esecuzione del codice dell'evento:

```java
@Override
public void onCylinderLeftConfirmed(PlatformCylinderLeftConfirm c, PitStop ps) {
	LOGGER.info("{}", c);
	simulateNewRequestInRandomTime();
}
```
Con `simulateNewRequestInRandomTime` il giro riprende con un nuovo drone ed un nuovo pitstop.

## Note finali

L'esempio appena descritto può essere eseguito come java application fornendo come parametro da command line l'*agent name* del mission controller.
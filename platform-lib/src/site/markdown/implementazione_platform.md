---
PageTitle: Implementazione di un simulatore di Platform
---
# Come implementare un simulatore di Platform

## Riferimenti
L'API per implementare una Platform si trova nel progetto *platform-lib*.

Un esempio di implementazione di una Platform si trova nel progetto *platform-sample* (classe `mars.platform.samples.Platform1`).

## Primi passi
Per implementare una Platform, è necessario definire l'implementazione di 3 interfacce diverse e fornire un metodo (una factory) che costruisca l'istanza di una di esse (`PlatformInternals`).

Le interfacce da implementare sono:
* `mars.platform.logics.Bay` che rappresenta una baia in cui vengono custoditi i payload disponibili per i pitstop e le loro sedi di conservazione e ricarica elettrica
* `mars.platform.logics.PlatformAutomationSystem` che rappresenta il sistema di automazione e/o di orchestrazione delle operazioni di pitstop di una Platform
* `mars.platform.logics.PlatformInternals` che contiene l'implementazione del `PlatformAutomationSystem` e altre proprietà aggiuntive della Platform, come le sue coordinate geografiche

Il nostro esempio utilizza la classe `SimulativeBay` come implementazione di `Bay`, `DummyAutomationSystem` come implementazione di `PlatformAutomationSystem` e `DummyPlatformInternals` come implementazione di `PlatformInternals`. Tutte queste classi verranno analizzate in seguito.

Il metodo di `Platform1` che crea gli *internals* inizializza le classi con i valori letti da un file json di configurazione e fornisce alle classi base un collegamento con il sistema di comunicazione del simulatore `ComSystem` basato su RabbitMQ:

```java
private static PlatformInternals<?> createInternals(final String confFile, ComSystem comSystem, String name)
		throws IOException {
	final DummyAutomationSystem automationSystem = new DummyAutomationSystem(comSystem, name, El.executor());
	final DummyPlatformInternals internals = new DummyPlatformInternals(automationSystem);
	new ConfiguratorFromFile().configure(internals, automationSystem, new File(confFile));
	return internals;
}
```

Per eseguire il nostro esempio è necessario passare come parametro da command line il nome della platform (ad esempio *PT1*) e il nome del file json di configurazione (ad esempio *pt1.json*).

## Interfaccia Bay
Abbiamo detto che all'interno del simulatore l'interfaccia `Bay` rappresenta una baia in cui è custodito (e ricaricato elettricamente) un payload a disposizione della platform per i pitstop dei droni. Essa contiene i seguenti metodi:
* `getId` che restituisce un id numerico di identificazione della baia
* `getPayload` che deve restituire il `Payload` attualmente disponibile nella baia (la classe `mars.platform.logics.Payload` che modella un payload ed ha alcune semplici proprietà: un *id* di tipo stringa, un *tipo* sempre stringa ed una *percentuale di ricarica*, espressa con un numero decimale compreso tra 0 e 1)
* `getRestorationInstant` che deve restituire l'istante in cui la carica del payload sarà completa
* `getPrepareMillis` che indica il numero di millisecondi necessari per effettuare il setup della baia prima che possa essere nuovamente disponibile per un nuovo payload
* `addRechargeListener` che consente di registrare una istanza di `BayListener` che riceva le notifiche di fine carica dei payload e di cambio di payload nella baia

Il nostro esempio non definisce un nuovo tipo di `Bay` ma sfrutta la classe già presente `mars.Platform.sim.SimulativeBay`.

### SimulativeBay

La classe `SimulativeBay` implementa una semplice baia in cui il tempo di setup, il payload disponibile e la velocità di ricarica vengono passati come parametri di inizializzazione del costruttore della classe:

```java
public SimulativeBay(int id, Optional<Payload> payload, double chargePerSecond, long prepareMillis)
```

Oltre ad implementare i metodi dell'interfaccia `Bay` la classe `SimulativeBay` definisce anche un metodo per simulare l'inserimento di un payload (`put`) e un metodo per simularne la rimozione (`remove`). In seguito vedremo come verranno utilizzati.

Il metodo `put` registra il payload ed invia una notifica `bayContentUpdate` agli eventuali ascoltatori:

```java
public void put(Payload payload) {
	if (this.payload.isPresent())
		throw new IllegalStateException("Bay already occupied by " + this.payload);
	if (payload == null)
		throw new NullPointerException("Null payload");
	this.payload = Optional.of(payload);
	listenerNotifier.notifyListeners(l -> l.bayContentUpdate(this));	
	if (payload.charge < 1)
		recharge1();
}
```

Prima di terminare, il metodo `put` richiama la procedura `recharge1` che simula l'attivazione immediata della ricarica elettrica del payload.

Lo scopo della procedura `recharge1`, in tandem con la procedura `recharge`, è quindi quello di simulare la ricarica completa del payload procedendo a step dell'1%.

E' importante notare come `recharge` sfrutti l'EventLoop `El` per pianificare l'avanzamento della carica in termini temporali, in modo da mantenere la coerenza dei tempi all'interno del simulatore. 

Al completamente della ricarica viene inviata una notifica `rechargeComplete` agli eventuali ascoltatori.

Il metodo `remove` annulla l'eventuale ricarica in atto, rende nullo il payload della bay e poi invia una notifica `bayContentUpdate` agli eventuali ascoltatori:

```java
public Payload remove() {
	if (this.payload.isEmpty())
		throw new IllegalStateException("No payload to remove");
	rechargingTimeout.cancel();
	try {
		return payload.get();
	} finally {
		this.payload = Optional.empty();
		listenerNotifier.notifyListeners(l -> l.bayContentUpdate(this));
	}
}
```

## Interfaccia PlatformAutomationSystem

Per il simulatore l'interfaccia `PlatformAutomationSystem` rappresenta il gestore delle operazioni (automatiche o manuali o miste) di pitstop di una platform.

Essa contiene alcuni metodi per recuperare informazioni sullo stato delle operazioni:

* `addPlatformAutomationListener` che consente di registrare un ascoltatore di tipo `PlatformAutomationListener` a cui vengono inviate le notifiche di drone atterrato (`droneLanded`) e di drone decollato (`droneTookOff`)
* `getLandedDroneId` che ritorna l'id del drone presente nella platform
* `getPayloadBays` che restituisce l'elenco delle baie di ricarica (da cui è possibile anche controllarne lo stato)
* `getServiceTime` che restituisce il tempo di servizio del pitstop

Essa contiene anche alcuni metodi di comando:
* `preparePayload` che comanda al sistema di preparare il payload presente in una specifica baia per l'utilizzo in un pitstop
* `startPitStop` che fa partire un pitstop e restituisce una istanza di `CompletionStage` per notificare la fine del pitstop
* `abortPitstop` che forza l'annullamento del pitstop corrente e comanda il riposizionamento del payload in una delle baie di ricarica

Anche per l'interfaccia `PlatformAutomationSystem` il nostro esempio sfrutta una classe già disponibile: `mars.platform.sim.dummy.DummyAutomationSystem`

### DummyAutomationSystem

La classe `DummyAutomationSystem` fornisce una implementazione pronta di `PlatformAutomationSystem` e rappresenta anche un esempio per la creazione di nuove.

Il costruttore della classe riceve in ingresso l'interfaccia verso il sistema di comunicazione basato su RabbitMQ, la propria stringa identificativa ed un collegamento alle funzionalità dell'`EventLoop` che sono anche qui fondamentali per simulare le operazioni di pitstop in un sistema di riferimento temporale coerente con il resto del simulatore.

```java
public DummyAutomationSystem(ComSystem comSystem, String myPltName, Consumer<Event> executor) throws IOException {
	this.executor = executor;
	this.myPltName = myPltName;
	signalDispatcher = comSystem.createSignalSubscriber(Signals.EVENT.name(), "mars.drone.#");
	signalDispatcher.register(Events.MARS_DRONE_LANDED, DroneLandedEvent.class, this::onDroneLanded);
	signalDispatcher.register(Events.MARS_DRONE_TOOK_OFF, DroneTookOffEvent.class, this::onDroneTookOff);
}
```

Come si vede, viene effettuata una sottoscrizione agli eventi `MARS_DRONE_LANDED` e `MARS_DRONE_TOOK_OFF` delle code di messaggi associate ai droni (`mars.drone.#`). Vengono contestualmente registrate le funzioni private `onDroneLanded` e `onDroneTookOff` di `DummyAutomationSystem` in modo che vengano eseguite ogni qual volta si verificassero tali eventi.

Tralasciamo ora l'implementazione dei metodi di recupero delle informazioni, che sono banali, e vediamo invece come sono stati implementati i metodi di comando.

Il metodo `preparePayload` simula il setup di un payload sulla flangia di installazione sulla plaform. Esso controlla che:
* non ci sia un'altra baia coinvolta in una precedente operazione di estrazione del payload
* non ci sia un payload già presente sulla flangia
* la baia contenga effettivamente un payload disponibile

Nel caso uno di questi controlli fosse negativo, viene sollevata una eccezione e l'operazione viene interrotta.

```java
if (unpreparingTimeout.isPresent()) {
	LOGGER.error("Cannot ready payload beacuse another is unpreparing");
	throw new CannotReadyPayload("Another payload is unpreparing");
}
if (readyPayload.isPresent()) {
	LOGGER.error("Cannot ready payload beacuse another is ready");
	throw new CannotReadyPayload("Another payload has been prepared");
}
final var bay = getBay(bayId);
final var payload = bay.getPayload();
if (payload.isEmpty()) {
	LOGGER.error("Cannot ready payload beacuse bay: {} is empty", bayId);
	throw new CannotReadyPayload("Bay " + bayId + " is empty");
}
```

In caso di controlli positivi, la procedura salva l'id della baia che verrà svuotata nella variabile `exchangingBay` e comanda alla baia la rimozione del payload. Poi calcola il tempo di spostamento del payload dalla baia alla flangia e, attraverso `El`, attende il tempo necessario affinchè il payload sia disponibile per l'installazione sulla flangia. Quando il payload arriva dalla flangia, la procedura simula l'installazione sulla flangia e poi, al termine, richiama l'evento `onPsReady` che è stato passato come parametro della procedura.

```java
bay.remove();
movingPayload = payload.get();
this.exchangingBay = bayId;
final long payloadMovementMillis = payloadMovementMillis(bay);
preparingTimeout = Optional.of(El.setTimeout(payloadMovementMillis, () -> {
	putInFlange(movingPayload);
	movingPayload = null;
	preparingTimeout = empty();
	onPsReady.run();
```

Il metodo `startPitStop` come prima cosa controlla che sia presente un payload in `readyPayload`, ovvero che ci sia un payload attualmente installato sulla flangia della platform. Dopodiché la procedura controlla che la baia salvata in `exchangingBay` sia effettivamente stata liberata. Se qualcuno dei controlli dovesse fallire, viene sollevata una eccezione e il processo si interrompe.

```java
public CompletionStage<Void> startPitStop(Runnable onPsUnprepared) {
	final var payloadToUse = readyPayload.orElseGet(() -> {	
		throw new RuntimeException("No payload prepared");
	});
	final Bay<?> bay = getBay(exchangingBay);
	if (bay.getPayload().isPresent()) {		
		throw new RuntimeException("Bay " + exchangingBay + " is NOT empty");
	}
	serviceMillis);
	final long endsInMillis = serviceMillis;
	final CompletableFuture<Void> cf = new CompletableFuture<Void>();
	El.setTimeout(endsInMillis, () -> onServiceFinished(payloadToUse, bay, cf, onPsUnprepared));
	return cf;
}
```

Se tutti i controlli danno esito positivo, il pitstop può avere inizio sempre attraverso la mediazione di `El` che attende un tempo definito in `serviceMillis` (proprietà pubblica impostata dall'esterno della classe) prima di far scattare l'evento `onServiceFinished`. La procedura ritorna una istanza di `CompletableFuture`, una sorta di *promessa* circa la chiusura delle operazioni che, come vedremo, verrà rispettata da `onServiceFinished`.

`onServiceFinished` viene quindi eseguita dall'event loop quando la simulazione di cambio payload del drone si è conclusa:

```java
private void onServiceFinished(final Payload payloadToUse, final Bay<?> bay, final 		CompletableFuture<Void> cf, Runnable onPsUnprepared) {
	cf.complete(null);
	final PayloadOnBoard pob = payloadOnBoard.orElseGet(() -> {
		throw new RuntimeException("Expected a payload on board");
	});
	this.readyPayload = Optional.of(new Payload(pob.payloadType(), pob.id(), pob.charge()));
	unpreparePitStop(onPsUnprepared);
}

```
La procedura come prima cosa notifica l'avvenuta installazione del payload sul drone attivando il trigger implicito nella variabile `cf` e scambia il riferimento del payload sulla flangia (`readyPayload`) con il payload che era precedentemente installato sul drone (`payloadOnBoard`).

Alla fine viene richiamata la procedura `unpreparePitStop` che simula il posizionamento del payload ora presente sulla flangia (quello che era installato sul drone) nella baia che si è  liberata in modo che possa essere ricaricato elettricamente.

La procedura verifica che non sia già in corso un precedente spostamento, che la baia di scambio (`exchangingBay`) sia effettivamente libera e che sulla flangia ci sia effettivamente un payload da trasferire (`readPayload`). Questi controlli sono necessari perchè, come vedremo, questa medesima funzione potrebbe essere eseguita anche durante una procedura di annullamento del pitstop.

La procedura di unprepare controlla che non sia stata schedulata tramite l'eventloop una contemporanea procedura di prepare (`preparingTimeout` è il riferimento). In caso affermativo la annulla. In caso contrario, libera la flangia dal payload (`readyPayload`) che diventa il payload da riposizionare nella baia (`movingPayload`) e notifica (`onPsUnprepared.run()`) che, per il drone, le operazioni di pitstop si sono concluse e che può riprendere il volo.

```java
private void unpreparePitStop(Runnable onPsUnprepared) {
	if (unpreparingTimeout.isPresent()) {
		return;
	}
	final var bay = getBay(exchangingBay);
	if (bay.getPayload().isPresent()) {
		throw new RuntimeException("Bay " + exchangingBay + " is NOT empty");
	}
	if (readyPayload.isEmpty()) {
		throw new RuntimeException("Cannot unprepare payload because none is ready");
	}
	preparingTimeout.ifPresentOrElse(t -> {
		t.cancel();
		preparingTimeout = empty();
	}, () -> {
		movingPayload = readyPayload.get();
		readyPayload = empty();
		LOGGER.info("Flange is free");
		onPsUnprepared.run();
	});
	final long payloadMovementMillis = payloadMovementMillis(bay);
	unpreparingTimeout = Optional.of(El.setTimeout(payloadMovementMillis, () -> {
		bay.put(movingPayload);
		movingPayload = null;
		unpreparingTimeout = empty();
	}));
}
```

In realtà il pitstop per la platform non si è ancora concluso: il payload va riposizionato in una baia per essere messo sotto carica.

Lo spostamento viene simulato ancora una volta con l'eventloop che, dopo `payloadMovementMillis`, comanda alla baia libera di prendersi cura del payload estratto.

La terza procedura di comando, `abortPitStop`, è la più semplice. Si limita ad invocare la procedura `unpreparePitstop` appena descritta:

```java
public void abortPitStop(Runnable r) {

	unpreparePitStop(r);
}
```

Terminate le procedure di controllo, diamo un'occhiata alla due procedure di notifica per gli eventi di drone atterrato e drone decollato.

In caso di atterraggio del drone proprio sulla platform simulata, la procedura `onDroneLanded` controlla che non ci sia già un drone presente (questo è un caso limite) e memorizza l'id del drone in `droneOnFlange` e il payload del drone in `payloadOnBoard`. Poi vengono inviate le notifiche ai sottoscrittori. Tutto il codice è seguito sempre attraverso l'eventloop.

```java
private void onDroneLanded(String category, String id, String agentName, DroneLandedEvent e) {
	executor.accept(() -> {
		if (myPltName.equals(e.pltName())) {
			if (droneOnFlange == null) {
				droneOnFlange = e.droneId();
				payloadOnBoard = Optional.of(e.payloadOnBoard());
				listenerNotifier.notifyListeners(l -> l.droneLanded(e.droneId()));
			} else {
				LOGGER.error("There is already a drone on flange: {}, cannot land drone: {}", droneOnFlange,
						e.droneId());
			}
		} else {
			LOGGER.info("Ignoring because it I am {} and drone landed on {}", myPltName, e.pltName());
		}
	});
}
```

Quando il drone è decollato, l'evento `OnDroneTookOff` annulla i riferimenti al drone (`droneOnFlange`) e al payload presente sul drone (`payloadOnBoard`). Sempre vengono notificati i sottoscrittori e sempre il codice viene eseguito all'interno dell'eventloop:

```java
private void onDroneTookOff(String category, String id, String agentName, DroneTookOffEvent e) {
	executor.accept(() -> {
		if (myPltName.equals(e.pltName())) {
			if (e.droneId().equals(droneOnFlange)) {
				droneOnFlange = null;
				payloadOnBoard = empty();
				listenerNotifier.notifyListeners(l -> l.droneTookOff(e.droneId()));
			} else {
				LOGGER.error("There is no drone on flange: {}, it is not possible that took off drone: {}",
						droneOnFlange, e.droneId());
			}
		}
	});
}
```

## Interfaccia PlatformInternals

L'interfaccia PlatformInternals ha solo 2 metodi:
* `getAutomationSystem` che restituisce una instanza di `PlatformAutomationSystem` (che definisce implicitamente anche una implementazione di `Bay`)
* `getGeoCoord` che restituisce la longitudine e la latitudine della platform

Anche per `PlatformInternals` il nostro esempio non fornisce una implementazione ma sfrutta una classe disponibile nella libreria: `mars.platform.sim.dummy.DummyPlatformInternals`.

La classe è veramente banale per cui non ne descriveremo il dettaglio.
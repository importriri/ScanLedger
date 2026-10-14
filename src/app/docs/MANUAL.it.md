# Registro QR — Manuale d'uso

Guida per l'operatore della postazione. Registro QR tiene l'elenco delle
coppie **etichetta + lotto** di una linea che non ha il PC del portale; a fine
turno (o quando serve) esporti un CSV, e AutoFillSuite lo registra sul portale
dalla modalità **Da file**.

Questo stesso testo si legge dentro il programma: **Impostazioni > Manuale**.

## 1. Prima configurazione (una volta per postazione)

1. Avvia il JAR (`java -jar ScanLedger.jar`, Java 8 o più recente). Al primo
   avvio il programma chiede il **nome della postazione** (per esempio `L2`):
   finisce nel nome dei file e dentro il CSV, così AutoFillSuite sa da che linea
   arriva. Senza il nome non si registra niente.
2. Apri le **Impostazioni** (l'ingranaggio, in alto a destra). Ogni
   impostazione si salva appena la cambi.
3. **Cartelle**:
   - **Dati** — dove vivono il giornale delle giornate e l'archivio. Vuoto =
     `RegistroQR` nella cartella dell'utente. Vale dal prossimo avvio.
   - **Export** — dove finiscono i CSV. Vuoto = `export` dentro la cartella
     dati. Il programma non cancella e non sovrascrive mai un file qui.
   - **Copia** — facoltativa: la chiavetta o la cartella di rete da cui
     AutoFillSuite prenderà i file. Ogni export ci viene copiato insieme al
     giornale.
4. **Codici** (facoltativo ma consigliato): descrivi i due QR con
   un'espressione regolare (**Modello QR 1**, **Modello QR 2**), le stesse di
   AutoFillSuite. Così il programma riconosce il lotto sparato al posto
   dell'etichetta. Un'espressione scritta male si colora di rosso e viene
   ignorata.
5. **Suoni**: un suono acuto corto = coppia registrata; due suoni bassi =
   errore. **Aspetto**: tema scuro (Mocha) o chiaro (Latte), vale al riavvio.

La finestra ricorda posizione e dimensione. Se l'ultima posizione era su un
monitor che non c'è più, si riapre su quello principale.

## 2. Scansione

Per ogni pezzo spari due QR: l'**etichetta** in **QR 1**, il **lotto** in
**QR 2**.

1. Spara QR 1: il cursore passa da solo a QR 2.
2. Spara QR 2: la coppia viene **scritta su disco** e solo dopo compare in
   tabella, in cima. Suono acuto, riquadro verde **COPPIA REGISTRATA — #n**,
   campi vuoti, cursore di nuovo su QR 1.
3. Il contatore **COPPIE OGGI** conta le righe vive della giornata.

Lo scanner può chiudere ogni lettura con **INVIO** o con **TAB**: funzionano
tutti e due. Su QR 1 il TAB sposta solo il cursore; su QR 2 vale come INVIO.

La pastiglia in alto **salvato su disco hh:mm:ss** è l'ora dell'ultima
scrittura riuscita. Se diventa rossa (**DISCO NON SCRIVE**) il programma smette
di accettare coppie e riprova da solo ogni 2 secondi: la coppia che hai appena
sparato **non** è registrata, rimane nei campi.

### 2.1 Ho sparato il lotto in QR 1

Se hai descritto i due QR (§1), il programma se ne accorge: riquadro giallo
**CODICI INVERTITI**, niente entra in tabella. Premi **Scambia e registra
(F2)**: i due campi si invertono e la coppia viene registrata. Con **Raddrizza
da solo** acceso (Impostazioni > Codici) lo fa senza chiedere.

**Scambia (F2)** funziona anche a mano, in qualunque momento: inverte QR 1 e
QR 2 prima di registrare.

Un codice che non ha la forma di nessuno dei due QR: riquadro rosso
**FORMATO NON RICONOSCIUTO**, i campi restano pieni, controlli tu.

### 2.2 Già registrata oggi

Un'etichetta già presente nella giornata viene **sempre** rifiutata: riquadro
rosso **GIÀ REGISTRATA OGGI** con il numero della riga che la contiene. Sul
portale vince l'ultima registrazione: una seconda riga per la stessa etichetta
sarebbe una correzione silenziosa. Se il lotto era sbagliato, **modifica la
riga** (§4). **Vai alla riga** la mostra in tabella.

### 2.3 Dopo un errore

Dopo un rifiuto i due campi restano pieni e QR 1 è selezionato: lo sparo
successivo **sovrascrive** QR 1 e svuota QR 2, quindi puoi continuare a
sparare senza toccare la tastiera. **Svuota campi (Esc)** li pulisce a mano.

### 2.4 Annulla ultima (Ctrl+Z)

Toglie l'**ultima** coppia aggiunta (o l'ultimo intervallo intero). Non risale
alle righe più vecchie e non tocca niente che sia già stato esportato. La riga
annullata resta in tabella come **ELIMINATA** e si può ripristinare.

## 3. Intervallo

Per N etichette consecutive con lo stesso lotto.

1. Scegli **INTERVALLO** in alto a sinistra.
2. **Primo**: spara la prima etichetta. **Lotto**: spara il lotto.
   **Quantità**: quante etichette.
3. Il riquadro mostra l'anteprima, da quale a quale etichetta, e controlla le
   sovrapposizioni: **NESSUNA SOVRAPPOSIZIONE** in verde, oppure
   **SOVRAPPOSIZIONE** con le etichette già in tabella.
4. **Aggiungi N righe**: l'intervallo entra tutto insieme, **tutte le righe o
   nessuna**. Se anche una sola etichetta è già in tabella, l'intervallo è
   rifiutato intero.

Le ultime cifre dell'etichetta sono quelle che crescono (**Cifre mobili** in
Impostazioni > Codici, di solito 3). Un intervallo che arriverebbe oltre (998
+ 5 = 1000) viene rifiutato: **L'INTERVALLO SFORA**. Dividilo in due.

Lotto nel campo **Primo**: riquadro giallo **CODICI INVERTITI**, premi **F2**.
**Esc** svuota i campi; **Ctrl+Z** annulla l'ultimo intervallo intero.

## 4. Correggere una riga

Seleziona la riga in tabella e premi **Modifica (Invio)** o fai doppio click.
**Elimina (Canc)** la toglie, **Ripristina** riporta una riga eliminata.
I filtri sopra la tabella (Tutte, Modificate, Eliminate, Esportate, Corrette) e
la ricerca aiutano a trovarla.

### 4.1 Prima dell'export

Finché una riga non è mai uscita in un CSV puoi cambiare **etichetta e
lotto**, oppure eliminarla. Il motivo è facoltativo. La riga diventa
**MODIFICATA** e nel prossimo export esce con il valore nuovo. Il programma
rifiuta una modifica che crea un doppione o che ha la forma sbagliata.

### 4.2 Dopo l'export

Una riga esportata è già (o sarà presto) sul portale:

- **l'etichetta non si cambia** e **la riga non si elimina**. Se l'etichetta
  era sbagliata, serve **Delete item** sul portale, poi spari quella giusta
  come coppia nuova;
- **il lotto si corregge**: **Salva correzione**. La riga diventa
  **CORRETTA** e il prossimo export la porta come **CORREZIONE**, con il lotto
  vecchio accanto. AutoFillSuite la registra di nuovo con il lotto giusto: sul
  portale vince l'ultima.

Corretta due volte prima dell'export? Esce una sola correzione, con l'ultimo
lotto. Riportata al lotto che era stato esportato? La correzione si annulla da
sola: non esce niente.

## 5. Export e backup

1. **Esporta CSV** (in alto) apre il riepilogo: coppie nuove, correzioni,
   righe modificate a mano, eliminate (escluse), doppioni e codici fuori forma,
   il nome del file e la cartella di copia.
2. **Esporta**. Il file si chiama
   `registro_<postazione>_<giornata>_<NN>.csv`; NN cresce a ogni export della
   giornata. Ogni export contiene **solo** le righe nuove e le correzioni
   dall'export precedente.
3. Il programma rilegge il file appena scritto: se non torna, lo cancella e non
   segna niente come esportato.
4. Poi copia il file (e il giornale) nella cartella **Copia**. Se la chiavetta
   non c'è o viene tolta a metà, la copia resta **IN SOSPESO**: banner giallo,
   **Riprova ora**, e il programma riprova da solo a ogni avvio e a ogni
   export. L'export è valido lo stesso: è nell'archivio.

Gli export di una giornata vanno registrati in AutoFillSuite **in ordine**
(01, poi 02...). AutoFillSuite lo controlla.

### 5.1 Storico, rigenera, riesporta

**Impostazioni > Storico** elenca le giornate e i loro export.

- **Rigenera il NN**: se un file è andato perso, il programma lo ricostruisce
  dal giornale, **identico byte per byte**, e controlla che l'impronta sia
  quella registrata al momento dell'export.
- **Apri cartella archivio**: la cartella export.
- **Riesporta giornata intera**: un file nuovo con tutte le righe vive, tutte
  come nuove (`riesportazione=1`). Serve solo se il portale va ricostruito da
  capo: non cambia lo stato delle righe.

## 6. Chiudere la giornata

**Chiudi giornata** archivia la giornata: diventa di sola lettura e la
prossima coppia ne apre una nuova. Si chiude solo quando non c'è niente da
esportare: altrimenti il programma propone **Esporta adesso**.

Una giornata **non** si chiude da sola a mezzanotte: un turno che passa la
mezzanotte continua nella stessa giornata. Se all'avvio trovi la giornata di
ieri ancora aperta, il banner propone di esportarla o di chiuderla. Se riapri
una giornata nella stessa data (dopo averla chiusa), il file del giornale prende
`_2`, `_3`... e la numerazione di righe ed export continua.

## 7. Cosa succede se il PC si spegne

Niente di confermato si perde. Ogni coppia, intervallo, modifica ed
eliminazione viene scritta nel giornale e sincronizzata sul disco **prima** di
comparire in tabella. Non c'è un tasto Salva e non serve chiudere il programma
in un modo particolare.

Al riavvio dopo uno spegnimento:

- **Ripresa giornata — N coppie, nessuna persa**: la tabella è stata
  ricostruita dal giornale, puoi continuare;
- **Ultima coppia scartata: ...**: il PC si è spento proprio mentre scriveva
  quella coppia; non è registrata. **Sparala di nuovo**;
- **Export recuperato: ...**: il PC si è spento subito dopo aver scritto un
  CSV; ora è registrato nel giornale. Il file è valido;
- **COPIA IN SOSPESO**: la copia sulla chiavetta non era finita; riprova da
  sola.

Se il giornale risulta danneggiato a metà (non da uno spegnimento: da un disco
guasto), il programma ne fa una copia accanto (`.corrotto-...`), apre la
giornata in **sola lettura** e propone **Esporta righe leggibili**.

## 8. Cosa NON fare

- **Non aprire il CSV in Excel e risalvarlo.** Excel cambia separatori, zeri
  iniziali e codifica: AutoFillSuite se ne accorge dall'impronta e lo rifiuta
  come **MODIFICATO**. Per guardarlo aprilo e chiudi **senza salvare**; se
  l'hai risalvato, **Rigenera** l'originale dallo Storico.
- **Non rinominare né spostare i file** nella cartella export: il nome porta
  postazione, giornata e numero.
- **Non toccare i file in `giornate/`** (il giornale): è la memoria del
  programma.
- **Non aprire due volte il programma** sulla stessa cartella dati: il
  secondo si rifiuta di partire.
- **Non correggere un lotto sul portale a mano** per righe che verranno
  ancora esportate: correggilo qui, esce come CORREZIONE.

## 9. Problemi comuni

| Sintomo | Causa e rimedio |
|---|---|
| CODICI INVERTITI | Hai sparato il lotto in QR 1: premi **Scambia e registra (F2)**. |
| GIÀ REGISTRATA OGGI | L'etichetta è già in tabella: se il lotto è sbagliato modifica quella riga (**Vai alla riga**). |
| FORMATO NON RICONOSCIUTO | Nessuno dei due codici ha la forma attesa: controlla il pezzo, o i modelli in Impostazioni > Codici. |
| MANCA LA POSTAZIONE | Imposta il nome in Impostazioni > Postazione. |
| DISCO NON SCRIVE (pastiglia rossa) | Disco pieno o cartella dati non raggiungibile. Il programma riprova ogni 2 secondi; l'ultima coppia non è registrata, sparala di nuovo quando torna verde. |
| L'INTERVALLO SFORA | Le cifre mobili supererebbero 999: dividi l'intervallo. |
| SOVRAPPOSIZIONE | Alcune etichette dell'intervallo sono già in tabella: cambia il primo o la quantità. |
| «Etichetta non modificabile» | La riga è già esportata: si corregge solo il lotto. Per l'etichetta serve Delete item sul portale. |
| «Non si può eliminare» | La riga è già esportata: vedi sopra. |
| COPIA IN SOSPESO | Chiavetta assente o tolta: rimettila e premi **Riprova ora**. |
| NIENTE DA ESPORTARE | Nessuna riga nuova e nessuna correzione dall'ultimo export. |
| «Esiste già ... nell'archivio» | Nella cartella export c'è già un file con quel nome: qualcuno l'ha copiato lì a mano. Toglilo e riesporta. |
| AutoFillSuite dice MODIFICATO | Il CSV è stato risalvato (Excel): **Rigenera** dallo Storico e portagli quello. |
| AutoFillSuite dice TRONCATO | Il file è arrivato a metà (chiavetta tolta): ricopialo, o **Rigenera**. |
| File export cancellato per errore | Impostazioni > Storico > **Rigenera**: torna identico. |
| La giornata di ieri è ancora aperta | Esportala e chiudila dal banner, o continua: le coppie nuove vanno lì finché è aperta. |
| Giornale danneggiato: sola lettura | **Esporta righe leggibili**, poi chiudi la giornata. La copia del file danneggiato resta accanto al giornale. |
| Il programma non parte: «già aperto» | C'è già un Registro QR aperto su questa cartella dati: usa quello. |

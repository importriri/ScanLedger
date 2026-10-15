# Prova in reparto — ScanLedger (Registro QR) 1.0.0

Passi da fare sul PC vero della postazione, con il lettore QR vero, prima di
usare Registro QR per il lavoro di tutti i giorni. Servono circa 30 minuti,
una chiavetta USB e qualche etichetta di prova (le coppie di prova **non**
vanno registrate sul portale: alla fine la giornata di prova si chiude e i suoi
file si mettono da parte).

Per ogni passo: cosa fare, cosa deve succedere. Se un passo non va come scritto,
**fermarsi**, non usare il programma e annotare cosa si è visto (con una foto
dello schermo se si può).

Prima di cominciare:

- avviare `ScanLedger.jar`, dare un nome di prova alla postazione (per esempio
  `PROVA`) e, in Impostazioni > Cartelle, mettere la chiavetta come cartella
  **Copia**;
- tenere aperta la cartella dati (il percorso è in basso a sinistra nella
  finestra) e la cartella export (Impostazioni > Storico > Apri cartella
  archivio).

---

## 1. Venti coppie e corrente staccata

1. Scansione. Sparare **20 coppie** di prova al ritmo normale, senza fermarsi.
   Ogni coppia deve dare il suono acuto e comparire in cima alla tabella.
2. Subito dopo l'ultima coppia, **staccare la spina** del PC (non spegnere da
   Windows; su un portatile togliere anche la batteria, o tenere premuto il
   tasto di accensione).
3. Riaccendere e avviare Registro QR.

Deve succedere:

- compare il banner blu **Ripresa giornata del ... — 20 coppie, nessuna
  persa**;
- in tabella ci sono **tutte e 20** le coppie, con gli stessi numeri e gli
  stessi lotti di prima;
- se il PC si è spento proprio durante l'ultima coppia, al suo posto c'è il
  banner **Ultima coppia scartata: ...** con etichetta e lotto, e la tabella ne
  ha 19: risparando quella coppia si torna a 20. Una coppia che ha dato il
  suono acuto **non** deve mai mancare.

Variante, se c'è tempo: staccare la spina **mentre** si spara (fra QR 1 e
QR 2, o subito dopo QR 2). Vale la stessa regola: niente di confermato manca.

## 2. Export con la chiavetta tolta a metà

1. Con le 20 coppie del passo 1, premere **Esporta CSV**. Il riepilogo dice
   **Coppie nuove: 20** e mostra il nome del file e la cartella di copia
   **(presente)**.
2. Premere **Esporta** e, **subito**, sfilare la chiavetta.

Deve succedere:

- il programma dice **ESPORTATO** (il file è nella cartella export) e, se la
  copia non ha fatto in tempo, **COPIA IN SOSPESO** con il motivo;
- in alto resta il banner giallo **COPIA IN SOSPESO**, e in basso a destra
  **copia IN SOSPESO**;
- rimettere la chiavetta e premere **Riprova ora**: il banner sparisce; sulla
  chiavetta ci sono il CSV **e** il giornale della giornata;
- il CSV sulla chiavetta e quello nella cartella export sono **identici**
  (stessa dimensione; aprendoli in un editor di testo, stessa ultima riga
  `#fine;righe=20;sha256=...`).

Se la copia era già finita quando la chiavetta è stata tolta, ripetere con un
export nuovo (sparare un paio di coppie, Esporta, togliere la chiavetta prima
di premere Esporta): il risultato deve essere lo stesso **COPIA IN SOSPESO**,
poi **Riprova ora**.

Poi, in AutoFillSuite (modalità **Da file**), aprire il file **dalla
chiavetta**: le righe di controllo di impronta e righe devono essere verdi.
**Non** avviare la registrazione: è una giornata di prova.

## 3. Correzione di un lotto dopo l'export

1. In tabella, scegliere una riga **ESPORTATA** e premere **Modifica**.
2. Il campo Etichetta è bloccato ("l'etichetta non si cambia: è già
   registrata"). Cambiare il **lotto** e premere **Salva correzione**.
3. Guardare il tasto **Elimina** con la stessa riga selezionata.
4. Premere **Esporta CSV**, poi **Esporta**.

Deve succedere:

- dopo il passo 2 la riga dice **CORRETTA**, con il lotto nuovo;
- al passo 3 **Elimina** è spento, e sotto la tabella si legge che la riga si
  corregge solo nel lotto (è già sul portale: si toglie solo con Delete item);
- il riepilogo del passo 4 dice **Correzioni di righe già esportate: 1**;
- il file nuovo (`..._02.csv` o successivo) contiene **solo** quella riga, con
  tipo `CORREZIONE`, il lotto nuovo e, nell'ultima colonna, il lotto
  **vecchio**;
- in AutoFillSuite, aprendo prima l'export 01 e poi questo, l'ordine è
  accettato; aprendo questo **prima** dell'01, AutoFillSuite lo segnala
  **FUORI ORDINE**. Anche qui: **non** avviare la registrazione.

## 4. Rigenerazione di un export cancellato

1. Annotare l'impronta dell'export 01: Impostazioni > **Storico**, giornata di
   prova, colonna **Impronta**.
2. Nella cartella export, **cancellare** il file `..._01.csv` (o spostarlo sul
   desktop).
3. Storico, selezionare l'export **01**, premere **Rigenera il 01** e scegliere
   una cartella (per esempio il desktop).

Deve succedere:

- **RIGENERATO IDENTICO**, con l'impronta che coincide con quella del giornale;
- se il file originale è stato spostato e non cancellato, i due file sono
  identici byte per byte (stessa dimensione; con `certutil -hashfile <file>
  SHA256` da un prompt dei comandi, stessa impronta);
- in AutoFillSuite il file rigenerato ha impronta e righe verdi.

Variante: aprire il CSV in **Excel**, salvarlo, e aprirlo in AutoFillSuite.
Deve essere rifiutato come **MODIFICATO**. È il motivo per cui il manuale dice
di non risalvare mai il CSV.

---

## Alla fine

1. **Chiudi giornata** sulla giornata di prova (con tutto esportato il
   programma la chiude; altrimenti chiede di esportare prima).
2. Spostare i file di prova (`registro_PROVA_...`) dalla cartella export e
   dalla chiavetta in una cartella a parte, così nessuno li registra per
   sbaglio.
3. Rimettere il nome vero della postazione in Impostazioni > Postazione: vale
   dalla prossima giornata.

Annotare per ogni passo: **ok** o cosa si è visto. Solo con tutti e quattro
**ok** il programma va in uso.

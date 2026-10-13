# Contratto CSV — formato 1

Unico punto di contatto fra **Registro QR** (scrive) e **AutoFillSuite** (legge).
Va copiato **identico** in tutte e due le repo (`docs/CSV-FORMAT.md`), insieme ai
cinque file campione in `test/fixtures/`. Le due suite di test leggono gli stessi
campioni: se un'app cambia il formato, i test dell'altra diventano rossi. È questo
che sostituisce una libreria condivisa.

## Come si comporta il portale (il motivo di tutto il resto)

Ricavato dal portale finto `test-site/lifecycle-test.html` di AutoFillSuite, dai test
di `RegistrationVerifier` e da come lo usa l'operatore:

- **Append-only.** Ogni SAVE aggiunge una riga. L'export CSV contiene **tutto lo
  storico**, in ordine cronologico, separato da `|`, una riga per evento.
- **Vince l'ultima.** Registrare di nuovo la stessa etichetta con un altro lotto è
  una **correzione**: per il portale vale l'ultima riga. La prima resta nello storico.
- **La stampa** aggiunge una riga col lotto vuoto (`etichetta|`), che non è una
  registrazione.
- **I lotti sono testo libero**: contengono `;`, `"`, doppi spazi, punti, barre.
  Esempio vero (dai test di AutoFillSuite):
  `CA HVCO 50 T 048 3-2; M-CA HVCO 50 T 048 3-2  Rev.04; Serial No.: 000066076 ; 2023/12/11;A`.
  Un lotto si confronta **esatto**, doppi spazi compresi. Solo gli spazi ai bordi si
  tolgono (`trim()`, come fanno i campi di AutoFillSuite).

Conseguenze:

1. Una correzione dopo l'export **si può** propagare: basta registrare di nuovo con il
   lotto giusto. Il CSV quindi porta anche righe `CORREZIONE`.
2. Registrare due volte lo stesso file non cambia il valore effettivo se nel frattempo
   non è cambiato niente, ma sporca lo storico (doppioni nel verificatore).
3. **Il pericolo vero è l'ordine.** Un file **vecchio** registrato **dopo** una
   correzione rimette il lotto sbagliato, e in silenzio, perché vince l'ultima. Per
   questo AutoFillSuite importa i file di una giornata solo in ordine di `export`.

## Forma del file

```
#registro-qr;formato=1;postazione=L2;giornata=2026-10-13;export=2;creato=2026-10-13T17:28:41
n;etichetta;lotto;ora;modo;tipo;modificata;lotto_prima
2;209531.001_01-01_2612000232;900612202601310009;2026-10-13T13:10:05;SCAN;CORREZIONE;1;900612202601310007
7;209531.001_01-01_2612000237;900612202601310009;2026-10-13T13:12:30;SCAN;NUOVA;0;
8;209531.001_01-01_2612000238;"LOTTO ""B"" 7";2026-10-13T13:14:02;SCAN;NUOVA;0;
#fine;righe=3;sha256=c86f69ec88fa844a519bffad69deef42abb5090ded5ddce87d9a3799852fc78f
```

| Elemento | Regola |
|---|---|
| Codifica | UTF-8 **senza** BOM in scrittura. In lettura il BOM si toglie se c'è (Excel lo aggiunge quando risalva). |
| Fine riga | `\r\n` in scrittura. In lettura si accettano `\r\n` e `\n`. |
| Separatore | `;`, **lo stesso di `RunReport`** di AutoFillSuite. |
| Virgolette | Un campo si mette fra `"` **se e solo se** contiene `;` o `"`, e dentro le `"` si raddoppiano. È esattamente `RunReport.esc` (senza il caso a capo, che qui non esiste). |
| Caratteri vietati | TAB, CR, LF dentro un campo: rifiutati già allo sparo, quindi non arrivano mai nel file. |
| Riga 1 | `#registro-qr;chiave=valore;...`. Obbligatorie: `formato`, `postazione`, `giornata` (yyyy-MM-dd), `export` (progressivo nella giornata, da 1), `creato` (ISO locale al secondo). Facoltativa: `riesportazione=1`. Chiavi sconosciute: si ignorano. |
| Riga 2 | esattamente `n;etichetta;lotto;ora;modo;tipo;modificata;lotto_prima`. |
| `n` | identità della riga nella giornata, assegnata quando nasce, **mai** rinumerata. Unico dentro un file. Fra file diversi lo stesso `n` torna solo come `CORREZIONE`. |
| `ora` | ISO locale al secondo: quando è nata la riga, o per una `CORREZIONE` quando è stata corretta. |
| `modo` | `SCAN` o `INTERVALLO`. |
| `tipo` | `NUOVA` (mai esportata prima) o `CORREZIONE` (esportata in un file precedente, poi cambiato il lotto). |
| `modificata` | `1` se qualcuno l'ha toccata a mano, prima o dopo l'export. |
| `lotto_prima` | solo per `CORREZIONE`: il lotto che il portale dovrebbe avere adesso. Vuoto per `NUOVA`. |
| Ultima riga | `#fine;righe=N;sha256=H`. `N` = righe dati. |

### L'impronta

`H` = SHA-256, esadecimale minuscolo, dei byte UTF-8 di **ogni riga dalla 1 alla
penultima** (tutto tranne `#fine`), così come è scritta (virgolette comprese), ciascuna
**senza** il terminatore originale e **seguita da un solo `\n`**. Non cambia se il file
passa da `\r\n` a `\n`; cambia se cambia un carattere qualsiasi del contenuto, anche
solo la posizione di una virgoletta (Excel che risalva). Solo
`java.security.MessageDigest`, nessuna libreria.

## Export incrementale

Ogni export contiene **solo**:
- le righe `NUOVA` non ancora esportate e non eliminate;
- le righe già esportate il cui **lotto** è stato corretto dopo l'ultimo export (`CORREZIONE`, una volta sola, con il valore più recente).

Dopo un export riuscito l'**etichetta** di una riga esportata non si può più cambiare,
e la riga non si può più eliminare: sul portale c'è già. L'interfaccia lo dice ("è già
sul portale: per toglierla serve Delete item sul portale"). Il **lotto** invece si
può correggere, e la correzione finisce nel prossimo export.

**Riesporta giornata** (dallo Storico, per un file perso): scrive tutte le righe vive
con il valore attuale, tutte come `NUOVA`, e aggiunge `riesportazione=1`. Ha un numero
`export` suo, nuovo. AutoFillSuite su questi file fa **sempre** il pre-volo (sotto).

## Backup degli export

Ogni file esportato:
1. resta per sempre nell'archivio di Registro QR (`<dati>/export/`), mai cancellato da nessuno, nemmeno dal programma;
2. si copia nella **cartella di copia** se configurata (chiavetta o rete). Se in quel momento non c'è, la copia resta **in sospeso** e il programma la ritenta (a ogni avvio e a ogni export) e lo mostra finché non riesce;
3. si può **rigenerare identico byte per byte dal giornale** (Storico → Rigenera): il giornale registra cosa è uscito in ogni export, si rilegge fino a quel punto e si riscrive il file. L'impronta deve coincidere con quella annotata nel giornale, altrimenti errore. È anche la prova che il giornale è integro.

AutoFillSuite, a sua volta, tiene una copia di ogni CSV importato nella sua cartella
report (`importazioni/`), con l'impronta nel nome. Così il file sulla chiavetta si può
perdere dopo l'import senza perdere niente.

## Cosa fa AutoFillSuite con un file

In ordine, e **tutto prima del primo click del robot**:

1. `formato` deve essere `1` (altrimenti rifiuta e dice quale ha trovato).
2. `#fine` presente e `righe` coincide → altrimenti **file troncato**, rifiuto.
3. Impronta → altrimenti **file modificato fuori da Registro QR**: rosso, si sblocca solo con conferma esplicita.
4. Forma dei codici con le regex di ScanGuard già configurate. Le righe fuori forma si mostrano e **non** partono.
5. **Ordine**: per la stessa `postazione` + `giornata`, il file `export=k` si accetta solo se `k-1` è completato e nessun `k' > k` è già stato registrato. Un file fuori ordine rimetterebbe lotti già corretti. Sbloccabile solo con conferma esplicita che lo spiega.
6. **Registro dei file**: stessa impronta già completata → rifiuto (sbloccabile). Stessa impronta interrotta → "Riprendi da riga n".
7. **Pre-volo** (consigliato acceso, obbligatorio per `riesportazione=1`): un Export dal portale, poi per ogni riga si guarda l'**ultimo** lotto non vuoto di quell'etichetta:
   - uguale al lotto del file → `GIÀ PRESENTE`, si salta;
   - riga `NUOVA`, etichetta assente → si invia;
   - riga `CORREZIONE`, ultimo lotto = `lotto_prima` → si invia;
   - qualunque altro caso → **CONFLITTO**: si ferma tutto e si mostra la riga (qualcuno l'ha registrata in un altro modo; decide l'operatore, mai il programma).

## File campione (in `test/fixtures/` di entrambe le repo)

| File | Cosa contiene | Esito atteso |
|---|---|---|
| `contratto-v1-export1.csv` | export 1: 5 righe, `n` 1,2,3,4,6 (la 5 eliminata), la 4 modificata, la 6 col lotto libero con `;` e doppi spazi | valido, impronta `d570b815243882baae5e2a86912bb220f2440fb74c91e472f001b696f8a788db` |
| `contratto-v1-export2.csv` | export 2: correzione della riga 2, righe nuove 7 e 8 (la 8 con `"` nel lotto) | valido, impronta `c86f69ec88fa844a519bffad69deef42abb5090ded5ddce87d9a3799852fc78f` |
| `contratto-v1-manomesso.csv` | export 1 con un lotto cambiato a mano | file modificato |
| `contratto-v1-troncato.csv` | export 1 senza `#fine` | file troncato |
| `contratto-v1-risalvato-excel.csv` | export 1 con BOM e virgolette in più, come lo lascia Excel | file modificato (il BOM da solo non basterebbe: sono le virgolette) |

`_genera_campioni.py` è solo un promemoria di come sono stati costruiti: **non va
copiato nelle repo** e i campioni non si rigenerano. Se cambia il formato, è
`formato=2`.

## Versioni future

Un cambio incompatibile = `formato=2`, e AutoFillSuite deve leggere 1 e 2 finché
esistono file vecchi in giro. Aggiungere una chiave all'intestazione **non** è un
cambio di formato.

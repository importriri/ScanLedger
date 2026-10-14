# Registro QR — User Manual

A guide for the station operator. Registro QR keeps the list of **label + lot**
pairs of a line that has no portal PC; at the end of the shift (or whenever
needed) you export a CSV, and AutoFillSuite registers it on the portal from its
**Da file** mode.

The same text is available inside the program: **Impostazioni > Manuale**. The
screen is in Italian; the names in bold are the words you see on it.

## 1. First setup (once per station)

1. Start the JAR (`java -jar ScanLedger.jar`, Java 8 or newer). On the first
   start the program asks for the **station name** (for example `L2`): it goes
   into the file names and into the CSV, so AutoFillSuite knows which line the
   file comes from. Nothing can be recorded without it.
2. Open **Impostazioni** (the gear, top right). Every setting is saved as soon
   as you change it.
3. **Cartelle** (folders):
   - **Dati** — where the day journals and the archive live. Empty =
     `RegistroQR` in the user's home folder. Applies from the next start.
   - **Export** — where the CSV files go. Empty = `export` inside the data
     folder. The program never deletes or overwrites a file here.
   - **Copia** — optional: the USB stick or network folder AutoFillSuite will
     take the files from. Every export is copied there together with the
     journal.
4. **Codici** (optional but recommended): describe the two QR codes with a
   regular expression (**Modello QR 1**, **Modello QR 2**), the same ones as in
   AutoFillSuite. The program then recognises a lot scanned in place of the
   label. A broken expression turns red and is ignored.
5. **Suoni**: one short high tone = pair recorded; two low tones = error.
   **Aspetto**: dark (Mocha) or light (Latte) theme, applied at the next start.

The window remembers its position and size. If the last position was on a
monitor that is no longer there, it reopens on the main one.

## 2. Scanning (SCANSIONE)

For each piece you scan two QR codes: the **label** into **QR 1**, the **lot**
into **QR 2**.

1. Scan QR 1: the caret moves to QR 2 by itself.
2. Scan QR 2: the pair is **written to disk** and only then shows in the
   table, at the top. High tone, green box **COPPIA REGISTRATA — #n**, fields
   cleared, caret back on QR 1.
3. The **COPPIE OGGI** counter counts the live rows of the day.

The scanner may end each read with **ENTER** or with **TAB**: both work. On
QR 1, TAB only moves the caret; on QR 2 it counts as ENTER.

The pill at the top, **salvato su disco hh:mm:ss**, is the time of the last
successful write. If it turns red (**DISCO NON SCRIVE**, the disk does not
write) the program stops accepting pairs and retries on its own every 2
seconds: the pair you just scanned is **not** recorded, it stays in the fields.

### 2.1 I scanned the lot into QR 1

If you described the two codes (section 1), the program notices: yellow box
**CODICI INVERTITI**, nothing goes into the table. Press **Scambia e registra
(F2)**: the two fields are swapped and the pair is recorded. With **Raddrizza
da solo** on (Impostazioni > Codici) it does that without asking.

**Scambia (F2)** also works by hand at any time: it swaps QR 1 and QR 2
before recording.

A code that matches neither shape: red box **FORMATO NON RICONOSCIUTO**, the
fields stay filled, you check.

### 2.2 Already recorded today

A label already present in the day is **always** refused: red box **GIÀ
REGISTRATA OGGI** with the number of the row holding it. On the portal the last
registration wins: a second row for the same label would be a silent
correction. If the lot was wrong, **edit the row** (section 4). **Vai alla
riga** shows it in the table.

### 2.3 After an error

After a refusal both fields stay filled and QR 1 is selected: the next scan
**overwrites** QR 1 and empties QR 2, so you can keep scanning without
touching the keyboard. **Svuota campi (Esc)** clears them by hand.

### 2.4 Undo last (Ctrl+Z)

Removes the **last** pair added (or the whole last range). It does not walk
back into older rows and never touches anything already exported. The undone
row stays in the table as **ELIMINATA** and can be restored.

## 3. Range (INTERVALLO)

For N consecutive labels with the same lot.

1. Choose **INTERVALLO** at the top left.
2. **Primo**: scan the first label. **Lotto**: scan the lot. **Quantità**: how
   many labels.
3. The box previews the range, from which label to which, and checks for
   overlaps: green **NESSUNA SOVRAPPOSIZIONE**, or **SOVRAPPOSIZIONE** with the
   labels already in the table.
4. **Aggiungi N righe**: the range goes in all together, **all rows or none**.
   If even one label is already in the table, the whole range is refused.

The last digits of the label are the ones that grow (**Cifre mobili** in
Impostazioni > Codici, usually 3). A range that would go past them (998 + 5 =
1000) is refused: **L'INTERVALLO SFORA**. Split it in two.

A lot in the **Primo** field: yellow box **CODICI INVERTITI**, press **F2**.
**Esc** clears the fields; **Ctrl+Z** undoes the whole last range.

## 4. Correcting a row

Select the row in the table and press **Modifica (Invio)** or double-click it.
**Elimina (Canc)** removes it, **Ripristina** brings a deleted row back. The
filters above the table (Tutte, Modificate, Eliminate, Esportate, Corrette) and
the search help you find it.

### 4.1 Before the export

As long as a row has never gone out in a CSV you can change **label and lot**,
or delete it. The reason is optional. The row becomes **MODIFICATA** and goes
out with the new value in the next export. The program refuses an edit that
creates a duplicate or has the wrong shape.

### 4.2 After the export

An exported row is already (or soon will be) on the portal:

- **the label cannot change** and **the row cannot be deleted**. If the label
  was wrong, it takes **Delete item** on the portal, then you scan the right
  one as a new pair;
- **the lot can be corrected**: **Salva correzione**. The row becomes
  **CORRETTA** and the next export carries it as a **CORREZIONE**, with the old
  lot next to it. AutoFillSuite registers it again with the right lot: on the
  portal the last one wins.

Corrected twice before the export? One correction goes out, with the last lot.
Set back to the lot that was exported? The correction cancels itself: nothing
goes out.

## 5. Export and backup

1. **Esporta CSV** (at the top) opens the summary: new pairs, corrections,
   rows edited by hand, deleted rows (left out), duplicates and off-shape
   codes, the file name and the copy folder.
2. **Esporta**. The file is named
   `registro_<station>_<day>_<NN>.csv`; NN grows with every export of the day.
   Each export holds **only** the new rows and the corrections since the
   previous one.
3. The program reads the file back right after writing it: if it does not
   match, the file is deleted and nothing is marked as exported.
4. Then it copies the file (and the journal) to the **Copia** folder. If the
   USB stick is missing or pulled out halfway, the copy stays **IN SOSPESO**
   (pending): yellow banner, **Riprova ora**, and the program retries by
   itself at every start and every export. The export is valid anyway: it is
   in the archive.

The exports of a day must be registered in AutoFillSuite **in order** (01,
then 02...). AutoFillSuite checks this.

### 5.1 History, regenerate, re-export

**Impostazioni > Storico** lists the days and their exports.

- **Rigenera il NN**: if a file was lost, the program rebuilds it from the
  journal, **identical byte for byte**, and checks that its fingerprint is the
  one recorded at export time.
- **Apri cartella archivio**: the export folder.
- **Riesporta giornata intera**: a new file with every live row, all as new
  (`riesportazione=1`). Only needed if the portal has to be rebuilt from
  scratch: it does not change the state of the rows.

## 6. Closing the day

**Chiudi giornata** archives the day: it becomes read-only and the next pair
opens a new one. A day closes only when nothing is left to export; otherwise
the program offers **Esporta adesso**.

A day does **not** close by itself at midnight: a shift that runs past
midnight continues in the same day. If at start-up you find yesterday's day
still open, the banner offers to export it or close it. If you reopen a day on
the same date (after closing it), the journal file gets `_2`, `_3`... and the
row and export numbering continues.

## 7. What happens on a power cut

Nothing confirmed is lost. Every pair, range, edit and deletion is written to
the journal and synced to disk **before** it shows in the table. There is no
Save button and the program does not need to be closed in any particular way.

After a restart that follows a power cut:

- **Ripresa giornata — N coppie, nessuna persa**: the table was rebuilt from
  the journal, you can carry on;
- **Ultima coppia scartata: ...**: the PC went off exactly while writing that
  pair; it is not recorded. **Scan it again**;
- **Export recuperato: ...**: the PC went off right after writing a CSV; it is
  now recorded in the journal. The file is valid;
- **COPIA IN SOSPESO**: the copy to the USB stick had not finished; it retries
  by itself.

If the journal turns out damaged in the middle (not by a power cut: by a
failing disk), the program keeps a copy next to it (`.corrotto-...`), opens the
day **read-only** and offers **Esporta righe leggibili**.

## 8. What NOT to do

- **Do not open the CSV in Excel and save it again.** Excel changes
  separators, leading zeros and encoding: AutoFillSuite notices from the
  fingerprint and refuses it as **MODIFICATO**. To look at it, open it and
  close it **without saving**; if you did save it, **Rigenera** the original
  from Storico.
- **Do not rename or move the files** in the export folder: the name carries
  the station, the day and the number.
- **Do not touch the files in `giornate/`** (the journal): it is the program's
  memory.
- **Do not open the program twice** on the same data folder: the second one
  refuses to start.
- **Do not correct a lot by hand on the portal** for rows that will still be
  exported: correct it here, it goes out as a CORREZIONE.

## 9. Common problems

| Symptom | Cause and fix |
|---|---|
| CODICI INVERTITI | You scanned the lot into QR 1: press **Scambia e registra (F2)**. |
| GIÀ REGISTRATA OGGI | The label is already in the table: if the lot is wrong, edit that row (**Vai alla riga**). |
| FORMATO NON RICONOSCIUTO | Neither code has the expected shape: check the piece, or the patterns in Impostazioni > Codici. |
| MANCA LA POSTAZIONE | Set the name in Impostazioni > Postazione. |
| DISCO NON SCRIVE (red pill) | Disk full or data folder unreachable. The program retries every 2 seconds; the last pair is not recorded, scan it again when the pill is green. |
| L'INTERVALLO SFORA | The moving digits would go past 999: split the range. |
| SOVRAPPOSIZIONE | Some labels of the range are already in the table: change the first label or the quantity. |
| «Etichetta non modificabile» | The row is already exported: only the lot can be corrected. The label takes Delete item on the portal. |
| «Non si può eliminare» | The row is already exported: see above. |
| COPIA IN SOSPESO | USB stick missing or pulled out: put it back and press **Riprova ora**. |
| NIENTE DA ESPORTARE | No new row and no correction since the last export. |
| «Esiste già ... nell'archivio» | A file with that name is already in the export folder: someone copied it there by hand. Move it away and export again. |
| AutoFillSuite says MODIFICATO | The CSV was saved again (Excel): **Rigenera** it from Storico and bring that one. |
| AutoFillSuite says TRONCATO | The file arrived cut (USB stick pulled out): copy it again, or **Rigenera**. |
| Export file deleted by mistake | Impostazioni > Storico > **Rigenera**: it comes back identical. |
| Yesterday's day is still open | Export it and close it from the banner, or carry on: new pairs go there while it is open. |
| Damaged journal: read-only | **Esporta righe leggibili**, then close the day. The copy of the damaged file stays next to the journal. |
| The program does not start: «già aperto» | A Registro QR is already open on this data folder: use that one. |

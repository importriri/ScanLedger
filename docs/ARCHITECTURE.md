# ScanLedger — Architecture

ScanLedger ("Registro QR" on screen) is the station program of a production
line that has no portal PC. The operator scans label + lot pairs, or adds a
range; the program keeps them safe and exports them as CSV files that
AutoFillSuite reads and registers on the portal. The CSV contract is
`docs/CSV-FORMAT.md`, shared byte for byte with AutoFillSuite.

## Layers

```
app/
├── config/   Settings                         (imports nobody)
├── core/     Journal, Day, Days, Row, ScanGuard (imports config only, never Swing)
└── ui/       windows, table, theme, icons     (imports core and config)
```

## The journal: write-ahead, nothing saved at exit

Every operator action becomes one line in the day journal, forced to disk with
`FileChannel.force(true)`, and only then changes the model and the table. If the
write fails, the action did not happen: the row does not appear and the screen
says so. There is no Save button and no save on exit — how the program ends
does not matter.

```
<seq> TAB <ts ms> TAB <TYPE> TAB <field>... TAB <crc32>
```

- `seq` starts at 1 and never skips; the CRC32 covers everything before the
  last TAB; `\` TAB LF CR inside a field are escaped, so free-text lots
  (`;`, quotes, double spaces) survive untouched.
- The model (`Day`) is always rebuilt by replaying the file from the start. No
  snapshots, no compaction: a day is a few thousand events at most.
- Row numbers (`n`) are written inside the events and never recomputed, so a
  replay cannot renumber anything.

### Reading back after a crash (`Journal.recover`)

- **Torn last line** (no final `\n`, or a bad CRC on the last line only): a
  write cut by a shutdown. The file is cut back to the last good byte and the
  discarded label and lot are reported so the operator can scan them again.
- **Broken line in the middle**: corruption, not a shutdown. Nothing is
  dropped silently: the file is copied next to itself as `.corrotto-<time>`
  and the day opens read only up to the last good line.
- A failed write is rolled back (truncated to the last good length) and the
  journal refuses further writes until a probe succeeds: a half line followed
  by good lines would turn a shutdown into corruption.
- The torn-line banner names what the line was: a pair or a range to scan
  again, an edit or a deletion of row n to redo (scanning the label again
  would only give a duplicate).
- A damaged copy is made once per damage: an identical `.corrotto-` file is
  reused.

### A damaged day is set aside, not left blocking the station

A read-only day can record nothing, so it can never close the normal way.
**Chiudi giornata** on it (after a question only the mouse answers) appends a
single CHIUDI line and touches nothing else: the replay still stops at the
broken line, but the folder now sees the day as closed and the next pair
starts a new one. That day continues the numbers past **every line that still
decodes** (`Journal.highestNumbers`), the unreadable part included — those
lines are read for their numbers only, never applied — so no row or export
number that went out can come back. The banner says how many journal lines
were not read; they stay in the copy.

### An open day that cannot be read at start

If the open day exists but cannot be read (an antivirus or a backup holding
it), the start reports it and the two-second probe retries **opening that
day**. Nothing is recorded meanwhile: starting another day beside it would
leave its rows open, unexported and on no screen.

### One instance

`<data>/.lock` with `FileChannel.tryLock()`. A second instance is refused: two
processes appending to one journal is the only corruption left. The lock also
remembers which lock files this process holds — on POSIX, closing any channel
on a file drops every lock the process holds on it, so a refused second attempt
in the same process would otherwise release the first.

## The rules of a row (`Day`)

The portal is append-only and **the last registration of a label wins**. So:

- a label already alive in the day is a duplicate (with the row that holds it),
  and so is a label still alive in an earlier day of the **same date** (`_1`
  when this is `_2`): the CSV carries the same date and AutoFillSuite registers
  them as one day;
  a range with a single duplicate is refused whole — one `INTERVALLO` event,
  all rows or none;
- a row never exported can change label and lot;
- once exported, its **label** is on the portal and cannot change here (the
  refusal says to use Delete item on the portal); its **lot** can, and the
  correction goes out in the next export with `lotto_prima` = the lot of the
  last export it went out with — not an intermediate value;
- a correction back to the exported lot cancels itself;
- "undo last" removes the last addition (a pair, or a whole range), never
  something already exported, and never walks back into older rows.

## Days

`<data>/giornate/<yyyy-MM-dd>[_k].giornale`. A day is named after the date it
was opened and stays open until closed, past midnight included (shifts). A day
reopened on the same date (`_2`, `_3`...) continues the row and export numbers
of the earlier ones, so `(station, date, export)` stays unique for AutoFillSuite
and a row number never comes back meaning a different row. The earlier days
also hand over their live labels, for the duplicate rule.

The data folder is read **once per run** (`Ledger`): a change in the settings
counts from the next start, journals, lock and default archive together.

## Export, archive, backup

`Exporter` writes, in this order, so that every crash leaves something
recoverable:

1. the new rows and the pending corrections, by `n` (nothing: no file);
2. `registro_<station>_<date>_<NN>.csv.tmp`, forced, renamed atomically;
3. the file **read back** with `CsvReader`: valid and exactly those rows, or it
   is deleted and nothing is recorded;
4. the `ESPORTA` event: the rows become exported;
5. the copy folder (and the day journal with it), each through a forced
   temporary file and a rename, with a `COPIA` event either way. A failed copy
   stays **pending** and is retried at every start and every export, on screen
   until it succeeds. An export of the open day with **no** copy event at all
   (recovered at start, or the PC went off between the export event and the
   copy) is copied at start.

If the journal cannot record the export event (step 4 fails), the file is
removed again: nothing has left the station yet, and a file the journal does
not know would block every later export of the day. The disk pill turns red
and Esporta waits for the disk.

**The gap between 2 and 4.** If the PC goes off after the rename and before the
event, the file exists and the journal does not know it. At the next start
`recoverMissing` looks for files of the day numbered past the last recorded
export; when one is valid and its rows are exactly the rows still pending, the
missing event is recorded and the screen says so. Leftover `.tmp` files are
removed (they were never exports).

**The archive never loses a file.** Nothing in the export folder is ever
overwritten or deleted: a name already there is an error.

**Regeneration.** The journal replayed up to an `ESPORTA` event rebuilds the
same lines with the same function the exporter used (`Day.linesFor`), so the
file comes back byte for byte; the fingerprint must equal the one the event
recorded, otherwise the journal does not match what went out and that is a
serious error.

**Re-export.** Every live row with its current value, all `NUOVA`,
`riesportazione=1`, a new number, no state change. Refused while some live
row was never exported: since nothing changes state, such a row would reach
the portal and still be deletable and relabelable here. A closed day still records
a re-export and a late copy (its rows stay frozen). A damaged, read-only day
exports its readable rows as a re-export it cannot record.

**The CSV.** `CsvWriter` and `CsvReader` are pure and follow
`docs/CSV-FORMAT.md`; `CsvWriterTest` rebuilds both contract samples byte for
byte from a day built with the same events. AutoFillSuite carries a copy of the
reader, tested on the same five samples.

## Days and restarts

A clean exit clears a marker in the settings; a start that finds it set shows
"Ripresa giornata — N coppie, nessuna persa". A torn last line is shown with
its label and lot to scan again; an open day from an earlier date asks to be
exported or closed; closing needs nothing left to export.

## The screen

```
MainWindow
├── header      title + station, disk pill ("salvato su disco hh:mm:ss"), gear
├── left 360 px Segmented SCANSIONE | INTERVALLO over a CardLayout
│   ├── ScanPanel   QR 1, QR 2, outcome box, counter, undo, swap
│   └── RangePanel  first label, lot, quantity, preview, overlap box
├── right       TablePanel over RowTableModel (a view of Day, owns nothing)
└── footer      journal file, events, data folder
```

- **Ledger** (core) holds the current day, creates it at the first scan after
  a close, and is the only way the screen changes data: every command goes
  through it, so the screen hears about every change and every failed write.
- **The scanner** behaves exactly as in AutoFillSuite: ENTER on QR 1 moves to
  QR 2, ENTER or TAB on QR 2 accepts. The TAB is caught by `ScanTabSupport`
  only on the field marked with `scan.submitOnTab`.
- **A refusal** (inverted, unknown shape, duplicate) keeps both fields full,
  sounds the error and selects QR 1. The next scan overwrites QR 1, and QR 2 is
  emptied **at once** — a deferred clear would wipe the new QR 2, because a fast
  scanner has its keystrokes already queued.
- **Duplicates are always refused** in the day: a second row for the same label
  would be a silent correction on the portal. A lot is changed by editing.
- **The table never takes the focus.** A row clicked to look at it kept the
  focus, and the next scan's ENTER opened the edit dialog on that row while
  the codes rewrote it. Rows are selected with the mouse and edited with the
  buttons or a double click; every action hands the caret back to QR 1. All
  buttons are `focusable(false)`.
- **No modal window in the scanner's way.** Questions use `SafeConfirm`
  (copied from AutoFillSuite): ENTER, SPACE and ESC answer No, Si takes the
  mouse. Refusals of Elimina, Ripristina and Chiudi giornata are a red line
  under the table, not a message box that would swallow QR 1.
- **Range fields select their text on focus**, at once (the scanner's
  keystrokes are already queued): the lot kept from the last range is
  replaced by a lot scanned again, not glued to it. ENTER in the quantity is a
  key binding, because the formatted field's own ENTER never reaches an
  action listener.
- **The disk pill** shows the time of the last forced write. A failed write
  turns it red ("DISCO NON SCRIVE"), scanning stops, and a probe runs every two
  seconds until the journal is writable again.
- **Sizes are measured.** Scan fields have a fixed column count: a field sized
  by its own text grew past the column with a long code, and GridBag then
  dropped every row of the card to its minimum (the range preview went to 1 px).

## Tests

Most suites exist because of a bug, found here or already paid for in
AutoFillSuite; the right column names it, or says there was none.

| Suite | What it pins down | The bug that created it |
|---|---|---|
| JournalTest | round trip of every event type; every edit rule; cut at every byte = exact prefix; corruption = read only + one copy; torn-line types; lock | a second lock in the same process released the first (POSIX); a new `.corrotto-` copy at every start |
| CrashTest | 20 forced kills of a writing JVM: every confirmed pair is on disk, at most one more; 10 kills mid-export: every file has its event, every event its file | the promise "nothing lost however it is closed", proven with a real kill |
| RangesTest | moving digits, refusal past 999 | AutoFillSuite's 998 + 5 = 1000, 1001 |
| RowTableModelTest | newest on top, every state word, filter counts, search; follows every event | none: written with the table, which is only a view of the journal |
| ScanFlowTest | the real window driven like the scanner: ENTER, TAB, free-text lot, duplicate, inversion + F2, mismatch, range, Ctrl+Z, edits, torn-line banners, export preview; table and journal checked after every step | a deferred clear that would have wiped the next QR 2; "scan it again" after a torn edit; `#fine` wrapped out of the preview |
| ScannerKeysTest | real keys through the X server: a scan after clicking a row, the range lot and quantity, the close-day question, a refusal | a scan that edited the clicked row; a doubled range lot; ENTER that closed the day; a pop-up that swallowed QR 1 |
| CsvWriterTest | the two contract samples rebuilt byte for byte from a day | the contract, shared with AutoFillSuite |
| CsvReaderTest | the five samples with their outcome; BOM, LF, format 2, columns, duplicate n, field rules | Excel resaving a file, a file cut in transit |
| ExporterTest | incremental exports with corrections, read-back, no overwrite, pending copy retried, crash between rename and event (and its copy), export the journal could not record, re-export rules, read-only day | the gap between the rename and the event; a recovered export never copied; an orphan file that blocked the day; a re-export that bypassed the portal rules |
| RegenerateTest | three exports and a re-export rebuilt identical from the journal; a forged journal is refused | "if the operator loses the files, nothing is lost" |
| DayLifecycleTest | first scan opens the day, closing, `_2`, past midnight, stale day, closed day takes copies and re-exports, restart, unreadable open day, same-date duplicates, data folder, damaged day set aside | a second day started beside an unreadable one; a `_1` label accepted again in `_2`; exports moved by a settings change; a damaged day that blocked the station |
| ScanGuardTest | inversion vs mismatch (AutoFillSuite cases) | the lot scanned into QR 1 |
| GlyphSafetyTest | no typed symbol in UI strings or manuals | empty boxes on Windows |
| ManualRenderTest | the markdown reader and both manuals, inside the JAR | none here: copied with the manual pane from AutoFillSuite |
| IconRenderTest | icons as pixels, the gear hub is a hole | a black disc from AlphaComposite.Clear |
| SpinnerCommitTest | on-screen number = model number | "chose 20, printed 30" |
| ScanTabSupportTest | TAB on the marked QR 2 = ENTER, nothing else; a third field breaks nothing | the scanner's TAB suffix |
| TextFitTest | overflow keeps the tail | unreadable final digits |
| StartupSmokeTest (x2) | the real app under Xvfb: station asked at first start, no clipping on both cards at default and minimum size, both themes, every settings tab, the settings open with no field holding the caret, first scan creates the day; also run at 125% and 150% UI scale | the range preview squashed to 1 px by a field sized by its text; (AutoFillSuite) the NPE of a restored state on a half-built panel |

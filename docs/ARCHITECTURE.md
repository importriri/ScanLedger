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

### One instance

`<data>/.lock` with `FileChannel.tryLock()`. A second instance is refused: two
processes appending to one journal is the only corruption left. The lock also
remembers which lock files this process holds — on POSIX, closing any channel
on a file drops every lock the process holds on it, so a refused second attempt
in the same process would otherwise release the first.

## The rules of a row (`Day`)

The portal is append-only and **the last registration of a label wins**. So:

- a label already alive in the day is a duplicate (with the row that holds it);
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
and a row number never comes back meaning a different row.

## Tests

| Suite | What it pins down | Why it exists |
|---|---|---|
| JournalTest | round trip of every event type; every edit rule; cut at every byte = exact prefix; corruption = read only + copy; lock | the promise "nothing lost however it is closed" |
| CrashTest | 20 forced kills of a writing JVM: every confirmed pair is on disk, at most one more | proves the promise with a real process kill |
| ScanGuardTest | inversion vs mismatch (AutoFillSuite cases) | the lot scanned into QR 1 |
| GlyphSafetyTest | no typed symbol in UI strings or manuals | empty boxes on Windows |
| IconRenderTest | icons as pixels, the gear hub is a hole | a black disc from AlphaComposite.Clear |
| SpinnerCommitTest | on-screen number = model number | "chose 20, printed 30" |
| ScanTabSupportTest | TAB on the marked QR 2 = ENTER, nothing else; a third field breaks nothing | the scanner's TAB suffix |
| TextFitTest | overflow keeps the tail | unreadable final digits |
| StartupSmokeTest (x2) | the real app under Xvfb, first start and saved settings | a window that compiles and shows nothing |

# Changelog

## 1.0.0 — 2026-10-15

- First public release.
- Recorded scanned label and lot pairs (QR 1, QR 2; ENTER or TAB suffix) and ranges of labels in a write-ahead day journal, forced to disk before a row shows in the table.
- Refused swapped codes (with F2 to swap and record), unknown shapes and labels already recorded in the day, including an earlier day of the same date.
- Allowed editing a row before it is exported, and only its lot after; the correction goes out in the next export.
- Exported incremental CSV files in the format shared with AutoFillSuite 1.2.0, read back before they count, into an archive that is never overwritten.
- Copied every export and its journal to an optional backup folder, retrying a failed copy at every start and every export.
- Recovered an export written just before a power cut, and set a damaged journal aside without reusing its numbers.
- Regenerated any export byte for byte from the journal, and re-exported a whole day.
- Kept modal windows out of the scanner's way: questions answer No to the keyboard, refusals show under the table.
- Embedded Italian and English operator manuals.

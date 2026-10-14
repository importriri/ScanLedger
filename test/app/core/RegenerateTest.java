package app.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// "if the operator loses the files, nothing is lost": every export can be
// rewritten from the journal alone, byte for byte, and the fingerprint proves
// it. three exports with corrections in between, the archive emptied, all
// three rebuilt. and a journal that no longer matches what went out is a
// serious error, never a silently different file.
//
//   java -cp out app.core.RegenerateTest
public final class RegenerateTest {

    static final String P = "209531.001_01-01_26120005";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("rqr-regen").toFile();
        TestClock c = new TestClock("2026-10-13T07:00:00", 1000);
        Day d = new Days(dir, c).createNew("L2");
        File exp = new File(dir, "export");
        Exporter x = new Exporter(exp, null, c);
        d.add(P + "01", "LOT-A");
        d.add(P + "02", "CA HVCO; Rev.04  \"x\"");
        d.addRange(Arrays.asList(P + "10", P + "11"), "LOT-R");
        x.export(d);
        d.modify(1, P + "01", "LOT-B", "");
        d.add(P + "03", "LOT-C");
        x.export(d);
        d.modify(1, P + "01", "LOT-D", "");
        d.modify(3, P + "10", "LOT-S", "");
        x.reexport(d);
        x.export(d);

        List<byte[]> originals = new ArrayList<>();
        for (ExportRecord r : d.exports()) {
            File f = new File(exp, r.fileName);
            originals.add(Files.readAllBytes(f.toPath()));
            check("export " + r.number + " is deleted from the archive (the loss)", f.delete());
        }
        File out = new File(dir, "rigenerati");
        int i = 0;
        for (ExportRecord r : d.exports()) {
            Exporter.Regenerated g = Exporter.regenerate(d.file(), r.number, out);
            check("export " + r.number + " is regenerated identical ("
                  + (r.reexport ? "re-export" : "incremental") + ")",
                  g.identical && Arrays.equals(originals.get(i), Files.readAllBytes(g.file.toPath())));
            i++;
        }
        check("all four exports came back", i == 4);

        // a journal rewritten by hand (valid CRCs, one lot changed before export 1)
        d.journal().close();
        List<Event> events = Journal.read(d.file()).events;
        StringBuilder sb = new StringBuilder();
        for (Event e : events) {
            Event w = e;
            if (e.type == Event.Type.AGGIUNGI && "1".equals(e.field(0))) {
                List<String> f = new ArrayList<>(e.fields());
                f.set(2, "LOT-FALSO");
                w = new Event(e.seq, e.ts, e.type, f);
            }
            sb.append(Journal.encode(w));
        }
        File forged = new File(dir, "forged.giornale");
        Files.write(forged.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        Exporter.Regenerated bad = Exporter.regenerate(forged, 1, new File(dir, "falsi"));
        check("a journal that does not match what went out is a serious error",
              !bad.identical && bad.message.contains("non corrisponde"));
        check("no file is written from a mismatching journal", bad.file == null);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else      { failed++; System.out.println("  FAIL " + name); }
    }
}

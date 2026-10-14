package app.core;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

// the writer is the contract: a day built by hand with the same events as the
// samples must produce the two sample exports BYTE FOR BYTE. if this suite
// goes red, either the writer drifted or someone touched the samples — and
// AutoFillSuite reads the very same files.
//
//   java -cp out app.core.CsvWriterTest
public final class CsvWriterTest {

    static final String P = "209531.001_01-01_2612000";
    static final String FREE =
        "CA HVCO 50 T 048 3-2; M-CA HVCO 50 T 048 3-2  Rev.04; Serial No.: 000066076 ; 2023/12/11;A";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        quoting_followsTheContract();
        sampleDay_rebuildsBothExports_byteForByte();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void quoting_followsTheContract() {
        check("a plain field is not quoted", CsvWriter.esc("900612202601310007").equals("900612202601310007"));
        check("a field with ';' is quoted", CsvWriter.esc("a;b").equals("\"a;b\""));
        check("quotes inside are doubled", CsvWriter.esc("LOTTO \"B\" 7").equals("\"LOTTO \"\"B\"\" 7\""));
        check("double spaces stay as they are", CsvWriter.esc("A  B").equals("A  B"));
        check("the file name carries station, day and a two-digit number",
              CsvWriter.fileName("L2", "2026-10-13", 2).equals("registro_L2_2026-10-13_02.csv"));
    }

    /** The sample day: rows 1,2,3,4,6; 5 deleted; 4 touched by hand; 6 free text. */
    static Day sampleDayUntilExport1(File dir, TestClock c) throws Exception {
        c.set("2026-10-13T06:58:02");
        Day d = new Days(dir, c).createNew("L2");
        d.add(P + "231", "900612202601310007");
        c.set("2026-10-13T06:58:19");
        d.add(P + "232", "900612202601310007");
        c.set("2026-10-13T07:02:44");
        d.addRange(Arrays.asList(P + "233", P + "234"), "900612202601310008");
        c.set("2026-10-13T07:03:30");
        d.modify(4, P + "234", "900612202601310009", "sparato male");
        d.modify(4, P + "234", "900612202601310008", "ripensamento");
        c.set("2026-10-13T07:04:12");
        d.add(P + "235", "900612202601310008");
        d.delete(5);
        c.set("2026-10-13T07:05:10");
        d.add(P + "236", FREE);
        c.set("2026-10-13T12:00:41");
        return d;
    }

    static void continueUntilExport2(Day d, TestClock c) {
        c.set("2026-10-13T13:10:05");
        d.modify(2, P + "232", "900612202601310009", "lotto sbagliato");
        c.set("2026-10-13T13:12:30");
        d.add(P + "237", "900612202601310009");
        c.set("2026-10-13T13:14:02");
        d.add(P + "238", "LOTTO \"B\" 7");
        c.set("2026-10-13T17:28:41");
    }

    private static void sampleDay_rebuildsBothExports_byteForByte() throws Exception {
        File dir = Files.createTempDirectory("rqr-writer").toFile();
        TestClock c = new TestClock("2026-10-13T06:00:00");
        Day d = sampleDayUntilExport1(dir, c);
        Exporter x = new Exporter(new File(dir, "export"), null, c);
        Exporter.Result r1 = x.export(d);
        check("export 1 succeeds", r1.ok);
        byte[] want1 = Files.readAllBytes(new File("test/fixtures/contratto-v1-export1.csv").toPath());
        byte[] got1 = r1.file == null ? new byte[0] : Files.readAllBytes(r1.file.toPath());
        check("export 1 is byte for byte contratto-v1-export1.csv", Arrays.equals(want1, got1));
        if (!Arrays.equals(want1, got1)) System.out.println(new String(got1, "UTF-8"));
        check("export 1 has the sample's fingerprint",
              "d570b815243882baae5e2a86912bb220f2440fb74c91e472f001b696f8a788db".equals(r1.sha256));

        continueUntilExport2(d, c);
        Exporter.Result r2 = x.export(d);
        check("export 2 succeeds", r2.ok);
        byte[] want2 = Files.readAllBytes(new File("test/fixtures/contratto-v1-export2.csv").toPath());
        byte[] got2 = r2.file == null ? new byte[0] : Files.readAllBytes(r2.file.toPath());
        check("export 2 is byte for byte contratto-v1-export2.csv", Arrays.equals(want2, got2));
        if (!Arrays.equals(want2, got2)) System.out.println(new String(got2, "UTF-8"));
        check("export 2 has the sample's fingerprint",
              "c86f69ec88fa844a519bffad69deef42abb5090ded5ddce87d9a3799852fc78f".equals(r2.sha256));
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ok   " + name); }
        else      { failed++; System.out.println("  FAIL " + name); }
    }
}

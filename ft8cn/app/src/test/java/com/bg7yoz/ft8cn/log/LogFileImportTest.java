package com.bg7yoz.ft8cn.log;

import static com.google.common.truth.Truth.assertThat;

import com.bg7yoz.ft8cn.html.ImportTaskList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Drive {@link LogFileImport} against on-disk ADIF fixtures. The production
 * constructor takes a file path (it reads via FileInputStream), so we copy
 * each classpath resource into a TemporaryFolder file and feed the path in.
 *
 * Robolectric is here for {@code android.util.Log}; the import code itself
 * is plain Java.
 */
@RunWith(RobolectricTestRunner.class)
public class LogFileImportTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ImportTaskList.ImportTask task;

    @Before
    public void setUp() {
        // Session id is opaque to the import path; any value works.
        task = new ImportTaskList.ImportTask(0);
    }

    @Test
    public void wellFormedAdif_parsesAllRecords() throws IOException {
        File f = fixture("adif/sample-wsjtx.adi");
        LogFileImport imp = new LogFileImport(task, f.getAbsolutePath());
        ArrayList<HashMap<String, String>> records = imp.getLogRecords();
        assertThat(records).hasSize(3);
    }

    @Test
    public void wellFormedAdif_uppercasesFieldKeys() throws IOException {
        File f = fixture("adif/sample-wsjtx.adi");
        LogFileImport imp = new LogFileImport(task, f.getAbsolutePath());
        HashMap<String, String> first = imp.getLogRecords().get(0);
        // Production code uppercases the field name before put() (line 96).
        assertThat(first).containsKey("CALL");
        assertThat(first).containsKey("MODE");
        assertThat(first).containsKey("BAND");
        // Lowercase keys should not exist.
        assertThat(first).doesNotContainKey("call");
    }

    @Test
    public void wellFormedAdif_extractsValuesUsingDeclaredLength() throws IOException {
        File f = fixture("adif/sample-wsjtx.adi");
        LogFileImport imp = new LogFileImport(task, f.getAbsolutePath());
        HashMap<String, String> first = imp.getLogRecords().get(0);
        assertThat(first.get("CALL")).isEqualTo("K1ABC");
        assertThat(first.get("GRIDSQUARE")).isEqualTo("FN42");
        assertThat(first.get("MODE")).isEqualTo("FT8");
    }

    @Test
    public void getLogBody_returnsContentAfterEoh() throws IOException {
        File f = fixture("adif/sample-wsjtx.adi");
        LogFileImport imp = new LogFileImport(task, f.getAbsolutePath());
        String body = imp.getLogBody();
        // The header is stripped; first surviving content should be the
        // first record marker.
        assertThat(body).doesNotContain("<adif_ver");
        assertThat(body).contains("<call:5>K1ABC");
    }

    @Test
    public void malformedAdif_skipsBadRecordsAndReportsErrorCount() throws IOException {
        File f = fixture("adif/sample-malformed.adi");
        LogFileImport imp = new LogFileImport(task, f.getAbsolutePath());
        ArrayList<HashMap<String, String>> records = imp.getLogRecords();

        // The fixture has 4 pieces between <eor> markers:
        //   1. a valid K1ABC record               -> should be parsed
        //   2. a free-text line with no '<' tag   -> short-circuits at `s.contains("<")`
        //   3. <call:notanumber>BADREC...         -> NumberFormatException, counted in errorLines
        //   4. a valid W1AW record                -> should be parsed
        // The K1ABC record is added before iteration even reaches the bad
        // record, so the good records on either side survive.
        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("CALL")).isEqualTo("K1ABC");
        assertThat(records.get(1).get("CALL")).isEqualTo("W1AW");
        assertThat(imp.getErrorCount()).isEqualTo(1);
    }

    @Test
    public void emptyFile_returnsEmptyRecordList() throws IOException {
        File empty = tmp.newFile("empty.adi");
        // Write a header-only file with no <eoh> — getLogBody returns "".
        try (FileOutputStream out = new FileOutputStream(empty)) {
            out.write("header only, no eoh marker".getBytes(StandardCharsets.UTF_8));
        }
        LogFileImport imp = new LogFileImport(task, empty.getAbsolutePath());
        assertThat(imp.getLogRecords()).isEmpty();
    }

    private File fixture(String resource) throws IOException {
        File out = tmp.newFile(new File(resource).getName());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).isNotNull();
            Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return out;
    }
}

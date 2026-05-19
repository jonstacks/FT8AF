package com.bg7yoz.ft8cn.log;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.bg7yoz.ft8cn.GeneralVariables;

import org.json.JSONObject;
import org.json.JSONStringer;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

enum ServiceType{
    Cloudlog,
    QRZ
}

public class ThirdPartyService {
    public static String TAG = "ThirdPartyService";

    private static String QSLRecordToADIF(QSLRecord qslRecord, ServiceType serv){
        StringBuilder logStr = new StringBuilder();
        logStr.append(String.format("<call:%d>%s "
                , qslRecord.getToCallsign().length()
                , qslRecord.getToCallsign()));

        if (qslRecord.getToMaidenGrid() != null) {
            logStr.append(String.format("<gridsquare:%d>%s "
                    , qslRecord.getToMaidenGrid().length()
                    , qslRecord.getToMaidenGrid()));
        }

        if (qslRecord.getMode() != null) {
            logStr.append(String.format("<mode:%d>%s "
                    , qslRecord.getMode().length()
                    , qslRecord.getMode()));
        }

        if (String.valueOf(qslRecord.getSendReport()) != null) {
            logStr.append(String.format("<rst_sent:%d>%s "
                    , String.valueOf(qslRecord.getSendReport()).length()
                    , String.valueOf(qslRecord.getSendReport())));
        }

        if (String.valueOf(qslRecord.getReceivedReport()) != null) {
            logStr.append(String.format("<rst_rcvd:%d>%s "
                    , String.valueOf(qslRecord.getReceivedReport()).length()
                    , String.valueOf(qslRecord.getReceivedReport())));
        }

        if (qslRecord.getQso_date() != null) {
            logStr.append(String.format("<qso_date:%d>%s "
                    , qslRecord.getQso_date().length()
                    , qslRecord.getQso_date()));
        }

        if (qslRecord.getTime_on() != null) {
            logStr.append(String.format("<time_on:%d>%s "
                    , qslRecord.getTime_on().length()
                    , qslRecord.getTime_on()));
        }
        if (qslRecord.getBandLength() != null) {
            logStr.append(String.format("<band:%d>%s "
                    , qslRecord.getBandLength().length()
                    , qslRecord.getBandLength()));
        }

        if (qslRecord.getQso_date_off() != null) {
            logStr.append(String.format("<qso_date_off:%d>%s "
                    , qslRecord.getQso_date_off().length()
                    , qslRecord.getQso_date_off()));
        }

        if (qslRecord.getTime_off() != null) {
            logStr.append(String.format("<time_off:%d>%s "
                    , qslRecord.getTime_off().length()
                    , qslRecord.getTime_off()));
        }

        if (String.valueOf(qslRecord.getBandFreq()) != null) {
            String freq = "";
            Log.d(TAG,String.valueOf(qslRecord.getBandFreq()));
            if (serv == ServiceType.Cloudlog || serv == ServiceType.QRZ){
                double i = (double)qslRecord.getBandFreq() / 1000000;
                freq = String.valueOf(i);
            }

            logStr.append(String.format("<freq:%d>%s "
                    , freq.length()
                    , freq));
        }

        if (qslRecord.getMyCallsign() != null) {
            logStr.append(String.format("<station_callsign:%d>%s "
                    , qslRecord.getMyCallsign().length()
                    , qslRecord.getMyCallsign()));
        }

        if (qslRecord.getMyMaidenGrid() != null) {
            logStr.append(String.format("<my_gridsquare:%d>%s "
                    , qslRecord.getMyMaidenGrid().length()
                    , qslRecord.getMyMaidenGrid()));
        }

        String comment = qslRecord.getComment();

        //<comment:15>Distance: 99 km <eor>
        //When writing to the database, be sure to append " km"
        logStr.append(String.format("<comment:%d>%s <eor>\n"
                , comment.length()
                , comment));
        return logStr.toString();
    }
    public static void UploadToCloudLog(QSLRecord qslRecord){
        // Convert to ADIF format
        String logStr = QSLRecordToADIF(qslRecord,ServiceType.Cloudlog);
        uploadAdifToCloudlog(logStr);
    }

    /**
     * Posts a single ADIF record (or any ADIF body) to Cloudlog/Wavelog/Nextlog.
     * Returns true on HTTP 2xx, false otherwise.
     */
    public static boolean uploadAdifToCloudlog(String adif) {
        String address = GeneralVariables.getCloudlogServerAddress();
        if (address == null || address.isEmpty()) return false;
        if (!address.endsWith("/")){
            address+="/";
        }
        JSONStringer js = new JSONStringer();
        try {
            String result = js.object().key("key").value(GeneralVariables.getCloudlogServerApiKey()).key("station_profile_id").value(GeneralVariables.getCloudlogStationID())
                    .key("type").value("adif").key("string").value(adif).endObject().toString();
            String clRes = sendPostRequest(address+"api/qso/",result);
            Log.d(TAG, "Cloudlog upload " + (clRes != null ? "succeeded" : "failed"));
            return clRes != null;
        }catch (Exception k){
            Log.d(TAG, "Cloudlog upload error: " + k.getClass().getSimpleName());
            return false;
        }
    }
    public static boolean CheckCloudlogConnection(){
        String address = GeneralVariables.getCloudlogServerAddress();
        String apiKey = GeneralVariables.getCloudlogServerApiKey();
        // Check if the address ends with /
        if (!address.endsWith("/")){
            address+="/";
        }
        try{
            // The Cloudlog auth endpoint takes the key as a path segment, so the constructed
            // URL is unavoidably credential-bearing. Do not log it.
            String url = address + "api/auth/"+ apiKey;
            String result = sendGetRequest(url);
            if (result == null) {
                Log.d(TAG, "Cloudlog connection failed: no response");
                return false;
            }
            // Nextlog and Wavelog both implement /api/auth but return slightly different shapes
            // (XML declaration, extra whitespace, etc.). Match on the meaningful markers so all
            // three Cloudlog-compatible backends report Pass.
            String compact = result.replaceAll("\\s+", "");
            return compact.contains("<status>Valid</status>")
                    && compact.contains("<rights>rw</rights>");
        }catch (Exception e){
            Log.d(TAG, "Cloudlog auth error: " + e.getClass().getSimpleName());
            return false;
        }
    }

    public static boolean CheckQRZConnection(){
        String apiKey = GeneralVariables.getQrzApiKey();
        try{
            // POST so the API key is in the body rather than the URL, where it could leak
            // via proxies, server access logs, or our own logcat.
            String body = "KEY=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
                    + "&ACTION=STATUS";
            String result = sendPostFormRequest("https://logbook.qrz.com/api", body);
            if (result == null) {
                Log.d(TAG, "QRZ connection failed: no response");
                return false;
            }
            String qrzResult = null;
            for (String s : result.split("&")) {
                String[] split = s.split("=", 2);
                if (split.length > 1 && "RESULT".equals(split[0])) {
                    qrzResult = split[1];
                    break;
                }
            }
            Log.d(TAG, "QRZ status RESULT=" + qrzResult);
            return "OK".equals(qrzResult);
        }catch (Exception e){
            Log.d(TAG, "QRZ status error: " + e.getClass().getSimpleName());
            return false;
        }
    }

    public static void UploadToQRZ(QSLRecord qslRecord){
        // Convert to ADIF format
        String logStr = QSLRecordToADIF(qslRecord, ServiceType.QRZ);
        uploadAdifToQrz(logStr);
    }

    /**
     * Posts a single ADIF record to QRZ. Returns true if QRZ returned RESULT=OK or
     * RESULT=REPLACE (the latter means the QSO already existed and was updated —
     * still a success from a "the record is now on QRZ" standpoint).
     */
    public static boolean uploadAdifToQrz(String adif) {
        String apikey = GeneralVariables.getQrzApiKey();
        if (apikey == null || apikey.isEmpty()) return false;
        try {
            // POST keeps both the API key and the ADIF payload out of the URL.
            String body = "KEY=" + URLEncoder.encode(apikey, StandardCharsets.UTF_8.name())
                    + "&ACTION=INSERT"
                    + "&ADIF=" + URLEncoder.encode(adif, StandardCharsets.UTF_8.name());
            String result = sendPostFormRequest("https://logbook.qrz.com/api", body);
            Log.d(TAG, "QRZ upload " + (result != null ? "succeeded" : "failed"));
            if (result == null) return false;
            // QRZ encodes status as RESULT=OK|FAIL|REPLACE within an &-separated body
            String qrzResult = null;
            for (String s : result.split("&")) {
                String[] split = s.split("=", 2);
                if (split.length > 1 && "RESULT".equals(split[0])) {
                    qrzResult = split[1];
                    break;
                }
            }
            return "OK".equals(qrzResult) || "REPLACE".equals(qrzResult);
        }catch (Exception k){
            Log.d(TAG, "QRZ upload error: " + k.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Progress callback used during a batch re-upload.
     */
    public interface SyncProgress {
        void onProgress(int done, int total, int cloudlogOk, int qrzOk);
    }

    public static class SyncResult {
        public final int total;
        public final int cloudlogOk;
        public final int qrzOk;
        public final boolean cloudlogAttempted;
        public final boolean qrzAttempted;

        SyncResult(int total, int cloudlogOk, int qrzOk,
                   boolean cloudlogAttempted, boolean qrzAttempted) {
            this.total = total;
            this.cloudlogOk = cloudlogOk;
            this.qrzOk = qrzOk;
            this.cloudlogAttempted = cloudlogAttempted;
            this.qrzAttempted = qrzAttempted;
        }
    }

    /**
     * Re-upload every QSO in QSLTable to whichever third-party services the user has
     * enabled. Services dedupe by callsign+date+time+mode so repeated calls are safe.
     *
     * Blocks the calling thread — invoke from a background thread/coroutine.
     */
    public static SyncResult syncAllQSOs(SQLiteDatabase db, SyncProgress progress) {
        boolean cl = GeneralVariables.enableCloudlog;
        boolean qrz = GeneralVariables.enableQRZ;
        int total = 0;
        int cloudlogOk = 0;
        int qrzOk = 0;
        if (db == null || (!cl && !qrz)) {
            return new SyncResult(0, 0, 0, cl, qrz);
        }
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("select * from QSLTable order by id asc", null);
            total = cursor.getCount();
            if (progress != null) progress.onProgress(0, total, 0, 0);
            int done = 0;
            while (cursor.moveToNext()) {
                if (cl) {
                    String adif = buildAdifFromCursor(cursor, ServiceType.Cloudlog);
                    if (uploadAdifToCloudlog(adif)) cloudlogOk++;
                }
                if (qrz) {
                    String adif = buildAdifFromCursor(cursor, ServiceType.QRZ);
                    if (uploadAdifToQrz(adif)) qrzOk++;
                }
                done++;
                if (progress != null) progress.onProgress(done, total, cloudlogOk, qrzOk);
            }
        } catch (Exception e) {
            Log.e(TAG, "syncAllQSOs error: " + e.getClass().getSimpleName() + " " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return new SyncResult(total, cloudlogOk, qrzOk, cl, qrz);
    }

    /**
     * Builds a single-record ADIF body from a QSLTable cursor row. Mirrors the field
     * set produced by {@link #QSLRecordToADIF} so Cloudlog/QRZ see identical payloads
     * to the immediate-after-QSO upload path.
     */
    private static String buildAdifFromCursor(Cursor c, ServiceType serv) {
        StringBuilder s = new StringBuilder();
        appendAdif(s, "call", colStr(c, "call"));
        appendAdif(s, "gridsquare", colStr(c, "gridsquare"));
        appendAdif(s, "mode", colStr(c, "mode"));
        appendAdif(s, "rst_sent", colStr(c, "rst_sent"));
        appendAdif(s, "rst_rcvd", colStr(c, "rst_rcvd"));
        appendAdif(s, "qso_date", colStr(c, "qso_date"));
        appendAdif(s, "time_on", colStr(c, "time_on"));
        appendAdif(s, "band", colStr(c, "band"));
        appendAdif(s, "qso_date_off", colStr(c, "qso_date_off"));
        appendAdif(s, "time_off", colStr(c, "time_off"));

        // QSLTable stores freq as a string; QSLRecordToADIF outputs MHz floats for
        // both Cloudlog and QRZ. The DB column is already in MHz form (set by the
        // ADIF export path) so we can pass it through verbatim.
        appendAdif(s, "freq", colStr(c, "freq"));

        appendAdif(s, "station_callsign", colStr(c, "station_callsign"));
        appendAdif(s, "my_gridsquare", colStr(c, "my_gridsquare"));

        String comment = colStr(c, "comment");
        if (comment == null) comment = "";
        s.append(String.format("<comment:%d>%s <eor>\n", comment.length(), comment));
        return s.toString();
    }

    private static void appendAdif(StringBuilder sb, String tag, String value) {
        if (value == null || value.isEmpty()) return;
        sb.append(String.format("<%s:%d>%s ", tag, value.length(), value));
    }

    private static String colStr(Cursor c, String name) {
        int idx = c.getColumnIndex(name);
        if (idx < 0) return null;
        return c.getString(idx);
    }

    public static String sendPostRequest(String url, String json) throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL urlObj = new URL(url);
            conn = (HttpURLConnection) urlObj.openConnection();

            // Set request method to POST
            conn.setRequestMethod("POST");
            // Set request headers
            conn.setRequestProperty("Content-Type", "application/json");

            // Get OutputStream and write request data to the stream
            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes());
            os.flush();

            // Get server response
            int responseCode = conn.getResponseCode();
            // Cloudlog uses HTTP_CREATED as the response for successful record creation
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode==HttpURLConnection.HTTP_CREATED) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (reader != null) {
                reader.close();
            }
        }

        return null;
    }
    public static String sendPostFormRequest(String url, String formBody) throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL urlObj = new URL(url);
            conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(formBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK
                    || responseCode == HttpURLConnection.HTTP_CREATED) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(),
                        StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (reader != null) {
                reader.close();
            }
        }
        return null;
    }

    public static String sendGetRequest(String url) throws IOException {
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            URL urlObj = new URL(url);
            conn = (HttpURLConnection) urlObj.openConnection();

            // Set request method to GET
            conn.setRequestMethod("GET");
            // Set request headers
            conn.setRequestProperty("Content-Type", "application/json");

            // Get server response
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (reader != null) {
                reader.close();
            }
        }
        return null;
    }
}

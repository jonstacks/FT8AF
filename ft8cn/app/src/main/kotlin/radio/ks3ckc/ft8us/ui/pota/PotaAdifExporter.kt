package radio.ks3ckc.ft8us.ui.pota

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.bg7yoz.ft8cn.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import radio.ks3ckc.ft8us.pota.model.PotaActivation
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes an ADIF file for a single POTA activation and shares it via system intent.
 *
 * pota.app's website upload (https://pota.app/#/user/upload) expects an ADIF where each
 * QSO carries MY_SIG=POTA / MY_SIG_INFO=<park ref>. SIG/SIG_INFO are filled for P2P
 * (Park-to-Park) contacts. We pull rows from QSLTable that fall inside the activation's
 * time window AND match the park ref so the file contains only the activation's QSOs.
 */
object PotaAdifExporter {

    private const val AUTHORITY = "radio.ks3ckc.ft8af.fileprovider"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun shareActivationAdif(
        context: Context,
        mainViewModel: MainViewModel,
        activation: PotaActivation,
        onResult: (Boolean) -> Unit,
    ) {
        val db = mainViewModel.databaseOpr?.db ?: run {
            onResult(false)
            return
        }
        scope.launch {
            try {
                val cursor = db.rawQuery(
                    "SELECT * FROM QSLTable WHERE my_sig = 'POTA' AND my_sig_info = ? " +
                        "ORDER BY qso_date, time_on",
                    arrayOf(activation.parkRef),
                )
                val sb = StringBuilder()
                sb.append("FT8AF POTA Activation ${activation.parkRef}\n")
                sb.append("<ADIF_VER:5>3.1.4 ")
                sb.append("<PROGRAMID:5>FT8AF ")
                sb.append("<EOH>\n")
                cursor.use { c ->
                    val callIdx = c.getColumnIndex("call")
                    val gridIdx = c.getColumnIndex("gridsquare")
                    val modeIdx = c.getColumnIndex("mode")
                    val bandIdx = c.getColumnIndex("band")
                    val freqIdx = c.getColumnIndex("freq")
                    val rstSentIdx = c.getColumnIndex("rst_sent")
                    val rstRcvdIdx = c.getColumnIndex("rst_rcvd")
                    val dateIdx = c.getColumnIndex("qso_date")
                    val timeOnIdx = c.getColumnIndex("time_on")
                    val timeOffIdx = c.getColumnIndex("time_off")
                    val stationIdx = c.getColumnIndex("station_callsign")
                    val myGridIdx = c.getColumnIndex("my_gridsquare")
                    val mySigIdx = c.getColumnIndex("my_sig")
                    val mySigInfoIdx = c.getColumnIndex("my_sig_info")
                    val sigIdx = c.getColumnIndex("sig")
                    val sigInfoIdx = c.getColumnIndex("sig_info")
                    while (c.moveToNext()) {
                        adifField(sb, "CALL", c.getString(callIdx))
                        adifField(sb, "GRIDSQUARE", c.getString(gridIdx))
                        adifField(sb, "MODE", c.getString(modeIdx))
                        adifField(sb, "BAND", c.getString(bandIdx))
                        adifField(sb, "FREQ", c.getString(freqIdx))
                        adifField(sb, "RST_SENT", c.getString(rstSentIdx))
                        adifField(sb, "RST_RCVD", c.getString(rstRcvdIdx))
                        adifField(sb, "QSO_DATE", c.getString(dateIdx))
                        adifField(sb, "TIME_ON", c.getString(timeOnIdx))
                        adifField(sb, "TIME_OFF", c.getString(timeOffIdx))
                        adifField(sb, "STATION_CALLSIGN", c.getString(stationIdx))
                        adifField(sb, "MY_GRIDSQUARE", c.getString(myGridIdx))
                        adifField(sb, "MY_SIG", c.getString(mySigIdx))
                        adifField(sb, "MY_SIG_INFO", c.getString(mySigInfoIdx))
                        adifField(sb, "SIG", c.getString(sigIdx))
                        adifField(sb, "SIG_INFO", c.getString(sigInfoIdx))
                        sb.append("<EOR>\n")
                    }
                }

                val dir = context.getExternalFilesDir(null) ?: run {
                    onResult(false)
                    return@launch
                }
                val ts = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(activation.startedAtMs))
                val file = File(dir, "pota-${activation.parkRef}-$ts.adi")
                file.writeText(sb.toString())

                val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "POTA activation ${activation.parkRef}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, "Share POTA ADIF").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    private fun adifField(sb: StringBuilder, name: String, value: String?) {
        if (value.isNullOrEmpty()) return
        sb.append("<").append(name).append(":").append(value.length).append(">").append(value).append(" ")
    }
}

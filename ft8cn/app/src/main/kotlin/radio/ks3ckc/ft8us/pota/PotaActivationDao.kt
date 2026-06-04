package radio.ks3ckc.ft8us.pota

import android.database.sqlite.SQLiteDatabase
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.database.DatabaseOpr
import radio.ks3ckc.ft8us.pota.model.PotaActivation

/**
 * Thin DAO over the pota_activation SQLite table. Single-threaded callers from the
 * session manager — no locking beyond what SQLite gives us.
 */
internal object PotaActivationDao {

    private fun db(): SQLiteDatabase =
        DatabaseOpr.getInstance(GeneralVariables.getMainContext(), null).db

    fun startActivation(parkRef: String, operator: String?, notes: String?): PotaActivation {
        val now = System.currentTimeMillis()
        val db = db()
        db.execSQL(
            "INSERT INTO pota_activation(park_ref, operator, started_at, qso_count, notes) " +
                "VALUES (?, ?, ?, 0, ?)",
            arrayOf<Any?>(parkRef, operator, now, notes),
        )
        val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
        val id = if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        cursor.close()
        return PotaActivation(
            id = id,
            parkRef = parkRef,
            operator = operator,
            startedAtMs = now,
            endedAtMs = null,
            qsoCount = 0,
            notes = notes,
        )
    }

    fun endActivation(id: Long) {
        db().execSQL(
            "UPDATE pota_activation SET ended_at = ? WHERE id = ?",
            arrayOf<Any?>(System.currentTimeMillis(), id),
        )
    }

    /** Refresh a single row (used to pick up the qso_count bumps DatabaseOpr writes). */
    fun reload(id: Long): PotaActivation? {
        val cursor = db().rawQuery("SELECT * FROM pota_activation WHERE id = ?", arrayOf(id.toString()))
        return cursor.use { c -> if (c.moveToFirst()) c.toActivation() else null }
    }

    fun history(limit: Int = 50): List<PotaActivation> {
        val cursor = db().rawQuery(
            "SELECT * FROM pota_activation ORDER BY started_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        )
        val out = mutableListOf<PotaActivation>()
        cursor.use { c ->
            while (c.moveToNext()) out.add(c.toActivation())
        }
        return out
    }

    private fun android.database.Cursor.toActivation(): PotaActivation {
        val endedIdx = getColumnIndex("ended_at")
        return PotaActivation(
            id = getLong(getColumnIndex("id")),
            parkRef = getString(getColumnIndex("park_ref")) ?: "",
            operator = getString(getColumnIndex("operator")),
            startedAtMs = getLong(getColumnIndex("started_at")),
            endedAtMs = if (isNull(endedIdx)) null else getLong(endedIdx),
            qsoCount = getInt(getColumnIndex("qso_count")),
            notes = getString(getColumnIndex("notes")),
        )
    }
}

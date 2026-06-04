package radio.ks3ckc.ft8us.ui.map

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.bg7yoz.ft8cn.R

/**
 * Lazily loads Natural Earth 110m land outlines from res/raw/world_land.json.
 * Each entry in the returned list is one polygon ring's outer boundary,
 * stored as a FloatArray of interleaved [lon, lat, lon, lat, ...].
 *
 * Holes are ignored — at 110m resolution they are negligible for a basemap.
 * GeoJSON coordinate order is [lon, lat]; we preserve that here.
 */
internal object WorldOutlines {
    @Volatile private var cached: List<FloatArray>? = null

    fun load(context: Context): List<FloatArray> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = context.resources.openRawResource(R.raw.world_land)
                .bufferedReader().use { it.readText() }
            val features = JSONObject(text).getJSONArray("features")
            val rings = ArrayList<FloatArray>(features.length())
            for (i in 0 until features.length()) {
                val geom = features.getJSONObject(i).getJSONObject("geometry")
                when (geom.getString("type")) {
                    "Polygon" -> {
                        rings.add(toFlat(geom.getJSONArray("coordinates").getJSONArray(0)))
                    }
                    "MultiPolygon" -> {
                        val polys = geom.getJSONArray("coordinates")
                        for (p in 0 until polys.length()) {
                            rings.add(toFlat(polys.getJSONArray(p).getJSONArray(0)))
                        }
                    }
                }
            }
            cached = rings
            return rings
        }
    }

    private fun toFlat(ring: JSONArray): FloatArray {
        val n = ring.length()
        val out = FloatArray(n * 2)
        for (i in 0 until n) {
            val pt = ring.getJSONArray(i)
            out[i * 2] = pt.getDouble(0).toFloat()      // lon
            out[i * 2 + 1] = pt.getDouble(1).toFloat()  // lat
        }
        return out
    }
}

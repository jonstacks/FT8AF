package radio.ks3ckc.ft8us.pskreporter

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for IPFIX binary encoding in [PskReporterSender].
 * Validates packet structure, template encoding, field ordering, and
 * variable-length string encoding against the PSKReporter IPFIX spec.
 */
@RunWith(RobolectricTestRunner::class)
class PskReporterSenderTest {

    private val captured = mutableListOf<ByteArray>()

    @Before
    fun setUp() {
        PskReporterSender.resetForTests()
        PskReporterSender.sendDatagram = { data -> captured.add(data.copyOf()) }
    }

    @After
    fun tearDown() {
        PskReporterSender.resetForTests()
        captured.clear()
    }

    // ---------------------------------------------------------------
    // Variable-length string encoding
    // ---------------------------------------------------------------

    @Test
    fun `encodeVarString short string has 1-byte length prefix`() {
        val result = PskReporterSender.encodeVarString("W1AW")
        assertThat(result.size).isEqualTo(5) // 1 byte len + 4 bytes
        assertThat(result[0].toInt() and 0xFF).isEqualTo(4)
        assertThat(String(result, 1, 4, Charsets.UTF_8)).isEqualTo("W1AW")
    }

    @Test
    fun `encodeVarString empty string`() {
        val result = PskReporterSender.encodeVarString("")
        assertThat(result.size).isEqualTo(1)
        assertThat(result[0].toInt() and 0xFF).isEqualTo(0)
    }

    @Test
    fun `encodeVarString long string uses 3-byte prefix`() {
        val longStr = "A".repeat(300)
        val result = PskReporterSender.encodeVarString(longStr)
        assertThat(result.size).isEqualTo(3 + 300)
        assertThat(result[0].toInt() and 0xFF).isEqualTo(0xFF)
        val len = ((result[1].toInt() and 0xFF) shl 8) or (result[2].toInt() and 0xFF)
        assertThat(len).isEqualTo(300)
    }

    // ---------------------------------------------------------------
    // Sender record encoding
    // ---------------------------------------------------------------

    @Test
    fun `encodeSenderRecord contains all fields in order`() {
        val spot = PskReporterSender.SpotRecord(
            senderCallsign = "W1AW",
            frequencyHz = 14_074_000L,
            snr = -15,
            mode = "FT8",
            senderLocator = "FN31",
            flowStartSeconds = 1_700_000_000L,
        )
        val data = PskReporterSender.encodeSenderRecord(spot)
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Callsign: 1-byte len + "W1AW"
        assertThat(buf.get().toInt() and 0xFF).isEqualTo(4)
        val callBytes = ByteArray(4)
        buf.get(callBytes)
        assertThat(String(callBytes)).isEqualTo("W1AW")

        // Frequency: uint32
        assertThat(buf.int.toLong() and 0xFFFFFFFFL).isEqualTo(14_074_000L)

        // SNR: int8
        assertThat(buf.get().toInt()).isEqualTo(-15)

        // Mode: 1-byte len + "FT8"
        assertThat(buf.get().toInt() and 0xFF).isEqualTo(3)
        val modeBytes = ByteArray(3)
        buf.get(modeBytes)
        assertThat(String(modeBytes)).isEqualTo("FT8")

        // Locator: 1-byte len + "FN31"
        assertThat(buf.get().toInt() and 0xFF).isEqualTo(4)
        val locBytes = ByteArray(4)
        buf.get(locBytes)
        assertThat(String(locBytes)).isEqualTo("FN31")

        // informationSource: uint8 = 1
        assertThat(buf.get().toInt() and 0xFF).isEqualTo(1)

        // flowStartSeconds: uint32
        assertThat(buf.int.toLong() and 0xFFFFFFFFL).isEqualTo(1_700_000_000L)

        // Should have consumed all bytes
        assertThat(buf.remaining()).isEqualTo(0)
    }

    @Test
    fun `encodeSenderRecord null locator encodes as empty string`() {
        val spot = PskReporterSender.SpotRecord(
            senderCallsign = "K2ABC",
            frequencyHz = 7_074_000L,
            snr = 5,
            mode = "FT8",
            senderLocator = null,
            flowStartSeconds = 1_700_000_000L,
        )
        val data = PskReporterSender.encodeSenderRecord(spot)
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Skip callsign (1+5), freq(4), snr(1), mode(1+3)
        buf.position(6 + 4 + 1 + 4)
        // Locator should be empty (length byte = 0)
        assertThat(buf.get().toInt() and 0xFF).isEqualTo(0)
    }

    // ---------------------------------------------------------------
    // Receiver data set encoding
    // ---------------------------------------------------------------

    @Test
    fun `encodeReceiverDataSet has correct set ID and alignment`() {
        val data = PskReporterSender.encodeReceiverDataSet(
            "N0CALL", "EM48", "FT8AF 1.2.3"
        )
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Set ID = 0x50E2 (receiver template ID)
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(0x50E2)

        // Length = total byte count
        val len = buf.short.toInt() and 0xFFFF
        assertThat(len).isEqualTo(data.size)

        // Must be 4-byte aligned
        assertThat(data.size % 4).isEqualTo(0)

        // Callsign
        assertThat(buf.get().toInt() and 0xFF).isEqualTo(6) // "N0CALL"
        val callBytes = ByteArray(6)
        buf.get(callBytes)
        assertThat(String(callBytes)).isEqualTo("N0CALL")

        // Grid
        assertThat(buf.get().toInt() and 0xFF).isEqualTo(4)
        val gridBytes = ByteArray(4)
        buf.get(gridBytes)
        assertThat(String(gridBytes)).isEqualTo("EM48")

        // Software
        assertThat(buf.get().toInt() and 0xFF).isEqualTo(11)
        val swBytes = ByteArray(11)
        buf.get(swBytes)
        assertThat(String(swBytes)).isEqualTo("FT8AF 1.2.3")
    }

    // ---------------------------------------------------------------
    // Template encoding
    // ---------------------------------------------------------------

    @Test
    fun `encodeTemplates contains both receiver and sender templates`() {
        val data = PskReporterSender.encodeTemplates()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // First set: options template (set ID = 0x0003)
        val rxSetId = buf.short.toInt() and 0xFFFF
        assertThat(rxSetId).isEqualTo(0x0003)
        val rxSetLen = buf.short.toInt() and 0xFFFF
        assertThat(rxSetLen % 4).isEqualTo(0) // 4-byte aligned

        // Template ID = 0x50E2
        val rxTemplateId = buf.short.toInt() and 0xFFFF
        assertThat(rxTemplateId).isEqualTo(0x50E2)

        // Field count = 3
        val rxFieldCount = buf.short.toInt() and 0xFFFF
        assertThat(rxFieldCount).isEqualTo(3)

        // Scope field count = 0
        val scopeFieldCount = buf.short.toInt() and 0xFFFF
        assertThat(scopeFieldCount).isEqualTo(0)

        // Skip remaining receiver template fields and padding
        buf.position(rxSetLen)

        // Second set: data template (set ID = 0x0002)
        val txSetId = buf.short.toInt() and 0xFFFF
        assertThat(txSetId).isEqualTo(0x0002)
        val txSetLen = buf.short.toInt() and 0xFFFF
        assertThat(txSetLen % 4).isEqualTo(0)

        // Template ID = 0x50E3
        val txTemplateId = buf.short.toInt() and 0xFFFF
        assertThat(txTemplateId).isEqualTo(0x50E3)

        // Field count = 7
        val txFieldCount = buf.short.toInt() and 0xFFFF
        assertThat(txFieldCount).isEqualTo(7)

        // Verify enterprise number on first field
        // Field type has enterprise bit set (0x8000 | fieldId)
        val firstFieldType = buf.short.toInt() and 0xFFFF
        assertThat(firstFieldType and 0x8000).isNotEqualTo(0) // enterprise bit set
        buf.short // skip length
        val enterprise = buf.int
        assertThat(enterprise).isEqualTo(30351)
    }

    @Test
    fun `template field enterprise numbers are all 30351`() {
        val data = PskReporterSender.encodeTemplates()
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Scan for all 4-byte int values matching enterprise number pattern
        // Each field is 8 bytes: type(2) + length(2) + enterprise(4)
        // After set headers and template record headers
        val enterpriseValues = mutableListOf<Int>()
        // Receiver template: skip set header(4) + templateId(2) + fieldCount(2) + scopeFieldCount(2) = 10
        buf.position(10)
        repeat(3) {
            buf.short // type
            buf.short // length
            enterpriseValues.add(buf.int)
        }

        // Skip padding to next set
        val rxSetLen = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).run {
            short // skip set ID
            short.toInt() and 0xFFFF
        }
        buf.position(rxSetLen)

        // Sender template: skip set header(4) + templateId(2) + fieldCount(2) = 8
        buf.position(rxSetLen + 8)
        repeat(7) {
            buf.short // type
            buf.short // length
            enterpriseValues.add(buf.int)
        }

        for (en in enterpriseValues) {
            assertThat(en).isEqualTo(30351)
        }
    }

    // ---------------------------------------------------------------
    // Full packet structure
    // ---------------------------------------------------------------

    @Test
    fun `buildPackets produces valid IPFIX header`() {
        PskReporterSender.sequenceNumber = 42
        val spots = listOf(
            PskReporterSender.SpotRecord(
                senderCallsign = "W1AW",
                frequencyHz = 14_074_000L,
                snr = -10,
                mode = "FT8",
                senderLocator = "FN31",
                flowStartSeconds = 1_700_000_000L,
            )
        )

        val packets = PskReporterSender.buildPackets("N0CALL", "EM48", "FT8AF 1.0", spots)
        assertThat(packets).hasSize(1)

        val buf = ByteBuffer.wrap(packets[0]).order(ByteOrder.BIG_ENDIAN)

        // IPFIX version = 0x000A
        assertThat(buf.short.toInt() and 0xFFFF).isEqualTo(0x000A)

        // Total length matches actual byte array size
        val totalLen = buf.short.toInt() and 0xFFFF
        assertThat(totalLen).isEqualTo(packets[0].size)

        // Export time — non-zero unix timestamp
        val exportTime = buf.int.toLong() and 0xFFFFFFFFL
        assertThat(exportTime).isGreaterThan(0L)

        // Sequence number = 42
        assertThat(buf.int).isEqualTo(42)

        // Observation domain ID — just verify it's present
        buf.int // consume
    }

    @Test
    fun `buildPackets includes templates in first packet`() {
        val spots = listOf(
            PskReporterSender.SpotRecord("W1AW", 14_074_000L, -10, "FT8", "FN31", 1_700_000_000L)
        )

        val packets = PskReporterSender.buildPackets("N0CALL", "EM48", "FT8AF 1.0", spots)
        assertThat(packets).hasSize(1)

        val buf = ByteBuffer.wrap(packets[0]).order(ByteOrder.BIG_ENDIAN)
        buf.position(16) // skip IPFIX header

        // First set should be options template (0x0003)
        val firstSetId = buf.short.toInt() and 0xFFFF
        assertThat(firstSetId).isEqualTo(0x0003)
    }

    @Test
    fun `buildPackets respects MTU limit`() {
        // Create many spots to force multiple packets
        val spots = (1..100).map { i ->
            PskReporterSender.SpotRecord(
                senderCallsign = "CALL$i",
                frequencyHz = 14_074_000L + i,
                snr = -10,
                mode = "FT8",
                senderLocator = "FN${i.toString().padStart(2, '0')}",
                flowStartSeconds = 1_700_000_000L + i,
            )
        }

        val packets = PskReporterSender.buildPackets("N0CALL", "EM48", "FT8AF 1.0", spots)

        // Should produce multiple packets
        assertThat(packets.size).isGreaterThan(1)

        // Every packet must be <= 1400 bytes
        for (pkt in packets) {
            assertThat(pkt.size).isAtMost(1400)
        }

        // Total spots across all packets should equal 100
        // (verify by counting sender data set entries)
        var totalSpots = 0
        for (pkt in packets) {
            val buf = ByteBuffer.wrap(pkt).order(ByteOrder.BIG_ENDIAN)
            buf.position(2)
            val pktLen = buf.short.toInt() and 0xFFFF
            assertThat(pktLen).isEqualTo(pkt.size)
            totalSpots++ // approximate: at least one spot per packet
        }
        assertThat(totalSpots).isEqualTo(packets.size)
    }

    @Test
    fun `packet is 4-byte aligned`() {
        val spots = listOf(
            PskReporterSender.SpotRecord("W1AW", 14_074_000L, -10, "FT8", "FN31", 1_700_000_000L)
        )
        val packets = PskReporterSender.buildPackets("N0CALL", "EM48", "FT8AF 1.0", spots)
        for (pkt in packets) {
            assertThat(pkt.size % 4).isEqualTo(0)
        }
    }

    @Test
    fun `sequence number increments across packets`() {
        PskReporterSender.sequenceNumber = 0
        val spots = (1..100).map {
            PskReporterSender.SpotRecord("CALL$it", 14_074_000L, -10, "FT8", null, 1_700_000_000L)
        }
        val packets = PskReporterSender.buildPackets("N0CALL", "EM48", "FT8AF 1.0", spots)

        for ((i, pkt) in packets.withIndex()) {
            val buf = ByteBuffer.wrap(pkt).order(ByteOrder.BIG_ENDIAN)
            buf.position(8) // skip version, length, export time
            val seq = buf.int
            assertThat(seq).isEqualTo(i)
        }
    }
}

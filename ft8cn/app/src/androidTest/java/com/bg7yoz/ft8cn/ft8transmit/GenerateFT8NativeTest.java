package com.bg7yoz.ft8cn.ft8transmit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bg7yoz.ft8cn.ft8signal.FT8Package;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GenerateFT8NativeTest {

    @Test
    public void hashHelpers_matchKnownReferenceVectors() {
        assertHash("K1ABC", 712, 2851, 2920267);
        assertHash("W1AW", 73, 292, 299915);
        assertHash("BG7YOZ", 917, 3671, 3759609);
        assertHash("DL/W1AW", 219, 876, 897847);
    }

    @Test
    public void ft8Encode_zeroPayloadMatchesCostasReferenceVector() {
        byte[] payload = new byte[12];

        byte[] tones = GenerateFT8.encodeTonesForTest(payload);

        assertArrayEquals(new byte[]{
                3, 1, 4, 0, 6, 5, 2,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                3, 1, 4, 0, 6, 5, 2,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                3, 1, 4, 0, 6, 5, 2
        }, tones);
    }

    @Test
    public void ft8Encode_nonZeroPayloadMatchesLdpcReferenceVector() {
        byte[] payload = new byte[]{
                0x63, 0x77, (byte) 0x98, (byte) 0x88, (byte) 0xc8, 0x6f,
                (byte) 0xff, (byte) 0x80, 0x00, 0x00, 0x00, 0x00
        };

        byte[] tones = GenerateFT8.encodeTonesForTest(payload);

        assertArrayEquals(new byte[]{
                3, 1, 4, 0, 6, 5, 2, 2, 0, 4, 7, 2, 4, 2, 0, 5, 3, 1, 5, 5, 1, 6, 7, 7, 7, 7, 0, 0, 0, 0, 0, 0, 1, 3, 0, 7,
                3, 1, 4, 0, 6, 5, 2, 0, 3, 7, 1, 0, 1, 3, 1, 7, 3, 3, 4, 3, 1, 4, 5, 5, 6, 6, 0, 7, 0, 6, 0, 7, 7, 2, 3, 3,
                3, 1, 4, 0, 6, 5, 2
        }, tones);
    }

    @Test
    public void generateFt8ByA91_usesExpectedSymbolPeriodAndProducesFiniteAudio() {
        byte[] payload = new byte[12];

        float[] signal = GenerateFT8.generateFt8ByA91(payload, 1000.0f, 12000);

        assertEquals(151680, signal.length);
        float maxAbs = 0.0f;
        for (float sample : signal) {
            assertFalse(Float.isNaN(sample));
            assertFalse(Float.isInfinite(sample));
            maxAbs = Math.max(maxAbs, Math.abs(sample));
        }
        assertTrue(maxAbs > 0.01f);
        assertTrue(maxAbs <= 1.05f);
    }

    private static void assertHash(String callsign, int hash10, int hash12, int hash22) {
        assertEquals(hash10, FT8Package.getHash10(callsign));
        assertEquals(hash12, FT8Package.getHash12(callsign));
        assertEquals(hash22, FT8Package.getHash22(callsign));
    }
}

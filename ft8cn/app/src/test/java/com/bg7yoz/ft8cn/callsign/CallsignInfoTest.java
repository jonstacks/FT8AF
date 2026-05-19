package com.bg7yoz.ft8cn.callsign;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Exercise {@link CallsignInfo}'s data-model constructors. The 10-arg
 * constructor is a plain field-assigner; the single-arg String form parses
 * cty.dat-style colon-delimited records.
 *
 * Robolectric is needed because the class imports android.util.Log for its
 * error-path logging.
 */
@RunWith(RobolectricTestRunner.class)
public class CallsignInfoTest {

    @Test
    public void argConstructor_assignsAllFields() {
        CallsignInfo info = new CallsignInfo(
                "K1ABC", "United States", "美国", 5, 8,
                "NA", 42.5f, -71.0f, -5.0f, "K");

        assertThat(info.CallSign).isEqualTo("K1ABC");
        assertThat(info.CountryNameEn).isEqualTo("United States");
        assertThat(info.CountryNameCN).isEqualTo("美国");
        assertThat(info.CQZone).isEqualTo(5);
        assertThat(info.ITUZone).isEqualTo(8);
        assertThat(info.Continent).isEqualTo("NA");
        assertThat(info.Latitude).isEqualTo(42.5f);
        assertThat(info.Longitude).isEqualTo(-71.0f);
        assertThat(info.GMT_offset).isEqualTo(-5.0f);
        assertThat(info.DXCC).isEqualTo("K");
    }

    @Test
    public void stringConstructor_parsesColonDelimitedRecord() {
        // cty.dat-style row: country:cq:itu:continent:lat:lon:gmtOffset:dxcc:callsign
        CallsignInfo info = new CallsignInfo(
                "United States:5:8:NA:42.5:-71.0:-5.0:K:K1ABC");

        assertThat(info.CountryNameEn).isEqualTo("United States");
        assertThat(info.CQZone).isEqualTo(5);
        assertThat(info.ITUZone).isEqualTo(8);
        assertThat(info.Continent).isEqualTo("NA");
        assertThat(info.Latitude).isEqualTo(42.5f);
        assertThat(info.Longitude).isEqualTo(-71.0f);
        assertThat(info.GMT_offset).isEqualTo(-5.0f);
        assertThat(info.DXCC).isEqualTo("K");
        assertThat(info.CallSign).isEqualTo("K1ABC");
    }

    @Test
    public void stringConstructor_stripsWhitespaceFromNumericFields() {
        // Real cty.dat input has leading spaces around the numeric columns.
        CallsignInfo info = new CallsignInfo(
                "Canada: 5: 8:NA: 45.0: -75.0: -5.0:VE:VE3XYZ");

        assertThat(info.CQZone).isEqualTo(5);
        assertThat(info.ITUZone).isEqualTo(8);
        assertThat(info.Latitude).isEqualTo(45.0f);
        assertThat(info.Longitude).isEqualTo(-75.0f);
    }

    @Test
    public void stringConstructor_belowMinFieldCount_doesNotThrow() {
        // Fewer than 9 fields logs an error and bails without populating; we
        // assert it doesn't throw and that the default-initialised fields stay
        // at their zero values rather than triggering a NumberFormatException.
        CallsignInfo info = new CallsignInfo("too:few:fields");
        assertThat(info.CallSign).isNull();
        assertThat(info.CountryNameEn).isNull();
        assertThat(info.CQZone).isEqualTo(0);
    }

    @Test
    public void stringConstructor_stripsNewlinesFromCountryName() {
        // CountryNameEn keeps interior spaces but strips leading/trailing
        // newlines (replace("\n","").trim()).
        CallsignInfo info = new CallsignInfo(
                "United States\n:5:8:NA:42.5:-71.0:-5.0:K:K1ABC");
        assertThat(info.CountryNameEn).isEqualTo("United States");
    }
}

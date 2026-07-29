package it.gov.pagopa.gpd.technicalsupport.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommonUtilityTest {

  @Test
  void deNullString_shouldReturnEmptyStringWhenValueIsNull() {
    assertThat(CommonUtility.deNull((String) null)).isEmpty();
  }

  @Test
  void deNullString_shouldReturnOriginalValueWhenValueIsNotNull() {
    assertThat(CommonUtility.deNull("test")).isEqualTo("test");
  }

  @Test
  void deNullObject_shouldReturnEmptyStringWhenValueIsNull() {
    assertThat(CommonUtility.deNull((Object) null)).isEmpty();
  }

  @Test
  void deNullObject_shouldReturnToStringValueWhenValueIsNotNull() {
    assertThat(CommonUtility.deNull(123)).isEqualTo("123");
  }

  @Test
  void deNullBoolean_shouldReturnFalseWhenValueIsNull() {
    assertThat(CommonUtility.deNull((Boolean) null)).isFalse();
  }

  @Test
  void deNullBoolean_shouldReturnOriginalValueWhenValueIsNotNull() {
    assertThat(CommonUtility.deNull(Boolean.TRUE)).isTrue();
    assertThat(CommonUtility.deNull(Boolean.FALSE)).isFalse();
  }

  @Test
  void createCsv_shouldCreateCsvWithSemicolonSeparatorAndSystemLineSeparator() {
    byte[] csv =
        CommonUtility.createCsv(
            List.of("header1", "header2"),
            List.of(
                List.of("row1-col1", "row1-col2"),
                List.of("row2-col1", "row2-col2")));

    String result = new String(csv, StandardCharsets.UTF_8);

    assertThat(result)
        .isEqualTo(
            "header1;header2"
                + System.lineSeparator()
                + "row1-col1;row1-col2"
                + System.lineSeparator()
                + "row2-col1;row2-col2");
  }

  @Test
  void createCsv_shouldCreateOnlyHeaderWhenRowsAreEmpty() {
    byte[] csv =
        CommonUtility.createCsv(
            List.of("header1", "header2"),
            List.of());

    String result = new String(csv, StandardCharsets.UTF_8);

    assertThat(result).isEqualTo("header1;header2");
  }

  @Test
  void getTimelapse_shouldReturnElapsedMillisecondsFromStartTime() {
    long startTime = Calendar.getInstance().getTimeInMillis() - 1000;

    long result = CommonUtility.getTimelapse(startTime);

    assertThat(result).isGreaterThanOrEqualTo(1000L);
  }

  @Test
  void constructor_shouldBePrivate() throws Exception {
    Constructor<CommonUtility> constructor = CommonUtility.class.getDeclaredConstructor();

    assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

    constructor.setAccessible(true);

    assertThat(constructor.newInstance()).isNotNull();
  }
}
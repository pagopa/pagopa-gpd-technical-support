package it.gov.pagopa.gpd.technicalsupport.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommonUtility {

  /**
   * @param value value to deNullify.
   * @return empty string if value is null
   */
  public static String deNull(String value) {
    return Optional.ofNullable(value).orElse("");
  }

  /**
   * @param value value to deNullify.
   * @return empty string if value is null
   */
  public static String deNull(Object value) {
    return Optional.ofNullable(value).orElse("").toString();
  }

  /**
   * @param value value to deNullify.
   * @return false if value is null
   */
  public static Boolean deNull(Boolean value) {
    return Optional.ofNullable(value).orElse(false);
  }

  /**
   * @param headers headers of the CSV file
   * @param rows rows of the CSV file
   * @return byte array of the CSV using semicolon as separator
   */
  public static byte[] createCsv(List<String> headers, List<List<String>> rows) {
    var csv = new StringBuilder();
    csv.append(String.join(";", headers));
    rows.forEach(
        row ->
            csv.append(System.lineSeparator())
                .append(String.join(";", row)));

    return csv.toString().getBytes(UTF_8);
  }

  public static long getTimelapse(long startTime) {
    Instant start = Instant.ofEpochMilli(startTime);
    return Duration.between(start, Instant.now()).toMillis();
  }
}
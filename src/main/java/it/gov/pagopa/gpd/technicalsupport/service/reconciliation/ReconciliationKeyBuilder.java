package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationKeyBuilder {

  public static final String RUN_KEY_SEPARATOR = "__";
  public static final String SERVICE_TYPES_SEPARATOR = "|";

  private static final DateTimeFormatter EXECUTION_ID_TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

  public String serviceTypesKey(List<ServiceType> serviceTypes) {
    return serviceTypes.stream()
        .distinct()
        .sorted(Comparator.comparing(Enum::name))
        .map(Enum::name)
        .collect(Collectors.joining(SERVICE_TYPES_SEPARATOR));
  }

  public String logicalRunKey(LocalDate day, List<ServiceType> serviceTypes) {
    return day + RUN_KEY_SEPARATOR + serviceTypesKey(serviceTypes);
  }

  public String executionId(String logicalRunKey, OffsetDateTime now) {
    return logicalRunKey + RUN_KEY_SEPARATOR + now.format(EXECUTION_ID_TIMESTAMP_FORMATTER);
  }
}
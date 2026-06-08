package it.gov.pagopa.gpd.technicalsupport.service.reconciliation;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationKeyBuilder {

  private static final DateTimeFormatter EXECUTION_ID_TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

  public String serviceTypesKey(List<ServiceType> serviceTypes) {
    return serviceTypes.stream()
        .distinct()
        .sorted(Comparator.comparing(Enum::name))
        .map(Enum::name)
        .collect(Collectors.joining("|"));
  }

  public String logicalRunKey(java.time.LocalDate day, List<ServiceType> serviceTypes) {
    return day + "#" + serviceTypesKey(serviceTypes);
  }

  public String executionId(String logicalRunKey, OffsetDateTime now) {
    return logicalRunKey + "#" + now.format(EXECUTION_ID_TIMESTAMP_FORMATTER);
  }
}
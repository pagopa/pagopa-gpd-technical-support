package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcPaymentOptionCandidateReaderTest {

  private final NamedParameterJdbcTemplate jdbcTemplate =
      mock(NamedParameterJdbcTemplate.class);

  private final JdbcPaymentOptionCandidateReader reader =
      new JdbcPaymentOptionCandidateReader(jdbcTemplate);

  @Test
  void forEachCandidateChunk_shouldReadCandidatesByChunksAndMapRows() throws Exception {
    LocalDate day = LocalDate.of(2026, Month.JUNE, 10);

    ResultSet firstRow =
        row(
            day,
            "GPD",
            "payment-position-id-1",
            "payment-option-id-1",
            "77777777777",
            "302131563536065220",
            "02131563536065220",
            "VALID",
            "PO_UNPAID",
            "SINGLE_OPTION");

    ResultSet secondRow =
        row(
            day,
            "WISP",
            "payment-position-id-2",
            "payment-option-id-2",
            "88888888888",
            "302131563536065221",
            "02131563536065221",
            "EXPIRED",
            "PO_UNPAID",
            "PLAN_1");

    ResultSet thirdRow =
        row(
            day,
            "GPD",
            "payment-position-id-3",
            "payment-option-id-3",
            "99999999999",
            "302131563536065222",
            "02131563536065222",
            "PARTIALLY_PAID",
            "PO_UNPAID",
            "PLAN_2");

    mockQueryRows(firstRow, secondRow, thirdRow);

    List<List<ReconciliationCandidate>> chunks = new ArrayList<>();

    reader.forEachCandidateChunk(
        day,
        List.of(ServiceType.GPD, ServiceType.GPD, ServiceType.WISP),
        2,
        chunks::add);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).hasSize(2);
    assertThat(chunks.get(1)).hasSize(1);

    ReconciliationCandidate firstCandidate = chunks.get(0).get(0);

    assertThat(firstCandidate.day()).isEqualTo(day);
    assertThat(firstCandidate.serviceType()).isEqualTo(ServiceType.GPD);
    assertThat(firstCandidate.paymentPositionId()).isEqualTo("payment-position-id-1");
    assertThat(firstCandidate.paymentOptionId()).isEqualTo("payment-option-id-1");
    assertThat(firstCandidate.ec()).isEqualTo("77777777777");
    assertThat(firstCandidate.nav()).isEqualTo("302131563536065220");
    assertThat(firstCandidate.iuv()).isEqualTo("02131563536065220");
    assertThat(firstCandidate.ppStatus()).isEqualTo(DebtPositionStatus.VALID);
    assertThat(firstCandidate.poStatus()).isEqualTo(PaymentOptionStatus.PO_UNPAID);
    assertThat(firstCandidate.paymentPlanId()).isEqualTo("SINGLE_OPTION");

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
        ArgumentCaptor.forClass(MapSqlParameterSource.class);

    verify(jdbcTemplate)
        .query(
            sqlCaptor.capture(),
            paramsCaptor.capture(),
            any(RowCallbackHandler.class));

    assertThat(sqlCaptor.getValue())
        .contains("FROM apd.payment_position pp")
        .contains("JOIN apd.payment_option po")
        .contains("pp.inserted_date >= :dayStart")
        .contains("pp.inserted_date <  :dayEnd")
        .contains("pp.service_type IN (:serviceTypes)")
        .contains("po.status = 'PO_UNPAID'")
        .contains("pp.archived = false")
        .contains("po.archived = false")
        .contains("pp.status <> 'PARTIALLY_PAID'")
        .contains("po.payment_plan_id IS NOT NULL")
        .contains("po_paid.status = 'PO_PAID'");

    MapSqlParameterSource params = paramsCaptor.getValue();

    assertThat(params.getValue("dayStart"))
        .isEqualTo(Timestamp.valueOf(day.atStartOfDay()));

    assertThat(params.getValue("dayEnd"))
        .isEqualTo(Timestamp.valueOf(day.plusDays(1).atStartOfDay()));

    assertThat(params.getValue("serviceTypes"))
        .isEqualTo(List.of("GPD", "WISP"));
  }

  @Test
  void forEachCandidateChunk_shouldNotEmitChunksWhenNoRowsAreReturned() {
    LocalDate day = LocalDate.of(2026, Month.JUNE, 10);

    mockQueryRows();

    List<List<ReconciliationCandidate>> chunks = new ArrayList<>();

    reader.forEachCandidateChunk(
        day,
        List.of(ServiceType.GPD),
        500,
        chunks::add);

    assertThat(chunks).isEmpty();
  }

  @Test
  void forEachCandidateChunk_shouldEmitSingleChunkWhenRowsAreLessThanChunkSize() throws Exception {
    LocalDate day = LocalDate.of(2026, Month.JUNE, 10);

    ResultSet firstRow =
        row(
            day,
            "GPD",
            "payment-position-id-1",
            "payment-option-id-1",
            "77777777777",
            "302131563536065220",
            "02131563536065220",
            "VALID",
            "PO_UNPAID",
            "SINGLE_OPTION");

    ResultSet secondRow =
        row(
            day,
            "GPD",
            "payment-position-id-2",
            "payment-option-id-2",
            "77777777777",
            "302131563536065221",
            "02131563536065221",
            "INVALID",
            "PO_UNPAID",
            "SINGLE_OPTION");

    mockQueryRows(firstRow, secondRow);

    List<List<ReconciliationCandidate>> chunks = new ArrayList<>();

    reader.forEachCandidateChunk(
        day,
        List.of(ServiceType.GPD),
        500,
        chunks::add);

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).hasSize(2);
  }

  private void mockQueryRows(ResultSet... rows) {
    doAnswer(
            invocation -> {
              RowCallbackHandler rowCallbackHandler = invocation.getArgument(2);

              for (ResultSet row : rows) {
                rowCallbackHandler.processRow(row);
              }

              return null;
            })
        .when(jdbcTemplate)
        .query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowCallbackHandler.class));
  }

  private ResultSet row(
      LocalDate day,
      String serviceType,
      String paymentPositionId,
      String paymentOptionId,
      String ec,
      String nav,
      String iuv,
      String ppStatus,
      String poStatus,
      String paymentPlanId)
      throws Exception {

    ResultSet rs = mock(ResultSet.class);

    when(rs.getObject("day", LocalDate.class)).thenReturn(day);
    when(rs.getString("service_type")).thenReturn(serviceType);
    when(rs.getString("payment_position_id")).thenReturn(paymentPositionId);
    when(rs.getString("payment_option_id")).thenReturn(paymentOptionId);
    when(rs.getString("ec")).thenReturn(ec);
    when(rs.getString("nav")).thenReturn(nav);
    when(rs.getString("iuv")).thenReturn(iuv);
    when(rs.getString("pp_status")).thenReturn(ppStatus);
    when(rs.getString("po_status")).thenReturn(poStatus);
    when(rs.getString("payment_plan_id")).thenReturn(paymentPlanId);

    return rs;
  }
}
package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcPaymentOptionCandidateReaderTest {

  private final NamedParameterJdbcTemplate jdbcTemplate =
      mock(NamedParameterJdbcTemplate.class);

  private final JdbcPaymentOptionCandidateReader reader =
      new JdbcPaymentOptionCandidateReader(jdbcTemplate);

  @Test
  void forEachCandidateChunk_shouldReadCandidatesByChunksAndMapRows()
      throws Exception {

    LocalDate day = LocalDate.of(2026, Month.JUNE, 10);

    LocalDateTime firstInsertedDate =
        day.atTime(0, 0, 1);

    LocalDateTime secondInsertedDate =
        day.atTime(0, 0, 2);

    LocalDateTime thirdInsertedDate =
        day.atTime(0, 0, 3);

    ResultSet firstRow =
        row(
            firstInsertedDate,
            "GPD",
            1L,
            101L,
            "77777777777",
            "302131563536065220",
            "02131563536065220",
            "VALID",
            "PO_UNPAID",
            "SINGLE_OPTION");

    ResultSet secondRow =
        row(
            secondInsertedDate,
            "WISP",
            2L,
            102L,
            "88888888888",
            "302131563536065221",
            "02131563536065221",
            "EXPIRED",
            "PO_UNPAID",
            "PLAN_1");

    ResultSet thirdRow =
        row(
            thirdInsertedDate,
            "GPD",
            3L,
            103L,
            "99999999999",
            "302131563536065222",
            "02131563536065222",
            "PARTIALLY_PAID",
            "PO_UNPAID",
            "PLAN_2");

    mockQueryRows(firstRow, secondRow, thirdRow);

    List<List<ReconciliationCandidate>> chunks =
        new ArrayList<>();

    reader.forEachCandidateChunk(
        day,
        List.of(
            ServiceType.GPD,
            ServiceType.GPD,
            ServiceType.WISP),
        2,
        chunks::add);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).hasSize(2);
    assertThat(chunks.get(1)).hasSize(1);

    ReconciliationCandidate firstCandidate =
        chunks.get(0).get(0);

    assertThat(firstCandidate.day())
        .isEqualTo(day);

    assertThat(firstCandidate.serviceType())
        .isEqualTo(ServiceType.GPD);

    assertThat(firstCandidate.paymentPositionId())
        .isEqualTo("1");

    assertThat(firstCandidate.paymentOptionId())
        .isEqualTo("101");

    assertThat(firstCandidate.ec())
        .isEqualTo("77777777777");

    assertThat(firstCandidate.nav())
        .isEqualTo("302131563536065220");

    assertThat(firstCandidate.iuv())
        .isEqualTo("02131563536065220");

    assertThat(firstCandidate.ppStatus())
        .isEqualTo(DebtPositionStatus.VALID);

    assertThat(firstCandidate.poStatus())
        .isEqualTo(PaymentOptionStatus.PO_UNPAID);

    assertThat(firstCandidate.paymentPlanId())
        .isEqualTo("SINGLE_OPTION");

    ReconciliationCandidate thirdCandidate =
        chunks.get(1).get(0);

    assertThat(thirdCandidate.paymentPositionId())
        .isEqualTo("3");

    assertThat(thirdCandidate.paymentOptionId())
        .isEqualTo("103");

    assertThat(thirdCandidate.ppStatus())
        .isEqualTo(DebtPositionStatus.PARTIALLY_PAID);

    ArgumentCaptor<String> sqlCaptor =
        ArgumentCaptor.forClass(String.class);

    ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
        ArgumentCaptor.forClass(MapSqlParameterSource.class);

    verify(jdbcTemplate, times(2))
        .query(
            sqlCaptor.capture(),
            paramsCaptor.capture(),
            any(RowMapper.class));

    String sql =
        sqlCaptor.getAllValues().get(0);

    assertThat(sql)
        .contains("FROM apd.payment_position pp")
        .contains("JOIN apd.payment_option po")
        .contains("pp.inserted_date >= :dayStart")
        .contains("pp.inserted_date <  :dayEnd")
        .contains("pp.service_type IN (:serviceTypes)")
        .contains("po.status = 'PO_UNPAID'")
        .contains("pp.archived = false")
        .contains("po.archived = false")
        .contains(":lastInsertedDate")
        .contains(":lastPaymentPositionId")
        .contains(":lastPaymentOptionId")
        .contains("pp.status <> 'PARTIALLY_PAID'")
        .contains("po.payment_plan_id IS NOT NULL")
        .contains("po_paid.status = 'PO_PAID'")
        .contains("ORDER BY")
        .contains("LIMIT :chunkSize");

    List<MapSqlParameterSource> capturedParams =
        paramsCaptor.getAllValues();

    MapSqlParameterSource firstPageParams =
        capturedParams.get(0);

    MapSqlParameterSource secondPageParams =
        capturedParams.get(1);

    assertThat(firstPageParams.getValue("dayStart"))
        .isEqualTo(
            Timestamp.valueOf(day.atStartOfDay()));

    assertThat(firstPageParams.getValue("dayEnd"))
        .isEqualTo(
            Timestamp.valueOf(
                day.plusDays(1).atStartOfDay()));

    assertThat(firstPageParams.getValue("serviceTypes"))
        .isEqualTo(List.of("GPD", "WISP"));

    assertThat(firstPageParams.getValue("chunkSize"))
        .isEqualTo(2);

    /*
     * The first page starts from the minimum possible composite cursor
     * for the selected processing day.
     */
    assertThat(firstPageParams.getValue("lastInsertedDate"))
        .isEqualTo(
            Timestamp.valueOf(day.atStartOfDay()));

    assertThat(
        firstPageParams.getValue(
            "lastPaymentPositionId"))
        .isEqualTo(Long.MIN_VALUE);

    assertThat(
        firstPageParams.getValue(
            "lastPaymentOptionId"))
        .isEqualTo(Long.MIN_VALUE);

    /*
     * The second page must start from the composite key of the last row
     * returned by the first page.
     */
    assertThat(secondPageParams.getValue("lastInsertedDate"))
        .isEqualTo(
            Timestamp.valueOf(secondInsertedDate));

    assertThat(
        secondPageParams.getValue(
            "lastPaymentPositionId"))
        .isEqualTo(2L);

    assertThat(
        secondPageParams.getValue(
            "lastPaymentOptionId"))
        .isEqualTo(102L);

    assertThat(secondPageParams.getValue("chunkSize"))
        .isEqualTo(2);
  }

  @Test
  void forEachCandidateChunk_shouldNotEmitChunksWhenNoRowsAreReturned() {

    LocalDate day =
        LocalDate.of(2026, Month.JUNE, 10);

    mockQueryRows();

    List<List<ReconciliationCandidate>> chunks =
        new ArrayList<>();

    reader.forEachCandidateChunk(
        day,
        List.of(ServiceType.GPD),
        500,
        chunks::add);

    assertThat(chunks).isEmpty();

    ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
        ArgumentCaptor.forClass(MapSqlParameterSource.class);

    verify(jdbcTemplate)
        .query(
            anyString(),
            paramsCaptor.capture(),
            any(RowMapper.class));

    MapSqlParameterSource params =
        paramsCaptor.getValue();

    assertThat(params.getValue("lastInsertedDate"))
        .isEqualTo(
            Timestamp.valueOf(day.atStartOfDay()));

    assertThat(params.getValue("lastPaymentPositionId"))
        .isEqualTo(Long.MIN_VALUE);

    assertThat(params.getValue("lastPaymentOptionId"))
        .isEqualTo(Long.MIN_VALUE);
  }

  @Test
  void forEachCandidateChunk_shouldEmitSingleChunkWhenRowsAreLessThanChunkSize()
      throws Exception {

    LocalDate day =
        LocalDate.of(2026, Month.JUNE, 10);

    ResultSet firstRow =
        row(
            day.atTime(0, 0, 1),
            "GPD",
            1L,
            101L,
            "77777777777",
            "302131563536065220",
            "02131563536065220",
            "VALID",
            "PO_UNPAID",
            "SINGLE_OPTION");

    ResultSet secondRow =
        row(
            day.atTime(0, 0, 2),
            "GPD",
            2L,
            102L,
            "77777777777",
            "302131563536065221",
            "02131563536065221",
            "INVALID",
            "PO_UNPAID",
            "SINGLE_OPTION");

    mockQueryRows(firstRow, secondRow);

    List<List<ReconciliationCandidate>> chunks =
        new ArrayList<>();

    reader.forEachCandidateChunk(
        day,
        List.of(ServiceType.GPD),
        500,
        chunks::add);

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).hasSize(2);

    assertThat(chunks.get(0).get(0).paymentPositionId())
        .isEqualTo("1");

    assertThat(chunks.get(0).get(1).paymentPositionId())
        .isEqualTo("2");

    assertThat(chunks.get(0).get(1).ppStatus())
        .isEqualTo(DebtPositionStatus.INVALID);

    verify(jdbcTemplate)
        .query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void mockQueryRows(ResultSet... rows) {

    AtomicInteger nextRowIndex =
        new AtomicInteger();

    doAnswer(
            invocation -> {
              MapSqlParameterSource params =
                  invocation.getArgument(1);

              RowMapper<Object> rowMapper =
                  invocation.getArgument(2);

              int chunkSize =
                  ((Number) params.getValue("chunkSize"))
                      .intValue();

              List<Object> page =
                  new ArrayList<>();

              while (
                  page.size() < chunkSize
                      && nextRowIndex.get() < rows.length) {

                ResultSet resultSet =
                    rows[nextRowIndex.getAndIncrement()];

                Object mappedRow =
                    rowMapper.mapRow(
                        resultSet,
                        page.size());

                page.add(mappedRow);
              }

              return page;
            })
        .when(jdbcTemplate)
        .query(
            anyString(),
            any(MapSqlParameterSource.class),
            any(RowMapper.class));
  }

  private ResultSet row(
      LocalDateTime insertedDate,
      String serviceType,
      long paymentPositionId,
      long paymentOptionId,
      String ec,
      String nav,
      String iuv,
      String ppStatus,
      String poStatus,
      String paymentPlanId)
      throws Exception {

    ResultSet resultSet =
        mock(ResultSet.class);

    when(resultSet.getTimestamp("inserted_date"))
        .thenReturn(
            Timestamp.valueOf(insertedDate));

    when(resultSet.getObject("day", LocalDate.class))
        .thenReturn(
            insertedDate.toLocalDate());

    when(resultSet.getString("service_type"))
        .thenReturn(serviceType);

    when(resultSet.getLong("payment_position_id"))
        .thenReturn(paymentPositionId);

    when(resultSet.getLong("payment_option_id"))
        .thenReturn(paymentOptionId);

    when(resultSet.getString("ec"))
        .thenReturn(ec);

    when(resultSet.getString("nav"))
        .thenReturn(nav);

    when(resultSet.getString("iuv"))
        .thenReturn(iuv);

    when(resultSet.getString("pp_status"))
        .thenReturn(ppStatus);

    when(resultSet.getString("po_status"))
        .thenReturn(poStatus);

    when(resultSet.getString("payment_plan_id"))
        .thenReturn(paymentPlanId);

    return resultSet;
  }
}
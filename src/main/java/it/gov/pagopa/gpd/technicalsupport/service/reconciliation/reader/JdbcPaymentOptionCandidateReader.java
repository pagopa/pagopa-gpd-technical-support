package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.reader;

import it.gov.pagopa.gpd.technicalsupport.model.gpd.DebtPositionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.PaymentOptionStatus;
import it.gov.pagopa.gpd.technicalsupport.model.gpd.ServiceType;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.apd.ReconciliationCandidate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JdbcPaymentOptionCandidateReader implements PaymentOptionCandidateReader {

  /*
   * Selects APD payment options that are eligible for status reconciliation for a single processing day.
   *
   * The query starts from payment_position because the reconciliation run is day-based and the
   * inserted_date range allows PostgreSQL to reduce the dataset early by using the available date index.
   * It then joins payment_option through payment_position_id and keeps only unpaid payment options
   * belonging to debt positions in statuses relevant for reconciliation.
   *
   * PARTIALLY_PAID behavior: only unpaid options that belong to an installment
   * plan must be selected, and only when at least one option in the same plan has already been paid.
   * For this reason, payment_plan_id must be not null and the EXISTS clause checks for a PO_PAID option
   * with the same payment_position_id and payment_plan_id. Null payment_plan_id values represent single
   * payment options, not installment plan branches, and are therefore not considered by this rule.
   *
   * This reader intentionally uses a projection-based JDBC query instead of JPA entities. The reconciliation
   * job only needs a small, read-only subset of columns from very large tables, so avoiding entity
   * loading, persistence context management, lazy relations and object graph keeps
   * the query more lighter in memory and easier to tune through SQL execution plan.
   *
   * OFFSET pagination is intentionally not used because it becomes increasingly expensive on large tables:
   * PostgreSQL still has to scan and discard skipped rows. The query is currently executed per bounded day
   * window.
   */
  private static final String FIND_CANDIDATES_SQL = """
      SELECT
          pp.inserted_date::date      AS day,
          pp.service_type             AS service_type,
          pp.id::text                 AS payment_position_id,
          po.id::text                 AS payment_option_id,
          po.organization_fiscal_code AS ec,
          po.nav                      AS nav,
          po.iuv                      AS iuv,
          pp.status                   AS pp_status,
          po.status                   AS po_status,
          po.payment_plan_id          AS payment_plan_id
      FROM apd.payment_position pp
      JOIN apd.payment_option po
        ON po.payment_position_id = pp.id
      WHERE pp.inserted_date >= :dayStart
        AND pp.inserted_date <  :dayEnd
        AND pp.service_type IN (:serviceTypes)
        AND pp.status IN ('VALID', 'PARTIALLY_PAID', 'EXPIRED', 'INVALID')
        AND po.status = 'PO_UNPAID'
        AND pp.archived = false
        AND po.archived = false
        AND (
              pp.status <> 'PARTIALLY_PAID'
              OR (
                  po.payment_plan_id IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM apd.payment_option po_paid
                      WHERE po_paid.payment_position_id = po.payment_position_id
                        AND po_paid.status = 'PO_PAID'
                        AND po_paid.archived = false
                        AND po_paid.payment_plan_id = po.payment_plan_id
                  )
              )
            )
      """;

  private final NamedParameterJdbcTemplate apdReadReplicaNamedParameterJdbcTemplate;

  @Override
  public List<ReconciliationCandidate> findCandidates(
      LocalDate day,
      List<ServiceType> serviceTypes) {

    LocalDateTime dayStart = day.atStartOfDay();
    LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

    List<String> serviceTypeNames =
        serviceTypes.stream()
            .distinct()
            .map(Enum::name)
            .toList();

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("dayStart", Timestamp.valueOf(dayStart))
            .addValue("dayEnd", Timestamp.valueOf(dayEnd))
            .addValue("serviceTypes", serviceTypeNames);

    log.info(
        "Reading APD reconciliation candidates. day={}, dayStart={}, dayEnd={}, serviceTypes={}",
        day,
        dayStart,
        dayEnd,
        serviceTypeNames);

    List<ReconciliationCandidate> candidates =
        apdReadReplicaNamedParameterJdbcTemplate.query(
            FIND_CANDIDATES_SQL,
            params,
            this::mapCandidate);

    log.info(
        "APD reconciliation candidates loaded. day={}, serviceTypes={}, candidates={}",
        day,
        serviceTypeNames,
        candidates.size());

    return candidates;
  }

  private ReconciliationCandidate mapCandidate(ResultSet rs, int rowNum) throws SQLException {
    return new ReconciliationCandidate(
        rs.getObject("day", LocalDate.class),
        ServiceType.valueOf(rs.getString("service_type")),
        rs.getString("payment_position_id"),
        rs.getString("payment_option_id"),
        rs.getString("ec"),
        rs.getString("nav"),
        rs.getString("iuv"),
        DebtPositionStatus.valueOf(rs.getString("pp_status")),
        PaymentOptionStatus.valueOf(rs.getString("po_status")),
        rs.getString("payment_plan_id"));
  }
}
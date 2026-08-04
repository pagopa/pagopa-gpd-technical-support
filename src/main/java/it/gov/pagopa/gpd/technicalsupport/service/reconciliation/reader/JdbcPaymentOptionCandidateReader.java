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
import java.util.function.Consumer;
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
	 * Selects APD payment options eligible for status reconciliation for a
	 * single processing day.
	 *
	 * PARTIALLY_PAID positions contribute only unpaid options belonging to an
	 * installment plan where at least one option in the same plan has been paid.
	 *
	 * OFFSET pagination is intentionally avoided because PostgreSQL would still
	 * scan and discard all preceding rows before returning the requested page.
	 * Instead, candidates are retrieved via keyset pagination using the composite
	 * cursor (payment_position.inserted_date, payment_position.id, payment_option.id).
	 * The query results are ordered by these same fields.
	 * 
	 * Execution flow example:
	 *
	 * - The initial query fetches the first chunk of the day (e.g., 100 rows).
	 *   Once processed, the composite key of the last row is recorded, for example
	 *   (10:15:00, 5000, 12000).
	 *
	 * - The subsequent query does not use OFFSET to skip the first 100 rows.
	 *   Instead, it directly requests rows where the composite key is greater than
	 *   the saved cursor:
	 *   (inserted_date, payment_position_id, payment_option_id) > ('10:15:00', 5000, 12000)
	 *
	 * - The cursor is updated after each chunk. The process repeats until a query
	 *   returns fewer rows than the configured chunk size, signaling the end of
	 *   the dataset.
	 *
	 * This ordering aligns with the index on inserted_date, allowing PostgreSQL
	 * to resume reading directly from the last processed key without scanning
	 * earlier records.
	 *
	 * Each page is retrieved using an independent, bounded query. The JDBC ResultSet
	 * is closed before candidates are passed to the reconciliation processor to keep
	 * read snapshots as short as possible.
	 */
	private static final String FIND_CANDIDATES_PAGE_SQL =
			"""
			SELECT
			    pp.inserted_date             AS inserted_date,
			    pp.inserted_date::date       AS day,
			    pp.service_type              AS service_type,
			    pp.id                        AS payment_position_id,
			    po.id                        AS payment_option_id,
			    po.organization_fiscal_code AS ec,
			    po.nav                       AS nav,
			    po.iuv                       AS iuv,
			    pp.status                    AS pp_status,
			    po.status                    AS po_status,
			    po.payment_plan_id           AS payment_plan_id
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
			        pp.inserted_date,
			        pp.id,
			        po.id
			      ) > (
			        :lastInsertedDate,
			        :lastPaymentPositionId,
			        :lastPaymentOptionId
			      )
			  AND (
			        pp.status <> 'PARTIALLY_PAID'
			        OR (
			            po.payment_plan_id IS NOT NULL
			            AND EXISTS (
			                SELECT 1
			                FROM apd.payment_option po_paid
			                WHERE po_paid.payment_position_id =
			                      po.payment_position_id
			                  AND po_paid.status = 'PO_PAID'
			                  AND po_paid.archived = false
			                  AND po_paid.payment_plan_id =
			                      po.payment_plan_id
			            )
			        )
			      )
			ORDER BY
			    pp.inserted_date ASC,
			    pp.id ASC,
			    po.id ASC
			LIMIT :chunkSize
			""";

	private final NamedParameterJdbcTemplate
	apdReadReplicaNamedParameterJdbcTemplate;

	@Override
	public void forEachCandidateChunk(
			LocalDate day,
			List<ServiceType> serviceTypes,
			int chunkSize,
			Consumer<List<ReconciliationCandidate>> chunkConsumer) {

		if (chunkSize <= 0) {
			throw new IllegalArgumentException(
					"chunkSize must be greater than zero");
		}

		LocalDateTime dayStart = day.atStartOfDay();
		LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

		List<String> serviceTypeNames =
				serviceTypes.stream()
				.distinct()
				.map(Enum::name)
				.toList();

		if (serviceTypeNames.isEmpty()) {
			log.info(
					"No service types provided for APD reconciliation candidate "
							+ "reading. day={}",
							day);
			return;
		}

		log.info(
				"Reading APD reconciliation candidates using composite keyset "
						+ "pagination. day={}, dayStart={}, dayEnd={}, "
						+ "serviceTypes={}, chunkSize={}",
						day,
						dayStart,
						dayEnd,
						serviceTypeNames,
						chunkSize);

		CandidateCursor cursor =
				new CandidateCursor(
						dayStart,
						Long.MIN_VALUE,
						Long.MIN_VALUE);

		long totalCandidates = 0;
		long chunkNumber = 0;

		List<CandidatePageRow> page;

		do {
			page =
					readCandidatePage(
							dayStart,
							dayEnd,
							serviceTypeNames,
							cursor,
							chunkSize);

			if (!page.isEmpty()) {
				CandidateCursor nextCursor =
						page.get(page.size() - 1).cursor();

				if (nextCursor.compareTo(cursor) <= 0) {
					throw new IllegalStateException(
							"Keyset pagination cursor did not advance. "
									+ "currentCursor="
									+ cursor
									+ ", nextCursor="
									+ nextCursor);
				}

				List<ReconciliationCandidate> candidates =
						page.stream()
						.map(CandidatePageRow::candidate)
						.toList();

				chunkNumber++;

				processChunk(
						day,
						serviceTypeNames,
						chunkNumber,
						candidates,
						chunkConsumer);

				totalCandidates += candidates.size();
				cursor = nextCursor;
			}
		} while (page.size() == chunkSize);

		log.info(
				"APD reconciliation candidates fully read. "
						+ "day={}, serviceTypes={}, totalCandidates={}, chunks={}",
						day,
						serviceTypeNames,
						totalCandidates,
						chunkNumber);
	}

	private List<CandidatePageRow> readCandidatePage(
			LocalDateTime dayStart,
			LocalDateTime dayEnd,
			List<String> serviceTypeNames,
			CandidateCursor cursor,
			int chunkSize) {

		MapSqlParameterSource params =
				new MapSqlParameterSource()
				.addValue("dayStart", Timestamp.valueOf(dayStart))
				.addValue("dayEnd", Timestamp.valueOf(dayEnd))
				.addValue("serviceTypes", serviceTypeNames)
				.addValue(
						"lastInsertedDate",
						Timestamp.valueOf(cursor.insertedDate()))
				.addValue(
						"lastPaymentPositionId",
						cursor.paymentPositionId())
				.addValue(
						"lastPaymentOptionId",
						cursor.paymentOptionId())
				.addValue("chunkSize", chunkSize);

		return apdReadReplicaNamedParameterJdbcTemplate.query(
				FIND_CANDIDATES_PAGE_SQL,
				params,
				this::mapCandidatePageRow);
	}

	private void processChunk(
			LocalDate day,
			List<String> serviceTypeNames,
			long chunkNumber,
			List<ReconciliationCandidate> candidates,
			Consumer<List<ReconciliationCandidate>> chunkConsumer) {

		log.info(
				"APD reconciliation candidate chunk loaded. "
						+ "day={}, serviceTypes={}, chunkNumber={}, chunkSize={}",
						day,
						serviceTypeNames,
						chunkNumber,
						candidates.size());

		chunkConsumer.accept(candidates);
	}

	private CandidatePageRow mapCandidatePageRow(
			ResultSet rs,
			int rowNumber)
					throws SQLException {

		LocalDateTime insertedDate =
				rs.getTimestamp("inserted_date").toLocalDateTime();

		long paymentPositionId =
				rs.getLong("payment_position_id");

		long paymentOptionId =
				rs.getLong("payment_option_id");

		ReconciliationCandidate candidate =
				new ReconciliationCandidate(
						rs.getObject("day", LocalDate.class),
						ServiceType.valueOf(rs.getString("service_type")),
						Long.toString(paymentPositionId),
						Long.toString(paymentOptionId),
						rs.getString("ec"),
						rs.getString("nav"),
						rs.getString("iuv"),
						DebtPositionStatus.valueOf(rs.getString("pp_status")),
						PaymentOptionStatus.valueOf(rs.getString("po_status")),
						rs.getString("payment_plan_id"));

		return new CandidatePageRow(
				new CandidateCursor(
						insertedDate,
						paymentPositionId,
						paymentOptionId),
				candidate);
	}

	private record CandidatePageRow(
			CandidateCursor cursor,
			ReconciliationCandidate candidate) {}

	private record CandidateCursor(
			LocalDateTime insertedDate,
			long paymentPositionId,
			long paymentOptionId)
	implements Comparable<CandidateCursor> {

		@Override
		public int compareTo(CandidateCursor other) {
			int insertedDateComparison =
					insertedDate.compareTo(other.insertedDate);

			if (insertedDateComparison != 0) {
				return insertedDateComparison;
			}

			int paymentPositionComparison =
					Long.compare(
							paymentPositionId,
							other.paymentPositionId);

			if (paymentPositionComparison != 0) {
				return paymentPositionComparison;
			}

			return Long.compare(
					paymentOptionId,
					other.paymentOptionId);
		}
	}
}
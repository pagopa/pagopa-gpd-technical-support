package it.gov.pagopa.gpd.technicalsupport.service.reconciliation.pay;

import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.biz.BizPositiveEvent;
import it.gov.pagopa.gpd.technicalsupport.model.reconciliation.gpd.GpdPayPaymentOptionRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class GpdPayRequestMapper {

  public GpdPayPaymentOptionRequest toPayRequest(BizPositiveEvent event) {
    return new GpdPayPaymentOptionRequest(
        event.paymentDateTime(),
        defaultIfBlank(event.paymentMethod(), "other"),
        event.pspCode(),
        event.pspTaxCode(),
        defaultIfBlank(event.pspCompany(), "UNKNOWN"),
        event.receiptId(),
        normalizeFeeInCents(event.fee()));
  }

  private String normalizeFeeInCents(String fee) {
    String normalizedFee = defaultIfBlank(fee, "0").trim();

    try {
      if (normalizedFee.contains(".")) {
        BigDecimal feeInCurrency = new BigDecimal(normalizedFee);

        if (feeInCurrency.signum() < 0) {
          throw new IllegalArgumentException("Biz fee cannot be negative: " + fee);
        }

        long feeInCents =
            feeInCurrency
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();

        return Long.toString(feeInCents);
      }

      long feeInCents = Long.parseLong(normalizedFee);

      if (feeInCents < 0) {
        throw new IllegalArgumentException("Biz fee cannot be negative: " + fee);
      }

      return Long.toString(feeInCents);

    } catch (NumberFormatException | ArithmeticException exception) {
      throw new IllegalArgumentException(
          "Invalid Biz fee. Expected either integer cents or a decimal value "
              + "with at most two decimal digits: "
              + fee,
          exception);
    }
  }

  private String defaultIfBlank(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
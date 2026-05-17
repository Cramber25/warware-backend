package pl.cramber.assetstore.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

public record SaleDto(
        UUID orderId,
        String status,
        ZonedDateTime createdAt,
        String buyer,
        String assetTitle,
        Integer price,
        Double priceUsd,
        String paymentMethod,
        String promoCode
) {}
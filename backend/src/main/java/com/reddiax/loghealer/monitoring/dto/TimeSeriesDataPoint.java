package com.reddiax.loghealer.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesDataPoint {
    private Instant timestamp;
    private Double value;
}

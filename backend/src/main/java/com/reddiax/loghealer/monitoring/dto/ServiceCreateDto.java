package com.reddiax.loghealer.monitoring.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCreateDto {
    
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;
    
    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;
    
    @NotBlank(message = "URL is required")
    @Size(max = 500, message = "URL must be at most 500 characters")
    private String url;
    
    @NotBlank(message = "Health endpoint is required")
    @Size(max = 200, message = "Health endpoint must be at most 200 characters")
    private String healthEndpoint;
    
    @Min(value = 10, message = "Check interval must be at least 10 seconds")
    private Integer checkIntervalSeconds = 30;
    
    @Min(value = 1000, message = "Timeout must be at least 1000ms")
    private Integer timeoutMs = 5000;
}

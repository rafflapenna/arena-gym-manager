package com.gymmanager.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DashboardStatsResponseDTO {
    private BigDecimal monthlyRevenue;
    private long newMembersThisMonth;
}

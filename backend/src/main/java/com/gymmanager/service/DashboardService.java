package com.gymmanager.service;

import com.gymmanager.dto.DashboardStatsResponseDTO;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.PaymentRepository;
import com.gymmanager.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public DashboardStatsResponseDTO getStats() {
        Long gymId = currentUser.gymId();
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = YearMonth.from(today).atDay(1);

        BigDecimal monthlyRevenue = paymentRepository.sumAmountByGymIdAndPaymentDateBetween(gymId, firstOfMonth, today);
        long newMembersThisMonth = memberRepository.countByGymIdAndRegistrationDateBetween(gymId, firstOfMonth, today);

        return DashboardStatsResponseDTO.builder()
                .monthlyRevenue(monthlyRevenue)
                .newMembersThisMonth(newMembersThisMonth)
                .build();
    }
}

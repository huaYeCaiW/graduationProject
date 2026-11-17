package com.donghuan.service;

import com.donghuan.model.command.RejectCommand;
import com.donghuan.model.enums.Role;
import com.donghuan.model.security.LoginUser;
import com.donghuan.model.view.ServiceApplicationView;
import com.donghuan.store.ServiceApplicationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceApplicationServiceTest {

    @Mock
    private ServiceApplicationStore store;

    @InjectMocks
    private ServiceApplicationService service;

    private static final long APP_ID = 10L;

    private LoginUser user(Role role) {
        return new LoginUser(1L, role.name().toLowerCase(), role, 3L);
    }

    private ServiceApplicationView view(String status, RejectCommand command) {
        return new ServiceApplicationView(APP_ID, "SA1", 3L, "合作社",
                1L, "张三", "1号棚", "13800000000",
                List.of(), status, Instant.now(), command.reason());
    }

    @Test
    void approve_success_returnsUpdatedView(RejectCommand command) {
        // Arrange
        LoginUser admin = user(Role.ADMIN);
        when(store.approve(APP_ID, admin.getId())).thenReturn(1);
        when(store.findById(APP_ID)).thenReturn(Optional.of(view("APPROVED", command)));

        // Act
        ServiceApplicationView result = service.approve(admin, APP_ID);

        // Assert
        assertThat(result.status()).isEqualTo("APPROVED");
        verify(store).approve(APP_ID, admin.getId());
    }

    @Test
    void approve_farmer_isForbiddenAndNeverTouchesStore() {
        assertThatThrownBy(() -> service.approve(user(Role.FARMER), APP_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(store);
    }

    @Test
    void approve_notFound_returns404() {
        when(store.approve(APP_ID, 1L)).thenReturn(0);
        when(store.findById(APP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(user(Role.ADMIN), APP_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void approve_statusNotPending_returns409(RejectCommand command) {
        when(store.approve(APP_ID, 1L)).thenReturn(0);
        when(store.findById(APP_ID)).thenReturn(Optional.of(view("INSTALLED", command)));

        assertThatThrownBy(() -> service.approve(user(Role.ADMIN), APP_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void reject_trimsReasonAndPassesItToStore(RejectCommand command) {
        LoginUser admin = user(Role.ADMIN);
        when(store.reject(APP_ID, admin.getId(), "条件不符")).thenReturn(1);
        when(store.findById(APP_ID)).thenReturn(Optional.of(view("REJECTED", command)));

        service.reject(admin, APP_ID, new RejectCommand("  条件不符  "));

        verify(store).reject(APP_ID, admin.getId(), "条件不符");
    }

    @Test
    void reject_blankReason_returns400AndDoesNotWrite() {
        assertThatThrownBy(() -> service.reject(user(Role.ADMIN), APP_ID,
                new RejectCommand("   ")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(store, never()).reject(anyLong(), anyLong(), anyString());
    }

    @Test
    void confirm_statusNotApproved_returns409(RejectCommand command) {
        when(store.confirm(APP_ID, 1L)).thenReturn(0);
        when(store.findById(APP_ID)).thenReturn(Optional.of(view("PENDING", command)));

        assertThatThrownBy(() -> service.confirm(user(Role.OPS), APP_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
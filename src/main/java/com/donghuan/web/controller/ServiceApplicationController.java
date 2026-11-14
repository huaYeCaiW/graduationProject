package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.LoginUser;
import com.donghuan.model.ServiceApplicationCommand;
import com.donghuan.model.ServiceApplicationView;
import com.donghuan.service.ServiceApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-applications")
@RequiredArgsConstructor
public class ServiceApplicationController {

    private final ServiceApplicationService applicationService;

    @PostMapping
    public ResponseEntity<ServiceApplicationView> submit(
            @RequestBody ServiceApplicationCommand command,
            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.submit(user, command));
    }

    @GetMapping("/mine")
    public List<ServiceApplicationView> mine(
            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return applicationService.mine(user);
    }

    @GetMapping
    public List<ServiceApplicationView> list(
            @RequestParam(required = false) String status,
            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return applicationService.list(user, status);
    }

    @GetMapping("/{id}")
    public ServiceApplicationView get(@PathVariable long id,
                                      @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return applicationService.get(user, id);
    }

    @PostMapping("/{id}/approve")
    public ServiceApplicationView approve(@PathVariable long id,
                                          @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return applicationService.approve(user, id);
    }

    @PostMapping("/{id}/reject")
    public ServiceApplicationView reject(@PathVariable long id,
                                         @RequestBody RejectRequest request,
                                         @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return applicationService.reject(user, id, request == null ? null : request.reason());
    }

    public record RejectRequest(String reason) {
    }
}

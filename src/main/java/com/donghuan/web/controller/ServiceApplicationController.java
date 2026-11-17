package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.command.RejectCommand;
import com.donghuan.model.security.LoginUser;
import com.donghuan.model.command.ServiceApplicationCommand;
import com.donghuan.model.view.ServiceApplicationView;
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

    @PostMapping("/{id}/approve")
    public ServiceApplicationView approve(
            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user, @PathVariable long id){
        return applicationService.approve(user, id);
    }

    @PostMapping("/{id}/reject")
    public ServiceApplicationView reject(
            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user,
            @RequestBody RejectCommand command,
            @PathVariable long id
            ) {
        return applicationService.reject(user, id, command);
    }

    @PostMapping("/{id}/confirm")
    public ServiceApplicationView confirm(
            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user, @PathVariable long id
    ) {
        return applicationService.confirm(user, id);
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

}

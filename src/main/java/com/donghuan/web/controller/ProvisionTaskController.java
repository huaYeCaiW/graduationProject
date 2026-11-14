package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.LoginUser;
import com.donghuan.model.ProvisionCommand;
import com.donghuan.model.ProvisionTaskView;
import com.donghuan.service.ProvisionTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/provision-tasks")
@RequiredArgsConstructor
public class ProvisionTaskController {

    private final ProvisionTaskService provisionTaskService;

    @GetMapping
    public List<ProvisionTaskView> list(
            @RequestParam(required = false) String status,
            @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return provisionTaskService.list(user, status);
    }

    @GetMapping("/{id}")
    public ProvisionTaskView get(@PathVariable long id,
                                 @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return provisionTaskService.get(user, id);
    }

    @PostMapping("/{id}/provision")
    public ProvisionTaskView provision(@PathVariable long id,
                                       @RequestBody ProvisionCommand command,
                                       @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return provisionTaskService.provision(user, id, command);
    }
}

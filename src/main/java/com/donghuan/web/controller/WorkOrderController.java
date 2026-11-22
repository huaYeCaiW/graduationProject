package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.command.CancelCommand;
import com.donghuan.model.command.CompleteCommand;
import com.donghuan.model.command.WorkOrderCommand;
import com.donghuan.model.security.LoginUser;
import com.donghuan.model.view.WorkOrderView;
import com.donghuan.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {
    private final WorkOrderService workOrderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrderView submit(@RequestAttribute(AuthConstants.REQUEST_USER)LoginUser user, @RequestBody WorkOrderCommand command) {
        return workOrderService.submit(user, command);
    }

    @GetMapping("/mine")
    public List<WorkOrderView> mine(@RequestAttribute(AuthConstants.REQUEST_USER)LoginUser user) {
        return workOrderService.mine(user);
    }

    @GetMapping
    public List<WorkOrderView> list(@RequestAttribute(AuthConstants.REQUEST_USER)LoginUser user, @RequestParam(required = false) String status) {
        return workOrderService.list(user, status);
    }

    @GetMapping("/{id}")
    public WorkOrderView get(@RequestAttribute(AuthConstants.REQUEST_USER)LoginUser user, @PathVariable long id) {
        return workOrderService.get(user, id);
    }

    @PostMapping("/{id}/accept")
    public WorkOrderView accept(@RequestAttribute(AuthConstants.REQUEST_USER)LoginUser user, @PathVariable long id) {
        return workOrderService.accept(user, id);
    }

    @PostMapping("/{id}/complete")
    public WorkOrderView complete(@RequestAttribute(AuthConstants.REQUEST_USER)LoginUser user, @PathVariable long id, @RequestBody CompleteCommand command) {
        return workOrderService.complete(user, id, command);
    }

    @PostMapping("/{id}/confirm")
    public WorkOrderView confirm(@RequestAttribute(AuthConstants.REQUEST_USER)LoginUser user, @PathVariable long id) {
        return workOrderService.confirm(user, id);
    }

    @PostMapping("/{id}/cancel")
    public WorkOrderView cancel(
            @RequestAttribute(AuthConstants.REQUEST_USER)LoginUser user,
            @PathVariable long id,
            @RequestBody(required = false) CancelCommand command) {
        return workOrderService.cancel(user, id, command);
    }
}

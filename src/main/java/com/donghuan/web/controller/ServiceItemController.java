package com.donghuan.web.controller;

import com.donghuan.common.AuthConstants;
import com.donghuan.model.LoginUser;
import com.donghuan.model.ServiceItemCommand;
import com.donghuan.model.ServiceItemView;
import com.donghuan.service.ServiceItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-items")
@RequiredArgsConstructor
public class ServiceItemController {

    private final ServiceItemService serviceItemService;

    @GetMapping
    public List<ServiceItemView> list(@RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return serviceItemService.list(user);
    }

    @GetMapping("/{code}")
    public ServiceItemView get(@PathVariable String code,
                               @RequestAttribute(AuthConstants.REQUEST_USER) LoginUser user) {
        return serviceItemService.get(user, code);
    }

    @PostMapping
    public ResponseEntity<ServiceItemView> create(@RequestBody ServiceItemCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceItemService.create(command));
    }

    @PutMapping("/{code}")
    public ServiceItemView update(@PathVariable String code,
                                  @RequestBody ServiceItemCommand command) {
        return serviceItemService.update(code, command);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> disable(@PathVariable String code) {
        serviceItemService.disable(code);
        return ResponseEntity.noContent().build();
    }
}

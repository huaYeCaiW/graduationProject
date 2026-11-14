package com.donghuan.store;

import com.donghuan.model.ServiceItemCommand;
import com.donghuan.model.ServiceItemView;

import java.util.List;
import java.util.Optional;

public interface ServiceItemStore {

    List<ServiceItemView> findAll(boolean includeDisabled);

    Optional<ServiceItemView> findByCode(String code);

    long insert(ServiceItemCommand command);

    void update(long id, ServiceItemCommand command);

    void disable(long id);
}

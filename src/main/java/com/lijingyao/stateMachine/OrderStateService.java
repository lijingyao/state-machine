/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lijingyao.stateMachine;

import com.lijingyao.stateMachine.Order;
import com.lijingyao.stateMachine.OrderRepo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

@Component
public class OrderStateService {

    private PersistStateMachineHandler handler;


    public OrderStateService(PersistStateMachineHandler handler) {
        this.handler = handler;
    }

    @Autowired
    private OrderRepo repo;


    public String listDbEntries() {
        List<Order> orders = repo.findAll();
        StringJoiner sj = new StringJoiner(",");
        for (Order order : orders) {
            sj.add(order.toString());
        }
        return sj.toString();
    }


    public boolean change(int order, OrderStatusChangeEvent event) {
        Order o = repo.findByOrderId(order);
        return handler.handleEventWithState(MessageBuilder.withPayload(event).setHeader("order", order).build(), o.getStatus());
    }




}

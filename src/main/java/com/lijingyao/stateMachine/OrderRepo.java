package com.lijingyao.stateMachine;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Created by lijingyao on 2017/11/9 16:45.
 */
public interface OrderRepo extends JpaRepository<Order, Integer> {


    Order findByOrderId(Integer order);
}

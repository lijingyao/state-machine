package com.lijingyao.stateMachine;

/**
 * Created by lijingyao on 2017/11/24 11:43.
 */
public enum OrderStatusChangeEvent {

    // 支付，发货，确认收货,用户退货（款）
    PAYED, DELIVERY, RECEIVED,REFUND
}

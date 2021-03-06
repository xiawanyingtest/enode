package com.enodeframework.samples.domain.bank.deposittransaction;


import com.enodeframework.eventing.DomainEvent;

/// <summary>存款交易预存款已确认
/// </summary>
public class DepositTransactionPreparationCompletedEvent extends DomainEvent<String> {
    public String AccountId;

    public DepositTransactionPreparationCompletedEvent() {
    }

    public DepositTransactionPreparationCompletedEvent(String accountId) {
        AccountId = accountId;
    }
}

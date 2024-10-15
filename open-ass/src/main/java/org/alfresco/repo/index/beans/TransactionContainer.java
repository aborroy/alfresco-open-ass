package org.alfresco.repo.index.beans;

import java.util.List;

/**
 * Represents a container for a list of transactions in the Alfresco repository.
 */
public class TransactionContainer {

    private List<Transaction> transactions;
    private long maxTxnCommitTime;
    private long maxTxnId;

    // Getters and Setters
    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    public long getMaxTxnCommitTime() {
        return maxTxnCommitTime;
    }

    public void setMaxTxnCommitTime(long maxTxnCommitTime) {
        this.maxTxnCommitTime = maxTxnCommitTime;
    }

    public long getMaxTxnId() {
        return maxTxnId;
    }

    public void setMaxTxnId(long maxTxnId) {
        this.maxTxnId = maxTxnId;
    }

    @Override
    public String toString() {
        return "TransactionResponse{" +
                "transactions=" + transactions +
                ", maxTxnCommitTime=" + maxTxnCommitTime +
                ", maxTxnId=" + maxTxnId +
                '}';
    }
}

package org.alfresco.repo.index.beans;

/**
 * Represents a transaction in the Alfresco repository.
 */
public class Transaction {
    private long id; // Unique identifier for the transaction
    private long commitTimeMs; // Commit time of the transaction in milliseconds
    private int updates; // Number of updates in the transaction
    private int deletes; // Number of deletes in the transaction

    /**
     * Constructor for Jackson
     */
    public Transaction() {}

    /**
     * Retrieves the unique identifier of the transaction.
     *
     * @return The ID of the transaction.
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the transaction.
     *
     * @param id The ID of the transaction.
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Retrieves the commit time of the transaction in milliseconds.
     *
     * @return The commit time of the transaction.
     */
    public long getCommitTimeMs() {
        return commitTimeMs;
    }

    /**
     * Sets the commit time of the transaction in milliseconds.
     *
     * @param commitTimeMs The commit time of the transaction.
     */
    public void setCommitTimeMs(long commitTimeMs) {
        this.commitTimeMs = commitTimeMs;
    }

    /**
     * Retrieves the number of updates in the transaction.
     *
     * @return The number of updates.
     */
    public int getUpdates() {
        return updates;
    }

    /**
     * Sets the number of updates in the transaction.
     *
     * @param updates The number of updates.
     */
    public void setUpdates(int updates) {
        this.updates = updates;
    }

    /**
     * Retrieves the number of deletes in the transaction.
     *
     * @return The number of deletes.
     */
    public int getDeletes() {
        return deletes;
    }

    /**
     * Sets the number of deletes in the transaction.
     *
     * @param deletes The number of deletes.
     */
    public void setDeletes(int deletes) {
        this.deletes = deletes;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", commitTimeMs=" + commitTimeMs +
                ", updates=" + updates +
                ", deletes=" + deletes +
                '}';
    }

}
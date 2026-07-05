package ai.velr;

/**
 * Callback run inside a Velr transaction.
 *
 * @param <T> callback result type
 */
@FunctionalInterface
public interface TransactionCallback<T> {
    /**
     * Execute work using an open transaction.
     *
     * @param tx active transaction
     * @return callback result
     * @throws Exception any error to propagate and roll back the transaction
     */
    T run(VelrTx tx) throws Exception;
}

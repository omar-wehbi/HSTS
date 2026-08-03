package server.db;

import org.hibernate.Transaction;

/** Rolls back without letting the rollback itself throw. */
final class Transactions {
    private Transactions() { }

    static void rollbackQuietly(Transaction tx) {
        if (tx == null) return;
        try {
            tx.rollback();
        } catch (RuntimeException alreadyFinished) {
            // the failed commit already ended it — nothing left to undo
            System.err.println("[Transactions] rollback after failed commit was a no-op: "
                    + alreadyFinished.getMessage());
        }
    }
}

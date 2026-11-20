package org.example.ui.components;

import org.example.Service.ServiceException;

import javax.swing.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs a service call off the Swing thread so the window never freezes while
 * waiting for the database, then delivers the result back on the Swing thread.
 */
public final class AsyncTask {

    @FunctionalInterface
    public interface Work<T> {
        T run() throws Exception;
    }

    private AsyncTask() {
    }

    public static <T> SwingWorker<T, Void> run(Work<T> work,
                                               Consumer<T> onSuccess,
                                               Consumer<ServiceException> onError) {
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.run();
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    onError.accept(toServiceException(e.getCause()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        worker.execute();
        return worker;
    }

    static ServiceException toServiceException(Throwable t) {
        if (t instanceof ServiceException se) {
            return se;
        }
        t.printStackTrace();
        return new ServiceException(ServiceException.Kind.INTERNAL,
                "Something went wrong. Please try again.", t);
    }
}

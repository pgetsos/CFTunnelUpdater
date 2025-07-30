package gr.pgetsos.cftunnelupdater;

import java.util.Map;

public interface WorkerApiCallbacks {

    interface GenericWorkerApiCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    interface WorkerGetNameApiCallback {
        void onNameRetrieved(String name);
        void onError(String error);
    }

    interface WorkerGetAllNamesApiCallback {
        void onAllNamesRetrieved(Map<String, String> names);
        void onError(String error);
    }
}

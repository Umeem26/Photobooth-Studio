package service;

/**
 * Callback progres untuk tugas background PhotoboothService.
 * Dipanggil dari thread worker, bukan thread GUI.
 */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (percent, message) -> { };

    /**
     * @param percent 0..100
     * @param message keterangan langkah yang sedang berjalan
     */
    void onProgress(int percent, String message);

    static ProgressListener orNone(ProgressListener listener) {
        return listener == null ? NONE : listener;
    }
}

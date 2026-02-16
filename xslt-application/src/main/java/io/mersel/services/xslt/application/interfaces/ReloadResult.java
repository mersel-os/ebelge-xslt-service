package io.mersel.services.xslt.application.interfaces;

import java.util.List;

/**
 * Tek bir {@link Reloadable} bileşenin yeniden yükleme sonucu.
 */
public record ReloadResult(
        String componentName,
        Status status,
        int loadedCount,
        long durationMs,
        List<String> errors
) {
    public enum Status { OK, PARTIAL, FAILED }

    public static ReloadResult success(String name, int count, long durationMs) {
        return new ReloadResult(name, Status.OK, count, durationMs, List.of());
    }

    public static ReloadResult partial(String name, int count, long durationMs, List<String> errors) {
        return new ReloadResult(name, Status.PARTIAL, count, durationMs, errors);
    }

    public static ReloadResult failed(String name, long durationMs, String error) {
        return new ReloadResult(name, Status.FAILED, 0, durationMs, List.of(error));
    }
}

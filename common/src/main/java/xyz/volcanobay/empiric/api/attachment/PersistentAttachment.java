package xyz.volcanobay.empiric.api.attachment;

import xyz.volcanobay.empiric.core.attachment.Attachment;

public abstract class PersistentAttachment implements WorldAttachment {

    private boolean active;
    private byte[] pending;

    public boolean isActive() {
        return active;
    }

    public void active(boolean active) {
        this.active = active;
    }

    public byte[] pending() {
        return pending;
    }

    public void pending(byte[] pending) {
        this.pending = pending;
    }

    public byte[] takePending() {
        byte[] bytes = pending;
        pending = null;
        return bytes;
    }

    public static boolean isActive(Attachment attachment) {
        return !(attachment instanceof PersistentAttachment persistent) || persistent.active;
    }
}

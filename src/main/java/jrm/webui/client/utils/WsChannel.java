package jrm.webui.client.utils;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Small JSNI wrapper around the browser native {@code WebSocket} (no third-party dep, GWT 2.13 compatible).
 * <p>
 * Follows the existing JSNI style in {@link EnhJSO}: native blocks reference {@code $wnd} and opaque handles are
 * carried as {@link JavaScriptObject}. All lifecycle callbacks funnel into the given {@link Handler}.
 * </p>
 */
public final class WsChannel {

    /** Lifecycle callbacks for a channel attempt. */
    public interface Handler {
        /** Called when the socket opens. */
        void onOpen();

        /** Called for each inbound text message. */
        void onMessage(String data);

        /** Called when the socket closes or fails before opening. */
        void onClose();
    }

    private WsChannel() {
    }

    /**
     * Opens a WebSocket to the given URL.
     *
     * @param url the ws(s) URL
     * @param handler the lifecycle handler
     * @return an opaque socket handle, or {@code null} when WebSocket is unsupported
     */
    public static native JavaScriptObject open(String url, Handler handler) /*-{
        if (typeof $wnd.WebSocket === 'undefined')
            return null;
        var ws = new $wnd.WebSocket(url);
        ws.onopen = function() {
            handler.@jrm.webui.client.utils.WsChannel.Handler::onOpen()();
        };
        ws.onmessage = function(evt) {
            handler.@jrm.webui.client.utils.WsChannel.Handler::onMessage(Ljava/lang/String;)(evt.data);
        };
        ws.onclose = function() {
            handler.@jrm.webui.client.utils.WsChannel.Handler::onClose()();
        };
        ws.onerror = function() {
            // Error alone carries no detail; onclose follows and drives fallback.
        };
        return ws;
    }-*/;

    /**
     * Sends a text message when the socket is open.
     *
     * @param ws the opaque handle from {@link #open}
     * @param msg the message
     * @return {@code true} when sent
     */
    public static native boolean send(JavaScriptObject ws, String msg) /*-{
        if (ws && ws.readyState === 1) {
            ws.send(msg);
            return true;
        }
        return false;
    }-*/;

    /**
     * Returns whether the socket is currently open.
     *
     * @param ws the opaque handle from {@link #open}
     * @return {@code true} when {@code readyState == OPEN}
     */
    public static native boolean isOpen(JavaScriptObject ws) /*-{
        return !!(ws && ws.readyState === 1);
    }-*/;

    /**
     * Closes the socket.
     *
     * @param ws the opaque handle from {@link #open}
     */
    public static native void close(JavaScriptObject ws) /*-{
        if (ws)
            ws.close(1000, "client closing");
    }-*/;
}

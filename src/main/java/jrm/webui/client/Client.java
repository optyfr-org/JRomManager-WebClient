package jrm.webui.client;

import java.util.HashSet;
import java.util.Set;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.user.client.Timer;
import com.smartgwt.client.rpc.RPCManager;
import com.smartgwt.client.rpc.RPCRequest;
import com.smartgwt.client.rpc.RPCResponse;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.util.Page;
import com.smartgwt.client.util.SC;
import com.smartgwt.client.widgets.Label;
import com.smartgwt.client.widgets.Window;

import jrm.webui.client.protocol.A_;
import jrm.webui.client.protocol.A_CatVer;
import jrm.webui.client.protocol.A_Compressor;
import jrm.webui.client.protocol.A_Dat2Dir;
import jrm.webui.client.protocol.A_Global;
import jrm.webui.client.protocol.A_NPlayers;
import jrm.webui.client.protocol.A_Profile;
import jrm.webui.client.protocol.A_Progress;
import jrm.webui.client.protocol.A_Report;
import jrm.webui.client.protocol.A_ReportLite;
import jrm.webui.client.protocol.A_Session;
import jrm.webui.client.protocol.A_TrntChk;
import jrm.webui.client.ui.MainWindow;
import jrm.webui.client.utils.WsChannel;

/**
 * GWT {@link EntryPoint} for the JRomManager web client.
 * <p>
 * Bootstraps the application by establishing a server session, creating the
 * main window, and running the long-polling request (LPR) loop that dispatches
 * server-pushed commands to the appropriate protocol handlers.
 *
 * @since 2.5
 */
public class Client implements EntryPoint {
    /** The current server session descriptor. */
    private static A_Session session = null;

    /** The singleton main window instance. */
    private static MainWindow mainWindow = null;
    /** Set of child windows (reports, viewers, etc.) opened from the main window. */
    private static Set<Window> childWindows = new HashSet<>();

    /** Timer that drives the long-polling request loop. */
    private static Timer lprTimer;

    /** Active WebSocket handle when in WS mode; {@code null} otherwise. */
    private static JavaScriptObject wsHandle = null;

    /** Heartbeat timer sending {@code Global.ping} while WS is open (~120s). */
    private static Timer wsPingTimer = null;

    /** Probe timer bounding the WS open wait (~3s). */
    private static Timer wsProbeTimer = null;

    /** {@code true} once a WS session was established (drop -&gt; re-init fallback, not silent re-probe). */
    private static boolean wsEstablished = false;

    /** Guard against reconnect storms: a single fallback transition per drop. */
    private static boolean wsFallingBack = false;

    /** Creates a new client entry point. */
    public Client() {
        super();
    }

    /**
     * Incremental command that parses a server-pushed JSON message and dispatches
     * the resulting protocol action to the appropriate handler.
     */
    private class EatSleepRaveRepeat implements RepeatingCommand {
        /** The raw JSON message received from the server. */
        final String msg;

        /**
         * Constructs a new dispatcher for the given raw message.
         *
         * @param msg the raw JSON message
         */
        private EatSleepRaveRepeat(String msg) {
            this.msg = msg;
        }

        @Override
        public boolean execute() {
            execute(new A_(JsonUtils.safeEval(msg)));
            return false;
        }

        private void execute(A_ a) {
            if (a.getCmd().startsWith("Global.")) {
                executeGlobal(a);
                return;
            }
            switch (a.getCmd()) {
                case "Progress" -> mainWindow.update(new A_Progress(a));
                case "Progress.close" -> mainWindow.update(new A_Progress.Close(a));
                case "Progress.canCancel" -> mainWindow.update(new A_Progress.CanCancel(a));
                case "Progress.setInfos" -> mainWindow.update(new A_Progress.SetInfos(a));
                case "Progress.extendInfos" -> mainWindow.update(new A_Progress.ExtendInfos(a));
                case "Progress.clearInfos" -> mainWindow.update(new A_Progress.ClearInfos(a));
                case "Progress.setFullProgress" -> mainWindow.update(new A_Progress.SetFullProgress(a));
                case "Profile.loaded" -> mainWindow.update(new A_Profile.Loaded(a));
                case "Profile.scanned" -> mainWindow.update(new A_Profile.Scanned(a));
                case "Profile.fixed" -> mainWindow.update(new A_Profile.Fixed(a));
                case "Profile.imported" -> mainWindow.update(new A_Profile.Imported(a));
                case "CatVer.loaded" -> mainWindow.update(new A_CatVer.Loaded(a));
                case "NPlayers.loaded" -> mainWindow.update(new A_NPlayers.Loaded(a));
                case "Report.applyFilters" -> mainWindow.update(new A_Report.ApplyFilter(a));
                case "ReportLite.applyFilters" -> mainWindow.update(new A_ReportLite.ApplyFilter(a));
                case "Report.fixDatCreated" -> mainWindow.update(new A_Report.FixDatCreated(a));
                case "Dat2Dir.clearResults" -> mainWindow.update(new A_Dat2Dir.ClearResults(a));
                case "Dat2Dir.updateResult" -> mainWindow.update(new A_Dat2Dir.UpdateResult(a));
                case "Dat2Dir.end" -> mainWindow.update(new A_Dat2Dir.End(a));
                case "Dat2Dir.showSettings" -> mainWindow.update(new A_Dat2Dir.ShowSettings(a));
                case "TrntChk.clearResults" -> mainWindow.update(new A_TrntChk.ClearResults(a));
                case "TrntChk.updateResult" -> mainWindow.update(new A_TrntChk.UpdateResult(a));
                case "TrntChk.end" -> mainWindow.update(new A_TrntChk.End(a));
                case "Compressor.clearResults" -> mainWindow.update(new A_Compressor.ClearResults(a));
                case "Compressor.updateResult" -> mainWindow.update(new A_Compressor.UpdateResult(a));
                case "Compressor.updateFile" -> mainWindow.update(new A_Compressor.UpdateFile(a));
                case "Compressor.end" -> mainWindow.update(new A_Compressor.End(a));
                default -> { /* NOSONAR */ }
            }
        }

        private void executeGlobal(A_ a) {
            switch (a.getCmd()) {
                case "Global.setMemory" -> mainWindow.update(new A_Global.SetMemory(a));
                case "Global.updateProperty" -> new A_Global.UpdateProperty(a).getProperties().forEach((k, v) -> {
                    if (v instanceof String s)
                        session.setSetting(k, s);
                    else if (v instanceof Boolean b)
                        session.setSetting(k, b);
                    else if (v instanceof Integer i)
                        session.setSetting(k, i);
                });
                case "Global.warn" -> SC.warn(new A_Global.Warn(a).getMsg());
                case "Global.multiCMD" -> {
                    for (final var sa : new A_Global.MultiCMD(a).getSubCMDs())
                        execute(sa);
                }
                default -> { /* NOSONAR */ }
            }
        }

    }

    /**
     * Schedules incremental parsing and dispatch of a raw server message.
     *
     * @param msg the raw JSON message
     */
    private void processCmd(String msg) {
        if (msg == null || msg.isBlank())
            return;
        try {
            Scheduler.get().scheduleIncremental(new EatSleepRaveRepeat(msg));
        } catch (Exception e) {
            // Do nothing
        }
    }

    /**
     * Performs a long-polling request to the server.
     *
     * @param init {@code true} for the initial session-establishing request
     */
    private void lpr(boolean init) {
        updateMainWindow();
        RPCRequest request = new RPCRequest();
        request.setActionURL(init ? "/actions/init" : "/actions/lpr");
        request.setContentType("application/json");
        request.setUseSimpleHttp(true); // required because otherwise we'd be talking to a SmartClient RPC server, which is not the case here
        request.setHttpMethod("GET"); // keep the request as simple as possible (POST is more complex for HTTP and requires 2 round-trips)
        request.setWillHandleError(true); // we handle error statuses ourselves
        RPCManager.sendRequest(request, (response, rawData, req) -> {
            if (response.getHttpResponseCode() == 200) {
                processCmd(rawData.toString());
                lprTimer.schedule(125);
            } else if (response.getHttpResponseCode() == 401) // Session lost => reload page
                com.google.gwt.user.client.Window.Location.reload();
            else
                lprTimer.schedule(500);
        });
    }

    /**
     * Sends a command message to the server: over the WS socket when open, else via POST to {@code /actions/cmd}
     * (covers transient reconnect; the server routes pushes to the live WS manager when open).
     *
     * @param msg the command payload
     */
    public static void sendMsg(String msg) {
        if (wsHandle != null && WsChannel.send(wsHandle, msg))
            return;
        RPCRequest request = new RPCRequest();
        request.setActionURL("/actions/cmd");
        SC.logWarn(request.getActionURL());
        request.setContentType("application/json");
        request.setUseSimpleHttp(true); // required because otherwise we'd be talking to a SmartClient RPC server, which is not the case here
        request.setHttpMethod("POST"); // keep the request as simple as possible (POST is more complex for HTTP and requires 2 round-trips)
        request.setData(msg);
        RPCManager.sendRequest(request);
    }

    /**
     * Starts the classic LPR loop ({@code /actions/init} once, then {@code /actions/lpr}).
     */
    private void startLpr() {
        setLprTimer(new Timer() {
            boolean init = true;

            @Override
            public void run() {
                lpr(init);
                init = false;
            }
        }).schedule(1);
    }

    /**
     * Probes the optional {@code /ws} actions channel. On open, enters WS mode (init payloads arrive from the server
     * {@code onOpen}); on error/close-before-open/timeout, falls back to {@link #startLpr()}. No parallel LPR runs
     * while probing: the probe outcome decides which channel starts.
     */
    private void probeWs() {
        final String scheme = "https:".equals(com.google.gwt.user.client.Window.Location.getProtocol()) ? "wss://" : "ws://";
        final String url = scheme + com.google.gwt.user.client.Window.Location.getHost() + "/ws";
        final var instance = this;
        wsEstablished = false;
        wsFallingBack = false;
        wsProbeTimer = new Timer() {
            @Override
            public void run() {
                // Probe timed out (~3s): fall back to LPR.
                fallbackToLpr(false);
            }
        };
        wsProbeTimer.schedule(3000);
        final JavaScriptObject[] holder = new JavaScriptObject[1];
        holder[0] = WsChannel.open(url, new WsChannel.Handler() {
            @Override
            public void onOpen() {
                if (wsProbeTimer != null) {
                    wsProbeTimer.cancel();
                    wsProbeTimer = null;
                }
                if (wsFallingBack)
                    return;
                wsHandle = holder[0];
                wsEstablished = true;
                updateMainWindow();
                startWsPing();
            }

            @Override
            public void onMessage(String data) {
                instance.processCmd(data);
            }

            @Override
            public void onClose() {
                // Close-before-open (or probe failure) -> plain LPR fallback; established drop -> full re-init.
                fallbackToLpr(wsEstablished);
            }
        });
        if (holder[0] == null)
            fallbackToLpr(false);
    }

    /**
     * Falls back to LPR exactly once per drop. A fresh drop performs a full re-init ({@code /actions/init} once to
     * re-seed profile/catver/nplayers + {@code Progress.reload}, then {@code /actions/lpr}) because pushes sent
     * during WS mode were socket-only and never queued.
     *
     * @param reinit {@code true} when dropping an established WS session
     */
    private void fallbackToLpr(boolean reinit) {
        if (wsFallingBack)
            return;
        wsFallingBack = true;
        if (wsProbeTimer != null) {
            wsProbeTimer.cancel();
            wsProbeTimer = null;
        }
        stopWsPing();
        if (wsHandle != null) {
            WsChannel.close(wsHandle);
            wsHandle = null;
        }
        wsEstablished = false;
        // Both cases start with init=true (fresh re-seed); plain LPR fallback and post-drop re-init coincide.
        startLpr();
        wsFallingBack = false;
    }

    /**
     * Starts the WS heartbeat: {@code {"cmd":"Global.ping"}} every ~120s (server replies nothing; keeps the
     * HttpSession alive since WS frames do not touch {@code lastAccessedTime}).
     */
    private static void startWsPing() {
        stopWsPing();
        wsPingTimer = new Timer() {
            @Override
            public void run() {
                if (wsHandle != null && WsChannel.isOpen(wsHandle))
                    WsChannel.send(wsHandle, "{\"cmd\":\"Global.ping\"}");
            }
        };
        wsPingTimer.scheduleRepeating(120000);
    }

    /** Stops the WS heartbeat timer. */
    private static void stopWsPing() {
        if (wsPingTimer != null) {
            wsPingTimer.cancel();
            wsPingTimer = null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * Initializes the main application window, establishes the server session,
     * and sets up the user interface components.
     */
    @Override
    public void onModuleLoad() {

        final var title = new Label();
        title.setWidth100();
        title.setAlign(Alignment.CENTER);
        title.setContents("<span style='font:bold 24px arial'>JRomManager</span>");
        title.draw();
        Page.setTitle("JRomManager");

        final var sessionReq = new RPCRequest();
        sessionReq.setActionURL("/session");
        RPCManager.sendRequest(
                sessionReq,
                (RPCResponse response, Object rawData, RPCRequest request) -> {
                    if (response.getHttpResponseCode() == 200) {
                        setSession(JsonUtils.safeEval(rawData.toString()));
                        // Wait for probe outcome before starting any channel: WS hint on -> probe /ws first
                        // (no parallel LPR), else pure LPR as before.
                        if (getSession() != null && getSession().getWebsocket())
                            probeWs();
                        else
                            startLpr();
                    }
                });
    }

    /**
     * Stores the current server session descriptor.
     *
     * @param session the session descriptor received from the server
     */
    private static synchronized void setSession(A_Session session) {
        Client.session = session;
    }

    /**
     * Returns the current server session descriptor.
     *
     * @return the current session, or {@code null} if not yet established
     */
    public static synchronized A_Session getSession() {
        return session;
    }

    /**
     * Lazily creates the singleton main window if it does not yet exist.
     */
    private static synchronized void updateMainWindow() {
        if (Client.mainWindow == null)
            Client.mainWindow = new MainWindow();
    }

    /**
     * Returns the singleton main window instance.
     *
     * @return the main window, or {@code null} if it has not been created yet
     */
    public static synchronized MainWindow getMainWindow() {
        return mainWindow;
    }

    /**
     * Returns the set of child windows opened from the main window.
     *
     * @return the modifiable set of child windows
     */
    public static Set<Window> getChildWindows() {
        return childWindows;
    }

    /**
     * Stores the long-polling request timer and returns it.
     *
     * @param lprTimer the timer driving the long-polling loop
     * @return the stored timer
     */
    private static synchronized Timer setLprTimer(Timer lprTimer) {
        Client.lprTimer = lprTimer;
        return Client.lprTimer;
    }
}

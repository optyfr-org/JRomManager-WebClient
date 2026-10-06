package jrm.webui.client.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Window.Location;
import com.smartgwt.client.data.Record;
import com.smartgwt.client.data.RecordList;
import com.smartgwt.client.rpc.RPCManager;
import com.smartgwt.client.rpc.RPCRequest;
import com.smartgwt.client.types.Overflow;
import com.smartgwt.client.types.TabBarControls;
import com.smartgwt.client.types.VisibilityMode;
import com.smartgwt.client.util.SC;
import com.smartgwt.client.widgets.Window;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.layout.SectionStack;
import com.smartgwt.client.widgets.layout.SectionStackSection;
import com.smartgwt.client.widgets.tab.Tab;
import com.smartgwt.client.widgets.tab.TabSet;

import jrm.webui.client.Client;
import jrm.webui.client.protocol.A_CatVer;
import jrm.webui.client.protocol.A_Compressor;
import jrm.webui.client.protocol.A_Dat2Dir;
import jrm.webui.client.protocol.A_Global;
import jrm.webui.client.protocol.A_NPlayers;
import jrm.webui.client.protocol.A_Profile;
import jrm.webui.client.protocol.A_Progress;
import jrm.webui.client.protocol.A_Report;
import jrm.webui.client.protocol.A_ReportLite;
import jrm.webui.client.protocol.A_TrntChk;
import jrm.webui.client.utils.EnhJSO;

/**
 * Main application window of the JRomManager web client.
 * <p>
 * Hosts the central {@link TabSet} with one tab per feature area (profiles,
 * scanner, Dir2Dat, batch tools, settings) and dispatches server-side events
 * received through the protocol {@code A_*} classes to the matching child panel
 * via the overloaded {@code update(...)} methods.
 *
 * @since 2.5
 */
public class MainWindow extends Window //NOSONAR
{
    /** Name of the scanner tab, also used as its tab identifier. */
    private static final String SCANNER = "scanner";

    /** The central tab set hosting the feature tabs. */
    TabSet mainPane;
    /** The profiles management panel. */
    ProfilePanel profilePanel;
    /** The scanner panel. */
    ScannerPanel scannerPanel;
    /** The batch DirUpd8r panel. */
    BatchDirUpd8rPanel batchDirUpd8rPanel;
    /** The batch torrent checker panel. */
    BatchTrrntChkPanel batchTrrntChkPanel;
    /** The batch compressor panel. */
    BatchCompressorPanel batchCompressorPanel;
    /** The Dir2Dat panel. */
    Dir2DatPanel dir2datPanel;
    /** The general settings panel. */
    SettingsGenPanel settingsGenPanel;
    /** The compressor settings panel. */
    SettingsCompressorPanel settingsCompressorPanel;
    /** The debug settings panel. */
    SettingsDebugPanel settingsDebugPanel;
    /** The current progress dialog, or {@code null} if none is open. */
    private Progress progress = null;

    /**
     * Constructs the main window: configures default list grid properties, the
     * window chrome and close (logout) handler, then builds and adds all feature
     * tabs to the central tab set.
     */
    public MainWindow() {
        super();
        final var deflist = new ListGrid();
        deflist.setEmptyMessage(Client.getSession().getMsg("MainWindow.NoItemsToShow")); //$NON-NLS-1$
        deflist.setLoadingDataMessage(Client.getSession().getMsg("MainWindow.LoadingData")); //$NON-NLS-1$
        ListGrid.setDefaultProperties(deflist);
        setTitle(Client.getSession().getMsg("MainWindow.Title")); //$NON-NLS-1$
        setWidth(1000);
        setHeight(600);
        setAnimateMinimize(true);
        setCanDragReposition(true);
        setCanDragResize(true);
        setShowHeaderIcon(true);
        setShowMaximizeButton(true);
        Map<String, Object> headerIconDefaults = new HashMap<>();
        headerIconDefaults.put("width", 16); //$NON-NLS-1$
        headerIconDefaults.put("height", 16); //$NON-NLS-1$
        headerIconDefaults.put("src", "rom.png"); //$NON-NLS-1$ //$NON-NLS-2$
        setHeaderIconDefaults(headerIconDefaults);
        setShowHeaderIcon(true);
        addCloseClickHandler(event -> {
            RPCRequest request = new RPCRequest();
            String logout = Location.getProtocol() + "//logout:logout@" + Location.getHost() + Location.getPath();
            request.setActionURL(logout);
            request.setSendNoQueue(true);
            request.setHttpHeaders(Collections.singletonMap("Authorization", "Basic AAAAAAAAAAAAAAAAAAA=")); //NOSONAR
            request.setWillHandleError(true);
            RPCManager.sendRequest(request, (response, rawData, request1) -> {
                Cookies.removeCookie("JSESSIONID"); //$NON-NLS-1$
                String logout2 = Location.getHref();
                Location.replace(logout);
                Location.replace(logout2);
            });
            close();
        });
        mainPane = new TabSet();
        mainPane.setPaneMargin(0);
        mainPane.setTabBarControls(TabBarControls.TAB_SCROLLER, TabBarControls.TAB_PICKER);
        mainPane.addTab(getProfileTab());
        mainPane.addTab(getScannerTab());
        mainPane.addTab(getDir2DatTab());
        mainPane.addTab(getBatchTab());
        mainPane.addTab(getSettingsTab());
        addItem(mainPane);
        centerInPage();
        show();
    }

    /**
     * Reacts to a profile loaded event: updates the profile info label, toggles the
     * scan/fix buttons, enables or disables the scanner tab, and refreshes the
     * scanner settings and filters from the loaded profile.
     *
     * @param params
     *            the profile loaded event parameters
     */
    public void update(A_Profile.Loaded params) {
        scannerPanel.lblProfileinfo.setContents(params.getSuccess() ? params.getName() : null);
        scannerPanel.btnScan.setDisabled(!params.getSuccess());
        scannerPanel.btnFix.setDisabled(true);
        if (params.getSuccess()) {
            mainPane.enableTab(SCANNER);
            mainPane.selectTab(SCANNER);
            EnhJSO settings = params.getSettings();
            scannerPanel.scannerDirPanel.initPropertyItemValues(settings);
            scannerPanel.scannerSettingsPanel.initPropertyItemValues(settings);
            scannerPanel.scannerFiltersPanel.systems.setData(Record.convertToRecordArray(params.getSystems()));
            scannerPanel.scannerFiltersPanel.sources.setData(Record.convertToRecordArray(params.getSources()));
            JsArrayString jsarrstr = params.getYears();
            ArrayList<String> arrlststr = new ArrayList<>();
            for (int i = 0; i < jsarrstr.length(); i++)
                arrlststr.add(jsarrstr.get(i));
            @SuppressWarnings("CollectionsToArray")
            String[] arrstr = arrlststr.toArray(new String[0]); /* NOSONAR */
            scannerPanel.scannerFiltersPanel.filterForm.getItem("cbYearMin").setValueMap(arrstr); //$NON-NLS-1$
            scannerPanel.scannerFiltersPanel.filterForm.getItem("cbYearMin").setDefaultValue(arrstr[0]); //$NON-NLS-1$
            scannerPanel.scannerFiltersPanel.filterForm.getItem("cbYearMax").setValueMap(arrstr); //$NON-NLS-1$
            scannerPanel.scannerFiltersPanel.filterForm.getItem("cbYearMax").setDefaultValue(arrstr[arrstr.length - 1]); //$NON-NLS-1$
            scannerPanel.scannerFiltersPanel.filterForm.initPropertyItemValues(settings);
        } else {
            if (mainPane.getSelectedTabNumber() == 1)
                mainPane.selectTab(0);
            mainPane.disableTab(SCANNER);
        }
        profilePanel.refreshListGrid();
    }

    /**
     * Reacts to a progress start event by opening a new progress dialog.
     * <p>
     * Reuses the existing dialog when one is already open (e.g. the server re-sends
     * the open event after a transport reconnect with a live worker); creating a second
     * overlapping window there made the progress display flash.
     *
     * @param params
     *            the progress event parameters (unused)
     */
    public void update(A_Progress params) //NOSONAR
    {
        if (progress != null && !progress.isProgressDestroyed())
            return;
        progress = new Progress();
    }

    /**
     * Reacts to a progress close event by closing the progress dialog and, when
     * errors were reported, displaying them in a warning popup.
     *
     * @param params
     *            the progress close event parameters
     */
    public void update(A_Progress.Close params) {
        progress.close();
        if (params.hasErrors())
            SC.warn(params.getErrors().stream().map(str -> "<pre>" + str + "</pre>").collect(Collectors.joining()));
    }

    /**
     * Reacts to a progress set-infos event by initializing the progress dialog
     * with the thread count and sub-info descriptors.
     *
     * @param params
     *            the progress set-infos event parameters
     */
    public void update(A_Progress.SetInfos params) {
        progress.setInfos(params.getThreadCnt(), params.getMultipleSubInfos());
    }

    /**
     * Reacts to a progress extend-infos event by extending the progress dialog
     * with additional thread and sub-info descriptors.
     *
     * @param params
     *            the progress extend-infos event parameters
     */
    public void update(A_Progress.ExtendInfos params) {
        progress.extendInfos(params.getThreadCnt(), params.getMultipleSubInfos());
    }

    /**
     * Reacts to a progress can-cancel event by toggling the cancel button of the
     * progress dialog.
     *
     * @param params
     *            the progress can-cancel event parameters
     */
    public void update(A_Progress.CanCancel params) {
        progress.canCancel(params.canCancel());
    }

    /**
     * Reacts to a progress clear-infos event by clearing the progress dialog info.
     *
     * @param params
     *            the progress clear-infos event parameters (unused)
     */
    public void update(A_Progress.ClearInfos params) //NOSONAR
    {
        progress.clearInfos();
    }

    /**
     * Reacts to a progress set-full-progress event by updating the progress dialog
     * with the full progress parameters.
     *
     * @param params
     *            the progress set-full-progress event parameters
     */
    public void update(A_Progress.SetFullProgress params) //NOSONAR
    {
        progress.setFullProgress(params.getParams());
    }

    /**
     * Reacts to a category/version file loaded event by updating the advanced
     * filters panel path field and disabling events on the category tree.
     *
     * @param params
     *            the category/version loaded event parameters
     */
    public void update(A_CatVer.Loaded params) //NOSONAR
    {
        scannerPanel.scannerAdvFiltersPanel.catverPath.setValue(params.getPath());
        scannerPanel.scannerAdvFiltersPanel.catverTree.enableEvents = false;
        scannerPanel.scannerAdvFiltersPanel.catverTree.invalidateCache();
    }

    /**
     * Reacts to an NPlayers file loaded event by updating the advanced filters
     * panel path field and disabling events on the NPlayers list.
     *
     * @param params
     *            the NPlayers loaded event parameters
     */
    public void update(A_NPlayers.Loaded params) //NOSONAR
    {
        scannerPanel.scannerAdvFiltersPanel.nplayersPath.setValue(params.getPath());
        scannerPanel.scannerAdvFiltersPanel.nplayersList.enableEvents = false;
        scannerPanel.scannerAdvFiltersPanel.nplayersList.invalidateCache();
    }

    /**
     * Reacts to a profile scanned event by toggling the fix button based on the
     * number of pending actions, showing or refreshing the report viewer, and
     * refreshing any open profile viewer.
     *
     * @param params
     *            the profile scanned event parameters
     */
    public void update(A_Profile.Scanned params) //NOSONAR
    {
        if (params.getSuccess()) {
            scannerPanel.btnFix.setDisabled(params.getActions() == null || params.getActions() == 0);
            if (params.hasReport()) {
                if (scannerPanel.reportViewer == null || !Client.getChildWindows().contains(scannerPanel.reportViewer))
                    scannerPanel.reportViewer = new ReportViewer();
                else if (scannerPanel.reportViewer.isVisible())
                    scannerPanel.reportViewer.bringToFront();
                else
                    scannerPanel.reportViewer.show();

            }
            if (Client.getMainWindow().scannerPanel.profileViewer != null && Client.getChildWindows().contains(Client.getMainWindow().scannerPanel.profileViewer)) {
                Client.getMainWindow().scannerPanel.profileViewer.anywareListList.refreshData();
                Client.getMainWindow().scannerPanel.profileViewer.anywareList.refreshData();
                Client.getMainWindow().scannerPanel.profileViewer.anyware.refreshData();
            }
        }
    }

    /**
     * Reacts to a profile fixed event by toggling the fix button based on the
     * number of remaining actions and refreshing any open profile viewer.
     *
     * @param params
     *            the profile fixed event parameters
     */
    public void update(A_Profile.Fixed params) {
        if (params.getSuccess()) {
            scannerPanel.btnFix.setDisabled(params.getActions() == null || params.getActions() == 0);
            if (Client.getMainWindow().scannerPanel.profileViewer != null && Client.getChildWindows().contains(Client.getMainWindow().scannerPanel.profileViewer)) {
                Client.getMainWindow().scannerPanel.profileViewer.anywareListList.refreshData();
                Client.getMainWindow().scannerPanel.profileViewer.anywareList.refreshData();
                Client.getMainWindow().scannerPanel.profileViewer.anyware.refreshData();
            }
        }
    }

    /**
     * Reacts to a report filter applied event by forwarding each filter parameter
     * to the scanner report viewer and reloading it.
     *
     * @param params
     *            the report apply-filter event parameters
     */
    public void update(A_Report.ApplyFilter params) {
        params.forEachParams((k, v) -> scannerPanel.reportViewer.applyFilter(k, v));
        scannerPanel.reportViewer.reload();
    }

    /**
     * Reacts to a successful fixDAT export.
     *
     * @param params the fixDAT created event parameters
     */
    public void update(A_Report.FixDatCreated params) {
        SC.say(Client.getSession().getMsg("Report.CreateFixDat"));
    }

    /**
     * Reacts to a lite report filter applied event by forwarding each filter
     * parameter to the batch DirUpd8r report and reloading it.
     *
     * @param params
     *            the lite report apply-filter event parameters
     */
    public void update(A_ReportLite.ApplyFilter params) {
        params.forEachParams((k, v) -> batchDirUpd8rPanel.report.applyFilter(k, v));
        batchDirUpd8rPanel.report.reload();
    }

    /**
     * Reacts to a Dat2Dir clear-results event by clearing the result column of all
     * batch DirUpd8r SDR rows.
     *
     * @param params
     *            the clear-results event parameters (unused)
     */
    public void update(A_Dat2Dir.ClearResults params) //NOSONAR
    {
        RecordList list = batchDirUpd8rPanel.sdr.getResultSet().getAllCachedRows();
        for (int i = 0; i < list.getLength(); i++)
            batchDirUpd8rPanel.sdr.setEditValue(i, 3, ""); //$NON-NLS-1$
    }

    /**
     * Reacts to a Dat2Dir update-result event by setting the result of the given
     * batch DirUpd8r SDR row.
     *
     * @param params
     *            the update-result event parameters
     */
    public void update(A_Dat2Dir.UpdateResult params) {
        final int row = params.getRow();
        final String result = params.getResult();
        batchDirUpd8rPanel.sdr.setEditValue(row, 3, result);
    }

    /**
     * Reacts to a Dat2Dir end event by disabling the scanner tab, cancelling any
     * pending edit, and refreshing and re-expanding the SDR rows.
     *
     * @param params
     *            the Dat2Dir end event parameters (unused)
     */
    public void update(A_Dat2Dir.End params) //NOSONAR
    {
        mainPane.disableTab(SCANNER);
        batchDirUpd8rPanel.sdr.cancelEditing();
        batchDirUpd8rPanel.sdr.refreshData((dsResponse, data, dsRequest) -> {
            ListGridRecord[] records = batchDirUpd8rPanel.sdr.getExpandedRecords();
            batchDirUpd8rPanel.sdr.collapseRecords(records);
            batchDirUpd8rPanel.sdr.expandRecords(records);
        });
    }

    /**
     * Reacts to a Dat2Dir show-settings event by opening the custom scanner
     * settings window for the given source DATs.
     *
     * @param params
     *            the show-settings event parameters
     */
    public void update(A_Dat2Dir.ShowSettings params) {
        batchDirUpd8rPanel.showSettings(params.getSettings(), params.getSrcs());
    }

    /**
     * Reacts to a TrntChk clear-results event by clearing the result column of all
     * batch torrent checker SDR rows.
     *
     * @param params
     *            the clear-results event parameters (unused)
     */
    public void update(A_TrntChk.ClearResults params) //NOSONAR
    {
        RecordList list = batchTrrntChkPanel.sdr.getResultSet().getAllCachedRows();
        for (int i = 0; i < list.getLength(); i++)
            batchTrrntChkPanel.sdr.setEditValue(i, 3, ""); //$NON-NLS-1$
    }

    /**
     * Reacts to a TrntChk update-result event by setting the result of the given
     * batch torrent checker SDR row.
     *
     * @param params
     *            the update-result event parameters
     */
    public void update(A_TrntChk.UpdateResult params) {
        final int row = params.getRow();
        final String result = params.getResult();
        batchTrrntChkPanel.sdr.setEditValue(row, 3, result);
    }

    /**
     * Reacts to a TrntChk end event by cancelling any pending edit and refreshing
     * and re-expanding the batch torrent checker SDR rows.
     *
     * @param params
     *            the TrntChk end event parameters (unused)
     */
    public void update(A_TrntChk.End params) //NOSONAR
    {
        batchTrrntChkPanel.sdr.cancelEditing();
        batchTrrntChkPanel.sdr.refreshData((dsResponse, data, dsRequest) -> {
            ListGridRecord[] records = batchTrrntChkPanel.sdr.getExpandedRecords();
            batchTrrntChkPanel.sdr.collapseRecords(records);
            batchTrrntChkPanel.sdr.expandRecords(records);
        });
    }

    /**
     * Reacts to a compressor clear-results event by clearing the result column of
     * all batch compressor grid rows.
     *
     * @param params
     *            the clear-results event parameters (unused)
     */
    public void update(A_Compressor.ClearResults params) //NOSONAR
    {
        for (int i = 0; i < batchCompressorPanel.fr.getTotalRows(); i++)
            batchCompressorPanel.fr.setEditValue(i, 1, ""); //$NON-NLS-1$
    }

    /**
     * Reacts to a compressor update-result event by setting the result of the
     * given batch compressor grid row.
     *
     * @param params
     *            the update-result event parameters
     */
    public void update(A_Compressor.UpdateResult params) {
        final int row = params.getRow();
        final String result = params.getResult();
        batchCompressorPanel.fr.setEditValue(row, 1, result);
    }

    /**
     * Reacts to a compressor update-file event by setting the file path of the
     * given batch compressor grid row.
     *
     * @param params
     *            the update-file event parameters
     */
    public void update(A_Compressor.UpdateFile params) {
        final int row = params.getRow();
        final String file = params.getFile();
        batchCompressorPanel.fr.setEditValue(row, 0, file);
    }

    /**
     * Reacts to a compressor end event by discarding all pending edits and
     * refreshing the batch compressor grid data.
     *
     * @param params
     *            the compressor end event parameters (unused)
     */
    public void update(A_Compressor.End params) //NOSONAR
    {
        batchCompressorPanel.fr.discardAllEdits();
        batchCompressorPanel.fr.refreshData();
    }

    /**
     * Reacts to a global set-memory event by updating the memory text field of the
     * debug settings panel.
     *
     * @param params
     *            the set-memory event parameters
     */
    public void update(A_Global.SetMemory params) {
        settingsDebugPanel.getItem("txtDbgMemory").setValue(params.getMsg()); //$NON-NLS-1$
    }

    /**
     * Reacts to a profile imported event by adding a new row to the profiles list
     * grid and selecting it.
     *
     * @param params
     *            the profile imported event parameters
     */
    public void update(A_Profile.Imported params) {
        Record rec = new Record();
        rec.setAttribute("Src", params.getPath()); //$NON-NLS-1$
        rec.setAttribute("Parent", params.getParent()); //$NON-NLS-1$
        rec.setAttribute("File", params.getName()); //$NON-NLS-1$
        profilePanel.listgrid.addData(rec, (dsResponse, data, dsRequest) -> profilePanel.listgrid.selectRecord(rec));
    }

    /**
     * Builds and returns the Settings tab containing general, compressor, debug, and admin sections.
     *
     * @return the Settings tab
     */
    private Tab getSettingsTab() {
        final var tab = new Tab();
        tab.setIcon("icons/cog.png"); //$NON-NLS-1$
        tab.setTitle(Client.getSession().getMsg("MainFrame.Settings")); //$NON-NLS-1$
        final var pane = new SectionStack();
        pane.setOverflow(Overflow.AUTO);
        pane.setVisibilityMode(VisibilityMode.MULTIPLE);
        final var gen = new SectionStackSection("General");
        gen.setCanClose(false);
        gen.setExpanded(true);
        settingsGenPanel = new SettingsGenPanel();
        gen.addItem(settingsGenPanel);
        pane.addSection(gen);
        final var compressors = new SectionStackSection(Client.getSession().getMsg("MainFrame.Compressors"));
        compressors.setCanClose(false);
        compressors.setExpanded(true);
        settingsCompressorPanel = new SettingsCompressorPanel();
        compressors.addItem(settingsCompressorPanel);
        pane.addSection(compressors);
        final var bug = new SectionStackSection(Client.getSession().getMsg("MainFrame.Debug"));
        bug.setCanClose(false);
        bug.setExpanded(true);
        settingsDebugPanel = new SettingsDebugPanel();
        bug.addItem(settingsDebugPanel);
        pane.addSection(bug);
        if (Client.getSession().isAdmin()) {
            final var admin = new SectionStackSection(Client.getSession().getMsg("MainWindow.Admin"));
            admin.setCanClose(false);
            admin.setExpanded(true);
            admin.addItem(new SettingsAdminPanel());
            pane.addSection(admin);
        }
        tab.setPane(pane);
        return tab;
    }

    /**
     * Builds and returns the Batch Tools tab containing Dat2Dir, TrrntChk, and Compressor panels.
     *
     * @return the Batch Tools tab
     */
    private Tab getBatchTab() {
        Tab tab = new Tab();
        tab.setIcon("icons/application_osx_terminal.png"); //$NON-NLS-1$
        tab.setTitle(Client.getSession().getMsg("MainFrame.BatchTools")); //$NON-NLS-1$
        tab.setPane(buildBatchTabSet());
        return tab;
    }

    /**
     * Builds the inner tab set of the Batch Tools tab, hosting the Dat2Dir,
     * torrent checker and compressor sub-tabs.
     *
     * @return the configured tab set
     */
    private TabSet buildBatchTabSet() {
        TabSet tabSet = new TabSet();
        tabSet.setPaneMargin(0);
        tabSet.setTabBarControls(TabBarControls.TAB_SCROLLER, TabBarControls.TAB_PICKER);
        tabSet.addTab(buildBatchDat2DirTab());
        tabSet.addTab(buildBatchTrrntChkTab());
        tabSet.addTab(buildBatchCompressorTab());
        return tabSet;
    }

    /**
     * Builds the batch Dat2Dir sub-tab and its embedded {@link BatchDirUpd8rPanel}.
     *
     * @return the configured tab
     */
    private Tab buildBatchDat2DirTab() {
        Tab tab = new Tab();
        tab.setTitle(Client.getSession().getMsg("MainFrame.panelBatchToolsDat2Dir.title")); //$NON-NLS-1$
        tab.setIcon("icons/application_cascade.png"); //$NON-NLS-1$
        batchDirUpd8rPanel = new BatchDirUpd8rPanel();
        tab.setPane(batchDirUpd8rPanel);
        return tab;
    }

    /**
     * Builds the batch torrent checker sub-tab and its embedded
     * {@link BatchTrrntChkPanel}.
     *
     * @return the configured tab
     */
    private Tab buildBatchTrrntChkTab() {
        Tab tab = new Tab();
        tab.setTitle(Client.getSession().getMsg("MainFrame.panelBatchToolsDir2Torrent.title")); //$NON-NLS-1$
        tab.setIcon("icons/drive_web.png"); //$NON-NLS-1$
        batchTrrntChkPanel = new BatchTrrntChkPanel();
        tab.setPane(batchTrrntChkPanel);
        return tab;
    }

    /**
     * Builds the batch compressor sub-tab and its embedded
     * {@link BatchCompressorPanel}.
     *
     * @return the configured tab
     */
    private Tab buildBatchCompressorTab() {
        Tab tab = new Tab();
        tab.setTitle(Client.getSession().getMsg("BatchPanel.Compressor")); //$NON-NLS-1$
        tab.setIcon("icons/compress.png"); //$NON-NLS-1$
        batchCompressorPanel = new BatchCompressorPanel();
        tab.setPane(batchCompressorPanel);
        return tab;
    }

    /**
     * Builds and returns the Dir2Dat tab for directory-to-DAT conversion.
     *
     * @return the Dir2Dat tab
     */
    private Tab getDir2DatTab() {
        Tab tab = new Tab();
        tab.setIcon("icons/drive_go.png"); //$NON-NLS-1$
        tab.setTitle(Client.getSession().getMsg("MainFrame.Dir2Dat")); //$NON-NLS-1$
        dir2datPanel = new Dir2DatPanel();
        tab.setPane(dir2datPanel);
        return tab;
    }

    /**
     * Builds and returns the Scanner tab, initially disabled until a profile is loaded.
     *
     * @return the Scanner tab
     */
    private Tab getScannerTab() {
        Tab tab = new Tab();
        tab.setName(SCANNER);
        tab.setIcon("icons/drive_magnify.png"); //$NON-NLS-1$
        tab.setTitle(Client.getSession().getMsg("MainFrame.Scanner")); //$NON-NLS-1$
        tab.setDisabled(true);
        scannerPanel = new ScannerPanel();
        tab.setPane(scannerPanel);
        return tab;
    }

    /**
     * Builds and returns the Profiles tab for profile management.
     *
     * @return the Profiles tab
     */
    private Tab getProfileTab() {
        Tab tab = new Tab();
        tab.setIcon("icons/script.png"); //$NON-NLS-1$
        tab.setTitle(Client.getSession().getMsg("MainFrame.Profiles")); //$NON-NLS-1$
        profilePanel = new ProfilePanel();
        tab.setPane(profilePanel);
        return tab;
    }
}

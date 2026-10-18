package jrm.webui.client.ui;

import java.util.LinkedHashMap;

import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.widgets.form.fields.ButtonItem;
import com.smartgwt.client.widgets.form.fields.CheckboxItem;
import com.smartgwt.client.widgets.form.fields.SelectItem;
import com.smartgwt.client.widgets.form.fields.TextItem;

import jrm.webui.client.Client;

/**
 * General settings panel for threading and backup configuration.
 * <p>
 * Exposes the worker thread count selector and the backup destination
 * directory (with an enable checkbox and a chooser button), all persisted as
 * global settings.
 *
 * @since 2.5
 */
final class SettingsGenPanel extends SettingsForm /* NOSONAR */ {
    /** Field name for the backup-destination-enabled checkbox. */
    private static final String TF_BACKUP_G_DEST_ENABLED = "tfBackupGDestEnabled";
    /** Field name for the backup destination directory text item. */
    private static final String TF_BACKUP_G_DEST = "tfBackupGDest";
    /** Field name for the thread count selector. */
    private static final String CB_THREAD_COUNT = "cbThreadCount";

    /**
     * Builds the general settings form: thread count selector, backup
     * destination text item with chooser button, and backup-enable checkbox.
     */
    public SettingsGenPanel() {
        setWidth("80%");
        setLayoutAlign(Alignment.CENTER);
        setNumCols(4);
        setColWidths(150, "*", 20, 20);
        final var session = Client.getSession();
        final var threadCount = new SelectItem(CB_THREAD_COUNT);
        threadCount.setTitle(session.getMsg("SettingsGenPanel.lblThreading.text"));
        final var map = new LinkedHashMap<String, String>();
        map.put("-1", session.getMsg("SettingsGenPanel.threadCount.adaptive"));
        map.put("0", session.getMsg("SettingsGenPanel.threadCount.maxAvailable"));
        final Integer maxThreadCountSetting = session.getSettingAsInteger("MaxThreadCount", 8);
        final int maxThreadCount = Math.max(1, maxThreadCountSetting != null ? maxThreadCountSetting : 8);
        for (int i = 1; i <= maxThreadCount; i++)
            map.put(Integer.toString(i), i == 1 ? "1 thread" : i + " threads"); //$NON-NLS-1$ //$NON-NLS-2$
        threadCount.setValueMap(map);
        threadCount.setWidth("*");
        threadCount.setColSpan(3);
        threadCount.addChangedHandler(event -> setGPropertyItemValue(CB_THREAD_COUNT, fname2name.get(CB_THREAD_COUNT), threadCount.getValueAsInteger()));
        final Integer threadCountSetting = session.getSettingAsInteger(fname2name.get(CB_THREAD_COUNT), -1);
        threadCount.setDefaultValue(Integer.toString(threadCountSetting != null ? threadCountSetting : -1));
        final var backupDest = new TextItem(TF_BACKUP_G_DEST, Client.getSession().getMsg("MainFrame.lblBackupDest.text"));
        backupDest.setWidth("*");
        backupDest.setCanEdit(false);
        backupDest.setDisabled(true);
        backupDest.setEndRow(false);
        backupDest.setDisabled(!Client.getSession().getSettingAsBoolean(fname2name.get(TF_BACKUP_G_DEST_ENABLED), false));
        backupDest.setDefaultValue(Client.getSession().getSetting(fname2name.get(TF_BACKUP_G_DEST), "%work/backup"));
        final var backupDestBtn = new ButtonItem("tfBackupGDestBtn");
        backupDestBtn.setStartRow(false);
        backupDestBtn.setIcon("icons/disk.png");
        backupDestBtn.setTitle(null);
        backupDestBtn.setDisabled(true);
        backupDestBtn.setValueIconRightPadding(0);
        backupDestBtn.setEndRow(false);
        backupDestBtn.addClickHandler(event -> new RemoteFileChooser("tfBackupDest", null, records -> setGPropertyItemValue(TF_BACKUP_G_DEST, "backup_dest_dir", records[0].path)));
        backupDestBtn.setDisabled(!Client.getSession().getSettingAsBoolean(fname2name.get(TF_BACKUP_G_DEST_ENABLED), false));
        final var backupDestEnabled = new CheckboxItem(TF_BACKUP_G_DEST_ENABLED);
        backupDestEnabled.setStartRow(false);
        backupDestEnabled.setShowLabel(false);
        backupDestEnabled.setShowTitle(false);
        backupDestEnabled.addChangedHandler(event -> {
            boolean selected = (boolean) event.getValue();
            backupDestBtn.setDisabled(!selected);
            backupDest.setDisabled(!selected);
            setGPropertyItemValue(TF_BACKUP_G_DEST_ENABLED, fname2name.get(TF_BACKUP_G_DEST_ENABLED), selected);
        });
        backupDestEnabled.setDefaultValue(Client.getSession().getSettingAsBoolean(fname2name.get(TF_BACKUP_G_DEST_ENABLED), false));
        setItems(threadCount, backupDest, backupDestBtn, backupDestEnabled);
    }
}

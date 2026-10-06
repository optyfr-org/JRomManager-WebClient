package jrm.webui.client.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gwt.core.client.JsArrayString;

import jrm.webui.client.utils.EnhJSO;

/**
 * Represents the client session state received from the server.
 * <p>
 * Contains session metadata (session ID, authentication status, admin privileges),
 * localized message strings, and user settings. Acts as both a data holder and
 * a settings accessor for the web client.
 *
 * @since 2.5
 */
public class A_Session extends EnhJSO // NOSONAR
{
    /** JSON property key for the {@code settings} object. */
    private static final String SETTINGS_STR = "settings";
    /** Cache for localized message strings, populated on first access. */
    private static Map<String, String> msgsCache = new HashMap<>();

    /** Protected constructor for JavaScript object overlay types. */
    protected A_Session() {
        super();
    }

    /**
     * Returns the session identifier.
     *
     * @return the session ID string
     */
    public final String getSession() {
        return get("session");
    }

    /**
     * Returns whether the current user is authenticated.
     *
     * @return {@code true} if authenticated, {@code false} otherwise
     */
    public final boolean isAuthenticated() {
        return Optional.ofNullable(getBoolean("authenticated")).orElse(false);
    }

    /**
     * Returns whether the current user has administrator privileges.
     *
     * @return {@code true} if the user is an admin, {@code false} otherwise
     */
    public final boolean isAdmin() {
        return Optional.ofNullable(getBoolean("admin")).orElse(false);
    }

    /**
     * Returns the server's WebSocket hint (absent -&gt; false).
     *
     * @return {@code true} when the server registered the {@code /ws} actions channel
     */
    public final boolean getWebsocket() {
        return Optional.ofNullable(getBoolean("websocket")).orElse(false);
    }

    /**
     * Returns the map of localized message strings.
     * <p>
     * The map is lazily populated from the server-provided JavaScript object on
     * first access and cached for subsequent calls.
     *
     * @return the message code-to-text map
     */
    public final Map<String, String> getMsgs() {
        if (msgsCache.size() == 0) {
            EnhJSO propJso = getJSO("msgs");
            JsArrayString props = getProperties(propJso);
            for (int i = 0; i < props.length(); i++)
                msgsCache.put(props.get(i), propJso.get(props.get(i)));
        }
        return msgsCache;
    }

    /**
     * Returns a string setting value, or a default if not present.
     *
     * @param key the setting key
     * @param def the default value if the key does not exist
     * @return the setting value, or {@code def}
     */
    public final String getSetting(String key, String def) {
        EnhJSO propJso = getJSO(SETTINGS_STR);
        return propJso.exists(key) ? propJso.get(key) : def;
    }

    /**
     * Returns a boolean setting value, or a default if not present.
     *
     * @param key the setting key
     * @param def the default value if the key does not exist
     * @return the setting value, or {@code def}
     */
    public final Boolean getSettingAsBoolean(String key, boolean def) {
        EnhJSO propJso = getJSO(SETTINGS_STR);
        return propJso.exists(key) ? propJso.getBoolean(key) : def;
    }

    /**
     * Returns an integer setting value, or a default if not present.
     *
     * @param key the setting key
     * @param def the default value if the key does not exist
     * @return the setting value, or {@code def}
     */
    public final Integer getSettingAsInteger(String key, int def) {
        EnhJSO propJso = getJSO(SETTINGS_STR);
        return propJso.exists(key) ? propJso.getInteger(key) : def;
    }

    /**
     * Sets a setting value as a JavaScript object.
     *
     * @param key the setting key
     * @param val the value to set
     */
    public final void setSetting(String key, EnhJSO val) {
        EnhJSO propJso = getJSO(SETTINGS_STR);
        propJso.set(key, val);
    }

    /**
     * Sets a setting value as a string.
     *
     * @param key the setting key
     * @param val the value to set
     */
    public final void setSetting(String key, String val) {
        EnhJSO propJso = getJSO(SETTINGS_STR);
        propJso.set(key, val);
    }

    /**
     * Sets a setting value as a boolean.
     *
     * @param key the setting key
     * @param val the value to set
     */
    public final void setSetting(String key, boolean val) {
        EnhJSO propJso = getJSO(SETTINGS_STR);
        propJso.set(key, val);
    }

    /**
     * Sets a setting value as an integer.
     *
     * @param key the setting key
     * @param val the value to set
     */
    public final void setSetting(String key, int val) {
        EnhJSO propJso = getJSO(SETTINGS_STR);
        propJso.set(key, val);
    }

    /**
     * Returns the localized message string for the given code.
     *
     * @param code the message code
     * @return the localized message text, or {@code null} if not found
     */
    public final String getMsg(String code) {
        return getMsgs().get(code);
    }

}

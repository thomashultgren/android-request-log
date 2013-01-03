package com.walmartlabs.ide.eclipse.requestlog.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.walmartlabs.ide.eclipse.requestlog.Activator;


/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

    private static final String defaultReplacements = "http[s]?://mobile(-e\\d+)?[.]walmart.com/m/j[?]{1}.*service=(.*)&method=([^&]*)(.*)" +
                                                      "///" +
                                                      "$2 $3 (mobile$1.walmart.com)" +
                                                      "||" +
                                                      "http[s]?://api-groceries(-qa\\d*)?.asda.com/api/([^/]*)/([^?]*).*" +
                                                      "///" +
                                                      "$2 $3 (api-groceries$1.asda.com)";

    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(PreferenceConstants.PREFERENCE_LOGCAT_COMMAND, "adb logcat HttpRequestExecutor:V *:S");
        store.setDefault(PreferenceConstants.PREFERENCE_START_TAG, "<RESULT_BEGIN>");
        store.setDefault(PreferenceConstants.PREFERENCE_END_TAG, "<RESULT_END>");
        store.setDefault(PreferenceConstants.PREFERENCE_HIGHLIGHT_COLOR_TAG, "214,249,220");
        store.setDefault(PreferenceConstants.PREFERENCE_REQUEST_URL_REPLACEMENT_TAG, defaultReplacements);
    }

}

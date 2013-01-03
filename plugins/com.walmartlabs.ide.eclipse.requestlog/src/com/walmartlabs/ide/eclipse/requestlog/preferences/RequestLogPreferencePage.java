package com.walmartlabs.ide.eclipse.requestlog.preferences;

import org.eclipse.jface.preference.ColorFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.walmartlabs.ide.eclipse.requestlog.Activator;

public class RequestLogPreferencePage
    extends FieldEditorPreferencePage
    implements IWorkbenchPreferencePage {

    public RequestLogPreferencePage() {
        super(GRID);
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("Request Log Preferences");
        setTitle("Request Log");
    }

    public void createFieldEditors() {
        addField(new StringFieldEditor(PreferenceConstants.PREFERENCE_LOGCAT_COMMAND, "Logcat command line", getFieldEditorParent()));
        addField(new StringFieldEditor(PreferenceConstants.PREFERENCE_START_TAG, "Start tag", getFieldEditorParent()));
        addField(new StringFieldEditor(PreferenceConstants.PREFERENCE_END_TAG, "End tag", getFieldEditorParent()));
        addField(new ColorFieldEditor(PreferenceConstants.PREFERENCE_HIGHLIGHT_COLOR_TAG, "Highlight color", getFieldEditorParent()));
        StringFieldEditor sfe = new StringFieldEditor(PreferenceConstants.PREFERENCE_REQUEST_URL_REPLACEMENT_TAG, "Replacements", getFieldEditorParent());
        Text text = sfe.getTextControl(getFieldEditorParent());
        text.setToolTipText("<REGEX1>///<REPLACEMENT1>||<REGEX2>///<REPLACEMENT2>");
        addField(sfe);
    }

    public void init(IWorkbench workbench) {
    }
}

package com.walmartlabs.ide.eclipse.requestlog.views;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.part.ViewPart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.walmartlabs.ide.eclipse.requestlog.Activator;
import com.walmartlabs.ide.eclipse.requestlog.preferences.PreferenceConstants;
import com.walmartlabs.ide.eclipse.requestlog.preferences.RequestLogPreferencePage;
import com.walmartlabs.requestlog.lib.LogProcessor;
import com.walmartlabs.requestlog.lib.ui.RequestLogPanel;


public class RequestLogView extends ViewPart {

    /**
     * The ID of the view as specified by the extension.
     */
    public static final String ID = "com.walmartlabs.ide.eclipse.requestlog.views.RequestLogView";

    private Action mStopStartAction;
    private Action mSettingsAction;
    private Action mCopyResponseAction;
    private RequestLogPanel mRequestLogPanel;
    private Composite mParent;
    private LogProcessor mLogProcessor;

    public RequestLogView() {
    }

    public void createPartControl(Composite parent) {
        mParent = parent;
        mRequestLogPanel = new RequestLogPanel(parent);
        mRequestLogPanel.init();
        final IPreferenceStore prefStore = Activator.getDefault().getPreferenceStore();
        final String replacements = prefStore.getString(PreferenceConstants.PREFERENCE_REQUEST_URL_REPLACEMENT_TAG);
        setRequestUrlReplacements(replacements, false);

        createAndRegisterActions();
        startLogCat();

        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.addPropertyChangeListener(new IPropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (PreferenceConstants.PREFERENCE_HIGHLIGHT_COLOR_TAG.equals(event.getProperty())) {
                    RGB newColor = null;
                    if (event.getNewValue() instanceof RGB) {
                        newColor = (RGB) event.getNewValue();
                    }  else if (event.getNewValue() instanceof String) {
                        String value = (String) event.getNewValue();
                        String[] split = value.split(",");
                        if (split != null && split.length == 3) {
                            try {
                                newColor = new RGB(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                            } catch (NumberFormatException nfe) {
                            }
                        }
                    }

                    if (newColor != null) {
                        mRequestLogPanel.setHighlightColor(newColor);
                    }
                } else if (PreferenceConstants.PREFERENCE_REQUEST_URL_REPLACEMENT_TAG.equals(event.getProperty())) {
                    String value = (String) event.getNewValue();
                    setRequestUrlReplacements(value, true);
                }
            }
        });
    }

    private void setRequestUrlReplacements(String value, boolean restartLogcat) {
        mRequestLogPanel.clearAllRequestUrlReplacements();
        String[] replacements = value.split("[||]");
        if (replacements == null || replacements.length == 0) {
            replacements = new String[] {value};
        }


        for (String replacement : replacements) {
            String[] split = replacement.split("///");
            if (split != null && split.length == 2) {
                mRequestLogPanel.addRequestUrlReplacement(split[0], split[1]);
            }
        }

        if (restartLogcat && mLogProcessor != null) {
            stopLogCat();
            startLogCat();
        }
    }

    private void createAndRegisterActions() {
        mStopStartAction = new Action() {
            public void run() {
                toggleLogCat();
            }
        };

        mSettingsAction = new Action() {
            public void run() {
                PreferenceManager mgr = new PreferenceManager();
                PreferenceNode node = new PreferenceNode("id", new RequestLogPreferencePage());
                mgr.addToRoot(node);
                PreferenceDialog dialog = new PreferenceDialog(mParent.getShell(), mgr);
                dialog.create();
                dialog.setMessage("Request Log");
                dialog.open();
            }
        };
        mSettingsAction.setToolTipText("Settings");
        mSettingsAction.setImageDescriptor(Activator.getImageDescriptor("icons/settings.png"));

        mCopyResponseAction = new Action() {
            public void run() {
                final String response = mRequestLogPanel.getCurrentResponse();
                if (response != null) {
                    final String prettyResponse = getPrettyResponse(response);
                    final Clipboard clipboard = new Clipboard(mParent.getDisplay());
                    clipboard.setContents(new Object[] { prettyResponse }, new Transfer[] { TextTransfer.getInstance()});
                    clipboard.dispose();
                }
            }
        };
        mCopyResponseAction.setToolTipText("Pretty copy response");
        mCopyResponseAction.setImageDescriptor(Activator.getImageDescriptor(ISharedImages.IMG_TOOL_COPY));


        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager manager = bars.getToolBarManager();
        manager.add(mCopyResponseAction);
        manager.add(mStopStartAction);
        manager.add(mSettingsAction);
    }

    private void toggleLogCat() {
        if (mLogProcessor != null) {
            stopLogCat();
        } else {
            startLogCat();
        }
    }

    private void startLogCat() {
        mLogProcessor = createLogProcessor();
        mRequestLogPanel.setLogProcessor(mLogProcessor);
        mStopStartAction.setImageDescriptor(Activator.getImageDescriptor("icons/stop.png"));
        mStopStartAction.setToolTipText("Stop adb logcat");
    }

    public void stopLogCat() {
        mLogProcessor.stop();
        mLogProcessor = null;
        mStopStartAction.setImageDescriptor(Activator.getImageDescriptor("icons/start.png"));
        mStopStartAction.setToolTipText("Start adb logcat");
    }

    private LogProcessor createLogProcessor() {
        final IPreferenceStore prefStore = Activator.getDefault().getPreferenceStore();
        final String cmdLine = prefStore.getString(PreferenceConstants.PREFERENCE_LOGCAT_COMMAND);
        final String startTag = prefStore.getString(PreferenceConstants.PREFERENCE_START_TAG);
        final String endTag = prefStore.getString(PreferenceConstants.PREFERENCE_END_TAG);
        return new LogProcessor(cmdLine, startTag, endTag, new String[] { "GET", "POST" });
    }

    @Override
    public void setFocus() {
    }

    private static String getPrettyResponse(String content) {
        String prettyContent = getPrettyJSON(content);
        if(prettyContent != null) {
            return prettyContent;
        }

        prettyContent = getPrettyXML(content, 4);
        if(prettyContent != null) {
            return prettyContent;
        }

        return content;
    }

    private static String getPrettyJSON(String content) {
        String prettyJSON = null;

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(content);
            prettyJSON = gson.toJson(element);
        } catch (Exception e) {
        }

        return prettyJSON;
    }

    public static String getPrettyXML(String input, int indent) {
        String prettyXML = null;

        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            prettyXML = xmlOutput.getWriter().toString();
        } catch (Exception e) {
        }

        return prettyXML;
    }
}

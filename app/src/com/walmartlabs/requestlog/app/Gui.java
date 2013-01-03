package com.walmartlabs.requestlog.app;


import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.walmartlabs.requestlog.lib.LogProcessor;
import com.walmartlabs.requestlog.lib.ui.RequestLogPanel;

public class Gui {

    private final Shell mShell;
    private final RequestLogPanel mRequestLogPanel;

    public Gui(Shell shell) {
        mShell = shell;
        mRequestLogPanel = new RequestLogPanel(mShell);
        mRequestLogPanel.init();
        LogProcessor logProcessor = new LogProcessor("adb logcat HttpRequestExecutor:V *:S", "<RESULT_BEGIN>", "<RESULT_END>", new String[] { "GET", "POST" });
        mRequestLogPanel.setLogProcessor(logProcessor);
        mRequestLogPanel.addRequestUrlReplacement("http[s]?://mobile(-e\\d+)?[.]walmart.com/m/j[?]{1}.*service=(.*)&method=([^&]*)(.*)", "$2 $3 (mobile$1.walmart.com)");
        mRequestLogPanel.addRequestUrlReplacement("http[s]?://api-groceries(-qa\\d*)?.asda.com/api/([^/]*)/([^?]*).*", "$2 $3 (api-groceries$1.asda.com)");
    }


    public static void main(String[] args) {
        final Display display = new Display ();
        Shell shell = new Shell (display);
        shell.setLayout(new FillLayout());

        new Gui(shell);

        shell.open ();
        while (!shell.isDisposed ()) {
           if (!display.readAndDispatch ()) display.sleep ();
        }
        display.dispose ();
    }

}

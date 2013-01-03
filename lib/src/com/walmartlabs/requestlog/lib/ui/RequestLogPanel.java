package com.walmartlabs.requestlog.lib.ui;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.tools.javac.util.Pair;
import com.walmartlabs.requestlog.lib.LogProcessor;
import com.walmartlabs.requestlog.lib.Request;
import com.walmartlabs.requestlog.lib.LogProcessor.OnLogReceivedListener;

public class RequestLogPanel {

    private Composite mParent;
    private Tree mResponseTree;
    private Tree mRequestTree;
    private SashForm mSashForm;
    private Clipboard mClipboard;
    private LogProcessor mLogProcessor;
    private final HashMap<Request, String> mRequestResponseMap = new HashMap<Request, String>();
    private final ArrayList<Pair<Pattern, String>> mRequestReplacements = new ArrayList<Pair<Pattern,String>>();

    private Color mHighlightColor;
    private Color mRegularColor;

    public RequestLogPanel(Composite parent) {
        mParent = parent;
        mClipboard = new Clipboard(parent.getDisplay());

        mHighlightColor = new Color(parent.getDisplay(), new RGB(0xd6, 0xf9, 0xdc));
        mRegularColor = new Color(parent.getDisplay(), new RGB(0xff, 0xff, 0xff));
    }

    public void clear() {
        mRequestResponseMap.clear();

        mRequestTree.removeAll();
        mResponseTree.removeAll();
    }

    public void setHighlightColor(RGB rgb) {
        mHighlightColor = new Color(mParent.getDisplay(), rgb);
    }

    public String getCurrentResponse() {
        final TreeItem[] selectedItems = mRequestTree.getSelection();
        if (selectedItems != null && selectedItems.length > 0) {
            final TreeItem item = selectedItems[0];
            return mRequestResponseMap.get(item.getData());
        }

        return null;
    }

    /**
     * Add a regular expression for which matched request urls will be replaced with the provided string
     *
     * Example:
     * regexp:	"http[s]?://mobile(-e\\d+)?[.]walmart.com/m/j[?]{1}.*service=(.*)&method=([^&]*)(.*)"
     * replace:	"$2 $3 (mobile$1.walmart.com)"
     * result for url: "http://mobile.walmart.com/m/j?e=1&service=AppVersion&method=getVersionRequired&p1=Android"
     * would be: "AppVersion getVersionRequired (mobile.walmart.com)"
     *
     * @param regex A regular expression matching urls
     * @param replace The replacement string. Use $n, where (0 < n < regexp group count), to expand with group n in the provided reg exp.
     */
    public void addRequestUrlReplacement(String regex, String replace) {
        mRequestReplacements.add(new Pair<Pattern, String>(Pattern.compile(regex), replace));
    }

    public void clearAllRequestUrlReplacements() {
        mRequestReplacements.clear();
    }

    public void setLogProcessor(LogProcessor logProcessor) {
        clear();

        if (mLogProcessor != null) {
            mLogProcessor.stop();
        }

        mLogProcessor = logProcessor;
        mLogProcessor.start(new OnLogReceivedListener() {

            @Override
            public void onRequest(final Request req) {
                mParent.getDisplay().asyncExec(new Runnable() {

                    @Override
                    public void run() {
                      mRequestResponseMap.put(req, "Waiting for response");
                      TreeItem treeItem = new TreeItem(mRequestTree, 0);
                      treeItem.setData(req);

                      treeItem.setText(getRequestTextForUrl(req.getUrl()));

                      TreeItem ti = new TreeItem(treeItem, 0);
                      ti.setText(req.getUrl());
                      ti = new TreeItem(treeItem, 0);
                      ti.setText(req.getMethod());
                    }
                });
            }

            @Override
            public void onResponse(final Request req, final String resp) {
                mParent.getDisplay().asyncExec(new Runnable() {

                    @Override
                    public void run() {
                        mRequestResponseMap.put(req, resp);

                        TreeItem[] selectedItems = mRequestTree.getSelection();
                        if (selectedItems != null && selectedItems.length > 0) {
                            if (req.equals(selectedItems[0].getData())) {
                                createTree(resp);
                            }
                        }
                    }
                });
            }
        });
    }

    private String getRequestTextForUrl(String requestUrl) {
        String requestText = requestUrl;
        String replace = null;

        for (Pair<Pattern, String> pair : mRequestReplacements) {
            Matcher matcher = pair.fst.matcher(requestUrl);
            if (matcher.matches()) {
                replace = pair.snd;

                for (int i = 1; i <= matcher.groupCount(); i++) {
                    if (replace.contains("$" + i)) {
                        String replaceText = matcher.group(i) != null ? matcher.group(i) : "";
                        replace = replace.replaceAll("[$]" + i, replaceText);
                    }
                }
            }

            if (replace != null && replace.length() > 0) {
                requestText = replace;
                break;
            }
        }

        return requestText;
    }

    public void init() {
        mSashForm = new SashForm(mParent, SWT.HORIZONTAL);
        mSashForm.setLayout(new FillLayout());

        mRequestTree = new Tree(mSashForm, SWT.BORDER);
        mRequestTree.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseDoubleClick(MouseEvent e) {
                final TreeItem[] selectedItems = mRequestTree.getSelection();
                if (selectedItems != null && selectedItems.length == 1) {
                    final Object data = selectedItems[0].getData();
                    if (data != null && data instanceof Request) {
                        final Request req = (Request) data;
                        Runtime rt = Runtime.getRuntime();
                        try {
                            rt.exec("open " + req.getUrl());
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }
        });
        mRequestTree.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent arg0) {
                try {
                    final TreeItem[] selection = mRequestTree.getSelection();
                    if (selection != null && selection.length > 0) {
                        int index = mRequestTree.indexOf(selection[0]);
                        // check if it's a request item (and not one of the request items' children)
                        if (index != -1) {
                            createTree(mRequestResponseMap.get(selection[0].getData()));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });

        mResponseTree = new Tree(mSashForm, SWT.BORDER);
        mResponseTree.addSelectionListener(new SelectionAdapter() {

            @Override
            public void widgetSelected(SelectionEvent e) {
                final TreeItem[] selection = mResponseTree.getSelection();
                if (selection != null && selection.length == 1) {
                    highlightNode(selection[0], mResponseTree.getItems(), false);
                    mResponseTree.deselectAll();
                }
            }

        });


        mRequestTree.addKeyListener(mKeyListener);
        mResponseTree.addKeyListener(mKeyListener);
    }

    private void highlightNode(TreeItem selectedItem, TreeItem[] items, boolean parentHighlighted) {
        boolean previousItemWasHighlightItemWithChildren = false;
        for (TreeItem item : items) {
            if (parentHighlighted || previousItemWasHighlightItemWithChildren || item == selectedItem) {
                item.setBackground(mHighlightColor);
                highlightNode(selectedItem, item.getItems(), true);
            } else {
                item.setBackground(mRegularColor);
                highlightNode(selectedItem, item.getItems(), false);
            }

            previousItemWasHighlightItemWithChildren = item == selectedItem && item.getItemCount() > 0;
        }
    }

    private KeyAdapter mKeyListener = new KeyAdapter() {

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.stateMask == SWT.COMMAND && e.keyCode == 'c') {
                String selectedText = null;
                if (e.widget == mRequestTree) {
                    selectedText = getSelectedText(mRequestTree);
                } else if (e.widget == mResponseTree) {
                    TreeItem firstHighlight = findFirstHighlight(mResponseTree.getItems());
                    if (firstHighlight != null) {
                        if (firstHighlight.getItemCount() > 0) {
                            StringBuilder builder = new StringBuilder(firstHighlight.getText().trim()).append("\n");
                            appendHighlightedTextFormatted(builder, firstHighlight.getItems(), 1);
                            TreeItem closingItem = getNextItem(firstHighlight);
                            builder.append(closingItem.getText().trim());
                            selectedText = builder.toString();
                        } else {
                            selectedText = firstHighlight.getText().trim();
                        }
                    }
                }

                if (selectedText != null) {
                    addToClipboard(selectedText);
                }
            }
        }
    };

    private TreeItem getNextItem(TreeItem item) {
        final TreeItem parentItem = item.getParentItem();
        if (parentItem != null) {
            int indexInParent = parentItem.indexOf(item);
            return parentItem.getItem(indexInParent + 1);
        } else {
            Tree parent = item.getParent();
            int indexInParent = parent.indexOf(item);
            return parent.getItem(indexInParent + 1);
        }
    }

    private TreeItem findFirstHighlight(TreeItem[] items) {
        for (TreeItem item : items) {
            if (item.getBackground() == mHighlightColor) {
                return item;
            }

            TreeItem childHighlight = findFirstHighlight(item.getItems());
            if (childHighlight != null) {
                return childHighlight;
            }
        }

        return null;
    }

    private void appendHighlightedTextFormatted(StringBuilder builder, TreeItem[] items, int indentlevel) {
        for (TreeItem item : items) {
            appendIndent(builder, indentlevel);
            builder.append(item.getText()).append("\n");
            appendHighlightedTextFormatted(builder, item.getItems(), indentlevel + 1);
        }
    }

    private static final String INDENT = "    ";
    private void appendIndent(StringBuilder builder, int indentLevel) {
        for (int i = 0; i < indentLevel; i++) {
            builder.append(INDENT);
        }
    }

    private String getSelectedText(Tree tree) {
        final TreeItem[] selection = tree.getSelection();
        if (selection != null && selection.length > 0) {
            return selection[0].getText();
        }

        return null;
    }

    private void addToClipboard(String text) {
        mClipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance()});
    }

    private void createTree(String response) {
        mResponseTree.removeAll();

        if (createJsonTree(response)) {
            return;
        }

        if (createXmlTree(response)) {
            return;
        }

        TreeItem ti = new TreeItem(mResponseTree, 0);
        ti.setText(response);
    }

    private boolean createXmlTree(String xml) {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            InputSource inputSource = new InputSource(new StringReader(xml));
            Document doc = documentBuilder.parse(inputSource);

            Element root = doc.getDocumentElement();
            createXmlTree(null, root);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private void createXmlTree(TreeItem parent, Node node) {
        TreeItem item;
        if (parent == null) {
            item = new TreeItem(mResponseTree, 0);
        } else {
            item = new TreeItem(parent, 0);
        }

        StringBuilder name = new StringBuilder("<").append(node.getNodeName()).append(">");

//		System.out.println("Node: " + node.getNodeName() + " -> " + node.getNodeValue());

        NodeList nodelist = node.getChildNodes();

        if (nodelist.getLength() == 1) {
//			Node child = nodelist.item(0);
//			System.out.println("Child name: " + child.getNodeName() + ", value: " + child.getNodeValue());
            StringBuilder end = new StringBuilder(name);
            name.append(nodelist.item(0).getNodeValue()).append(end.insert(1, "/"));
            item.setText(name.toString());
            return;
        } else if (nodelist.getLength() > 1) {
            for (int i = 0; i < nodelist.getLength(); i++) {
                createXmlTree(item, nodelist.item(i));
            }

            item.setText(name.toString());
            item.setExpanded(true);
            if (parent == null) {
                item = new TreeItem(mResponseTree, 0);
            } else {
                item = new TreeItem(parent, 0);
            }
            item.setText(name.insert(1, "/").toString());
        } else {
            System.out.println("Unexpected!! Node name" + node.getNodeName() + ", value: " + node.getNodeValue());
        }
    }

    private boolean createJsonTree(String json) {
        try {
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonElement = jsonParser.parse(json);

//            System.out.println("createJsonTree");

            TreeItem treeItem = new TreeItem(mResponseTree, 0);
            if (jsonElement.isJsonArray()) {
                treeItem.setText(" [");
            } else if (jsonElement.isJsonObject()) {
                treeItem.setText(" {");
            }
            createJsonTree(treeItem, jsonElement);

            TreeItem endItem = new TreeItem(mResponseTree, 0);
            if (jsonElement.isJsonArray()) {
                endItem.setText(" ]");
            } else if (jsonElement.isJsonObject()) {
                endItem.setText(" }");
            }

            treeItem.setExpanded(true);
//            System.out.println("done");

            return true;
        } catch (Exception e) {
            System.out.println("Not json: " + json);
        }

        return false;
    }

    private void createJsonTree(TreeItem parent, JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            Iterator<JsonElement> iter = array.iterator();
            while (iter.hasNext()) {
                createJsonTree(parent, iter.next());
            }
            parent.setExpanded(true);
        } else if (element.isJsonObject()) {
            TreeItem item;
            // Don't create another TreeItem if parent is a json object
            if (!(parent.getText().endsWith(" {"))) {
                item = new TreeItem(parent, 0);
                item.setText(" {");
            } else {
                item = parent;
            }
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                TreeItem entryItem = new TreeItem(item, 0);
                StringBuilder text = new StringBuilder(entry.getKey());
                JsonElement entryValue = entry.getValue();
                if (entryValue.isJsonPrimitive()) {
                    text.append(" : ");
                    boolean isString;
                    if (isString = entryValue.getAsJsonPrimitive().isString()) {
                        text.append("\"");
                    }
                    text.append(entryValue.getAsString());
                    if (isString) {
                        text.append("\"");
                    }
                    entryItem.setText(text.toString());
                } else if (entryValue.isJsonArray()) {
                    if (entryValue.getAsJsonArray().size() > 0) {
                        text.append(" : [");
                        entryItem.setText(text.toString());
                        createJsonTree(entryItem, entryValue);
                        TreeItem endItem = new TreeItem(item, 0);
                        endItem.setText("]");
                    } else {
                        text.append(" : [ ]");
                        entryItem.setText(text.toString());
                    }
                } else if (entryValue.isJsonObject()) {
                    text.append(" : {");
                    entryItem.setText(text.toString());
                    createJsonTree(entryItem, entryValue);
                    TreeItem endItem = new TreeItem(item, 0);
                    endItem.setText("}");
                    entryItem.setExpanded(true);
                } else {
                    System.out.println("Blubb");
                }
            }

            if (item != parent) {
                item.setExpanded(true);
                TreeItem endItem = new TreeItem(parent, 0);
                endItem.setText(" }");
            }
        } else if (element.isJsonPrimitive()) {
            // primitive in json array
            TreeItem item = new TreeItem(parent, 0);
            item.setText(element.getAsString());
        } else {
            System.out.println("Blabb: " + element.toString());
        }
    }
}

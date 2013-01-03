package com.walmartlabs.requestlog.app;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.walmartlabs.requestlog.lib.LogProcessor;
import com.walmartlabs.requestlog.lib.Request;
import com.walmartlabs.requestlog.lib.LogProcessor.OnLogReceivedListener;


public class Console {

    /**
     * @param args
     */
    public static void main(String[] args) {
        if(args.length > 0) {
            String cmdLine = args[0];
            String startTag = args[1];
            String endTag = args[2];
            String[] requestPrefixes = args[3].split("\\|");

            LogProcessor logProcessor = new LogProcessor(cmdLine, startTag, endTag, requestPrefixes);
            Process proc = logProcessor.start(new OnLogReceivedListener() {

                @Override
                public void onRequest(Request req) {
                }

                @Override
                public void onResponse(Request req, String resp) {
                    System.out.println(req);
                    System.out.println(getUnderline(req.toString().length()));
                    String prettyResponse = getPrettyResponse(resp);
                    System.out.println(prettyResponse);
                    System.out.println("\n");
                }
            });

            try {
                proc.waitFor();
            } catch (InterruptedException e) {
            }

        } else {
            System.out.println("Usage:\njava -jar rlogcat.jar <logcat cmd line> <start-tag> <end-tag> <request prefix list>");
            System.out.println("Example:\njava -jar rlogcat.jar \"adb logcat HttpRequestExecutor:I *:S\" \"<RESULT_BEGIN>\" \"<RESULT_END>\" \"GET|POST\"");
        }

    }

    private static String getUnderline(int length) {
        StringBuffer sb = new StringBuffer(length);
        for(int i = 0; i < length; i++) {
            sb.append('-');
        }

        return sb.toString();
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

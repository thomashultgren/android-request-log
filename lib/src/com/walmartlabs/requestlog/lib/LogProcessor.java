package com.walmartlabs.requestlog.lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogProcessor {

    public interface OnLogReceivedListener {
        public void onRequest(Request req);
        public void onResponse(Request req, String resp);
    }

    public static class StreamReaderRunnable implements Runnable {
        private static final Pattern sLogEntryPattern = Pattern.compile("([^:]*:) (.*)");

        private static final int REQUEST_GROUP_METHOD = 1;
        private static final int REQUEST_GROUP_URL = 2;
        private static final int LOG_ENTRY_GROUP_MESSAGE = 2;
        private static final int START_TAG_GROUP_URL = 1;

        private InputStream inputStream;

        private String endTag;
        private final OnLogReceivedListener mListener;


        private Pattern mRequestPattern;
        private Pattern mStartTagPattern;

        private HashMap<String, Request> mOngoingRequests = new HashMap<String, Request>();

        private boolean isErrorStream;

        public StreamReaderRunnable(InputStream inputStream, boolean isErrorStream, OnLogReceivedListener listener) {
            this.inputStream = inputStream;
            this.isErrorStream = isErrorStream;
            mListener = listener;
        }

        public void setStartTag(String tag) {
            mStartTagPattern = Pattern.compile(tag + " (.*)");
        }

        public void setEndTag(String tag) {
            this.endTag = tag;
        }

        public void setRequestPrefixes(String[] requestPrefixes) {

            final StringBuilder regex = new StringBuilder();
            regex.append("(");
            for (String prefix : requestPrefixes) {
                if (regex.length() != 1) {
                    regex.append("|");
                }
                regex.append(prefix).append("[^:]*");
            }
            regex.append(")")
            .append(": ")
            .append("(.*)");

            mRequestPattern = Pattern.compile(regex.toString());
        }

        @Override
        public void run() {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line = null;
            try {
                boolean isReadingResponse = false;
                StringBuffer buffer = new StringBuffer();
                System.out.println("\n");
                String requestUrl = null;
                while ((line = bufferedReader.readLine()) != null) {
                    if(!isErrorStream) {
                        Matcher logEntryMatcher = sLogEntryPattern.matcher(line);
                        if (logEntryMatcher.matches()) {
//                            for (int i = 0; i < logEntryMatcher.groupCount() + 1; i++) {
//                                System.out.println("group " + i + ":" + logEntryMatcher.group(i));
//                            }

                            final String logMessage = logEntryMatcher.group(LOG_ENTRY_GROUP_MESSAGE);
                            if(!isReadingResponse) {
                                Matcher requestMatcher = null;
                                Matcher startTagMatcher = mStartTagPattern.matcher(logMessage);

                                if(startTagMatcher.matches()) {
                                    isReadingResponse = true;
                                    buffer.delete(0, buffer.length());
                                    requestUrl = startTagMatcher.group(START_TAG_GROUP_URL);
                                } else if ((requestMatcher = mRequestPattern.matcher(logMessage)).matches()) {
                                    Request request = new Request(requestMatcher.group(REQUEST_GROUP_METHOD), requestMatcher.group(REQUEST_GROUP_URL));
                                    mOngoingRequests.put(request.getUrl(), request);
                                    mListener.onRequest(request);
                                }
                            } else {
                                if(logMessage.trim().equals(endTag)) {
                                    final Request request = mOngoingRequests.remove(requestUrl);
                                    mListener.onResponse(request, buffer.toString());
                                    isReadingResponse = false;
                                } else {
                                    buffer.append(logMessage);
                                }
                            }
                        } else {
                            System.out.println("No match: " + line);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public LogProcessor(String cmdLine, String startTag, String endTag, String[] requestPrefixes) {
        mCmdLine = cmdLine;
        mStartTag = startTag;
        mEndTag = endTag;
        mRequestPrefixes = requestPrefixes;
    }

    private final String mCmdLine;
    private final String mStartTag;
    private final String mEndTag;
    private final String[] mRequestPrefixes;
    private Process mProcess;

    public Process start(OnLogReceivedListener listener) {
        try {
            Runtime rt = Runtime.getRuntime();
            mProcess = rt.exec(mCmdLine);
            InputStream stdOut = mProcess.getInputStream();
            InputStream stdError = mProcess.getErrorStream();

            StreamReaderRunnable stdOutReader = new StreamReaderRunnable(stdOut, false, listener);
            stdOutReader.setStartTag(mStartTag);
            stdOutReader.setEndTag(mEndTag);
            stdOutReader.setRequestPrefixes(mRequestPrefixes);

            new Thread(stdOutReader).start();
            new Thread(new StreamReaderRunnable(stdError, true, null)).start();

            return mProcess;
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return null;
    }

    public void stop() {
        try {
            mProcess.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

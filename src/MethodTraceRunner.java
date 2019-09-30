import com.android.ddmlib.*;
import org.apache.commons.cli.*;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Created by likaci on 29/09/2017
 */
public class MethodTraceRunner {
    private static final String TAG = "MethodTraceRunner";
    private static String deviceSerial;
    private static String packageName;
    private static String outputDirectory;
    private static int bufferSize;
    private static int sampleRate;


    public static void main(String[] args) throws IOException {
        parseArgs(args);

        //bridge
        AndroidDebugBridge.init(true);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();

        //device
        waitForDevice(bridge);
        IDevice[] devices = bridge.getDevices();
        if (devices.length == 0) {
            exitWithError("no device found");
        }
        IDevice device = null;
        for (IDevice dev : devices) {
            if (deviceSerial.equals(dev.getSerialNumber())) {
                device = dev;
                break;
            }
        }
        if (device == null) {
            exitWithError("target device not available");
        }
        System.out.println("device: " + device);

        //client
        waitForClient(device, packageName);
        Client client = device.getClient(packageName);
        Log.d(TAG, "client: " + client);

        DdmPreferences.setProfilerBufferSizeMb(bufferSize);

        ClientData.setMethodProfilingHandler(new ClientData.IMethodProfilingHandler() {
            private long totalBytes = 0;
            private int trace_file_count = 1;

            @Override
            public void onSuccess(String s, Client client) {
                Log.d(TAG, "onSuccess: " + s + " " + client);
            }

            @Override
            public void onSuccess(byte[] bytes, Client client) {
                Log.d(TAG, "onSuccess: " + client);
//                Log.i(TAG, "bytes length: " + bytes.length);

                BufferedOutputStream bs = null;

                String current_filename = outputDirectory + File.separator + String.format("%02d", trace_file_count) + ".trace";
                try {
                    FileOutputStream fs = new FileOutputStream(new File(current_filename));
                    bs = new BufferedOutputStream(fs);
                    bs.write(bytes);
//                    totalBytes = totalBytes + bytes.length;
                    bs.close();
                    bs = null;
//                    if (totalBytes > bufferSize * 1024 * 1024) {
//                        Log.i(TAG, "totalBytes exceed bufferSize. change trace file.");
//                        totalBytes = 0;
//                        trace_file_count += 1;
//                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (bs != null) try {
                    bs.close();
                } catch (Exception ignored) {
                }

                Log.i(TAG, "trace file: " + current_filename);
                System.out.println("trace file saved at " + current_filename);
                System.exit(0);
            }

            @Override
            public void onStartFailure(Client client, String s) {
                exitWithError("onStartFailure: " + client + " " + s);
            }

            @Override
            public void onEndFailure(Client client, String s) {
                exitWithError("onEndFailure: " + client + " " + s);
            }

        });

        Log.i(TAG, String.format("will profile %s, device: %s, client: %s%n", packageName, device, client));
        System.out.println("start Sampling Profiler\nType 'stop' for stop profiler.");
        long s_time = System.currentTimeMillis();
//        client.startMethodTracer();
        client.startSamplingProfiler(sampleRate, TimeUnit.MILLISECONDS);

        boolean isWaiting = true;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            String input;
            while(isWaiting){
                while(!br.ready()) {
                    Thread.sleep(200);
                }
                input = br.readLine();
                if (input.equals("stop"))
                    isWaiting = false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "stop Sampling Profiler");
//        client.stopMethodTracer();
        client.stopSamplingProfiler();
    }

    private static void parseArgs(String[] args) {
        Log.d(TAG, "args: " + Arrays.toString(args));

        Options options = new Options();
        options.addOption("h", false, "show help msg");
        options.addOption("v", false, "verbose log");
        options.addOption("s", true, "device serial");
        options.addOption("p", true, "package name");
        options.addOption("o", true, "output directory");
        options.addOption("b", true, "buffer size (MB)");
        options.addOption("r", true, "sample rate (ms)");

        try {
            CommandLine cmd = new DefaultParser().parse(options, args);

            if (cmd.hasOption("h")) {
                printHelpMsg(options);
                System.exit(0);
            }

            if (cmd.hasOption("v")) {
                DdmPreferences.setLogLevel(Log.LogLevel.DEBUG.getStringValue());
            }

            deviceSerial = cmd.getOptionValue("s", "");
            if (deviceSerial.isEmpty()) {
                exitWithError("deviceSerial not specified, use -s arg to set");
            }
            Log.i(TAG, "deviceSerial: " + deviceSerial);

            packageName = cmd.getOptionValue("p", "");
            if (packageName.isEmpty()) {
                exitWithError("PackageName not specified, use -p arg to set");
            }
            Log.i(TAG, "packageName: " + packageName);

            outputDirectory = cmd.getOptionValue("o", "");
            if (outputDirectory.isEmpty()) {
                exitWithError("outputDirectory not specified, use -o arg to set");
            }
            Log.i(TAG, "outputDirectory: " + outputDirectory);

            bufferSize = Integer.parseInt(cmd.getOptionValue("b", "8"));
            Log.d(TAG, "bufferSize: " + bufferSize + " MB");

            sampleRate = Integer.parseInt(cmd.getOptionValue("r", "10"));
            Log.d(TAG, "sampleRate: " + sampleRate + " ms");
        } catch (Exception e) {
            printHelpMsg(options);
            exitWithError(e);
        }

    }

    private static void printHelpMsg(Options options) {
        new HelpFormatter().printHelp("mtr", options);
    }

    private static void waitForDevice(AndroidDebugBridge bridge) {
        int count = 0;
        while (!bridge.hasInitialDeviceList()) {
            try {
                Thread.sleep(100);
                count++;
            } catch (InterruptedException ignored) {
            }
            if (count > 100) {
                exitWithError("wait for device time out");
                break;
            }
        }
    }

    private static void waitForClient(IDevice device, String packageName) {
        int count = 0;
        while (device.getClient(packageName) == null) {
            try {
                Thread.sleep(100);
                count++;
            } catch (InterruptedException ignored) {
            }
            if (count > 100) {
                exitWithError("wait for client time out");
                break;
            }
        }
    }

    private static void exitWithError(String msg) {
        exitWithError(new Exception(msg));
    }

    private static void exitWithError(Exception exception) {
        if (exception != null) {
            exception.printStackTrace();
        }
        System.exit(1);
    }

}

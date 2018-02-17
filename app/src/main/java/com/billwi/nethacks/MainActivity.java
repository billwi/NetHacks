package com.billwi.nethacks;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends AppCompatActivity {

    private static final String DEFAULT_DNS_IP = "8.8.8.8";
    private static final String DEFAULT_DNS_PORT = "53";

    private File mBlockHosts;

    private Button mDnsButton;
    private Switch mPacketForwardSwitch;
    private EditText mIp;
    private EditText mPort;
    private Button mResetButton;
    private Switch mUseHostsSwitch;
    private Button mUpdateHostsButton;
    private TextView mHostLabel;

    private int mPacketForwardingEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDnsButton = findViewById(R.id.dnsButton);
        mPacketForwardSwitch = findViewById(R.id.packetForwardingSwitch);
        mIp = findViewById(R.id.dnsHost);
        mPort = findViewById(R.id.dnsPort);
        mResetButton = findViewById(R.id.resetIPTables);
        mUseHostsSwitch = findViewById(R.id.useHostsSwitch);
        mUpdateHostsButton = findViewById(R.id.updateHostsButton);
        mHostLabel = findViewById(R.id.HostsLabel);

        mBlockHosts = new File(this.getFilesDir(), "hosts.unified");

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String DnsIp = sharedPref.getString("DnsIp", DEFAULT_DNS_IP);
        String dnsPort = sharedPref.getString("DnsPort", DEFAULT_DNS_PORT);

        try {
            Scanner scanner = new Scanner(new File("/proc/sys/net/ipv4/ip_forward"));
            mPacketForwardingEnabled = scanner.nextInt();
        } catch (FileNotFoundException e) {
            mPacketForwardingEnabled = 0;
            e.printStackTrace();
        }

        if (mPacketForwardingEnabled == 0) {
            mPacketForwardSwitch.setChecked(false);
        } else {
            mPacketForwardSwitch.setChecked(true);
        }

        mIp.setText(DnsIp);
        mPort.setText(dnsPort);

        if (!checkHostsExist()) {
            mUseHostsSwitch.setEnabled(false);
        } else {
            updateHostText();
        }

        if (hostIsApplied()) {
            mUseHostsSwitch.setChecked(true);
        }



        mPacketForwardSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    String[] commands = {
                            "echo 1 > /proc/sys/net/ipv4/ip_forward",
                            "iptables -F -t filter",
                            "iptables -P FORWARD ACCEPT"
                    };
                    runAsRoot(commands);
                } else {
                    String[] commands = {
                            "echo 0 > /proc/sys/net/ipv4/ip_forward",
                            "iptables -F -t filter",
                            "iptables -P FORWARD DENY"
                    };
                    runAsRoot(commands);
                }

            }
        });

        mUseHostsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    System.out.println(mBlockHosts.getAbsolutePath());
                    String[] commands = {
                            "cp " + mBlockHosts.getAbsolutePath() + " /magisk/.core/hosts"
                    };
                    runAsRoot(commands);
                } else {
                    String[] commands = {
                            "rm /magisk/.core/hosts"
                    };
                    runAsRoot(commands);
                }
            }
        });
    }

    public void dnsButtonClick(View v) {
        String ip = mIp.getText().toString();
        String dnsPort = mPort.getText().toString();
        String[] commands = {
                "iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to " + ip + ":" + dnsPort,
                "iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to " + ip + ":" + dnsPort,
        };
        runAsRoot(commands);

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("DnsIp", ip);
        editor.putString("DnsPort", dnsPort);
        editor.apply();
    }

    public void resetButtonClick(View v) {
        String[] commands = {
                "iptables -t nat -D OUTPUT 1",
                "iptables -t nat -D OUTPUT 1"
        };
        runAsRoot(commands);
    }

    public void updateHostsClick(View v) {
        int WRITE_STORAGE = 0;

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                WRITE_STORAGE);

        String url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling/hosts";
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription("StevenBlack/hosts: Unified + fakenews + gambling");
        request.setTitle("Hosts file");
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "hosts.unified");

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        assert manager != null;
        manager.enqueue(request);

        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                File publicHosts = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "hosts.unified");
                try {
                    copy(publicHosts, mBlockHosts);
                    publicHosts.delete();
                    if (checkHostsExist()) {
                        mUseHostsSwitch.setEnabled(true);
                        updateHostText();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void runAsRoot(String[] cmd) {
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            for (String command : cmd) {
                outputStream.writeBytes(command + "\n");
                outputStream.flush();
            }
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            su.waitFor();
            outputStream.close();

        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkHostsExist() {
        return mBlockHosts.exists();
    }

    private static void copy(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src)) {
            try (OutputStream out = new FileOutputStream(dst)) {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    private boolean hostIsApplied() {
        File magiskHosts = new File("/magisk/.core/hosts");
        return magiskHosts.exists();
    }

    private void updateHostText(){
        Date lastMod = new Date(mBlockHosts.lastModified());
        String defaultHostsStr = getString(R.string.hosts_info);
        mHostLabel.setText(defaultHostsStr + "\nLast modified: " + lastMod.toString());
    }
}

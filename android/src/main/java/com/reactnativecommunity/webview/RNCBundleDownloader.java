package com.reactnativecommunity.webview;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

abstract class RNCBundleDownloader implements Runnable {
  private final String BUNDLE_FILE_NAME = "bundle.zip";
  private final String TAG = "RNCBundleDownloader";
  private Context context;
  private String bundleUrl;

  Handler mResultHandler = new Handler() {
    @Override
    public void handleMessage(@NonNull Message msg) {
      super.handleMessage(msg);
      Bundle data = msg.getData();
      Boolean success = data.getBoolean("success");
      String message = data.getString("message");
      done(success, message);
    }
  };

  public RNCBundleDownloader(Context context, String bundleUrl) {
    this.context = context;
    this.bundleUrl = bundleUrl;
  }

  @Override
  public void run() {
    Message message = new Message();
    Bundle bundleData = new Bundle();
    message.setData(bundleData);
    try {
      download();
      unzip();
      bundleData.putBoolean("success", true);
      bundleData.putString("message", "bundle download successfully");
    } catch (Exception e) {
      bundleData.putBoolean("success", false);
      bundleData.putString("message", e.getMessage());
    } finally {
      mResultHandler.sendMessage(message);
    }
  }

  private void download() throws Exception {
    Log.i(TAG, "download(): Started");
    InputStream input = null;
    OutputStream output = null;
    HttpURLConnection connection = null;

    URL url = new URL(bundleUrl);
    connection = (HttpURLConnection) url.openConnection();
    connection.connect();
    int responseCode = connection.getResponseCode();

    if (responseCode != HttpURLConnection.HTTP_OK) {
      throw new Exception("cannot download bundle (status: " + responseCode + ")");
    }

    input = connection.getInputStream();
    String downloadPath = context.getCacheDir() + "/" + BUNDLE_FILE_NAME;
    output = new FileOutputStream(downloadPath);
    Log.i(TAG, "download(): " + downloadPath);

    byte data[] = new byte[4096];
    int count;
    while ((count = input.read(data)) != -1) {
      output.write(data, 0, count);
    }

    // Cleanup
    try {
      if (output != null) {
        output.close();
      }
      if (input != null) {
        input.close();
      }
    } catch (IOException ignored) {}

    if (connection != null) {
      connection.disconnect();
    }
  }

  private void unzip() throws Exception {
    Log.i(TAG, "unzip(): Started");
    InputStream inputStream = null;
    ZipInputStream zipInputStream = null;

    String filename;
    String path = context.getCacheDir().toString();
    String filePath = path + "/" + BUNDLE_FILE_NAME;;
    Log.i(TAG, "unzip(): " + filePath);

    inputStream = new FileInputStream(filePath);
    zipInputStream = new ZipInputStream(new BufferedInputStream(inputStream));
    ZipEntry entry;
    byte[] buffer = new byte[1024];
    int count;

    while ((entry = zipInputStream.getNextEntry()) != null) {
      filename = entry.getName();

      if (entry.isDirectory()) {
        File fmd = new File(path + "/" + filename);
        fmd.mkdirs();
        continue;
      }

      FileOutputStream fileOutputStream = new FileOutputStream(path + "/" + filename);

      while ((count = zipInputStream.read(buffer)) != -1) {
        fileOutputStream.write(buffer, 0, count);
      }

      fileOutputStream.close();
      zipInputStream.closeEntry();
    }

    // Cleanup
    zipInputStream.close();
    if (inputStream != null) {
      try {
        inputStream.close();
      } catch (Exception e) {}
    }

    if (zipInputStream != null) {
      try {
        zipInputStream.close();
      } catch (Exception e) {}
    }
  }

  final public void start() {
    Thread thread = new Thread(this);
    thread.start();
  }

  protected abstract void done(Boolean success, String message);
}

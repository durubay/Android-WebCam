package com.faptastic.webcam;

import android.os.AsyncTask;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;


public class UploadAsyncTask extends AsyncTask<String, String, String> {

    public final boolean internetAvailable() {
        boolean check;
        try {
            InetAddress ipAddr = InetAddress.getByName("google.com");
            check = !ipAddr.equals("");
        } catch (Exception e) {
            check = false;
        }
        return check;
    }

    @Override
    protected String doInBackground(String... strings) {

        String sourceFilePath = strings[0];
        String destURL = strings[1];

        // Setup HTTPS connection
        //destURL = destURL.replace("http://", "https://");
        //if (!destURL.startsWith("https://")) destURL = "https://" + destURL;
        destURL = destURL.replace("https://", "http://");
        if (!destURL.startsWith("http://")) destURL = "http://" + destURL;

        if (!internetAvailable()) {
            Log.e("UploadTask", "Internet isn't available. Cancelling upload.");
            return null;
        }

        Log.i("UploadTask", "Attempting to upload: " + sourceFilePath + " to " + destURL);


        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";

        int bytesRead;
        int bytesAvailable;
        int bufferSize;
        byte[] buffer;
        int maxBufferSize = 1048576;

        // File Path
        File sourceFile = new File(sourceFilePath);

        System.out.println(sourceFile.toString());

        if (!sourceFile.isFile()) {
            Log.e("uploadFile", "Source File not exist: " + sourceFilePath);
            return null;
        }

        try {

            FileInputStream fileInputStream = new FileInputStream(sourceFile);

            // Setup HRRPs
            URL url = new URL(destURL);
            //HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(15000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            conn.setRequestProperty("uploaded_file", "upload.jpg");

            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

            String fileName = "upload.jpg";
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\"" + fileName + '"' + lineEnd);
            dos.writeBytes(lineEnd);

            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            while (bytesRead > 0) {
                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            }

            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            // Get Server response
            int serverResponseCode = conn.getResponseCode();
            String serverResponseMessage = conn.getResponseMessage();

            BufferedReader br = new BufferedReader((Reader) (new InputStreamReader(conn.getInputStream())));

            // Get response
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                output.append(line);
            }

            Log.i("UploadTask", "HTTP Response is: '" + output.toString() + "': " + serverResponseCode);

            if (serverResponseCode != 200) {
                Log.e("UploadTask", "Failed to upload. Server Error.");
            }

            //close the streams //
            fileInputStream.close();
            br.close();
            dos.flush();
            dos.close();
            conn.disconnect();

        } catch (MalformedURLException e)
        {
            e.printStackTrace();
            Log.e("UploadTask", "Bad URL : " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("UploadTask", "Exception : " + e.getMessage());
        }

        return null;

    }


}

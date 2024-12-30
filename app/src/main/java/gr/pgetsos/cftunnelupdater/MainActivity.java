package gr.pgetsos.cftunnelupdater;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private AtomicReference<String> publicIp = new AtomicReference<>();
    private String accountID;
    private String groupID;
    private String apiToken;
    private String ipAddr;
    private EditText ipEditText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Button button = findViewById(R.id.save_button);
        Button ipButton = findViewById(R.id.ip_button);

        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        accountID = sharedPreferences.getString("accountID", "");
        groupID = sharedPreferences.getString("groupID", "");
        apiToken = sharedPreferences.getString("apiToken", "");

        ipEditText = findViewById(R.id.ip_et);

        ((EditText) findViewById(R.id.account_et)).setText(accountID);
        ((EditText) findViewById(R.id.group_et)).setText(groupID);
        ((EditText) findViewById(R.id.api_et)).setText(apiToken);

        button.setOnClickListener(view-> {
            accountID = ((EditText) findViewById(R.id.account_et)).getText().toString();
            groupID = ((EditText) findViewById(R.id.group_et)).getText().toString();
            apiToken = ((EditText) findViewById(R.id.api_et)).getText().toString();
            ipAddr = ipEditText.getText().toString();

            if(ipAddr.isBlank()) {
                Toast.makeText(MainActivity.this, "Please enter an IP address", Toast.LENGTH_SHORT).show();
                return;
            }

            if(accountID.isBlank() || groupID.isBlank() || apiToken.isBlank()) {
                Toast.makeText(MainActivity.this, "Please enter credentials", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("accountID", accountID);
            editor.putString("groupID", groupID);
            editor.putString("apiToken", apiToken);
            editor.apply();

            getCurrentGroup();
        });

        ipButton.setOnClickListener(view-> getPublicIP());

        getPublicIP();
    }

    private void getCurrentGroup() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts/"+accountID+"/access/groups/"+groupID)
                .get()
                .addHeader("authorization", "Bearer "+apiToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("NetworkError", "Request failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    if (response.body() != null) {
                        String responseBody = response.body().string();
                        String updatedJson = changeAccessGroup(responseBody);
                        updateAccessGroup(updatedJson);
                    }
                } else {
                    Log.e("NetworkError", "Unexpected code " + response);
                }

                response.close();
            }
        });
    }

    private String changeAccessGroup(String jsonString) {
        Gson gson = new Gson();
        AccessGroupResponse response = gson.fromJson(jsonString, AccessGroupResponse.class);
        AccessGroupResponse.IncludeItem ii = new AccessGroupResponse.IncludeItem();
        AccessGroupResponse.Ip newIp = new AccessGroupResponse.Ip();
		InetAddress address = null;
		try {
			address = InetAddress.getByName(ipAddr);
		} catch (UnknownHostException e) {
			runOnUiThread(() -> Toast.makeText(MainActivity.this, "Invalid IP address", Toast.LENGTH_SHORT).show());
            return null;
		}
		if (address instanceof Inet6Address) {
            newIp.ip = ipAddr + "/64";
        } else if (address instanceof Inet4Address) {
            newIp.ip = ipAddr + "/32";
        }
        ii.ip = newIp;
        response.result.include.add(ii);
        List<AccessGroupResponse.IncludeItem> newList = new ArrayList<>();
        for (AccessGroupResponse.IncludeItem element : response.result.include) {
            if (!newList.contains(element)) {
                newList.add(element);
            }
        }
        response.result.include = newList;

        return gson.toJson(response);
    }

    private void updateAccessGroup(String jsonString) {
        if (jsonString == null) {
            return;
        }

        String bodyJson = "{"+jsonString.substring(jsonString.indexOf("\"include\""));
        bodyJson = bodyJson.substring(0, bodyJson.indexOf("]")+1)+"}";

        OkHttpClient client = new OkHttpClient();

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create( bodyJson, mediaType);
        Request request = new Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts/"+accountID+"/access/groups/"+groupID)
                .put(body)
                .addHeader("authorization", "Bearer "+apiToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("NetworkError", "Request failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    if (response.body() != null) {
                        successfulUpdate();
                    }
                } else {
                    Log.e("NetworkError", "Unexpected code " + response);
                }

                response.close();
            }
        });
    }

    private void successfulUpdate() {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Added IP successfully", Toast.LENGTH_SHORT).show());
    }

    private void getPublicIP() {
        Thread thread = new Thread(() -> {
            try {
                URL url = new URL("https://api64.ipify.org");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0"); // Set a User-Agent to avoid HTTP 403 Forbidden error

                try (Scanner s = new Scanner(connection.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                    publicIp.set(s.next());
                }

                runOnUiThread(() -> ipEditText.setText(publicIp.get()));

                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }
}

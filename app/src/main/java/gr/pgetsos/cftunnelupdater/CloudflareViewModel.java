package gr.pgetsos.cftunnelupdater;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.util.List;

public class CloudflareViewModel extends ViewModel {

	private final CloudflareApiHelper cloudflareApiHelper;
	private final MutableLiveData<List<String>> cloudflareIpsLiveData = new MutableLiveData<>();
	private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>();
	private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();

	private boolean hasFetchedOnce = false;
	private String currentAccountId;
	private String currentGroupId;
	private String currentApiToken;


	public CloudflareViewModel() {
		this.cloudflareApiHelper = new CloudflareApiHelper();
	}

	public LiveData<List<String>> getCloudflareIpsLiveData() {
		return cloudflareIpsLiveData;
	}

	public LiveData<Boolean> getIsLoadingLiveData() {
		return isLoadingLiveData;
	}

	public LiveData<String> getErrorLiveData() {
		return errorLiveData;
	}

	public void fetchIps(String accountID, String groupID, String apiToken) {
		if (hasFetchedOnce &&
				accountID.equals(currentAccountId) &&
				groupID.equals(currentGroupId) &&
				apiToken.equals(currentApiToken) &&
				cloudflareIpsLiveData.getValue() != null) {
			return;
		}

		this.currentAccountId = accountID;
		this.currentGroupId = groupID;
		this.currentApiToken = apiToken;

		isLoadingLiveData.postValue(true);
		cloudflareApiHelper.fetchIpsFromCloudflare(accountID, groupID, apiToken, new CloudflareApiHelper.ApiCallback<>() {
			@Override
			public void onSuccess(List<String> ips) {
				cloudflareIpsLiveData.postValue(ips);
				isLoadingLiveData.postValue(false);
				hasFetchedOnce = true;
			}

			@Override
			public void onError(Exception e) {
				errorLiveData.postValue("Failed to fetch IPs: " + e.getMessage());
				isLoadingLiveData.postValue(false);
				// ipListLiveData.postValue(null);
			}
		});
	}

	public void refreshIps() {
		if (currentAccountId != null && currentGroupId != null && currentApiToken != null) {
			hasFetchedOnce = false;
			fetchIps(currentAccountId, currentGroupId, currentApiToken);
		}
	}
}

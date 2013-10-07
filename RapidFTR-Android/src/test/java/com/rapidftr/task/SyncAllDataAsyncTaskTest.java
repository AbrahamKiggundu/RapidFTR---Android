package com.rapidftr.task;

import android.app.Notification;
import android.app.NotificationManager;
import android.view.Menu;
import android.view.MenuItem;
import com.rapidftr.CustomTestRunner;
import com.rapidftr.R;
import com.rapidftr.RapidFtrApplication;
import com.rapidftr.activity.RapidFtrActivity;
import com.rapidftr.model.Child;
import com.rapidftr.model.User;
import com.rapidftr.repository.ChildRepository;
import com.rapidftr.service.ChildService;
import com.rapidftr.service.FormService;
import com.rapidftr.utils.http.FluentRequest;
import com.xtremelabs.robolectric.Robolectric;
import com.xtremelabs.robolectric.shadows.ShadowToast;
import org.apache.http.HttpException;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Matchers;
import org.mockito.Mock;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(CustomTestRunner.class)
public class SyncAllDataAsyncTaskTest {

    @Mock private FormService formService;
    @Mock private ChildService childService;
    @Mock private ChildRepository childRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private RapidFtrActivity rapidFtrActivity;
    @Mock private NotificationManager notificationManager;
    @Mock private Menu menu;
    @Mock private MenuItem syncAll;
    @Mock private MenuItem cancelSyncAll;
    @Mock private User currentUser;
    private SyncAllDataAsyncTask syncTask;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        doReturn(syncAll).when(menu).getItem(0);
        doReturn(cancelSyncAll).when(menu).getItem(1);
        doReturn(menu).when(rapidFtrActivity).getMenu();

        given(rapidFtrActivity.getSystemService(Matchers.<String>any())).willReturn(notificationManager);

        syncTask = new SyncAllDataAsyncTask<Child>(formService, childService, childRepository, currentUser);
    }

    @Test
    public void shouldSyncFormsAndChildren() throws Exception {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        given(childRepository.toBeSynced()).willReturn(newArrayList(child1, child2));
        syncTask.setContext(rapidFtrActivity);

        syncTask.execute();
        verify(formService).getPublishedFormSections();
        verify(childService).sync(child1, currentUser);
        verify(childService).sync(child2, currentUser);
    }

    @Test
    public void shouldNotSyncFormsIfTaskIsCancelled() throws Exception {
        syncTask.setContext(rapidFtrActivity);
        syncTask = spy(syncTask);
        doReturn(true).when(syncTask).isCancelled();

        syncTask.doInBackground();

        verify(formService, never()).getPublishedFormSections();
    }

    @Test
    public void shouldNotSyncChildrenIfCancelled() throws Exception {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        given(childRepository.toBeSynced()).willReturn(newArrayList(child1, child2));

        syncTask.setContext(rapidFtrActivity);
        syncTask = spy(syncTask);
        doReturn(true).when(syncTask).isCancelled();

        syncTask.onPreExecute();
        syncTask.doInBackground();
        verify(childService, never()).sync(child1, currentUser);
        verify(childService, never()).sync(child2, currentUser);
    }

    @Test
    public void shouldNotGetIncomingChildrenFromServerIfCancelled() throws Exception {
        syncTask.setContext(rapidFtrActivity);
        HashMap<String, String> repositoryIDRevs = createRepositoryIdRevMap();

        given(childService.getIdsToDownload()).willReturn(Arrays.asList("asd97"));
        given(childRepository.getAllIdsAndRevs()).willReturn(repositoryIDRevs);

        syncTask = spy(syncTask);
        doReturn(true).when(syncTask).isCancelled();

        syncTask.onPreExecute();
        syncTask.doInBackground();

        verify(childService).getRecord(any(String.class));
        verify(childRepository, never()).createOrUpdate((Child) any());
        verify(childService, never()).setMedia((Child) any());
    }

    @Test
    public void shouldCreateOrUpdateExistingChild() throws Exception {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        HashMap<String, String> repositoryIDRevs = createRepositoryIdRevMap();

        given(childService.getIdsToDownload()).willReturn(Arrays.asList("qwerty0987","abcd1234"));
        given(childRepository.getAllIdsAndRevs()).willReturn(repositoryIDRevs);
        given(child1.getUniqueId()).willReturn("1234");
        given(child2.getUniqueId()).willReturn("5678");

        given(childService.getRecord("qwerty0987")).willReturn(child1);
        given(childService.getRecord("abcd1234")).willReturn(child2);

        given(childRepository.exists("1234")).willReturn(true);
        given(childRepository.exists("5678")).willReturn(false);

        syncTask.setContext(rapidFtrActivity);
        syncTask.execute();

        verify(childService).getRecord("qwerty0987");
        verify(childRepository).update(child1);
        verify(childRepository).createOrUpdate(child2);
    }

    @Test
    public void shouldToggleMenuOnPreExecute(){
        syncTask.setContext(rapidFtrActivity);

        syncTask.onPreExecute();

        verify(syncAll).setVisible(false);
        verify(cancelSyncAll).setVisible(true);
    }

    @Test
    public void shouldToggleMenuOnCancelAndOnPostExecute(){
        syncTask.setContext(rapidFtrActivity);

        syncTask.onPreExecute();

        syncTask.onCancelled();
        verify(syncAll).setVisible(true);
        verify(cancelSyncAll).setVisible(false);

        syncTask.onPreExecute();
        verify(syncAll).setVisible(true);
        verify(cancelSyncAll).setVisible(false);
    }

    @Test
    public void shouldNotCallSetProgressAndNotifyIfCancelled(){
        syncTask.setContext(rapidFtrActivity);
        syncTask = spy(syncTask);

        doReturn(true).when(syncTask).isCancelled();

        syncTask.onPreExecute();
        verify(notificationManager, never()).notify(anyInt(), (Notification) anyObject());
    }

	@Test
	public void shouldShowSessionTimeoutMessage() throws JSONException, IOException {
		Robolectric.getFakeHttpLayer().setDefaultHttpResponse(401, "Unauthorized");
		given(rapidFtrActivity.getString(R.string.session_timeout)).willReturn("Your session is timed out");
		syncTask.recordService = new ChildService(RapidFtrApplication.getApplicationInstance(), childRepository, new FluentRequest());
		syncTask.setContext(rapidFtrActivity);
		syncTask.execute();

		assertThat(ShadowToast.getTextOfLatestToast(), equalTo("Your session is timed out"));
	}

    @Test
    public void shouldCompareAndRetrieveIdsToBeDownloadedFromServer() throws JSONException, IOException, HttpException {
        Child child1 = mock(Child.class);
        Child child2 = mock(Child.class);
        given(childRepository.toBeSynced()).willReturn(newArrayList(child1, child2));
        given(childService.getIdsToDownload()).willReturn(Arrays.asList("qwerty0987", "abcd1234"));
        given(childService.getRecord("qwerty0987")).willReturn(mock(Child.class));
        given(childService.getRecord("abcd1234")).willReturn(mock(Child.class));

        syncTask.setContext(rapidFtrActivity);
        syncTask.execute();

        verify(formService).getPublishedFormSections();
        verify(childService).sync(child1, currentUser);
        verify(childService).sync(child2, currentUser);
        verify(childService).getIdsToDownload();
        verify(childService).getRecord("qwerty0987");
        verify(childService).getRecord("abcd1234");
    }

    private HashMap<String, String> createRepositoryIdRevMap() {
        HashMap<String, String> repositoryIDRevs = new HashMap<String, String>();
        repositoryIDRevs.put("abcd1234", "1-zxy321");
        repositoryIDRevs.put("abcd5678", "2-zxy765");
        repositoryIDRevs.put("abcd7689", "3-cdsf76");
        return repositoryIDRevs;
    }
}

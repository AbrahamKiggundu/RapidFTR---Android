package com.rapidftr.activity;

import android.widget.ListView;
import android.widget.TextView;
import com.google.inject.Injector;
import com.rapidftr.CustomTestRunner;
import com.rapidftr.R;
import com.rapidftr.model.Child;
import com.rapidftr.repository.ChildRepository;
import com.rapidftr.utils.SpyActivityController;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.util.ActivityController;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(CustomTestRunner.class)
public class SearchActivityTest {
    private ActivityController<SearchActivity> activityController;
    protected SearchActivity activity;

    @Mock
    private ChildRepository childRepository;

    @Before
    public void setUp() {
        initMocks(this);
        activityController = SpyActivityController.of(SearchActivity.class);
        activity = activityController.attach().get();

        Injector mockInjector = mock(Injector.class);
        doReturn(mockInjector).when(activity).getInjector();
        doReturn(childRepository).when(mockInjector).getInstance(ChildRepository.class);
    }

    @Test
    public void shouldListChildrenForSearchedString() throws JSONException {
        List<Child> searchResults = new ArrayList<Child>();
        searchResults.add(new Child("id1", "user1", "{ \"name\" : \"child1\", \"test2\" : 0, \"test3\" : [ \"1\", 2, \"3\" ] }"));
        String searchString = "Hild";
        when(childRepository.getMatchingChildren(searchString)).thenReturn(searchResults);

        activityController.create();
        TextView textView = (TextView) activity.findViewById(R.id.search_text);
        textView.setText(searchString);
        activity.findViewById(R.id.search_btn).performClick();
        ListView listView = (ListView) activity.findViewById(R.id.child_list);
        assertNull(listView.getEmptyView());
        assertNotNull(listView.getItemAtPosition(0));
    }

    @Test
    public void shouldShowEmptyViewForNoSearchResults() throws JSONException {
        List<Child> searchResults = new ArrayList<Child>();
        String searchString = "Hild";
        when(childRepository.getMatchingChildren(searchString)).thenReturn(searchResults);

        activityController.create();
        TextView textView = (TextView) activity.findViewById(R.id.search_text);
        textView.setText(searchString);
        activity.findViewById(R.id.search_btn).performClick();
        ListView listView = (ListView) activity.findViewById(R.id.child_list);
        assertNotNull(listView.getEmptyView());
    }

    @Test
    public void shouldReturnEmptyListForNoSearchString() throws JSONException {
        String searchString = " ";
        activityController.create();
        TextView textView = (TextView) activity.findViewById(R.id.search_text);
        textView.setText(searchString);
        activity.findViewById(R.id.search_btn).performClick();
        ListView listView = (ListView) activity.findViewById(R.id.child_list);
        verify(childRepository,never()).getMatchingChildren(searchString);
        assertNotNull(listView.getEmptyView());
    }

}

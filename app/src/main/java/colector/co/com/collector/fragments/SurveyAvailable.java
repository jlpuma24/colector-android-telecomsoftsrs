package colector.co.com.collector.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import colector.co.com.collector.R;
import colector.co.com.collector.SurveyActivity;
import colector.co.com.collector.adapters.SurveyAdapter;
import colector.co.com.collector.database.DatabaseHelper;
import colector.co.com.collector.listeners.OnDataBaseSave;
import colector.co.com.collector.listeners.OnUploadSurvey;
import colector.co.com.collector.model.Survey;
import colector.co.com.collector.model.request.SendSurveyRequest;
import colector.co.com.collector.model.response.SendSurveyResponse;
import colector.co.com.collector.network.BusProvider;
import colector.co.com.collector.session.AppSession;
import colector.co.com.collector.settings.AppSettings;


public class SurveyAvailable extends Fragment implements OnUploadSurvey, OnDataBaseSave {

    @BindView(R.id.list_items)
    ListView list;
    @BindView(R.id.loading)
    View loading;
    @BindView(R.id.coordinator)
    CoordinatorLayout coordinatorLayout;

    private String idTabs;
    private ArrayList<Survey> toPrint;
    private Bus mBus = BusProvider.getBus();
    private Survey surveyToUpload;
    private SurveyAdapter adapter;

    @Override
    public void onStart() {
        mBus.register(this);
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mBus.unregister(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_survey_available, container, false);
        ButterKnife.bind(this, v);
        idTabs = this.getTag();
        loading.setVisibility(View.VISIBLE);
        setupTabs();
        fillList();
        loading.setVisibility(View.GONE);
        return v;
    }

    private void setupTabs() {
        if (idTabs.equals(AppSettings.TAB_ID_AVAILABLE_SURVEY)) {
            AppSession.getInstance().cleanSurveyAvailable();
            toPrint = new ArrayList<>(AppSession.getInstance().getSurveyAvailable());


        } else if (idTabs.equals(AppSettings.TAB_ID_UPLOADED_SURVEY)) {
            toPrint = DatabaseHelper.getInstance().getSurveysUploaded(
                    new ArrayList<>(AppSession.getInstance().getSurveyAvailable()));


        } else if (idTabs.equals(AppSettings.TAB_ID_DONE_SURVEY)) {
            toPrint = DatabaseHelper.getInstance().getSurveysDone(
                    new ArrayList<>(AppSession.getInstance().getSurveyAvailable()));
        }
    }


    private void fillList() {
        adapter = new SurveyAdapter(getActivity(), toPrint, idTabs, this);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppSession.getInstance().setCurrentSurvey(toPrint.get(position), AppSettings.SURVEY_SELECTED_NEW);
                Intent intent = new Intent(getContext(), SurveyActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onUploadClicked(Survey survey) {
        surveyToUpload = survey;
        loading.setVisibility(View.VISIBLE);
        SendSurveyRequest uploadSurvey = new SendSurveyRequest(survey);
        mBus.post(uploadSurvey);
    }

    @Subscribe
    public void onSuccessUploadSurvey(SendSurveyResponse response) {
        Snackbar snack = Snackbar.make(coordinatorLayout, response.getResponseDescription(), Snackbar.LENGTH_LONG);
        ((TextView) (snack.getView().findViewById(android.support.design.R.id.snackbar_text))).setTextColor(Color.WHITE);
        snack.show();
        uploadSurveySave();
    }

    /**
     * After upload the survey to remote update local database
     */
    private void uploadSurveySave() {
        DatabaseHelper.getInstance().updateRealmSurveySave(surveyToUpload.getInstanceId(), this);
        adapter.getItems().remove(this.surveyToUpload);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onSuccess() {
        loading.setVisibility(View.GONE);
    }

    @Override
    public void onError() {
        loading.setVisibility(View.GONE);
    }
}

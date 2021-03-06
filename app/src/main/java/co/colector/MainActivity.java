package co.colector;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTabHost;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import co.colector.adapters.SurveyAdapter;
import co.colector.database.DatabaseHelper;
import co.colector.fragments.SurveyAvailable;
import co.colector.helpers.PreferencesManager;
import co.colector.listeners.OnDataBaseSave;
import co.colector.listeners.OnUploadSurvey;
import co.colector.model.ImageRequest;
import co.colector.model.ImageResponse;
import co.colector.model.Survey;
import co.colector.model.request.GetSurveysRequest;
import co.colector.model.request.SendSurveyRequest;
import co.colector.model.response.GetSurveysResponse;
import co.colector.model.response.SendSurveyResponse;
import co.colector.network.BusProvider;
import co.colector.session.AppSession;
import co.colector.settings.AppSettings;
import co.colector.utils.Utilities;

public class MainActivity extends AppCompatActivity implements OnDataBaseSave, OnUploadSurvey {

    private FragmentTabHost mTabHost;

    @BindView(R.id.toolbar)
    Toolbar mToolbar;

    private ArrayList<Survey> surveysDone = new ArrayList<>();
    private Bus mBus = BusProvider.getBus();
    private ProgressDialog progressDialog;
    private Survey surveyToUpload;
    private SurveyAdapter adapter;
    private ArrayList<ImageRequest> answersWithImages;
    private ImageRequest uploadingImage;
    private boolean itsIncompleteDownload = false;
    private int surveysToUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.sync_data));
        progressDialog.setCancelable(false);
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup(this, getSupportFragmentManager(), android.R.id.tabcontent);
        setUpToolbar();
        buildTabs();
    }

    private void setUpToolbar() {
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
            getSupportActionBar().setTitle("Colector");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mSyncronize:
                new AlertDialog.Builder(MainActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.sync_all_data)
                        .setPositiveButton(getString(R.string.common_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            if (Utilities.isNetworkConnected(MainActivity.this)) {
                                surveysDone = DatabaseHelper.getInstance().getSurveysDone(
                                        new ArrayList<>(AppSession.getInstance().getSurveyAvailable()));
                                itsIncompleteDownload = false;
                                surveysToUpload = surveysDone.size();
                                uploadSurveyDone();
                            }
                            else {
                                Toast.makeText(MainActivity.this, getString(R.string.common_internet_not_available),Toast.LENGTH_SHORT).show();
                            }
                            }
                        })
                        .setNegativeButton(getString(R.string.common_cancel), null)
                        .show();

                break;

            case R.id.logout:
                PreferencesManager.getInstance().logoutAccount();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void buildTabs() {
        mTabHost.addTab(
                mTabHost.newTabSpec(AppSettings.TAB_ID_AVAILABLE_SURVEY).setIndicator(getResources().getString(R.string.survey_surveys), null),
                SurveyAvailable.class, null);
        mTabHost.addTab(
                mTabHost.newTabSpec(AppSettings.TAB_ID_DONE_SURVEY).setIndicator(getResources().getString(R.string.survey_survyes_done), null),
                SurveyAvailable.class, null);
    }

    @Override
    public void onUploadClicked(Survey survey, SurveyAdapter adapter) {
        this.adapter = adapter;
        uploadSingleRemoteSurvey(survey);
    }

    /**
     * Method subscribed to change on SendSurveyResponses
     * If the upload if ok and there are images to upload, upload the images
     *
     * @param response to watch change
     */
    @Subscribe
    public void onSuccessUploadSurvey(SendSurveyResponse response) {
        if (response.getResponseCode().equals(200l)) {
            itsIncompleteDownload = false;
            getImagesToUpload();
        }
        else if (response.getResponseCode().equals(202l)){
            itsIncompleteDownload = true;
            uploadSurveySave();
        }
        else {
            showToastError(response.getResponseDescription());
        }
    }

    /**
     * Get the answer related with Images
     * If there is uploaded to the web service otherwise update local database
     */
    private void getImagesToUpload() {
        answersWithImages = new ArrayList<>();
        answersWithImages = ImageRequest.getFileSurveys(surveyToUpload);
        if (!answersWithImages.isEmpty()) uploadImages();
        else uploadSurveySave();
    }

    /**
     * Upload Images with web service
     */
    private void uploadImages() {
        uploadingImage = answersWithImages.get(0);
        mBus.post(uploadingImage);
    }

    /**
     * Method subscribed to the ImageResponse change
     * If there are no more images update data base
     *
     * @param response to watch change
     */
    @Subscribe
    public void onImageUploaded(ImageResponse response) {
        if (response.getResponseCode().equals("200")) {
            answersWithImages.remove(uploadingImage);
            if (answersWithImages.isEmpty()) {
                uploadSurveySave();
            } else uploadImages();
        } else showToastError(response.getResponseDescription());

    }

    /**
     * After upload the survey to remote update local database
     */
    private void uploadSurveySave() {
        if (!itsIncompleteDownload)
            DatabaseHelper.getInstance().updateRealmSurveySave(surveyToUpload.getInstanceId(), this);
        else
            onSuccess();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBus.register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mBus.unregister(this);
    }

    @Override
    public void onSuccess() {
        progressDialog.hide();
        surveysToUpload--;
        boolean isInAdapter = false;
        if (adapter != null) {
            isInAdapter = true;
            if (!itsIncompleteDownload) {
                surveyToUpload.setUploaded(true);
            }
            adapter.notifyDataSetChanged();
        }
        if (!surveysDone.isEmpty() && surveysToUpload != 0) {
            uploadSurveyDone();
        }
        else {
            if (!isInAdapter)
                uploadSurveysAvailable();
        }
    }

    @Override
    public void onError() {
        progressDialog.hide();
    }

    private void showToastError(String error) {
        progressDialog.hide();
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }

    private void uploadSurveyDone() {
        if (surveysToUpload != 0) {
            surveysDone = DatabaseHelper.getInstance().getSurveysDone(
                    new ArrayList<>(AppSession.getInstance().getSurveyAvailable()));
            if (!surveysDone.isEmpty()) {
                uploadSurveyRemote(surveysDone.get(surveysToUpload-1));
            } else uploadSurveysAvailable();
        }
        else {
            uploadSurveysAvailable();
        }
    }

    private void uploadSurveyRemote(Survey survey) {
        surveyToUpload = survey;
        progressDialog.show();
        SendSurveyRequest uploadSurvey = new SendSurveyRequest(survey);
        mBus.post(uploadSurvey);
    }

    private void uploadSingleRemoteSurvey(Survey survey){
        surveysToUpload = 1;
        surveyToUpload = survey;
        progressDialog.show();
        SendSurveyRequest uploadSurvey = new SendSurveyRequest(survey);
        mBus.post(uploadSurvey);
    }

    private void uploadSurveysAvailable() {
        progressDialog.show();
        GetSurveysRequest toSend = new GetSurveysRequest(AppSession.getInstance().getUser()
                .getColector_id());
        mBus.post(toSend);
    }

    @Subscribe
    public void onSuccessSurveysResponse(GetSurveysResponse response) {
        AppSession.getInstance().setSurveyAvailable(response.getResponseData());
        DatabaseHelper.getInstance().addSurveyAvailable(response.getResponseData(),
                new OnDataBaseSave() {
                    @Override
                    public void onSuccess() {
                        progressDialog.hide();
                        reLaunchActivity();
                    }

                    @Override
                    public void onError() {
                        progressDialog.hide();
                    }
                });
    }

    private void reLaunchActivity() {
        Toast.makeText(this, R.string.survey_save_send_ok, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
        finish();
    }

}
package co.colector.views;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import co.colector.R;
import co.colector.helpers.PreferencesManager;
import co.colector.listeners.CallDialogListener;
import co.colector.model.IdOptionValue;
import co.colector.model.IdValue;
import co.colector.model.Question;
import co.colector.model.AnswerValue;
import co.colector.model.QuestionVisibilityRules;
import io.realm.RealmList;

/**
 * @author Gabriel Rodriguez
 * @version 1.0
 */
public class MultipleItemViewContainer extends LinearLayout {

    @BindView(R.id.container)
    LinearLayout container;
    @BindView(R.id.label)
    TextView label;
    @BindView(R.id.show)
    TextView show;
    @BindView(R.id.collapse)
    TextView collapse;
    @BindView(R.id.editTextResults)
    EditText editTextResults;
    private Long id;
    private String validation;
    private int mType;
    private Activity activity;

    private ArrayList<IdOptionValue> options = new ArrayList<>();
    private boolean required = false;
    private String finalResult = "";

    private ArrayList<String> selectedResults = new ArrayList<>();

    private Question question;
    private SectionItemView sectionItemView;

    public Question getQuestion(){
        return question;
    }

    private boolean isGoneByRules;
    private String deafultValues;
    public RealmList<QuestionVisibilityRules> getVisibilityRules() {
        return visibilityRules;
    }

    private RealmList<QuestionVisibilityRules> visibilityRules;

    public MultipleItemViewContainer(Context context, SectionItemView sectionItemView) {
        super(context);
        this.sectionItemView = sectionItemView;
        init(context);
    }

    private void init(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.multiple_item_view_container, this, true);
        ButterKnife.bind(this, view);
        this.activity = (Activity) context;
    }

    public void bind(ArrayList<IdOptionValue> response, Question question,
                     @Nullable List<String> previewDefault) {
        if (response.isEmpty()) return;
        if (Boolean.parseBoolean(question.getSoloLectura()))
            container.setEnabled(false);
        this.mType = question.getType();
        this.id = question.getId();
        this.validation = question.getValidacion();
        required = question.getRequerido();
        this.options = response;
        container.setVisibility(!question.getValorVisibility().isEmpty() ? View.GONE : View.VISIBLE);
        isGoneByRules = question.getValorVisibility().isEmpty();
        visibilityRules = question.getValorVisibility();
        this.question = question;
        setOnClickListeners(question.getName(), response);
        //Bind the title
        if (required) {
            label.setText(getContext().getString(R.string.required_field, question.getName()));
        } else label.setText(question.getName());

        //Adding extra text info, to notify the user the action to make.
        label.setText(label.getText());
        //Bind the show and hide buttons
        bindShowButton();
        bindCollapseButton(collapse);
        if (previewDefault != null && ! previewDefault.isEmpty()) {
            bindDefaultSelected(previewDefault);
        }
        else {
            deafultValues = !question.getDefecto().isEmpty() ? question.getDefecto() : "";
            editTextResults.setText(deafultValues);
        }
        if (question.getoculto()) this.setVisibility(GONE);
    }

    public void fillData(List<String> results) {
        // Bind the items
        container.removeAllViews();
        if (!results.isEmpty()) {
            selectedResults = new ArrayList<>(results);
            String resultToDisplay = "";
            for (String result : selectedResults) {
                finalResult = finalResult.isEmpty() ? result : finalResult + ", " + result;
                resultToDisplay = resultToDisplay.isEmpty() ? result : resultToDisplay + ", " + result;
            }
            String[] wordsToDelete = finalResult.split(",");
            ArrayList<String> wordsToRealDelete = new ArrayList<String>();

            for (int i = 0; i < wordsToDelete.length; i++) {
                if (!resultToDisplay.contains(wordsToDelete[i]))
                    wordsToRealDelete.add(wordsToDelete[i]);
            }
            String totalResults = PreferencesManager.getInstance().getPrefs().getString(PreferencesManager.OPTIONS_SELECTEDS, "");
            if (totalResults.isEmpty()) {
                PreferencesManager.getInstance().storeOptionsSelecteds(resultToDisplay);
            } else {
                for (String s : wordsToRealDelete)
                    totalResults = totalResults.replace(s, "");
                PreferencesManager.getInstance().storeOptionsSelecteds(resultToDisplay + totalResults);
            }
            finalResult = resultToDisplay;
            editTextResults.setText("");
            editTextResults.setText(resultToDisplay);
        }
    }

    private void setOnClickListeners(final String title, final List<IdOptionValue> response) {
        final CallDialogListener listener = (CallDialogListener) activity;
        editTextResults.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.callDialog(title, response, MultipleItemViewContainer.this, 1, sectionItemView, deafultValues);
            }
        });
        editTextResults.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus)
                    listener.callDialog(title, response, MultipleItemViewContainer.this, 1, sectionItemView, deafultValues);
            }
        });
    }

    private void bindShowButton() {
        show.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                show.setText(getContext().getString(R.string.hide));
                bindCollapseButton(show);
                collapse.setVisibility(VISIBLE);
                container.setVisibility(VISIBLE);
            }
        });
    }

    private void bindCollapseButton(View view) {
        view.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                show.setText(getContext().getString(R.string.show));
                bindShowButton();
                collapse.setVisibility(GONE);
                container.setVisibility(GONE);
            }
        });
    }

    public boolean validateFields() {
        if (!required) return true;
        if (!selectedResults.isEmpty()) {
            label.setTextColor(ContextCompat.getColor(getContext(), R.color.text_color));
            return true;
        }
        label.setTextColor(ContextCompat.getColor(getContext(), R.color.red_label_error_color));
        return false;
    }

    public IdValue getResponses() {
        RealmList<AnswerValue> responses = new RealmList<>();
        if (!selectedResults.isEmpty())
            for (String item : selectedResults)
                responses.add(getSelectedId(item));
        return new IdValue(id, responses, validation, mType);
    }

    private AnswerValue getSelectedId(String selectedValue) {
        for (IdOptionValue option : options) {
            if (option.getValue().equals(selectedValue)) {
                return new AnswerValue(String.valueOf(option.getId()));
            }
        }
        return new AnswerValue(String.valueOf(selectedValue));
    }

    private void bindDefaultSelected(List<String> previewDefault) {
        getSelectedValues(previewDefault);
    }

    private void getSelectedValues(List<String> previewDefault) {
        ArrayList<String> values = new ArrayList<>();
        for (String value : previewDefault) {
            for (IdOptionValue option : options) {
                if (String.valueOf(option.getId()).equals(value)) values.add(option.getValue());
            }
        }
        fillData(values);
    }

    public void setVisibilityLabel(boolean isVisible) {
        container.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        container.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }
}
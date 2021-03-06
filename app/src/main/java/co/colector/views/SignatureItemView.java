package co.colector.views;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import butterknife.BindView;
import butterknife.ButterKnife;
import co.colector.ColectorApplication;
import co.colector.R;

/**
 * @author Gabriel Rodriguez
 * @version 1.0
 */

public class SignatureItemView extends FrameLayout {

    @BindView(R.id.photo)
    ImageView photo;
    public String url;

    public SignatureItemView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.signature_item_view, this, true);
        ButterKnife.bind(this, view);
    }

    public void bind(String url) {
        this.url = url;
        ColectorApplication.getInstance().getGlideInstance().load(url)
                .override(600, 600).into(photo);
    }
}

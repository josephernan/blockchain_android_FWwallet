/*
 * Copyright 2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ivan.fgwallet.schildbach.wallet.ui.preference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ivan.fgwallet.schildbach.wallet.ui.DialogBuilder;
import com.ivan.fgwallet.schildbach.wallet.util.Qr;
import com.ivan.fgwallet.R;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.ShareCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

/**
 * @author Andreas, soos
 */
public class ExtendedPublicKeyFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = ExtendedPublicKeyFragment.class.getName();

    private static final String KEY_XPUB = "xpub";

    private static final Logger log = LoggerFactory.getLogger(ExtendedPublicKeyFragment.class);

    public static void show(final FragmentManager fm, final CharSequence xpub) {
        instance(xpub).show(fm, FRAGMENT_TAG);
    }

    private static ExtendedPublicKeyFragment instance(final CharSequence xpub) {
        final ExtendedPublicKeyFragment fragment = new ExtendedPublicKeyFragment();

        final Bundle args = new Bundle();
        args.putCharSequence(KEY_XPUB, xpub);
        fragment.setArguments(args);

        return fragment;
    }

    private Activity activity;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final String xpub = getArguments().getCharSequence(KEY_XPUB).toString();

        final View view = LayoutInflater.from(activity).inflate(R.layout.extended_public_key_dialog, null);

        final BitmapDrawable bitmap = new BitmapDrawable(getResources(), Qr.bitmap(xpub));
        bitmap.setFilterBitmap(false);
        final ImageView imageView = (ImageView) view.findViewById(R.id.extended_public_key_dialog_image);
        imageView.setImageDrawable(bitmap);

        final DialogBuilder dialog = new DialogBuilder(activity);
        dialog.setView(view);
        dialog.setNegativeButton(R.string.button_dismiss, null);
        dialog.setPositiveButton(R.string.button_share, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                final ShareCompat.IntentBuilder builder = ShareCompat.IntentBuilder.from(activity);
                builder.setType("text/plain");
                builder.setText(xpub);
                builder.setSubject(getString(R.string.extended_public_key_fragment_title));
                builder.setChooserTitle(R.string.extended_public_key_fragment_share);
                builder.startChooser();
                log.info("xpub shared via intent: {}", xpub);
            }
        });

        return dialog.show();
    }
}

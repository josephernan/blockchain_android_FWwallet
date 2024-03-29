/*
 * Copyright 2014-2015 the original author or authors.
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

import java.util.Locale;

import org.bitcoinj.crypto.DeterministicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ivan.fgwallet.schildbach.wallet.Constants;
import com.ivan.fgwallet.WalletApplication;
import com.ivan.fgwallet.schildbach.wallet.ui.DialogBuilder;
import com.ivan.fgwallet.R;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

/**
 * @author Andreas, soos
 */
public final class DiagnosticsFragment extends PreferenceFragment {
    private Activity activity;
    private WalletApplication application;

    private static final String PREFS_KEY_INITIATE_RESET = "initiate_reset";
    private static final String PREFS_KEY_EXTENDED_PUBLIC_KEY = "extended_public_key";

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsFragment.class);

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
        this.application = (WalletApplication) activity.getApplication();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_diagnostics);
    }

    @Override
    public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference) {
        final String key = preference.getKey();

        if (PREFS_KEY_INITIATE_RESET.equals(key)) {
            handleInitiateReset();
            return true;
        } else if (PREFS_KEY_EXTENDED_PUBLIC_KEY.equals(key)) {
            handleExtendedPublicKey();
            return true;
        }

        return false;
    }

    private void handleInitiateReset() {
        final DialogBuilder dialog = new DialogBuilder(activity);
        dialog.setTitle(R.string.preferences_initiate_reset_title);
        dialog.setMessage(R.string.preferences_initiate_reset_dialog_message);
        dialog.setPositiveButton(R.string.preferences_initiate_reset_dialog_positive, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                log.info("manually initiated blockchain reset");

                application.resetBlockchain();
                activity.finish(); // TODO doesn't fully finish prefs on single pane layouts
            }
        });
        dialog.setNegativeButton(R.string.button_dismiss, null);
        dialog.show();
    }

    private void handleExtendedPublicKey() {
        final DeterministicKey extendedKey = application.getWallet().getWatchingKey();
        final String xpub = String.format(Locale.US, "%s?c=%d&h=bip32",
                extendedKey.serializePubB58(Constants.NETWORK_PARAMETERS), extendedKey.getCreationTimeSeconds());
        ExtendedPublicKeyFragment.show(getFragmentManager(), (CharSequence) xpub);
    }
}

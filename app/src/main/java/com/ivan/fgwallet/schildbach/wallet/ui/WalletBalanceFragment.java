/*
 * Copyright 2011-2015 the original author or authors.
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

package com.ivan.fgwallet.schildbach.wallet.ui;

import javax.annotation.Nullable;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.wallet.Wallet;

import com.ivan.fgwallet.schildbach.wallet.Configuration;
import com.ivan.fgwallet.schildbach.wallet.Constants;
import com.ivan.fgwallet.WalletApplication;
import com.ivan.fgwallet.schildbach.wallet.data.ExchangeRate;
import com.ivan.fgwallet.schildbach.wallet.data.ExchangeRatesLoader;
import com.ivan.fgwallet.schildbach.wallet.data.ExchangeRatesProvider;
import com.ivan.fgwallet.schildbach.wallet.service.BlockchainState;
import com.ivan.fgwallet.schildbach.wallet.service.BlockchainStateLoader;
import com.ivan.fgwallet.schildbach.wallet.ui.send.FeeCategory;
import com.ivan.fgwallet.schildbach.wallet.ui.send.SendCoinsActivity;
import com.ivan.fgwallet.R;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * @author Andreas, soos
 */
public final class WalletBalanceFragment extends Fragment {
    private WalletApplication application;
    private AbstractBindServiceActivity activity;
    private Configuration config;
    private Wallet wallet;
    private LoaderManager loaderManager;

    private View viewBalance;
    private CurrencyTextView viewBalanceBtc;
    private TextView viewBalanceWarning;
    private CurrencyTextView viewBalanceLocal;
    private TextView viewProgress;

    private boolean showLocalBalance;
    private boolean installedFromGooglePlay;

    @Nullable
    private Coin balance = null;
    @Nullable
    private ExchangeRate exchangeRate = null;
    @Nullable
    private BlockchainState blockchainState = null;

    private static final int ID_BALANCE_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;
    private static final int ID_BLOCKCHAIN_STATE_LOADER = 2;

    private static final long BLOCKCHAIN_UPTODATE_THRESHOLD_MS = DateUtils.HOUR_IN_MILLIS;
    private static final Coin SOME_BALANCE_THRESHOLD = Coin.COIN.divide(800);
    private static final Coin TOO_MUCH_BALANCE_THRESHOLD = Coin.COIN.divide(16);

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        this.activity = (AbstractBindServiceActivity) activity;
        this.application = (WalletApplication) activity.getApplication();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();
        this.loaderManager = getLoaderManager();

        showLocalBalance = getResources().getBoolean(R.bool.show_local_balance);
        installedFromGooglePlay = "com.android.vending"
                .equals(application.getPackageManager().getInstallerPackageName(application.getPackageName()));
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.wallet_balance_fragment, container, false);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final boolean showExchangeRatesOption = Constants.ENABLE_EXCHANGE_RATES
                && getResources().getBoolean(R.bool.show_exchange_rates_option);

        viewBalance = view.findViewById(R.id.wallet_balance);
        if (showExchangeRatesOption) {
            viewBalance.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    startActivity(new Intent(getActivity(), ExchangeRatesActivity.class));
                }
            });
        } else {
            viewBalance.setEnabled(false);
        }

        viewBalanceBtc = (CurrencyTextView) view.findViewById(R.id.wallet_balance_btc);
        viewBalanceBtc.setPrefixScaleX(0.9f);

        viewBalanceWarning = (TextView) view.findViewById(R.id.wallet_balance_warning);

        viewBalanceLocal = (CurrencyTextView) view.findViewById(R.id.wallet_balance_local);
        viewBalanceLocal.setInsignificantRelativeSize(1);
        viewBalanceLocal.setStrikeThru(Constants.TEST);

        viewProgress = (TextView) view.findViewById(R.id.wallet_balance_progress);
    }

    @Override
    public void onResume() {
        super.onResume();

        loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
        if (Constants.ENABLE_EXCHANGE_RATES)
            loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
        loaderManager.initLoader(ID_BLOCKCHAIN_STATE_LOADER, null, blockchainStateLoaderCallbacks);

        updateView();
    }

    @Override
    public void onPause() {
        loaderManager.destroyLoader(ID_BLOCKCHAIN_STATE_LOADER);
        if (Constants.ENABLE_EXCHANGE_RATES)
            loaderManager.destroyLoader(ID_RATE_LOADER);
        loaderManager.destroyLoader(ID_BALANCE_LOADER);

        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.wallet_balance_fragment_options, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        final boolean hasSomeBalance = balance != null && !balance.isLessThan(SOME_BALANCE_THRESHOLD);
        menu.findItem(R.id.wallet_balance_options_donate)
                .setVisible(Constants.DONATION_ADDRESS != null && (!installedFromGooglePlay || hasSomeBalance));

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case R.id.wallet_balance_options_donate:
            handleDonate();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void handleDonate() {
        SendCoinsActivity.startDonate(activity, null, FeeCategory.ECONOMIC, 0);
    }

    private void updateView() {
        if (!isAdded())
            return;

        final boolean showProgress;

        if (blockchainState != null && blockchainState.bestChainDate != null) {
            final long blockchainLag = System.currentTimeMillis() - blockchainState.bestChainDate.getTime();
            final boolean blockchainUptodate = blockchainLag < BLOCKCHAIN_UPTODATE_THRESHOLD_MS;
            final boolean noImpediments = blockchainState.impediments.isEmpty();

            showProgress = !(blockchainUptodate || !blockchainState.replaying);

            final String downloading = getString(noImpediments ? R.string.blockchain_state_progress_downloading
                    : R.string.blockchain_state_progress_stalled);

            if (blockchainLag < 2 * DateUtils.DAY_IN_MILLIS) {
                final long hours = blockchainLag / DateUtils.HOUR_IN_MILLIS;
                viewProgress.setText(getString(R.string.blockchain_state_progress_hours, downloading, hours));
            } else if (blockchainLag < 2 * DateUtils.WEEK_IN_MILLIS) {
                final long days = blockchainLag / DateUtils.DAY_IN_MILLIS;
                viewProgress.setText(getString(R.string.blockchain_state_progress_days, downloading, days));
            } else if (blockchainLag < 90 * DateUtils.DAY_IN_MILLIS) {
                final long weeks = blockchainLag / DateUtils.WEEK_IN_MILLIS;
                viewProgress.setText(getString(R.string.blockchain_state_progress_weeks, downloading, weeks));
            } else {
                final long months = blockchainLag / (30 * DateUtils.DAY_IN_MILLIS);
                viewProgress.setText(getString(R.string.blockchain_state_progress_months, downloading, months));
            }
        } else {
            showProgress = false;
        }

        if (!showProgress) {
            viewBalance.setVisibility(View.VISIBLE);

            if (!showLocalBalance)
                viewBalanceLocal.setVisibility(View.GONE);

            if (balance != null) {
                viewBalanceBtc.setVisibility(View.VISIBLE);
                viewBalanceBtc.setFormat(config.getFormat());
                viewBalanceBtc.setAmount(balance);

                if (showLocalBalance) {
                    if (exchangeRate != null) {
                        final Fiat localValue = exchangeRate.rate.coinToFiat(balance);
                        viewBalanceLocal.setVisibility(View.VISIBLE);
                        viewBalanceLocal.setFormat(Constants.LOCAL_FORMAT.code(0,
                                Constants.PREFIX_ALMOST_EQUAL_TO + exchangeRate.getCurrencyCode()));
                        viewBalanceLocal.setAmount(localValue);
                        viewBalanceLocal.setTextColor(getResources().getColor(R.color.fg_less_significant));
                    } else {
                        viewBalanceLocal.setVisibility(View.INVISIBLE);
                    }
                }
            } else {
                viewBalanceBtc.setVisibility(View.INVISIBLE);
            }

            if (balance != null && balance.isGreaterThan(TOO_MUCH_BALANCE_THRESHOLD)) {
                viewBalanceWarning.setVisibility(View.VISIBLE);
                viewBalanceWarning.setText(R.string.wallet_balance_fragment_too_much);
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                viewBalanceWarning.setVisibility(View.VISIBLE);
                viewBalanceWarning.setText(R.string.wallet_balance_fragment_insecure_device);
            } else {
                viewBalanceWarning.setVisibility(View.GONE);
            }

            viewProgress.setVisibility(View.GONE);
        } else {
            viewProgress.setVisibility(View.VISIBLE);
            viewBalance.setVisibility(View.INVISIBLE);
        }
    }

    private final LoaderCallbacks<BlockchainState> blockchainStateLoaderCallbacks = new LoaderManager.LoaderCallbacks<BlockchainState>() {
        @Override
        public Loader<BlockchainState> onCreateLoader(final int id, final Bundle args) {
            return new BlockchainStateLoader(activity);
        }

        @Override
        public void onLoadFinished(final Loader<BlockchainState> loader, final BlockchainState blockchainState) {
            WalletBalanceFragment.this.blockchainState = blockchainState;

            updateView();
        }

        @Override
        public void onLoaderReset(final Loader<BlockchainState> loader) {
        }
    };

    private final LoaderCallbacks<Coin> balanceLoaderCallbacks = new LoaderManager.LoaderCallbacks<Coin>() {
        @Override
        public Loader<Coin> onCreateLoader(final int id, final Bundle args) {
            return new WalletBalanceLoader(activity, wallet);
        }

        @Override
        public void onLoadFinished(final Loader<Coin> loader, final Coin balance) {
            WalletBalanceFragment.this.balance = balance;

            activity.invalidateOptionsMenu();
            updateView();
        }

        @Override
        public void onLoaderReset(final Loader<Coin> loader) {
        }
    };

    private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            return new ExchangeRatesLoader(activity, config);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null && data.getCount() > 0) {
                data.moveToFirst();
                exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
                updateView();
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };
}

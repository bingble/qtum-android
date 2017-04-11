package org.qtum.mromanovsky.qtum.ui.fragment.SendBaseFragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;

import org.qtum.mromanovsky.qtum.R;
import org.qtum.mromanovsky.qtum.dataprovider.RestAPI.gsonmodels.History.History;
import org.qtum.mromanovsky.qtum.dataprovider.RestAPI.gsonmodels.History.Vin;
import org.qtum.mromanovsky.qtum.dataprovider.RestAPI.gsonmodels.History.Vout;
import org.qtum.mromanovsky.qtum.dataprovider.RestAPI.gsonmodels.UnspentOutput;
import org.qtum.mromanovsky.qtum.dataprovider.TransactionListener;
import org.qtum.mromanovsky.qtum.dataprovider.UpdateService;
import org.qtum.mromanovsky.qtum.ui.activity.MainActivity.MainActivity;
import org.qtum.mromanovsky.qtum.ui.fragment.BaseFragment.BaseFragmentPresenterImpl;
import org.qtum.mromanovsky.qtum.ui.fragment.CurrencyFragment.CurrencyFragment;
import org.qtum.mromanovsky.qtum.ui.fragment.SendBaseFragment.QrCodeRecognitionFragment.QrCodeRecognitionFragment;

import java.math.BigDecimal;
import java.util.List;


class SendBaseFragmentPresenterImpl extends BaseFragmentPresenterImpl implements SendBaseFragmentPresenter {

    private SendBaseFragmentView mSendBaseFragmentView;
    private SendBaseFragmentInteractorImpl mSendBaseFragmentInteractor;
    private UpdateService mUpdateService;
    private Context mContext;


    SendBaseFragmentPresenterImpl(SendBaseFragmentView sendBaseFragmentView) {
        mSendBaseFragmentView = sendBaseFragmentView;
        mContext = getView().getContext();
        mSendBaseFragmentInteractor = new SendBaseFragmentInteractorImpl(mContext);
    }

    @Override
    public void onViewCreated() {
        super.onViewCreated();
        mUpdateService = ((MainActivity) getView().getFragmentActivity()).getUpdateService();
        mUpdateService.addTransactionListener(new TransactionListener() {
            @Override
            public void onNewHistory(History history) {
                calculateChangeInBalance(history,getInteractor().getAddresses());
                if(history.getChangeInBalance().doubleValue()<0){
                    getView().getFragmentActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateAvailableBalance();
                        }
                    });
                } else if(history.getBlockTime()!=null){
                    getView().getFragmentActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateAvailableBalance();
                        }
                    });
                }
            }

            @Override
            public boolean getVisibility() {
                return false;
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mUpdateService.removeTransactionListener();

        //TODO:unsubscribe rx
    }

    @Override
    public SendBaseFragmentView getView() {
        return mSendBaseFragmentView;
    }

    @Override
    public void onClickQrCode() {
        QrCodeRecognitionFragment qrCodeRecognitionFragment = QrCodeRecognitionFragment.newInstance();
        getView().hideKeyBoard();
        getView().openFragmentForResult(qrCodeRecognitionFragment);
    }

    private void updateAvailableBalance(){
        getView().setProgressBar();
        getInteractor().getUnspentOutputs(new SendBaseFragmentInteractorImpl.GetUnspentListCallBack() {
            @Override
            public void onSuccess(List<UnspentOutput> unspentOutputs) {
                BigDecimal balance = new BigDecimal("0");
                BigDecimal amount;
                for(UnspentOutput unspentOutput : unspentOutputs){
                    amount = new BigDecimal(String.valueOf(unspentOutput.getAmount()));
                    balance = balance.add(amount);
                }
                getView().updateAvailableBalance(balance.toString());
            }
        });
    }

    @Override
    public void initializeViews() {
        super.initializeViews();
        updateAvailableBalance();
    }

    public SendBaseFragmentInteractorImpl getInteractor() {
        return mSendBaseFragmentInteractor;
    }

    @Override
    public void isQrCodeRecognition(boolean isQrCodeRecognition) {

        if (isQrCodeRecognition) {
            QrCodeRecognitionFragment qrCodeRecognitionFragment = QrCodeRecognitionFragment.newInstance();
            getView().openFragmentForResult(qrCodeRecognitionFragment);

        }
    }

    @Override
    public void onResponse(String publicAddress, double amount) {
        getView().updateData(publicAddress, amount);
    }

    @Override
    public void onResponseError() {
        getView().errorRecognition();
    }

    @Override
    public void onCurrencyClick() {
        CurrencyFragment currencyFragment = CurrencyFragment.newInstance(true);
        getView().openFragment(currencyFragment);
    }

    @Override
    public void send(String[] sendInfo) {
        if (sendInfo[2].length() < 4) {
            getView().confirmError(getView().getContext().getString(R.string.pin_is_not_long_enough));
            return;
        } else {
            int intPassword = Integer.parseInt(sendInfo[2]);
            if (intPassword != getInteractor().getPassword()) {
                getView().confirmError(getView().getContext().getString(R.string.incorrect_pin));
                return;
            }
        }
        getView().clearError();
        getView().setProgressDialog("Sending");
        getInteractor().sendTx(sendInfo[0], sendInfo[1], new SendBaseFragmentInteractorImpl.SendTxCallBack() {
            @Override
            public void onSuccess() {
                getView().dismissProgressDialog();
                getView().setAlertDialog("Sent");
                (new Handler()).postDelayed(new Runnable() {
                    public void run() {
                        getView().dismissAlertDialog();
                    }
                }, 2000);
            }

            @Override
            public void onError(String error) {
                getView().dismissProgressDialog();
                getView().setAlertDialog(error);
                (new Handler()).postDelayed(new Runnable() {
                    public void run() {
                        getView().dismissAlertDialog();
                    }
                }, 2000);
            }
        });
    }

    private void calculateChangeInBalance(History history, List<String> addresses){
        BigDecimal changeInBalance = calculateVout(history,addresses).subtract(calculateVin(history,addresses));
        history.setChangeInBalance(changeInBalance);
    }

    private BigDecimal calculateVin(History history, List<String> addresses){
        BigDecimal totalVin = new BigDecimal("0.0");
        boolean equals = false;
        for(Vin vin : history.getVin()){
            for(String address : addresses){
                if(vin.getAddress().equals(address)){
                    vin.setOwnAddress(true);
                    equals = true;
                }
            }
        }
        if(equals){
            totalVin = history.getAmount();
        }
        return totalVin;
    }

    private BigDecimal calculateVout(History history, List<String> addresses){
        BigDecimal totalVout = new BigDecimal("0.0");
        for(Vout vout : history.getVout()){
            for(String address : addresses){
                if(vout.getAddress().equals(address)){
                    vout.setOwnAddress(true);
                    totalVout = totalVout.add(vout.getValue());
                }
            }
        }
        return totalVout;
    }

}

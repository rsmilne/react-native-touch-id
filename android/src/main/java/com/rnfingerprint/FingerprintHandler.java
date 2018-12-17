package com.rnfingerprint;

import android.annotation.TargetApi;
import android.os.Build;
import android.content.Context;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v4.os.CancellationSignal;

@TargetApi(Build.VERSION_CODES.M)
public class FingerprintHandler extends FingerprintManagerCompat.AuthenticationCallback {

    private CancellationSignal cancellationSignal;
    private boolean selfCancelled;

    private final FingerprintManagerCompat mFingerprintManager;
    private final Callback mCallback;

    public FingerprintHandler(Context context, Callback callback) {
        mFingerprintManager = context.getSystemService(FingerprintManagerCompat.class);
        mCallback = callback;
    }

    public void startAuth(FingerprintManagerCompat.CryptoObject cryptoObject) {
        cancellationSignal = new CancellationSignal();
        selfCancelled = false;
        mFingerprintManager.authenticate(cryptoObject, 0, cancellationSignal, this, null);
    }

    public void endAuth() {
        cancelAuthenticationSignal();
    }

    @Override
    public void onAuthenticationError(int errCode,
                                      CharSequence errString) {
        if (!selfCancelled) {
            mCallback.onError(errString.toString(), errCode);
        }
    }

    @Override
    public void onAuthenticationFailed() {
        mCallback.onError("Not recognized. Try again.", FingerprintAuthConstants.AUTHENTICATION_FAILED);
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
        mCallback.onAuthenticated();
        cancelAuthenticationSignal();
    }

    private void cancelAuthenticationSignal() {
        selfCancelled = true;
        if (cancellationSignal != null) {
            cancellationSignal.cancel();
            cancellationSignal = null;
        }
    }

    public interface Callback {
        void onAuthenticated();

        void onError(String errorString, int errorCode);

        void onCancelled();
    }
}

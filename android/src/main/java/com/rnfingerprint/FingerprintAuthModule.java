package com.rnfingerprint;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;

import javax.crypto.Cipher;

import static android.content.Context.KEYGUARD_SERVICE;
import static com.rnfingerprint.FingerprintDialog.FALLBACK_REQUEST_CODE;

public class FingerprintAuthModule extends ReactContextBaseJavaModule implements LifecycleEventListener, ActivityEventListener {

    private static final String FRAGMENT_TAG = "fingerprint_dialog";

    private KeyguardManager keyguardManager;
    private boolean isAppActive;
    private DialogResultHandler dialogResultHandler;

    public static boolean inProgress = false;

    public FingerprintAuthModule(final ReactApplicationContext reactContext) {
        super(reactContext);

        reactContext.addLifecycleEventListener(this);
        reactContext.addActivityEventListener(this);
    }

    private KeyguardManager getKeyguardManager() {
        if (keyguardManager != null) {
            return keyguardManager;
        }
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return null;
        }

        keyguardManager = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);

        return keyguardManager;
    }

    @Override
    public String getName() {
        return "FingerprintAuth";
    }

    @ReactMethod
    public void isSupported(final Callback reactErrorCallback, final Callback reactSuccessCallback) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }

        int result = isFingerprintAuthAvailable();
        if (result == FingerprintAuthConstants.IS_SUPPORTED) {
            reactSuccessCallback.invoke("Is supported.");
        } else {
            reactErrorCallback.invoke("Not supported.", result);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @ReactMethod
    public void authenticate(final String reason, final ReadableMap authConfig, final Callback reactErrorCallback, final Callback reactSuccessCallback) {
        final Activity activity = getCurrentActivity();
        if (inProgress || !isAppActive || activity == null) {
            return;
        }
        inProgress = true;

        int availableResult = isFingerprintAuthAvailable();
        boolean fallback = false;

        if (authConfig.hasKey("passcodeFallback")) {
            fallback = authConfig.getBoolean(("passcodeFallback"));
        }

        final Cipher cipher = new FingerprintCipher().getCipher();
        dialogResultHandler = new DialogResultHandler(reactErrorCallback, reactSuccessCallback);

        if (fallback && (cipher == null || FingerprintAuthConstants.isFingerprintNotPresent(availableResult))) {
            openSystemAuthentication(reactSuccessCallback);
            return;
        }

        if (availableResult != FingerprintAuthConstants.IS_SUPPORTED && !fallback) {
            inProgress = false;
            reactErrorCallback.invoke(getErrorText(availableResult), availableResult);
            return;
        }

        if (cipher == null) {
            inProgress = false;
            reactErrorCallback.invoke("Not supported", FingerprintAuthConstants.NOT_AVAILABLE);
            return;
        }

        final FingerprintManager.CryptoObject cryptoObject = new FingerprintManager.CryptoObject(cipher);

        final FingerprintDialog fingerprintDialog = new FingerprintDialog();
        fingerprintDialog.setCryptoObject(cryptoObject);
        fingerprintDialog.setAuthConfig(authConfig);
        fingerprintDialog.setDialogCallback(dialogResultHandler);

        if (!isAppActive) {
            inProgress = false;
            return;
        }

        fingerprintDialog.show(activity.getFragmentManager(), FRAGMENT_TAG);
    }

    private int isFingerprintAuthAvailable() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return FingerprintAuthConstants.NOT_SUPPORTED;
        }

        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return FingerprintAuthConstants.NOT_AVAILABLE; // we can't do the check
        }

        final KeyguardManager keyguardManager = getKeyguardManager();

        final FingerprintManager fingerprintManager = (FingerprintManager) activity.getSystemService(Context.FINGERPRINT_SERVICE);

        if (fingerprintManager == null || !fingerprintManager.isHardwareDetected()) {
            return FingerprintAuthConstants.NOT_PRESENT;
        }

        if (keyguardManager == null || !keyguardManager.isKeyguardSecure()) {
            return FingerprintAuthConstants.NOT_AVAILABLE;
        }

        if (!fingerprintManager.hasEnrolledFingerprints()) {
            return FingerprintAuthConstants.NOT_ENROLLED;
        }
        return FingerprintAuthConstants.IS_SUPPORTED;
    }

    private String getErrorText(int authError) {
        String errorText;

        switch (authError) {
            case FingerprintAuthConstants.NOT_PRESENT:
                errorText = "Not present";
                break;
            case FingerprintAuthConstants.NOT_AVAILABLE:
                errorText = "Not available";
                break;
            case FingerprintAuthConstants.NOT_ENROLLED:
                errorText = "Not enrolled";
                break;
            case FingerprintAuthConstants.AUTHENTICATION_FAILED:
                errorText = "Authentication failed";
                break;
            case FingerprintAuthConstants.AUTHENTICATION_CANCELED:
                errorText = "Authentication cancelled";
                break;
            case FingerprintAuthConstants.IS_SUPPORTED:
                errorText = null;
                break;
            default:
                errorText = "Not supported";
                break;
        }

        return errorText;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void openSystemAuthentication(final Callback successCallback) {
        final Activity activity = getCurrentActivity();

        if (activity == null) {
            return;
        }

        KeyguardManager km = (KeyguardManager) activity.getSystemService(KEYGUARD_SERVICE);

        if (km != null) {
            Intent i = km.createConfirmDeviceCredentialIntent("", "");

            if (i != null) {
                activity.startActivityForResult(i, FALLBACK_REQUEST_CODE);
            } else {
                inProgress = false;
                successCallback.invoke("Successfully authenticated.");
            }
        }
    }

    @Override
    public void onHostResume() {
        isAppActive = true;
    }

    @Override
    public void onHostPause() {
        isAppActive = false;
    }

    @Override
    public void onHostDestroy() {
        isAppActive = false;
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        inProgress = false;
        if (requestCode == FALLBACK_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            dialogResultHandler.onAuthenticated();
        } else if (requestCode == FALLBACK_REQUEST_CODE) {
            dialogResultHandler.onError("Authentication cancelled", FingerprintAuthConstants.AUTHENTICATION_CANCELED);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
    }
}

package com.rnfingerprint;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import com.facebook.react.bridge.ReadableMap;

import static android.content.Context.KEYGUARD_SERVICE;

public class FingerprintDialog extends DialogFragment implements FingerprintHandler.Callback {
    public static final int FALLBACK_REQUEST_CODE = 10;

    public interface DialogResultListener {
        void onAuthenticated();
        void onError(String errorString, int errorCode);
        void onCancelled();
    }

    private FingerprintManager.CryptoObject mCryptoObject;
    private DialogResultListener dialogCallback;
    private FingerprintHandler mFingerprintHandler;
    private boolean isAuthInProgress;

    private Drawable fingerPrintImage;
    private TextView messageTextView;

    private int imageColor = 0;
    private int imageErrorColor = 0;
    private String dialogTitle = "";
    private String cancelText = "";
    private String fallbackText;
    private boolean fallbackEnabled = false;
    private String sensorDescription = "";
    private String sensorErrorDescription = "";

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        this.mFingerprintHandler = new FingerprintHandler(context, this);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
        setCancelable(false);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        this.fingerPrintImage = getResources().getDrawable(android.R.drawable.ic_dialog_alert);
        this.fingerPrintImage.setColorFilter(this.imageColor, PorterDuff.Mode.MULTIPLY);

        DialogInterface.OnClickListener cancelListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                onCancelled();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setCancelable(false)
                .setTitle(this.dialogTitle)
                .setIcon(this.fingerPrintImage)
                .setMessage(this.sensorDescription)
                .setNegativeButton(this.cancelText, cancelListener);

        if (this.fallbackEnabled) {
            DialogInterface.OnClickListener fallbackListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    onFallback();
                }
            };

            builder.setPositiveButton(this.fallbackText, fallbackListener);
        }

        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (this.isAuthInProgress) {
            return;
        }

        this.isAuthInProgress = true;
        this.mFingerprintHandler.startAuth(mCryptoObject);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.isAuthInProgress) {
            this.mFingerprintHandler.endAuth();
            this.isAuthInProgress = false;
        }
    }


    public void setCryptoObject(FingerprintManager.CryptoObject cryptoObject) {
        this.mCryptoObject = cryptoObject;
    }

    public void setDialogCallback(DialogResultListener newDialogCallback) {
        this.dialogCallback = newDialogCallback;
    }

    public void setAuthConfig(final ReadableMap config) {
        if (config == null) {
            return;
        }

        if (config.hasKey("title")) {
            this.dialogTitle = config.getString("title");
        }

        if (config.hasKey("cancelText")) {
            this.cancelText = config.getString("cancelText");
        }

        if (config.hasKey("passcodeFallback")) {
            this.fallbackEnabled = config.getBoolean("passcodeFallback");
        }

        if (config.hasKey("fallbackLabel")) {
            this.fallbackText = config.getString("fallbackLabel");
        }

        if (config.hasKey("sensorDescription")) {
            this.sensorDescription = config.getString("sensorDescription");
        }

        if (config.hasKey("sensorErrorDescription")) {
            this.sensorErrorDescription = config.getString("sensorErrorDescription");
        } else {
            this.sensorErrorDescription = this.sensorDescription;
        }

        if (config.hasKey("imageColor")) {
            this.imageColor = config.getInt("imageColor");
        } else {
            this.imageColor = Color.BLACK;
        }

        if (config.hasKey("imageErrorColor")) {
            this.imageErrorColor = config.getInt("imageErrorColor");
        } else {
            this.imageErrorColor = Color.RED;
        }
    }

    private TextView getMessageTextView() {
        if (this.messageTextView == null) {
            this.messageTextView = getDialog().findViewById(android.R.id.message);
        }

        return this.messageTextView;
    }

    @Override
    public void onAuthenticated() {
        this.isAuthInProgress = false;
        this.dialogCallback.onAuthenticated();
        dismiss();
    }

    @Override
    public void onError(String errorString, int errorCode) {
        this.fingerPrintImage.setColorFilter(this.imageErrorColor, PorterDuff.Mode.MULTIPLY);
        getMessageTextView().setText(this.sensorErrorDescription);
    }

    @Override
    public void onCancelled() {
        this.isAuthInProgress = false;
        this.mFingerprintHandler.endAuth();
        this.dialogCallback.onCancelled();
        dismiss();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void onFallback() {
        if (getActivity() == null) {
            return;
        }

        KeyguardManager km = (KeyguardManager) getActivity().getSystemService(KEYGUARD_SERVICE);

        if (km != null) {
            Intent i = km.createConfirmDeviceCredentialIntent("", "");
            getActivity().startActivityForResult(i, FALLBACK_REQUEST_CODE);
        }

        dismiss();
    }
}

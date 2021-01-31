package dev.skomlach.biometric.compat.impl;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.skomlach.biometric.compat.BiometricAuthRequest;
import dev.skomlach.biometric.compat.BiometricManagerCompat;
import dev.skomlach.biometric.compat.BiometricPromptCompat;
import dev.skomlach.biometric.compat.BiometricType;
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.AuthenticationHelpReason;
import dev.skomlach.biometric.compat.engine.BiometricAuthentication;
import dev.skomlach.biometric.compat.engine.BiometricAuthenticationListener;
import dev.skomlach.biometric.compat.engine.BiometricMethod;
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl;
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs;
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl;
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes;
import dev.skomlach.common.misc.ExecutorHelper;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricPromptGenericImpl implements IBiometricPromptImpl, AuthCallback {

    @Nullable
    private BiometricPromptCompatDialogImpl dialog;
    private final BiometricAuthenticationListener fmAuthCallback
            = new BiometricAuthenticationCallbackImpl();
    private final BiometricPromptCompat.Builder compatBuilder;
    private BiometricPromptCompat.Result callback;
    private final AtomicBoolean isFingerprint = new AtomicBoolean(false);
    public BiometricPromptGenericImpl(BiometricPromptCompat.Builder compatBuilder) {

        this.compatBuilder = compatBuilder;

        isFingerprint.set(compatBuilder.getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_FINGERPRINT);
        if (!isFingerprint.get() && compatBuilder.getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_ANY) {
            for (BiometricAuthRequest request : BiometricPromptCompat.getAvailableAuthRequests()) {
                if (request.getApi() == compatBuilder.getBiometricAuthRequest().getApi()
                        && request.getType() == BiometricType.BIOMETRIC_FINGERPRINT) {
                    isFingerprint.set(BiometricManagerCompat.hasEnrolled(request));
                    break;
                }
            }
        }

    }

    @Override
    public void authenticate(@NonNull BiometricPromptCompat.Result callback) {
        if (this.callback == null) {
            this.callback = callback;
        }

        final boolean doNotShowDialog = isFingerprint.get() && DevicesWithKnownBugs.isHideDialogInstantly();
        if(!doNotShowDialog) {
            dialog = new BiometricPromptCompatDialogImpl(compatBuilder,
                    BiometricPromptGenericImpl.this,
                    isFingerprint.get() && DevicesWithKnownBugs.isShowInScreenDialogInstantly());
            dialog.showDialog();
        } else{
           onUiShown();
           startAuth();
        }
    }

    @Override
    public void cancelAuthenticate() {

        if (dialog != null)
            dialog.dismissDialog();
        else {
           stopAuth();
        }
    }

    @Override
    public boolean isNightMode() {
        if (dialog != null)
            return dialog.isNightMode();
        else {
            return DarkLightThemes.isNightMode(compatBuilder.getContext());
        }
    }

    @Override
    public BiometricPromptCompat.Builder getBuilder() {
        return compatBuilder;
    }

    @Override
    public List<String> getUsedPermissions() {
        final Set<String> permission = new HashSet<>();
        List<BiometricMethod> biometricMethodList = new ArrayList<>();
        if (compatBuilder.getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_ANY) {
            biometricMethodList.addAll(BiometricAuthentication.getAvailableBiometricMethods());
        } else {
            for (BiometricMethod m : BiometricAuthentication.getAvailableBiometricMethods()) {
                if (m.getBiometricType() == compatBuilder.getBiometricAuthRequest().getType()) {
                    biometricMethodList.add(m);
                }
            }
        }
        for (BiometricMethod method : biometricMethodList) {
            switch (method) {

                case DUMMY_BIOMETRIC:
                    permission.add("android.permission.CAMERA");
                    break;

                case IRIS_ANDROIDAPI:
                    permission.add("android.permission.USE_IRIS");
                    break;
                case IRIS_SAMSUNG:
                    permission.add("com.samsung.android.camera.iris.permission.USE_IRIS");
                    break;

                case FACELOCK:
                    permission.add("android.permission.WAKE_LOCK");
                    break;

                case FACE_HUAWEI:
                    permission.add("android.permission.CAMERA");
                    permission.add("android.permission.USE_FACERECOGNITION");
                    break;
                case FACE_SOTERAPI:
                    permission.add("android.permission.USE_FACERECOGNITION");
                    break;
                case FACE_ANDROIDAPI:
                    permission.add("android.permission.USE_FACE_AUTHENTICATION");
                    break;
                case FACE_SAMSUNG:
                    permission.add("com.samsung.android.bio.face.permission.USE_FACE");
                    break;
                case FACE_OPPO:
                    permission.add("oppo.permission.USE_FACE");
                    break;
                 //TODO: check permissions
//                case FACE_VIVO: break;
                case FACE_ONEPLUS:
                    permission.add("com.oneplus.faceunlock.permission.FACE_UNLOCK");
                    break;


                case FINGERPRINT_API23:
                case FINGERPRINT_SUPPORT:
                    permission.add("android.permission.USE_FINGERPRINT");
                    break;
                case FINGERPRINT_FLYME:
                    permission.add("com.fingerprints.service.ACCESS_FINGERPRINT_MANAGER");
                    break;
                case FINGERPRINT_SAMSUNG:
                    permission.add("com.samsung.android.providers.context.permission.WRITE_USE_APP_FEATURE_SURVEY");
                    break;
                //TODO: check permissions
//                case FINGERPRINT_SOTERAPI: break
            }
        }
        return new ArrayList<>(permission);
    }

    @Override
    public boolean cancelAuthenticateBecauseOnPause() {
        if (dialog != null) {
            if (dialog.cancelAuthenticateBecauseOnPause()) {
                return true;
            } else {
                return false;
            }
        } else {
            cancelAuthenticate();
            return true;
        }
    }

    @Override
    public void startAuth() {
        final List<BiometricType> types = compatBuilder.getBiometricAuthRequest().getType() == BiometricType.BIOMETRIC_ANY ?
                BiometricAuthentication.getAvailableBiometrics() :
                Collections.singletonList(compatBuilder.getBiometricAuthRequest().getType());

        BiometricAuthentication.authenticate(dialog!= null ? dialog.getContainer() : null, types, fmAuthCallback);
    }

    @Override
    public void stopAuth() {
        BiometricAuthentication.cancelAuthentication();
    }

    @Override
    public void cancelAuth() {
        if (callback != null)
            callback.onCanceled();
    }

    @Override
    public void onUiShown() {
        if (callback != null)
            callback.onUIShown();
    }

    private class BiometricAuthenticationCallbackImpl implements BiometricAuthenticationListener {
        @Override
        public void onSuccess(BiometricType module) {

            ExecutorHelper.INSTANCE.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    cancelAuthenticate();
                    callback.onSucceeded();
                }
            });
        }

        @Override
        public void onHelp(AuthenticationHelpReason helpReason, CharSequence msg) {
            if (helpReason != AuthenticationHelpReason.BIOMETRIC_ACQUIRED_GOOD && !TextUtils.isEmpty(msg)) {

                if(dialog !=null)
                dialog.onHelp(msg);
            }
        }

        @Override
        public void onFailure(AuthenticationFailureReason failureReason, BiometricType module) {
            if(dialog !=null)
            dialog.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT);
            if (failureReason != AuthenticationFailureReason.LOCKED_OUT) {
                //non fatal
                switch (failureReason) {
                    case SENSOR_FAILED:
                    case AUTHENTICATION_FAILED:
                        return;
                }
                ExecutorHelper.INSTANCE.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        cancelAuthenticate();
                        callback.onFailed(failureReason);
                    }
                });
            } else {
                HardwareAccessImpl.getInstance(compatBuilder.getBiometricAuthRequest()).lockout();
                ExecutorHelper.INSTANCE.getHandler().postDelayed(() -> {
                    cancelAuthenticate();
                    callback.onFailed(failureReason);
                }, 2000);
            }
        }
    }
}

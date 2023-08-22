package com.tencent.soter.core.model;

import androidx.annotation.NonNull;

/**
 * Created by henryye on 2018/1/10.
 */

public class SoterDelegate {
    private static final String TAG = "Soter.SoterDelegate";
    @NonNull
    private static volatile ISoterDelegate sSoterDelegateImp = new ISoterDelegate() {
        private boolean isTriggeredOOM; // once triggered OOM, we regard it as no attk or error stack. mark as not native support.

        @Override
        public void onTriggeredOOM() {
            SLogger.e(TAG, "soter: triggered OOM. using default imp, just record the flag");
            this.isTriggeredOOM = true;
        }

        @Override
        public boolean isTriggeredOOM() {
            return isTriggeredOOM;
        }

        @Override
        public void reset() {
            isTriggeredOOM = false;
        }
    };

    public static void setImplement(@NonNull ISoterDelegate instance) {
        sSoterDelegateImp = instance;
    }

    public static void onTriggerOOM() {
        sSoterDelegateImp.onTriggeredOOM();
    }

    public static boolean isTriggeredOOM() {
        return sSoterDelegateImp.isTriggeredOOM();
    }

    public static void reset() {
        sSoterDelegateImp.reset();
    }

    public interface ISoterDelegate {
        void onTriggeredOOM();

        boolean isTriggeredOOM();

        void reset();
    }
}
/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat.engine.internal.face.huawei

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.RestartPredicatesImpl
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.face.huawei.impl.HuaweiFaceManager
import dev.skomlach.biometric.compat.engine.internal.face.huawei.impl.HuaweiFaceManagerFactory
import dev.skomlach.biometric.compat.engine.internal.face.huawei.impl.HuaweiFaceRecognizeManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.TimeUnit


class HuaweiFaceUnlockModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACE_HUAWEI) {
    //EMUI 10.1.0
    private var huaweiFaceManagerLegacy: HuaweiFaceManager? = null

    init {
        ExecutorHelper.post {
            try {
                huaweiFaceManagerLegacy = HuaweiFaceManagerFactory.getHuaweiFaceManager()
                d("$name.huaweiFaceManagerLegacy - $huaweiFaceManagerLegacy")
                if (isHardwarePresent && HuaweiFaceRecognizeManager.shouldCheckCamera()) {
                    val cancellationSignal = CancellationSignal()
                    val checkTask = Runnable {
                        HuaweiFaceRecognizeManager.resetCheckCamera()
                        listener?.initFinished(biometricMethod, this@HuaweiFaceUnlockModule)
                        if (!cancellationSignal.isCanceled)
                            cancellationSignal.cancel()
                    }
                    ExecutorHelper.postDelayed(checkTask, TimeUnit.SECONDS.toMillis(5))
                    authenticate(null, cancellationSignal, object : AuthenticationListener {
                        override fun onHelp(msg: CharSequence?) {}

                        override fun onSuccess(
                            moduleTag: Int,
                            biometricCryptoObject: BiometricCryptoObject?
                        ) {
                            ExecutorHelper.removeCallbacks(checkTask)
                            checkTask.run()
                        }

                        override fun onFailure(
                            failureReason: AuthenticationFailureReason?,
                            moduleTag: Int
                        ) {
                            ExecutorHelper.removeCallbacks(checkTask)
                            checkTask.run()
                        }

                        override fun onCanceled(moduleTag: Int) {
                            ExecutorHelper.removeCallbacks(checkTask)
                            checkTask.run()
                        }
                    }, RestartPredicatesImpl.defaultPredicate())

                    return@post
                }
            } catch (e: Throwable) {
                if (DEBUG_MANAGERS)
                    e(e, name)
                huaweiFaceManagerLegacy = null
            }

            listener?.initFinished(biometricMethod, this@HuaweiFaceUnlockModule)
        }
    }

    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = false

    override val isManagerAccessible: Boolean
        get() = huaweiFaceManagerLegacy != null
    override val isHardwarePresent: Boolean
        get() {
            try {
                if (huaweiFaceManagerLegacy?.isHardwareDetected == true) return true
            } catch (e: Throwable) {

            }

            return false
        }

    override val hasEnrolled: Boolean
        get() {
            try {
                if (huaweiFaceManagerLegacy?.hasEnrolledTemplates() == true) return true
            } catch (e: Throwable) {

            }

            return false
        }

    @Throws(SecurityException::class)
    override fun authenticate(
        biometricCryptoObject: BiometricCryptoObject?,
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        try {
            d("$name.authenticate - $biometricMethod; Crypto=$biometricCryptoObject")
            // Why getCancellationSignalObject returns an Object is unexplained
            val signalObject =
                (if (cancellationSignal == null) null else cancellationSignal.cancellationSignalObject as android.os.CancellationSignal?)
                    ?: throw IllegalArgumentException("CancellationSignal cann't be null")
            huaweiFaceManagerLegacy?.let {

                val callback = AuthCallbackLegacy(
                    biometricCryptoObject,
                    restartPredicate,
                    cancellationSignal,
                    listener
                )
                signalObject.setOnCancelListener {
                    it.cancel()
                }
                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                authCallTimestamp.set(System.currentTimeMillis())
                it.authenticate(callback)
                return
            }

        } catch (e: Throwable) {
            e(e, "$name: authenticate failed unexpectedly")
        }
        listener?.onFailure(AuthenticationFailureReason.UNKNOWN, tag())
    }

    private inner class AuthCallbackLegacy(
        private val biometricCryptoObject: BiometricCryptoObject?,
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : HuaweiFaceManager.AuthenticatorCallback() {
        private var errorTs = System.currentTimeMillis()
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)

        override fun onAuthenticationError(errMsgId: Int) {
            d("$name.onAuthenticationError: $errMsgId")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                HuaweiFaceRecognizeManager.HUAWEI_FACE_AUTHENTICATOR_FAIL -> failureReason =
                    AuthenticationFailureReason.AUTHENTICATION_FAILED

                HuaweiFaceRecognizeManager.HUAWEI_FACE_AUTH_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                HuaweiFaceRecognizeManager.HUAWEI_FACE_AUTH_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT
                HuaweiFaceRecognizeManager.HUAWEI_FACE_AUTH_ERROR_LOCKED -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }
                else -> {
                    Core.cancelAuthentication(this@HuaweiFaceUnlockModule)
                    listener?.onCanceled(tag())
                    return
                }
            }
            if (restartCauseTimeout(failureReason)) {
                authenticate(biometricCryptoObject, cancellationSignal, listener, restartPredicate)
            } else
                if (failureReason == AuthenticationFailureReason.TIMEOUT || restartPredicate?.invoke(
                        failureReason
                    ) == true
                ) {
                    listener?.onFailure(failureReason, tag())
                } else {
                    if (mutableListOf(
                            AuthenticationFailureReason.SENSOR_FAILED,
                            AuthenticationFailureReason.AUTHENTICATION_FAILED
                        ).contains(failureReason)
                    ) {
                        lockout()
                        failureReason = AuthenticationFailureReason.LOCKED_OUT
                    }
                    listener?.onFailure(failureReason, tag())
                }
        }

        override fun onAuthenticationStatus(helpMsgId: Int) {
            d("$name.onAuthenticationHelp: $helpMsgId")
            listener?.onHelp(null)
        }

        override fun onAuthenticationSucceeded() {
            d("$name.onAuthenticationSucceeded: ")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            listener?.onSuccess(
                tag(),
                BiometricCryptoObject(
                    biometricCryptoObject?.signature,
                    biometricCryptoObject?.cipher,
                    biometricCryptoObject?.mac
                )
            )
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.AUTHENTICATION_FAILED
            if (restartPredicate?.invoke(failureReason) == true) {
                listener?.onFailure(failureReason, tag())
            } else {
                if (mutableListOf(
                        AuthenticationFailureReason.SENSOR_FAILED,
                        AuthenticationFailureReason.AUTHENTICATION_FAILED
                    ).contains(failureReason)
                ) {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }
                listener?.onFailure(failureReason, tag())
            }
        }
    }
}
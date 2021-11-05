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

package dev.skomlach.biometric.compat.engine.internal.fingerprint

import android.content.Context
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.engine.*
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.CodeToString.getErrorCode
import dev.skomlach.biometric.compat.utils.CodeToString.getHelpCode
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper

//actually perhaps not necessary impl.

class SupportFingerprintModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FINGERPRINT_SUPPORT) {
    private var managerCompat: FingerprintManagerCompat? = null

    init {
        managerCompat = try {
            FingerprintManagerCompat.from(context)
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
            null
        }
        listener?.initFinished(biometricMethod, this@SupportFingerprintModule)
    }

    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        val manager = try {
            val method = managerCompat?.javaClass?.declaredMethods?.firstOrNull {
                it.parameterTypes.size == 1 && it.parameterTypes[0] == Context::class.java &&
                        it.returnType.methods.firstOrNull { m -> m.name.equals("isHardwareDetected") } != null
            }
            val isAccessible = method?.isAccessible ?: true
            if (!isAccessible)
                method?.isAccessible = true
            val manager = try {
                method?.invoke(managerCompat, context)
            } finally {
                if (!isAccessible)
                    method?.isAccessible = false
            }
            manager
        } catch (ignore: Throwable) {
            null
        }
        manager?.let {
            managers.add(it)
        }
        return managers
    }

    override val isManagerAccessible: Boolean
        get() = managerCompat != null
    override val isHardwarePresent: Boolean
        get() {

            try {
                return managerCompat?.isHardwareDetected == true
            } catch (e: Throwable) {
                e(e, name)
            }

            return false
        }

    override fun hasEnrolled(): Boolean {
        try {
            return managerCompat?.isHardwareDetected == true && managerCompat?.hasEnrolledFingerprints() == true
        } catch (e: Throwable) {
            e(e, name)
        }
        return false
    }

    @Throws(SecurityException::class)
    override fun authenticate(
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        d("$name.authenticate - $biometricMethod")
        managerCompat?.let {
            try {
                val callback: FingerprintManagerCompat.AuthenticationCallback =
                    AuthCallbackCompat(restartPredicate, cancellationSignal, listener)
                requireNotNull(cancellationSignal?.cancellationSignalObject) { "CancellationSignal cann't be null" }

                // Occasionally, an NPE will bubble up out of FingerprintManager.authenticate
                it.authenticate(
                    null,
                    0,
                    cancellationSignal,
                    callback,
                    ExecutorHelper.handler
                )
                return
            } catch (e: Throwable) {
                e(e, "$name: authenticate failed unexpectedly")
            }
        }
        listener?.onFailure(
            AuthenticationFailureReason.UNKNOWN,
            tag()
        )
        return
    }

    internal inner class AuthCallbackCompat(
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) : FingerprintManagerCompat.AuthenticationCallback() {
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence) {
            d(name + ".onAuthenticationError: " + getErrorCode(errMsgId) + "-" + errString)
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
                BiometricCodes.BIOMETRIC_ERROR_HW_NOT_PRESENT -> failureReason =
                    AuthenticationFailureReason.NO_HARDWARE
                BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                BiometricCodes.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }
                BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                BiometricCodes.BIOMETRIC_ERROR_NO_SPACE -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED
                BiometricCodes.BIOMETRIC_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT
                BiometricCodes.BIOMETRIC_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }
                BiometricCodes.BIOMETRIC_ERROR_USER_CANCELED -> {
                    Core.cancelAuthentication(this@SupportFingerprintModule)
                    return
                }
                BiometricCodes.BIOMETRIC_ERROR_CANCELED ->                     // Don't send a cancelled message.
                    return
            }
            if (restartCauseTimeout(failureReason)) {
                authenticate(cancellationSignal, listener, restartPredicate)
            } else
                if (restartPredicate?.invoke(failureReason) == true) {
                    listener?.onFailure(failureReason, tag())
                    authenticate(cancellationSignal, listener, restartPredicate)
                } else {
                    when (failureReason) {
                        AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED -> {
                            lockout()
                            failureReason = AuthenticationFailureReason.LOCKED_OUT
                        }
                    }
                    listener?.onFailure(failureReason, tag())
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence) {
            d(name + ".onAuthenticationHelp: " + getHelpCode(helpMsgId) + "-" + helpString)
            listener?.onHelp(AuthenticationHelpReason.getByCode(helpMsgId), helpString)
        }

        override fun onAuthenticationSucceeded(result: FingerprintManagerCompat.AuthenticationResult) {
            d("$name.onAuthenticationSucceeded: %s", result)
            listener?.onSuccess(tag())
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            listener?.onFailure(
                AuthenticationFailureReason.AUTHENTICATION_FAILED,
                tag()
            )
        }
    }

}
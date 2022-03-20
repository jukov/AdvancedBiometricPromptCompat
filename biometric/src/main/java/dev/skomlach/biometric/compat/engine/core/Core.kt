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

package dev.skomlach.biometric.compat.engine.core

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.DummyBiometricModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.set


object Core {
    private val lock = ReentrantLock()
    private val cancellationSignals =
        Collections.synchronizedMap(HashMap<BiometricModule, CancellationSignal>())
    private val reprintModuleHashMap = Collections.synchronizedMap(HashMap<Int, BiometricModule>())
    fun cleanModules() {
        try {
            lock.runCatching { this.lock() }
            reprintModuleHashMap.clear()
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }


    fun registerModule(module: BiometricModule?) {
        try {
            lock.runCatching { this.lock() }
            if (module == null || reprintModuleHashMap.containsKey(module.tag())) {
                return
            }
            if (module.isHardwarePresent) {
                reprintModuleHashMap[module.tag()] = module
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }

    val isLockOut: Boolean
        get() {
            try {
                lock.runCatching { this.lock() }
                for (module in reprintModuleHashMap.values) {
                    if (module.isLockOut) {
                        return true
                    }
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            } finally {
                lock.runCatching {
                    this.unlock()
                }
            }
            return false
        }
    val isHardwareDetected: Boolean
        get() {
            try {
                lock.runCatching { this.lock() }
                for (module in reprintModuleHashMap.values) {
                    if (module.isHardwarePresent) return true
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            } finally {
                lock.runCatching {
                    this.unlock()
                }
            }
            return false
        }


    fun hasEnrolled(): Boolean {
        try {
            lock.runCatching { this.lock() }
            for (module in reprintModuleHashMap.values) {
                if (module.hasEnrolled()) return true
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
        return false
    }
    /**
     * Start an authentication request.
     *
     * @param listener         The listener to be notified.
     * @param restartPredicate The predicate that determines whether to restart or not.
     */
    /**
     * Start a fingerprint authentication request.
     *
     *
     * Equivalent to calling [.authenticate] with
     * [RestartPredicatesImpl.defaultPredicate]
     *
     * @param listener The listener that will be notified of authentication events.
     */

    @JvmOverloads
    fun authenticate(
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate? = RestartPredicatesImpl.defaultPredicate()
    ) {
        var m: BiometricModule? = null
        try {
            lock.runCatching { this.lock() }
            for (module in reprintModuleHashMap.values) {
                m = module
                authenticate(module, listener, restartPredicate)
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
            listener?.onFailure(
                AuthenticationFailureReason.INTERNAL_ERROR,
                m?.tag() ?: DummyBiometricModule(null).tag()
            )
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }


    fun authenticate(
        module: BiometricModule,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        try {
            lock.runCatching { this.lock() }
            if (!module.isHardwarePresent || !module.hasEnrolled() || module.isLockOut) throw RuntimeException(
                "Module " + module.javaClass.simpleName + " not ready"
            )
            cancelAuthentication(module)
            val cancellationSignal = CancellationSignal()
            cancellationSignals[module] = cancellationSignal
            module.authenticate(cancellationSignal, listener, restartPredicate)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
            listener?.onFailure(AuthenticationFailureReason.INTERNAL_ERROR, module.tag())
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }


    fun cancelAuthentication() {
        for (module in reprintModuleHashMap.values) {
            cancelAuthentication(module)
        }
    }


    fun cancelAuthentication(module: BiometricModule) {
        try {
            lock.runCatching { this.lock() }
            val signal = cancellationSignals[module]
            if (signal != null && !signal.isCanceled) {
                signal.cancel()
            }
            cancellationSignals.remove(module)
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }

    /**
     * Start a fingerprint authentication request.
     *
     *
     * This variant will not restart the fingerprint reader after any failure, including non-fatal
     * failures.
     *
     * @param listener The listener that will be notified of authentication events.
     */

    fun authenticateWithoutRestart(listener: AuthenticationListener?) {
        authenticate(listener, RestartPredicatesImpl.neverRestart())
    }
}
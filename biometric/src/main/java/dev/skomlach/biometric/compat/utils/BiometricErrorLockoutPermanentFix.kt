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

package dev.skomlach.biometric.compat.utils

import android.content.SharedPreferences
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.common.storage.SharedPreferenceProvider.getPreferences
import java.util.concurrent.locks.ReentrantLock


object BiometricErrorLockoutPermanentFix {
    private const val TS_PREF = "user_unlock_device"
    private val sharedPreferences: SharedPreferences =
        getPreferences("BiometricCompat_ErrorLockoutPermanentFix")
    private val lock = ReentrantLock()
    fun setBiometricSensorPermanentlyLocked(type: BiometricType) {
        try {
            lock.runCatching { this.lock() }
            sharedPreferences.edit().putBoolean(TS_PREF + "-" + type.name, false).apply()
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }

    fun resetBiometricSensorPermanentlyLocked() {
        try {
            lock.runCatching { this.lock() }
            sharedPreferences.edit().clear().apply()
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }

    fun isBiometricSensorPermanentlyLocked(type: BiometricType): Boolean {
        try {
            lock.runCatching { this.lock() }

            return !sharedPreferences.getBoolean(TS_PREF + "-" + type.name, true)
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }
}
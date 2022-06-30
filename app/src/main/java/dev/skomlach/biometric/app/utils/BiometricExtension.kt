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

package dev.skomlach.biometric.app.utils

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import dev.skomlach.biometric.compat.*
import dev.skomlach.biometric.compat.crypto.CryptographyManagerProvider
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import java.nio.charset.Charset

private val testString = "Test data"
private var cryptoTest = CryptoTest(testString.toByteArray(Charset.forName("UTF-8")))

fun Fragment.startBiometric(biometricAuthRequest: BiometricAuthRequest) {

    if (!BiometricManagerCompat.isBiometricReady(biometricAuthRequest)) {
        if (!BiometricManagerCompat.isHardwareDetected(biometricAuthRequest))
            showAlertDialog(
                requireActivity(),
                "No hardware for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",

                )
        else if (!BiometricManagerCompat.hasEnrolled(biometricAuthRequest)) {
            val result =
                BiometricManagerCompat.openSettings(requireActivity(), biometricAuthRequest)
            showAlertDialog(
                requireActivity(),
                "No enrolled biometric for - ${biometricAuthRequest.api}/${biometricAuthRequest.type}\nTrying to open system settings - $result",
            )
        } else if (BiometricManagerCompat.isLockOut(biometricAuthRequest))
            showAlertDialog(
                requireActivity(),
                "Biometric sensor temporary locked for ${biometricAuthRequest.api}/${biometricAuthRequest.type}\nTry again later",
            )
        else if (BiometricManagerCompat.isBiometricSensorPermanentlyLocked(biometricAuthRequest))
            showAlertDialog(
                requireActivity(),
                "Biometric sensor permanently locked for ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
            )


        return
    }

    val start = System.currentTimeMillis()
    BiometricLoggerImpl.e("CheckBiometric.start() for $biometricAuthRequest")
    val cryptoObject = if (cryptoTest.vector == null) {
        BiometricCryptoObject(
            cipher = CryptographyManagerProvider.CryptographyManager()
                ?.getInitializedCipherForEncryption("test")
        )
    } else {
        BiometricCryptoObject(
            cipher = CryptographyManagerProvider.CryptographyManager()
                ?.getInitializedCipherForDecryption("test", cryptoTest.vector)
        )
    }
    val biometricPromptCompat = BiometricPromptCompat.Builder(
        biometricAuthRequest,
        requireActivity(),
        cryptoObject
    )
        .setTitle("Biometric for Fragment: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
        .setSubtitle("Biometric Subtitle: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
        .setDescription("Biometric Description: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more")
        .setNegativeButton(
            "Cancel: BlaBlablabla Some very long text BlaBlablabla and more text and more and more and more",
            null
        )
        .build()

    BiometricLoggerImpl.e(
        "CheckBiometric.isEnrollChanged -  ${
            BiometricManagerCompat.isBiometricEnrollChanged(
                biometricAuthRequest
            )
        }"
    )


    biometricPromptCompat.authenticate(object : BiometricPromptCompat.AuthenticationCallback() {
        override fun onSucceeded(confirmed: Set<AuthenticationResult>) {
            var cryptoText = "Crypto doesn't work or disabled"
            confirmed.firstOrNull {
                it.cryptoObject != null
            }?.cryptoObject?.cipher?.let {
                if (cryptoTest.vector == null) {
                    CryptographyManagerProvider.CryptographyManager()
                        ?.encryptData(cryptoTest.byteArray, it)?.let {
                            cryptoText = "Crypto encryption work"
                            cryptoTest = CryptoTest(it.ciphertext, it.initializationVector)
                        }
                   
                } else {
                    CryptographyManagerProvider.CryptographyManager()
                        ?.decryptData(cryptoTest.byteArray, it)?.let {
                            cryptoText = if (String(
                                    it,
                                    Charset.forName("UTF-8")
                                ) == testString
                            ) "Crypto decryption work" else "Crypto decryption doesn't work"
                            cryptoTest = CryptoTest(testString.toByteArray(Charset.forName("UTF-8")))
                        }

                }
            }

            BiometricLoggerImpl.e("CheckBiometric.onSucceeded() for $confirmed; $cryptoText")
            Toast.makeText(AndroidContext.appContext, "Succeeded - $confirmed; $cryptoText", Toast.LENGTH_SHORT)
                .show()
        }

        override fun onCanceled() {
            BiometricLoggerImpl.e("CheckBiometric.onCanceled()")
            Toast.makeText(AndroidContext.appContext, "Canceled", Toast.LENGTH_SHORT).show()
        }

        override fun onFailed(reason: AuthenticationFailureReason?) {
            BiometricLoggerImpl.e("CheckBiometric.onFailed() - $reason")
            try {
                showAlertDialog(requireActivity(), "Failure: $reason")
            } catch (ignore: Throwable) {
                Toast.makeText(AndroidContext.appContext, "Failure: $reason", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        override fun onUIOpened() {
            BiometricLoggerImpl.e("CheckBiometric.onUIOpened()")
            Toast.makeText(AndroidContext.appContext, "onUIOpened", Toast.LENGTH_SHORT).show()
        }

        override fun onUIClosed() {
            BiometricLoggerImpl.e("CheckBiometric.onUIClosed()")
            Toast.makeText(AndroidContext.appContext, "onUIClosed", Toast.LENGTH_SHORT).show()
        }
    })
    Toast.makeText(
        AndroidContext.appContext,
        "Start biometric ${biometricAuthRequest.api}/${biometricAuthRequest.type}",
        Toast.LENGTH_SHORT
    ).show()
}

private fun showAlertDialog(context: Context, msg: String) {
    AlertDialog.Builder(context).setTitle("Biometric Error").setMessage(msg)
        .setNegativeButton(android.R.string.cancel, null).show()
}
data class CryptoTest(
    val byteArray: ByteArray,
    val vector: ByteArray? = null
)
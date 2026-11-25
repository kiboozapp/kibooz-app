package com.nthb.kibooz.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.nthb.kibooz.BuildConfig

sealed interface GoogleCredentialOutcome {
    data class Success(val idToken: String, val emailHint: String?) : GoogleCredentialOutcome
    data object Cancelled : GoogleCredentialOutcome
    data object NotConfigured : GoogleCredentialOutcome
    data object NoAccountAvailable : GoogleCredentialOutcome
    data class Error(val detail: String?) : GoogleCredentialOutcome
}

/**
 * Mengambil Google ID token lewat Android Credential Manager agar dikirim dan diverifikasi ke server Kibooz.
 */
class GoogleSignInClient(
    private val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
) {
    suspend fun requestIdToken(activityContext: Context): GoogleCredentialOutcome {
        val clientId = webClientId.trim()
        if (clientId.isEmpty()) {
            return GoogleCredentialOutcome.NotConfigured
        }

        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleCredentialOutcome.Success(
                    idToken = googleCredential.idToken,
                    emailHint = googleCredential.id
                )
            } else {
                GoogleCredentialOutcome.Error("Tipe kredensial Google tidak dikenali")
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleCredentialOutcome.Cancelled
        } catch (_: NoCredentialException) {
            GoogleCredentialOutcome.NoAccountAvailable
        } catch (e: GoogleIdTokenParsingException) {
            GoogleCredentialOutcome.Error(e.message)
        } catch (e: GetCredentialException) {
            GoogleCredentialOutcome.Error(e.errorMessage?.toString() ?: e.message)
        }
    }
}

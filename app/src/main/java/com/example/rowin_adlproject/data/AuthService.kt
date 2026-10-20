package com.example.rowin_adlproject.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

/**
 * Firebase Authentication (email + password) for practicals #1 and #4.
 *
 * The signed-in uid is what Firestore security rules check, so every cloud
 * document belongs to exactly one verified account. Without
 * google-services.json [isAvailable] is false and the repository falls back
 * to local-only accounts.
 */
class AuthService {

    /** Outcome of an auth call, already mapped from Firebase's exception types. */
    sealed interface Outcome {
        data class Success(val uid: String) : Outcome
        data object InvalidCredentials : Outcome
        data object EmailInUse : Outcome
        data object WeakPassword : Outcome
        data object Offline : Outcome
        data class Failure(val message: String) : Outcome
    }

    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    val isAvailable: Boolean get() = auth != null

    /** Uid of the account Firebase currently holds a session for, if any. */
    val currentUid: String? get() = auth?.currentUser?.uid

    suspend fun signIn(email: String, password: String): Outcome = call {
        it.signInWithEmailAndPassword(email, password).await().user?.uid
    }

    suspend fun createAccount(email: String, password: String): Outcome = call {
        it.createUserWithEmailAndPassword(email, password).await().user?.uid
    }

    /** Emails a reset link; [Outcome.Success] carries an empty uid. */
    suspend fun sendPasswordReset(email: String): Outcome = call {
        it.sendPasswordResetEmail(email).await()
        ""
    }

    fun signOut() {
        auth?.signOut()
    }

    private suspend fun call(block: suspend (FirebaseAuth) -> String?): Outcome {
        val firebase = auth ?: return Outcome.Failure("Firebase is not configured in this build.")
        return try {
            block(firebase)?.let { Outcome.Success(it) } ?: Outcome.Failure("Sign-in did not return an account.")
        } catch (e: FirebaseNetworkException) {
            Outcome.Offline
        } catch (e: FirebaseAuthWeakPasswordException) {
            // Subclass of InvalidCredentials - must be caught first.
            Outcome.WeakPassword
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Outcome.InvalidCredentials
        } catch (e: FirebaseAuthInvalidUserException) {
            Outcome.InvalidCredentials
        } catch (e: FirebaseAuthUserCollisionException) {
            Outcome.EmailInUse
        } catch (e: FirebaseTooManyRequestsException) {
            Outcome.Failure("Too many attempts. Wait a minute and try again.")
        } catch (e: FirebaseAuthException) {
            if (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
                Outcome.Failure(
                    "Email sign-in is turned off for this Firebase project. " +
                        "Enable it under Authentication > Sign-in method.",
                )
            } else {
                Outcome.Failure(e.message ?: "Authentication failed.")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Authentication failed.")
        }
    }
}

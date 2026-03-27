package org.ole.planet.myplanet.model

import android.content.Context
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.realm.Realm
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utils.Utilities

class RealmUserTest {

    @MockK
    lateinit var mockRealm: Realm

    @MockK
    lateinit var mockContext: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(Utilities)
        Dispatchers.setMain(Dispatchers.Unconfined)
        MainApplication.applicationScope = CoroutineScope(Dispatchers.Unconfined)
        mockkStatic(Utilities::class)
        every { Utilities.toast(any(), any()) } returns Unit
        every { Utilities.toast(any(), any(), any()) } returns Unit
        MainApplication.context = mockContext
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `updateUserDetails triggers success block on transaction success`() {
        // Arrange
        val userId = "user123"

        // Mock Realm.executeTransactionAsync to trigger the success callback
        every {
            mockRealm.executeTransactionAsync(any(), any(), any())
        } answers {
            val transaction = arg<Realm.Transaction>(0)
            val successCallback = arg<Realm.Transaction.OnSuccess>(1)

            // Execute the transaction body (just to cover it, though in mock it does nothing without real DB)
            try {
                transaction.execute(mockRealm)
            } catch (e: Exception) {
                // Ignore for mock purposes
            }

            successCallback.onSuccess()
            mockk()
        }

        // Mock the realm where query inside the transaction
        val mockQuery = mockk<io.realm.RealmQuery<RealmUser>>(relaxed = true)
        every { mockRealm.where(RealmUser::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", userId) } returns mockQuery

        val mockRealmUser = mockk<RealmUser>(relaxed = true)
        every { mockQuery.findFirst() } returns mockRealmUser

        val onSuccessMock = mockk<() -> Unit>(relaxed = true)

        // Act
        RealmUser.updateUserDetails(
            realm = mockRealm,
            userId = userId,
            firstName = "John",
            lastName = "Doe",
            middleName = "",
            email = "john@example.com",
            phoneNumber = "1234567890",
            level = "1",
            language = "en",
            gender = "Male",
            dob = "2000-01-01",
            onSuccess = onSuccessMock
        )

        // Assert
        verify(exactly = 1) { onSuccessMock.invoke() }
        verify { Utilities.toast(mockContext, "User details updated successfully") }

        // Verify user properties were set
        verify { mockRealmUser.firstName = "John" }
        verify { mockRealmUser.lastName = "Doe" }
        verify { mockRealmUser.email = "john@example.com" }
        verify { mockRealmUser.isUpdated = true }
    }

    @Test
    fun `updateUserDetails triggers error block on transaction failure`() {
        // Arrange
        val userId = "user123"
        val mockError = Throwable("Transaction failed")

        // Mock Realm.executeTransactionAsync to trigger the error callback
        every {
            mockRealm.executeTransactionAsync(any(), any(), any())
        } answers {
            val errorCallback = arg<Realm.Transaction.OnError>(2)
            errorCallback.onError(mockError)
            mockk()
        }

        val onSuccessMock = mockk<() -> Unit>(relaxed = true)

        // Act
        RealmUser.updateUserDetails(
            realm = mockRealm,
            userId = userId,
            firstName = "John",
            lastName = "Doe",
            middleName = "",
            email = "john@example.com",
            phoneNumber = "1234567890",
            level = "1",
            language = "en",
            gender = "Male",
            dob = "2000-01-01",
            onSuccess = onSuccessMock
        )

        // Assert
        verify(exactly = 0) { onSuccessMock.invoke() }
        verify { Utilities.toast(mockContext, "User details update failed") }
    }
}

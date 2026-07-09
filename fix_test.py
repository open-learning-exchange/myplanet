import re

with open('app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryImplTest.kt', 'r') as file:
    content = file.read()

test_block = """
    @Test
    fun `getUserById delegates to userRepository`() = testScope.runTest {
        val testUserId = "test_user_123"
        val mockUser = mockk<RealmUser>()

        coEvery { userRepository.getUserById(testUserId) } returns mockUser

        val user = repository.getUserById(testUserId)
        assertEquals(mockUser, user)
    }
}
"""

content = content.replace("io.mockk.verify(exactly = 1) { mockLabels.remove(\"testLabel\") }\n    }\n}", "io.mockk.verify(exactly = 1) { mockLabels.remove(\"testLabel\") }\n    }\n" + test_block)

with open('app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryImplTest.kt', 'w') as file:
    file.write(content)

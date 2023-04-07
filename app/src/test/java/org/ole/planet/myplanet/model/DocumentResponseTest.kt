package org.ole.planet.myplanet.model

import junit.framework.TestCase

class DocumentResponseTest : TestCase() {
    private lateinit var response: DocumentResponse


    public override fun setUp() {
        super.setUp()
        // Create a new DocumentResponse object for testing
        response = DocumentResponse()
        response.total_rows = 10.toString()
        response.offset = 5.toString()

    }

    fun testGetTotal_rows() {
        // Test that the getTotal_rows() method returns the correct value
        assertEquals(10, response.total_rows.toInt())
    }

    fun testGetOffset() {
        // Test that the getOffset() method returns the correct value
        assertEquals(5, response.offset.toInt())
    }

}
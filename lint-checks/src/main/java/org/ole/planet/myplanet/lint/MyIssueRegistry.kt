package org.ole.planet.myplanet.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class MyIssueRegistry : IssueRegistry() {
    override val issues = listOf(HardcodedCodeDetector.ISSUE)
    override val api = CURRENT_API
    override val vendor = Vendor(
        vendorName = "myPlanet",
        feedbackUrl = "https://github.com/open-learning-exchange/myplanet/issues",
        contact = "github"
    )
}

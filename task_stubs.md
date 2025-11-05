Issue #8908: adopting a survey malfunction – 90
Problem: Adopting a survey and then navigating back to the list shows the survey as unadopted, but the button still says "record survey."
Solution: Ensure the survey's "adopted" state is correctly persisted and reflected in the UI.

Fix instructions:
1.  Verify that the survey's "adopted" state is correctly saved to the database.
2.  Ensure the UI correctly reflects the "adopted" state when the user navigates back to the survey list.
3.  Commit and push changes.
4.  Open a PR titled "adopting a survey malfunction (fixes #8908)" with "fixes #8908" followed by a summary of the solution.
5.  Do not run `./gradlew` test.

Issue #8906: Matrix question auto-selects previous answer when navigating between answered and unanswered items – 85
Problem: When navigating between matrix questions, previously selected answers are incorrectly retained.
Solution: Clear the selected answer when navigating to a question that has not been answered yet.

Fix instructions:
1.  Implement logic to clear the selected answer in a matrix question when the user navigates to it and it hasn't been answered yet.
2.  Commit and push changes.
3.  Open a PR titled "Matrix question auto-selects previous answer when navigating between answered and unanswered items (fixes #8906)" with "fixes #8906" followed by a summary of the solution.
4.  Do not run `./gradlew` test.

Issue #8876: survey: time taken not updating after taking a survey – 80
Problem: The time taken for a survey is not updating after the user completes it.
Solution: Ensure the time taken is correctly calculated and updated in the UI after a survey is completed.

Fix instructions:
1.  Verify that the time taken is correctly calculated after a survey is completed.
2.  Update the UI to display the correct time taken.
3.  Commit and push changes.
4.  Open a PR titled "survey: time taken not updating after taking a survey (fixes #8876)" with "fixes #8876" followed by a summary of the solution.
5.  Do not run `./gradlew` test.

Issue #8866: teams: blank screen and delay after submitting the survey – 75
Problem: A blank screen and delay occur after submitting a survey in a team context. The provided stack trace indicates an ANR related to `okhttp3`.
Solution: Move the network request for uploading the exam result to a background thread to avoid blocking the main thread.

Fix instructions:
1.  Refactor the code to perform the exam result upload in a background thread.
2.  Commit and push changes.
3.  Open a PR titled "teams: blank screen and delay after submitting the survey (fixes #8866)" with "fixes #8866" followed by a summary of the solution.
4.  Do not run `./gradlew` test.

Issue #8850: submissions couchdb failed updates – 70
Problem: Repeated/multiple PUT requests to the `submissions` database are causing it to inflate. This is likely due to incorrect handling of `_rev` fields.
Solution: Ensure that when updating existing documents, the correct `_rev` field is included in the request.

Fix instructions:
1.  Investigate the code responsible for updating documents in the `submissions` database.
2.  Ensure that the `_rev` field is correctly included in all update requests.
3.  Commit and push changes.
4.  Open a PR titled "submissions couchdb failed updates (fixes #8850)" with "fixes #8850" followed by a summary of the solution.
5.  Do not run `./gradlew` test.

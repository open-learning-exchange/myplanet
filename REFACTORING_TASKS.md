# Refactoring Tasks

This document outlines ongoing and future refactoring tasks for the myPlanet application.

## Guidelines

### Team Membership Checks

*   **Rule**: Always use `TeamsRepository.isMember(userId, teamId)` to check if a user is a member of a team.
*   **Reason**: Centralizes membership logic, ensuring consistency and correctness across the application. It handles edge cases (like handling `docType` correctly) and abstraction over the data source.
*   **Do Not**: Query `RealmMyTeam` directly in UI components to check for membership.

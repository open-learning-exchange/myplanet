package org.ole.planet.myplanet.repository

interface TeamsRepository :
    TeamMembershipRepository,
    TeamTaskRepository,
    TeamResourceRepository,
    TeamReportRepository

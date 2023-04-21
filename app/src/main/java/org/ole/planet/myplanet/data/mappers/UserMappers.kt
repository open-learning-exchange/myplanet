package org.ole.planet.myplanet.data.mappers

import org.ole.planet.myplanet.domain.models.Leader
import org.ole.planet.myplanet.model.RealmUserModel

fun RealmUserModel.toLeader(): Leader =
    Leader(id = id, name = this.toString(), email =  email)
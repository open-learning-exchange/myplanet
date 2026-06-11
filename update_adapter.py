import re

with open("app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt", "r") as f:
    content = f.read()

search_block = """            val sharedChildren = if (isCommunityShared) setOf(context.getString(R.string.community)) else emptySet()
            expandableDetailList = getData() as HashMap<String, List<String>>
            expandableTitleList = ArrayList(expandableDetailList.keys)
            expandableListAdapter = ChatShareTargetAdapter(context, expandableTitleList, expandableDetailList, sharedChildren)
            chatShareDialogBinding.listView.setAdapter(expandableListAdapter)

            chatShareDialogBinding.listView.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
                if (expandableTitleList[groupPosition] == context.getString(R.string.share_with_team_enterprise)) {
                    val section = expandableDetailList[expandableTitleList[groupPosition]]?.get(childPosition)
                    if (section == context.getString(R.string.teams)) {
                        showGrandChildRecyclerView(shareTargets.teams, context.getString(R.string.teams), item, sharedIds)
                    } else {
                        showGrandChildRecyclerView(shareTargets.enterprises, context.getString(R.string.enterprises), item, sharedIds)
                    }
                } else if (!isCommunityShared) {
                    showEditTextAndShareButton(shareTargets.community, context.getString(R.string.community), item)
                }
                dialog?.dismiss()
                false
            }"""

replace_block = """            val sharedChildren = if (isCommunityShared) setOf(context.getString(R.string.community)) else emptySet()
            expandableDetailList = getData() as HashMap<String, List<String>>
            expandableTitleList = ArrayList(expandableDetailList.keys)

            chatShareDialogBinding.recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            expandableListAdapter = ChatShareTargetAdapter(context, sharedChildren) { groupTitle, childTitle ->
                if (groupTitle == context.getString(R.string.share_with_team_enterprise)) {
                    if (childTitle == context.getString(R.string.teams)) {
                        showGrandChildRecyclerView(shareTargets.teams, context.getString(R.string.teams), item, sharedIds)
                    } else {
                        showGrandChildRecyclerView(shareTargets.enterprises, context.getString(R.string.enterprises), item, sharedIds)
                    }
                } else if (!isCommunityShared) {
                    showEditTextAndShareButton(shareTargets.community, context.getString(R.string.community), item)
                }
                dialog?.dismiss()
            }
            chatShareDialogBinding.recyclerView.adapter = expandableListAdapter
            expandableListAdapter.submitData(expandableTitleList, expandableDetailList)"""

content = content.replace(search_block, replace_block)

with open("app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt", "w") as f:
    f.write(content)

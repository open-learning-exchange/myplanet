package org.ole.planet.myplanet.ui.notifications

import android.text.Html
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowNotificationHeaderBinding
import org.ole.planet.myplanet.databinding.RowNotificationsBinding
import org.ole.planet.myplanet.model.Notification
import org.ole.planet.myplanet.model.NotificationListItem
import org.ole.planet.myplanet.utils.DiffUtils

class NotificationsAdapter(
    private val onMarkAsReadClick: (String) -> Unit,
    private val onNotificationClick: (Notification) -> Unit,
    private val onToggleSelection: (String) -> Unit,
    private val onToggleGroupExpansion: (String) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ListAdapter<NotificationListItem, RecyclerView.ViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { old, new ->
            when {
                old is NotificationListItem.Header && new is NotificationListItem.Header -> old.type == new.type
                old is NotificationListItem.Item && new is NotificationListItem.Item -> old.notification.id == new.notification.id
                else -> false
            }
        },
        areContentsTheSame = { old, new -> old == new }
    )
) {

    private var dateFormatter: DateTimeFormatter? = null
    private var lastLocale: Locale? = null
    private val parsedHtmlCache = LruCache<String, CharSequence>(100)

    private fun getDateFormatter(): DateTimeFormatter {
        val currentLocale = Locale.getDefault()
        val cached = dateFormatter
        if (cached != null && lastLocale == currentLocale) {
            return cached
        }
        return DateTimeFormatter.ofPattern("MMM d, yyyy", currentLocale).withZone(ZoneId.systemDefault()).also {
            dateFormatter = it
            lastLocale = currentLocale
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is NotificationListItem.Header -> VIEW_TYPE_HEADER
        is NotificationListItem.Item -> VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                RowNotificationHeaderBinding.inflate(inflater, parent, false),
                onToggleGroupExpansion
            )
            else -> ItemViewHolder(
                RowNotificationsBinding.inflate(inflater, parent, false),
                onMarkAsReadClick,
                onNotificationClick,
                onToggleSelection
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NotificationListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is NotificationListItem.Item -> (holder as ItemViewHolder).bind(item)
        }
    }

    class HeaderViewHolder(
        private val binding: RowNotificationHeaderBinding,
        private val onToggleGroupExpansion: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: NotificationListItem.Header) {
            binding.tvHeaderLabel.text = header.label
            binding.ivHeaderIcon.setImageResource(iconResFor(header.type))
            if (header.unreadCount > 0) {
                binding.tvUnreadBadge.visibility = View.VISIBLE
                binding.tvUnreadBadge.text = header.unreadCount.toString()
            } else {
                binding.tvUnreadBadge.visibility = View.GONE
            }
            binding.ivExpand.rotation = if (header.isExpanded) 0f else 180f
            binding.root.setOnClickListener { onToggleGroupExpansion(header.type) }
        }
    }

    inner class ItemViewHolder(
        private val binding: RowNotificationsBinding,
        private val onMarkAsReadClick: (String) -> Unit,
        private val onNotificationClick: (Notification) -> Unit,
        private val onToggleSelection: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationListItem.Item) {
            val notification = item.notification
            val rawText = notification.formattedText.toString()
            var titleText = parsedHtmlCache.get(rawText)
            if (titleText == null) {
                titleText = Html.fromHtml(
                    rawText,
                    Html.FROM_HTML_MODE_LEGACY
                )
                parsedHtmlCache.put(rawText, titleText)
            }
            binding.title.text = titleText
            binding.timestamp.text = formatRelativeTime(notification.createdAt)
            binding.root.alpha = if (notification.isRead) 0.6f else 1.0f

            if (item.isSelectionMode) {
                binding.cbSelect.visibility = View.VISIBLE
                binding.cbSelect.isChecked = item.isSelected
                binding.btnMarkAsRead.visibility = View.GONE
                binding.root.setOnClickListener { onToggleSelection(notification.id) }
                binding.root.setOnLongClickListener(null)
            } else {
                binding.cbSelect.visibility = View.GONE
                if (notification.isRead) {
                    binding.btnMarkAsRead.visibility = View.GONE
                    binding.btnMarkAsRead.setOnClickListener(null)
                } else {
                    binding.btnMarkAsRead.visibility = View.VISIBLE
                    binding.btnMarkAsRead.setOnClickListener { onMarkAsReadClick(notification.id) }
                }
                binding.root.setOnClickListener { onNotificationClick(notification) }
                binding.root.setOnLongClickListener {
                    onToggleSelection(notification.id)
                    true
                }
            }
        }

        private fun formatRelativeTime(createdAt: Long): String {
            val diff = now() - createdAt
            val context = binding.root.context
            return when {
                diff < 60_000L -> context.getString(R.string.just_now)
                diff < 3_600_000L -> context.getString(R.string.minutes_ago, diff / 60_000L)
                diff < 86_400_000L -> context.getString(R.string.hours_ago, diff / 3_600_000L)
                diff < 172_800_000L -> context.getString(R.string.yesterday)
                diff < 604_800_000L -> context.getString(R.string.days_ago, diff / 86_400_000L)
                else -> getDateFormatter().format(Instant.ofEpochMilli(createdAt))
            }
        }
    }
}

private val ICON_BY_TYPE = mapOf(
    "join_request" to R.drawable.ic_join_request,
    "team_join" to R.drawable.ic_activity,
    "task" to R.drawable.ic_date,
    "chat" to R.drawable.ic_mic,
    "voice_reply" to R.drawable.ic_send,
    "resource" to R.drawable.ic_folder,
    "storage" to R.drawable.ic_warn
)

internal fun iconResFor(type: String): Int =
    ICON_BY_TYPE[type.lowercase(Locale.ROOT)] ?: R.drawable.ic_notifications

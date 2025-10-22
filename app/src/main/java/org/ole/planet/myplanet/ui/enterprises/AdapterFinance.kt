package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowFinanceBinding
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.ui.enterprises.AdapterFinance.ViewHolderFinance
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class AdapterFinance(
    private val context: Context,
    list: List<RealmMyTeam>,
) : RecyclerView.Adapter<ViewHolderFinance>() {
    private lateinit var rowFinanceBinding: RowFinanceBinding
    private val balances = mutableListOf<Int>()
    private var list: List<RealmMyTeam> = list.toList()

    init {
        recomputeBalances()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderFinance {
        rowFinanceBinding = RowFinanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderFinance(rowFinanceBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderFinance, position: Int) {
        val item = list[position]
        rowFinanceBinding.date.text = formatDate(item.date, "MMM dd, yyyy")
        rowFinanceBinding.note.text = item.description
        if (TextUtils.equals(item.type?.lowercase(Locale.getDefault()), "debit")) {
            rowFinanceBinding.debit.text = context.getString(R.string.number_placeholder, item.amount)
            rowFinanceBinding.credit.text = context.getString(R.string.message_placeholder, " -")
            rowFinanceBinding.credit.setTextColor(Color.BLACK)
        } else {
            rowFinanceBinding.credit.text = context.getString(R.string.number_placeholder, item.amount)
            rowFinanceBinding.debit.text = context.getString(R.string.message_placeholder, " -")
            rowFinanceBinding.debit.setTextColor(Color.BLACK)
        }
        rowFinanceBinding.balance.text = getBalance(position)
        updateBackgroundColor(rowFinanceBinding.llayout, position)
    }

    private fun getBalance(position: Int): String {
        return balances.getOrNull(position)?.toString() ?: ""
    }

    fun updateData(results: List<RealmMyTeam>) {
        list = results.toList()
        recomputeBalances()
    }

    override fun getItemCount(): Int {
        return list.size
    }

    private fun recomputeBalances() {
        balances.clear()
        var balance = 0
        for (team in list) {
            balance += if ("debit".equals(team.type, ignoreCase = true)) {
                -team.amount
            } else {
                team.amount
            }
            balances.add(balance)
        }
    }

    private fun updateBackgroundColor(layout: LinearLayout, position: Int) {
        if (position % 2 < 1) {
            val border = GradientDrawable()
            border.setColor(-0x1) //white background
            border.setStroke(1, ContextCompat.getColor(context, R.color.black_overlay))
            border.gradientType = GradientDrawable.LINEAR_GRADIENT
            val layers = arrayOf<Drawable>(border)
            val layerDrawable = LayerDrawable(layers)
            layerDrawable.setLayerInset(0, -10, 0, -10, 0)
            layout.background = layerDrawable
        }
    }

    class ViewHolderFinance(rowFinanceBinding: RowFinanceBinding) : RecyclerView.ViewHolder(
        rowFinanceBinding.root
    )
}
